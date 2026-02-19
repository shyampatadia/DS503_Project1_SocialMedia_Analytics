package circlenet.taskD;

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
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TaskDSimple {
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

    public static class FollowsMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private static final Text OUT = new Text("F,1");

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                outKey.set(f[2].trim());
                context.write(outKey, OUT);
            }
        }
    }

    public static class JoinReducer extends Reducer<Text, Text, Text, IntWritable> {
        private final IntWritable out = new IntWritable();

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
                out.set(count);
                context.write(new Text(key.toString() + "," + nick), out);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 3) {
            System.err.println("Usage: TaskDSimple <pages_in> <follows_in> <out>");
            System.exit(1);
        }
        Job job = Job.getInstance(new Configuration(), "TaskDSimple");
        job.setJarByClass(TaskDSimple.class);
        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, PageMapper.class);
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, FollowsMapper.class);
        job.setReducerClass(JoinReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileOutputFormat.setOutputPath(job, new Path(args[2]));
        boolean ok = JobTimer.run(job, "TaskDSimple");
        JobTimer.total("TaskDSimple", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
