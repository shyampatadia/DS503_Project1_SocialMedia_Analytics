package circlenet.taskB;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TaskBSimple {
    public static class AccessCountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private static final IntWritable ONE = new IntWritable(1);
        private final Text pageId = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                pageId.set(f[2].trim());
                context.write(pageId, ONE);
            }
        }
    }

    public static class SumReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private final IntWritable out = new IntWritable();

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int s = 0;
            for (IntWritable v : values) {
                s += v.get();
            }
            out.set(s);
            context.write(key, out);
        }
    }

    public static class PageMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outVal = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                outKey.set(f[0].trim());
                outVal.set("P," + f[1].trim() + "," + f[2].trim());
                context.write(outKey, outVal);
            }
        }
    }

    public static class CountMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outVal = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.splitTab(value.toString());
            if (f.length >= 2) {
                outKey.set(f[0].trim());
                outVal.set("C," + f[1].trim());
                context.write(outKey, outVal);
            }
        }
    }

    public static class JoinReducer extends Reducer<Text, Text, NullWritable, Text> {
        private final Text out = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String nick = "";
            String job = "";
            int count = 0;
            for (Text t : values) {
                String[] p = CsvUtils.split(t.toString());
                if (p.length >= 2 && "P".equals(p[0])) {
                    nick = p[1];
                    job = p.length > 2 ? p[2] : "";
                } else if (p.length >= 2 && "C".equals(p[0])) {
                    count = CsvUtils.toInt(p[1], 0);
                }
            }
            out.set(count + "," + key.toString() + "," + nick + "," + job);
            context.write(NullWritable.get(), out);
        }
    }

    public static class TopMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            context.write(NullWritable.get(), value);
        }
    }

    public static class TopReducer extends Reducer<NullWritable, Text, NullWritable, Text> {
        private final TreeMap<Long, String> top = new TreeMap<Long, String>();
        private long seq = 0L;

        @Override
        protected void reduce(NullWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            for (Text t : values) {
                String[] f = CsvUtils.split(t.toString());
                if (f.length >= 4) {
                    int count = CsvUtils.toInt(f[0], 0);
                    long keyRank = (count * 1000000L) + (seq++);
                    top.put(keyRank, f[1] + "," + f[2] + "," + f[3] + "," + count);
                    if (top.size() > 10) {
                        top.pollFirstEntry();
                    }
                }
            }
            for (Map.Entry<Long, String> e : top.descendingMap().entrySet()) {
                context.write(NullWritable.get(), new Text(e.getValue()));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 5) {
            System.err.println("Usage: TaskBSimple <activity_in> <pages_in> <tmp_count_out> <tmp_join_out> <out>");
            System.exit(1);
        }
        Configuration conf = new Configuration();

        Job job1 = Job.getInstance(conf, "TaskB Simple - Count Access");
        job1.setJarByClass(TaskBSimple.class);
        job1.setMapperClass(AccessCountMapper.class);
        job1.setReducerClass(SumReducer.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job1, new Path(args[0]));
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        if (!JobTimer.run(job1, "TaskBSimple-job1-count")) {
            JobTimer.total("TaskBSimple", totalStart);
            System.exit(1);
        }

        Job job2 = Job.getInstance(conf, "TaskB Simple - Join");
        job2.setJarByClass(TaskBSimple.class);
        job2.setReducerClass(JoinReducer.class);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(Text.class);
        job2.setOutputKeyClass(NullWritable.class);
        job2.setOutputValueClass(Text.class);
        MultipleInputs.addInputPath(job2, new Path(args[1]), TextInputFormat.class, PageMapper.class);
        MultipleInputs.addInputPath(job2, new Path(args[2]), TextInputFormat.class, CountMapper.class);
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        if (!JobTimer.run(job2, "TaskBSimple-job2-join")) {
            JobTimer.total("TaskBSimple", totalStart);
            System.exit(1);
        }

        Job job3 = Job.getInstance(conf, "TaskB Simple - Top10");
        job3.setJarByClass(TaskBSimple.class);
        job3.setMapperClass(TopMapper.class);
        job3.setReducerClass(TopReducer.class);
        job3.setNumReduceTasks(1);
        job3.setMapOutputKeyClass(NullWritable.class);
        job3.setMapOutputValueClass(Text.class);
        job3.setOutputKeyClass(NullWritable.class);
        job3.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job3, new Path(args[3]));
        FileOutputFormat.setOutputPath(job3, new Path(args[4]));
        boolean ok = JobTimer.run(job3, "TaskBSimple-job3-top10");
        JobTimer.total("TaskBSimple", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
