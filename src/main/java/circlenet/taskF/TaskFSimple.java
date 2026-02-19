package circlenet.taskF;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
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

public class TaskFSimple {
    public static class PageMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outVal = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 2) {
                outKey.set(f[0].trim());
                outVal.set("P," + f[1].trim());
                context.write(outKey, outVal);
            }
        }
    }

    public static class FollowMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private static final Text ONE = new Text("F,1");

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                outKey.set(f[2].trim());
                context.write(outKey, ONE);
            }
        }
    }

    public static class JoinCountReducer extends Reducer<Text, Text, NullWritable, Text> {
        private final Text out = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String nick = null;
            int count = 0;
            for (Text t : values) {
                String[] p = CsvUtils.split(t.toString());
                if (p.length >= 2 && "P".equals(p[0])) {
                    nick = p[1];
                } else if (p.length >= 2 && "F".equals(p[0])) {
                    count += CsvUtils.toInt(p[1], 0);
                }
            }
            if (nick != null) {
                out.set(key.toString() + "," + nick + "," + count);
                context.write(NullWritable.get(), out);
            }
        }
    }

    public static class AvgMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private static final Text K = new Text("avg");
        private final IntWritable out = new IntWritable();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                out.set(CsvUtils.toInt(f[2], 0));
                context.write(K, out);
            }
        }
    }

    public static class AvgReducer extends Reducer<Text, IntWritable, NullWritable, DoubleWritable> {
        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            long sum = 0;
            long cnt = 0;
            for (IntWritable v : values) {
                sum += v.get();
                cnt++;
            }
            double avg = cnt == 0 ? 0.0 : (sum * 1.0) / cnt;
            context.write(NullWritable.get(), new DoubleWritable(avg));
        }
    }

    public static class FilterMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
        private double avg;
        private final Text out = new Text();

        @Override
        protected void setup(Context context) {
            avg = context.getConfiguration().getDouble("task.f.avg", 0.0);
        }

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                int count = CsvUtils.toInt(f[2], 0);
                if (count > avg) {
                    out.set(f[0] + "," + f[1] + "," + count);
                    context.write(NullWritable.get(), out);
                }
            }
        }
    }

    public static class ReadAvgReducer extends Reducer<NullWritable, Text, NullWritable, Text> {
        @Override
        protected void reduce(NullWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            for (Text t : values) {
                context.write(NullWritable.get(), t);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 5) {
            System.err.println("Usage: TaskFSimple <pages_in> <follows_in> <tmp_join_out> <tmp_avg_out> <out>");
            System.exit(1);
        }
        Configuration conf = new Configuration();
        Job job1 = Job.getInstance(conf, "TaskF Simple - JoinCount");
        job1.setJarByClass(TaskFSimple.class);
        MultipleInputs.addInputPath(job1, new Path(args[0]), TextInputFormat.class, PageMapper.class);
        MultipleInputs.addInputPath(job1, new Path(args[1]), TextInputFormat.class, FollowMapper.class);
        job1.setReducerClass(JoinCountReducer.class);
        job1.setMapOutputKeyClass(Text.class);
        job1.setMapOutputValueClass(Text.class);
        job1.setOutputKeyClass(NullWritable.class);
        job1.setOutputValueClass(Text.class);
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        if (!JobTimer.run(job1, "TaskFSimple-job1-joincount")) {
            JobTimer.total("TaskFSimple", totalStart);
            System.exit(1);
        }

        Job job2 = Job.getInstance(conf, "TaskF Simple - Average");
        job2.setJarByClass(TaskFSimple.class);
        job2.setMapperClass(AvgMapper.class);
        job2.setReducerClass(AvgReducer.class);
        job2.setNumReduceTasks(1);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(IntWritable.class);
        job2.setOutputKeyClass(NullWritable.class);
        job2.setOutputValueClass(DoubleWritable.class);
        FileInputFormat.addInputPath(job2, new Path(args[2]));
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        if (!JobTimer.run(job2, "TaskFSimple-job2-average")) {
            JobTimer.total("TaskFSimple", totalStart);
            System.exit(1);
        }

        FileSystem fs = FileSystem.get(conf);
        List<String> lines = new ArrayList<String>();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(fs.open(new Path(args[3] + "/part-r-00000"))));
        String line;
        while ((line = br.readLine()) != null) {
            lines.add(line);
        }
        br.close();
        double avg = 0.0;
        if (!lines.isEmpty()) {
            String[] p = CsvUtils.splitTab(lines.get(0));
            avg = CsvUtils.toInt(p[p.length - 1], 0);
            try {
                avg = Double.parseDouble(p[p.length - 1].trim());
            } catch (Exception ignored) {
            }
        }

        Configuration conf3 = new Configuration();
        conf3.setDouble("task.f.avg", avg);
        Job job3 = Job.getInstance(conf3, "TaskF Simple - Filter");
        job3.setJarByClass(TaskFSimple.class);
        job3.setMapperClass(FilterMapper.class);
        job3.setReducerClass(ReadAvgReducer.class);
        job3.setNumReduceTasks(1);
        job3.setMapOutputKeyClass(NullWritable.class);
        job3.setMapOutputValueClass(Text.class);
        job3.setOutputKeyClass(NullWritable.class);
        job3.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job3, new Path(args[2]));
        FileOutputFormat.setOutputPath(job3, new Path(args[4]));
        boolean ok = JobTimer.run(job3, "TaskFSimple-job3-filter");
        JobTimer.total("TaskFSimple", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
