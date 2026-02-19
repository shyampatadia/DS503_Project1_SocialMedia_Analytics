package circlenet.taskG;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
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

public class TaskGOptimized {
    public static class UserMaxMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private final Text outKey = new Text();
        private final IntWritable outVal = new IntWritable();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 5) {
                int by = CsvUtils.toInt(f[1], -1);
                int t = CsvUtils.toInt(f[4], -1);
                if (by > 0 && t >= 0) {
                    outKey.set(String.valueOf(by));
                    outVal.set(t);
                    context.write(outKey, outVal);
                }
            }
        }
    }

    public static class MaxReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int max = Integer.MIN_VALUE;
            for (IntWritable v : values) {
                if (v.get() > max) {
                    max = v.get();
                }
            }
            context.write(key, new IntWritable(max == Integer.MIN_VALUE ? 0 : max));
        }
    }

    public static class OutdatedMapJoin extends Mapper<LongWritable, Text, NullWritable, Text> {
        private final Map<String, Integer> userLast = new HashMap<String, Integer>();
        private int threshold;
        private final Text out = new Text();

        @Override
        protected void setup(Context context) throws IOException {
            threshold = context.getConfiguration().getInt("task.g.threshold", 0);
            URI[] files = context.getCacheFiles();
            if (files == null) {
                return;
            }
            FileSystem fs = FileSystem.get(context.getConfiguration());
            for (URI u : files) {
                BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(u.getPath()))));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = CsvUtils.splitTab(line);
                    if (p.length >= 2) {
                        userLast.put(p[0].trim(), CsvUtils.toInt(p[1], 0));
                    }
                }
                br.close();
            }
        }

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 2) {
                String id = f[0].trim();
                String nick = f[1].trim();
                Integer last = userLast.get(id);
                if (last == null || last < threshold) {
                    out.set(id + "," + nick);
                    context.write(NullWritable.get(), out);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 4) {
            System.err.println("Usage: TaskGOptimized <pages_in> <activity_in> <tmp_user_max_out> <out>");
            System.exit(1);
        }
        Configuration conf = new Configuration();

        Job job1 = Job.getInstance(conf, "TaskG Optimized - User Max");
        job1.setJarByClass(TaskGOptimized.class);
        job1.setMapperClass(UserMaxMapper.class);
        job1.setCombinerClass(MaxReducer.class);
        job1.setReducerClass(MaxReducer.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job1, new Path(args[1]));
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        if (!JobTimer.run(job1, "TaskGOptimized-job1-usermax")) {
            JobTimer.total("TaskGOptimized", totalStart);
            System.exit(1);
        }

        FileSystem fs = FileSystem.get(conf);
        BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(args[2] + "/part-r-00000"))));
        String line;
        int globalMax = 0;
        while ((line = br.readLine()) != null) {
            String[] p = CsvUtils.splitTab(line);
            if (p.length >= 2) {
                int t = CsvUtils.toInt(p[1], 0);
                if (t > globalMax) {
                    globalMax = t;
                }
            }
        }
        br.close();

        int threshold = globalMax - 2160;
        Configuration conf2 = new Configuration();
        conf2.setInt("task.g.threshold", threshold);
        Job job2 = Job.getInstance(conf2, "TaskG Optimized - Map Join");
        job2.setJarByClass(TaskGOptimized.class);
        job2.setMapperClass(OutdatedMapJoin.class);
        job2.setNumReduceTasks(0);
        job2.setOutputKeyClass(NullWritable.class);
        job2.setOutputValueClass(Text.class);
        job2.addCacheFile(new Path(args[2] + "/part-r-00000").toUri());
        FileInputFormat.addInputPath(job2, new Path(args[0]));
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        boolean ok = JobTimer.run(job2, "TaskGOptimized-job2-mapjoin");
        JobTimer.total("TaskGOptimized", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
