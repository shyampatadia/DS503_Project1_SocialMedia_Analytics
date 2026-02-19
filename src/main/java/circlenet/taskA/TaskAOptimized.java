package circlenet.taskA;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TaskAOptimized {
    public static class MapperA extends Mapper<LongWritable, Text, Text, IntWritable> {
        private static final IntWritable ONE = new IntWritable(1);
        private final Text hobby = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] fields = CsvUtils.split(value.toString());
            if (fields.length >= 5) {
                hobby.set(fields[4].trim());
                if (!fields[4].trim().isEmpty()) {
                    context.write(hobby, ONE);
                }
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

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 2) {
            System.err.println("Usage: TaskAOptimized <pages_in> <out>");
            System.exit(1);
        }
        Job job = Job.getInstance(new Configuration(), "TaskAOptimized");
        job.setJarByClass(TaskAOptimized.class);
        job.setMapperClass(MapperA.class);
        job.setCombinerClass(SumReducer.class);
        job.setReducerClass(SumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        boolean ok = JobTimer.run(job, "TaskAOptimized");
        JobTimer.total("TaskAOptimized", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
