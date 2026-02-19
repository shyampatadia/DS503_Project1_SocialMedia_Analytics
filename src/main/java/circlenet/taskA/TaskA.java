package circlenet.taskA;

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

public class TaskA {
    public static class TaskAMapper extends Mapper<LongWritable, Text, Text, IntWritable>{
        private final static IntWritable one = new IntWritable(1);
        private Text hobby = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException{
            String line = value.toString();

            String[] fields = line.split(",");
            if(fields.length == 5){
                hobby.set(fields[4]);
                context.write(hobby,one);
            }
        }
    }

    public static class TaskAReducer extends Reducer<Text, IntWritable, Text, IntWritable>{
        private IntWritable result = new IntWritable();

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException{
            int sum = 0;

            for (IntWritable val : values) {
                sum += val.get();
            }

            result.set(sum);
            context.write(key, result);
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 2) {
            System.err.println("Usage: TaskA <in> <out>");
            System.exit(-1);
        }
        System.out.println("Arg 0: " + args[0]);
        System.out.println("Arg 1: " + args[1]);

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "TaskA - Hobby Frequency");

        job.setJarByClass(TaskA.class);
        job.setMapperClass(TaskAMapper.class);
        job.setReducerClass(TaskAReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        boolean success = JobTimer.run(job, "TaskA");
        JobTimer.total("TaskA", totalStart);

        System.exit(success ? 0: 1);
    }


}
