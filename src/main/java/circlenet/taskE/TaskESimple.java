package circlenet.taskE;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
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

public class TaskESimple {
    public static class ActivityMapper extends Mapper<LongWritable, Text, IntWritable, Text> {
        private final IntWritable byWho = new IntWritable();
        private final Text page = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                int by = CsvUtils.toInt(f[1], -1);
                int p = CsvUtils.toInt(f[2], -1);
                if (by > 0 && p > 0) {
                    byWho.set(by);
                    page.set("A," + p);
                    context.write(byWho, page);
                }
            }
        }
    }

    public static class PageOwnerMapper extends Mapper<LongWritable, Text, IntWritable, Text> {
        private final IntWritable owner = new IntWritable();
        private static final Text MARKER = new Text("P,1");

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 1) {
                int id = CsvUtils.toInt(f[0], -1);
                if (id > 0) {
                    owner.set(id);
                    context.write(owner, MARKER);
                }
            }
        }
    }

    public static class StatsReducer extends Reducer<IntWritable, Text, IntWritable, Text> {
        private final Text out = new Text();

        @Override
        protected void reduce(IntWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            boolean isOwner = false;
            int total = 0;
            Set<Integer> distinct = new HashSet<Integer>();
            for (Text v : values) {
                String[] p = CsvUtils.split(v.toString());
                if (p.length >= 2 && "P".equals(p[0])) {
                    isOwner = true;
                } else if (p.length >= 2 && "A".equals(p[0])) {
                    total++;
                    distinct.add(CsvUtils.toInt(p[1], -1));
                }
            }
            if (isOwner) {
                out.set(total + "," + distinct.size());
                context.write(key, out);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 3) {
            System.err.println("Usage: TaskESimple <pages_in> <activity_in> <out>");
            System.exit(1);
        }
        Job job = Job.getInstance(new Configuration(), "TaskESimple");
        job.setJarByClass(TaskESimple.class);
        job.setReducerClass(StatsReducer.class);
        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, PageOwnerMapper.class);
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, ActivityMapper.class);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(Text.class);
        FileOutputFormat.setOutputPath(job, new Path(args[2]));
        boolean ok = JobTimer.run(job, "TaskESimple");
        JobTimer.total("TaskESimple", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
