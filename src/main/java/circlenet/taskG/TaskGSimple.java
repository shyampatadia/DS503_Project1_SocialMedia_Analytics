package circlenet.taskG;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TaskGSimple {
    public static class GlobalMaxMapper extends Mapper<LongWritable, Text, NullWritable, IntWritable> {
        private final IntWritable out = new IntWritable();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 5) {
                out.set(CsvUtils.toInt(f[4], -1));
                if (out.get() >= 0) {
                    context.write(NullWritable.get(), out);
                }
            }
        }
    }

    public static class MaxReducer extends Reducer<NullWritable, IntWritable, NullWritable, IntWritable> {
        @Override
        protected void reduce(NullWritable key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int max = Integer.MIN_VALUE;
            for (IntWritable v : values) {
                if (v.get() > max) {
                    max = v.get();
                }
            }
            if (max == Integer.MIN_VALUE) {
                max = 0;
            }
            context.write(NullWritable.get(), new IntWritable(max));
        }
    }

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

    public static class UserMaxReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
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

    public static class MaxMapOutMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outVal = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.splitTab(value.toString());
            if (f.length >= 2) {
                outKey.set(f[0].trim());
                outVal.set("M," + f[1].trim());
                context.write(outKey, outVal);
            }
        }
    }

    public static class OutdatedReducer extends Reducer<Text, Text, NullWritable, Text> {
        private int threshold;

        @Override
        protected void setup(Context context) {
            threshold = context.getConfiguration().getInt("task.g.threshold", 0);
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String nick = null;
            Integer last = null;
            for (Text t : values) {
                String[] p = CsvUtils.split(t.toString());
                if (p.length >= 2 && "P".equals(p[0])) {
                    nick = p[1];
                } else if (p.length >= 2 && "M".equals(p[0])) {
                    last = CsvUtils.toInt(p[1], -1);
                }
            }
            if (nick != null && (last == null || last < threshold)) {
                context.write(NullWritable.get(), new Text(key.toString() + "," + nick));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 5) {
            System.err.println("Usage: TaskGSimple <pages_in> <activity_in> <tmp_global_max_out> <tmp_user_max_out> <out>");
            System.exit(1);
        }
        Configuration conf = new Configuration();

        Job job1 = Job.getInstance(conf, "TaskG Simple - Global Max");
        job1.setJarByClass(TaskGSimple.class);
        job1.setMapperClass(GlobalMaxMapper.class);
        job1.setReducerClass(MaxReducer.class);
        job1.setNumReduceTasks(1);
        job1.setMapOutputKeyClass(NullWritable.class);
        job1.setMapOutputValueClass(IntWritable.class);
        job1.setOutputKeyClass(NullWritable.class);
        job1.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job1, new Path(args[1]));
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        if (!JobTimer.run(job1, "TaskGSimple-job1-globalmax")) {
            JobTimer.total("TaskGSimple", totalStart);
            System.exit(1);
        }

        Job job2 = Job.getInstance(conf, "TaskG Simple - User Max");
        job2.setJarByClass(TaskGSimple.class);
        job2.setMapperClass(UserMaxMapper.class);
        job2.setReducerClass(UserMaxReducer.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job2, new Path(args[1]));
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        if (!JobTimer.run(job2, "TaskGSimple-job2-usermax")) {
            JobTimer.total("TaskGSimple", totalStart);
            System.exit(1);
        }

        FileSystem fs = FileSystem.get(conf);
        BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(args[2] + "/part-r-00000"))));
        String line = br.readLine();
        br.close();
        int globalMax = 0;
        if (line != null) {
            String[] p = CsvUtils.splitTab(line);
            globalMax = CsvUtils.toInt(p[p.length - 1], 0);
        }
        int threshold = globalMax - 2160;

        Configuration conf3 = new Configuration();
        conf3.setInt("task.g.threshold", threshold);
        Job job3 = Job.getInstance(conf3, "TaskG Simple - Outdated");
        job3.setJarByClass(TaskGSimple.class);
        MultipleInputs.addInputPath(job3, new Path(args[0]), TextInputFormat.class, PageMapper.class);
        MultipleInputs.addInputPath(job3, new Path(args[3]), TextInputFormat.class, MaxMapOutMapper.class);
        job3.setReducerClass(OutdatedReducer.class);
        job3.setMapOutputKeyClass(Text.class);
        job3.setMapOutputValueClass(Text.class);
        job3.setOutputKeyClass(NullWritable.class);
        job3.setOutputValueClass(Text.class);
        FileOutputFormat.setOutputPath(job3, new Path(args[4]));
        boolean ok = JobTimer.run(job3, "TaskGSimple-job3-outdated");
        JobTimer.total("TaskGSimple", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
