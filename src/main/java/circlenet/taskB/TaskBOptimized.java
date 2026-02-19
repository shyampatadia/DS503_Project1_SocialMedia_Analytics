package circlenet.taskB;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TaskBOptimized {
    public static class AccessCountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private static final IntWritable ONE = new IntWritable(1);
        private final Text outKey = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                outKey.set(f[2].trim());
                context.write(outKey, ONE);
            }
        }
    }

    public static class SumReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private final IntWritable out = new IntWritable();

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable v : values) {
                sum += v.get();
            }
            out.set(sum);
            context.write(key, out);
        }
    }

    public static class TopMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            context.write(NullWritable.get(), value);
        }
    }

    public static class TopJoinReducer extends Reducer<NullWritable, Text, NullWritable, Text> {
        private final TreeMap<Long, String> top = new TreeMap<Long, String>();
        private long seq = 0L;
        private final Map<String, String> pageInfo = new HashMap<String, String>();

        @Override
        protected void setup(Context context) throws IOException {
            URI[] files = context.getCacheFiles();
            if (files == null) {
                return;
            }
            FileSystem fs = FileSystem.get(context.getConfiguration());
            for (URI u : files) {
                BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(u.getPath()))));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = CsvUtils.split(line);
                    if (p.length >= 3) {
                        pageInfo.put(p[0].trim(), p[1].trim() + "," + p[2].trim());
                    }
                }
                br.close();
            }
        }

        @Override
        protected void reduce(NullWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            for (Text v : values) {
                String[] f = CsvUtils.splitTab(v.toString());
                if (f.length >= 2) {
                    int count = CsvUtils.toInt(f[1], 0);
                    String id = f[0].trim();
                    long keyRank = (count * 1000000L) + (seq++);
                    top.put(keyRank, id + "," + count);
                    if (top.size() > 10) {
                        top.pollFirstEntry();
                    }
                }
            }
            for (Map.Entry<Long, String> e : top.descendingMap().entrySet()) {
                String[] pair = CsvUtils.split(e.getValue());
                String id = pair[0];
                String info = pageInfo.containsKey(id) ? pageInfo.get(id) : ",";
                String[] pj = CsvUtils.split(info);
                String nick = pj.length > 0 ? pj[0] : "";
                String job = pj.length > 1 ? pj[1] : "";
                String count = pair.length > 1 ? pair[1] : "0";
                context.write(NullWritable.get(), new Text(id + "," + nick + "," + job + "," + count));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 4) {
            System.err.println("Usage: TaskBOptimized <activity_in> <pages_file> <tmp_count_out> <out>");
            System.exit(1);
        }
        Configuration conf = new Configuration();

        Job job1 = Job.getInstance(conf, "TaskB Optimized - Count");
        job1.setJarByClass(TaskBOptimized.class);
        job1.setMapperClass(AccessCountMapper.class);
        job1.setCombinerClass(SumReducer.class);
        job1.setReducerClass(SumReducer.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job1, new Path(args[0]));
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        if (!JobTimer.run(job1, "TaskBOptimized-job1-count")) {
            JobTimer.total("TaskBOptimized", totalStart);
            System.exit(1);
        }

        Job job2 = Job.getInstance(conf, "TaskB Optimized - Top10 Join");
        job2.setJarByClass(TaskBOptimized.class);
        job2.setMapperClass(TopMapper.class);
        job2.setReducerClass(TopJoinReducer.class);
        job2.setNumReduceTasks(1);
        job2.setMapOutputKeyClass(NullWritable.class);
        job2.setMapOutputValueClass(Text.class);
        job2.setOutputKeyClass(NullWritable.class);
        job2.setOutputValueClass(Text.class);
        job2.addCacheFile(new Path(args[1]).toUri());
        FileInputFormat.addInputPath(job2, new Path(args[2]));
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        boolean ok = JobTimer.run(job2, "TaskBOptimized-job2-top10-join");
        JobTimer.total("TaskBOptimized", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
