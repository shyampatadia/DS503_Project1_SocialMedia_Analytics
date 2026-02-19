package circlenet.taskC;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TaskCSimple {
    public static class MapperC extends Mapper<LongWritable, Text, Text, Text> {
        private String targetHobby;
        private final Text outKey = new Text();
        private final Text outVal = new Text();

        @Override
        protected void setup(Context context) {
            targetHobby = context.getConfiguration().get("task.c.hobby", "").trim();
        }

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 5 && f[4].trim().equalsIgnoreCase(targetHobby)) {
                outKey.set(f[1].trim());
                outVal.set(f[2].trim());
                context.write(outKey, outVal);
            }
        }
    }

    public static class PassReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            for (Text v : values) {
                context.write(key, v);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 3) {
            System.err.println("Usage: TaskCSimple <pages_in> <out> <hobby>");
            System.exit(1);
        }
        Configuration conf = new Configuration();
        conf.set("task.c.hobby", args[2]);
        Job job = Job.getInstance(conf, "TaskCSimple");
        job.setJarByClass(TaskCSimple.class);
        job.setMapperClass(MapperC.class);
        job.setReducerClass(PassReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        boolean ok = JobTimer.run(job, "TaskCSimple");
        JobTimer.total("TaskCSimple", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
