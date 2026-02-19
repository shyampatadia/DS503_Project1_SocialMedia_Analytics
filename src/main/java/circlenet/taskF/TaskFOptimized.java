package circlenet.taskF;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

public class TaskFOptimized {
    public static class CountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
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
            int s = 0;
            for (IntWritable v : values) {
                s += v.get();
            }
            out.set(s);
            context.write(key, out);
        }
    }

    public static class FilterMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
        private final Map<String, Integer> counts = new HashMap<String, Integer>();
        private double avg;
        private final Text out = new Text();

        @Override
        protected void setup(Context context) throws IOException {
            URI[] files = context.getCacheFiles();
            if (files != null) {
                FileSystem fs = FileSystem.get(context.getConfiguration());
                for (URI u : files) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(u.getPath()))));
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] p = CsvUtils.splitTab(line);
                        if (p.length >= 2) {
                            counts.put(p[0].trim(), CsvUtils.toInt(p[1], 0));
                        }
                    }
                    br.close();
                }
            }
            avg = context.getConfiguration().getDouble("task.f.avg", 0.0);
        }

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 2) {
                String id = f[0].trim();
                String nick = f[1].trim();
                int c = counts.containsKey(id) ? counts.get(id) : 0;
                if (c > avg) {
                    out.set(id + "," + nick + "," + c);
                    context.write(NullWritable.get(), out);
                }
            }
        }
    }

    public static class PassReducer extends Reducer<NullWritable, Text, NullWritable, Text> {
        @Override
        protected void reduce(NullWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            for (Text t : values) {
                context.write(NullWritable.get(), t);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 4) {
            System.err.println("Usage: TaskFOptimized <pages_in> <follows_in> <tmp_count_out> <out>");
            System.exit(1);
        }
        Configuration conf = new Configuration();

        Job job1 = Job.getInstance(conf, "TaskF Optimized - Count");
        job1.setJarByClass(TaskFOptimized.class);
        job1.setMapperClass(CountMapper.class);
        job1.setCombinerClass(SumReducer.class);
        job1.setReducerClass(SumReducer.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job1, new Path(args[1]));
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        if (!JobTimer.run(job1, "TaskFOptimized-job1-count")) {
            JobTimer.total("TaskFOptimized", totalStart);
            System.exit(1);
        }

        FileSystem fs = FileSystem.get(conf);
        BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(args[2] + "/part-r-00000"))));
        String line;
        long sum = 0;
        long ownersWithFollowers = 0;
        while ((line = br.readLine()) != null) {
            String[] p = CsvUtils.splitTab(line);
            if (p.length >= 2) {
                sum += CsvUtils.toInt(p[1], 0);
                ownersWithFollowers++;
            }
        }
        br.close();

        long totalPages = 0;
        BufferedReader brPages = new BufferedReader(new InputStreamReader(fs.open(new Path(args[0]))));
        while (brPages.readLine() != null) {
            totalPages++;
        }
        brPages.close();
        double avg = totalPages == 0 ? 0.0 : (sum * 1.0) / totalPages;

        Configuration conf2 = new Configuration();
        conf2.setDouble("task.f.avg", avg);
        Job job2 = Job.getInstance(conf2, "TaskF Optimized - Filter");
        job2.setJarByClass(TaskFOptimized.class);
        job2.setMapperClass(FilterMapper.class);
        job2.setReducerClass(PassReducer.class);
        job2.setNumReduceTasks(1);
        job2.setMapOutputKeyClass(NullWritable.class);
        job2.setMapOutputValueClass(Text.class);
        job2.setOutputKeyClass(NullWritable.class);
        job2.setOutputValueClass(Text.class);
        job2.addCacheFile(new Path(args[2] + "/part-r-00000").toUri());
        FileInputFormat.addInputPath(job2, new Path(args[0]));
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        boolean ok = JobTimer.run(job2, "TaskFOptimized-job2-filter");
        JobTimer.total("TaskFOptimized", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
