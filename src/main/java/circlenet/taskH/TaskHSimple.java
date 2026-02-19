package circlenet.taskH;

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

public class TaskHSimple {
    public static class SameRegionEdgeMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Map<String, Integer> regionById = new HashMap<String, Integer>();
        private final Text outKey = new Text();
        private final Text outVal = new Text();

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
                    if (p.length >= 4) {
                        regionById.put(p[0].trim(), CsvUtils.toInt(p[3], -1));
                    }
                }
                br.close();
            }
        }

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.split(value.toString());
            if (f.length >= 3) {
                String a = f[1].trim();
                String b = f[2].trim();
                Integer ra = regionById.get(a);
                Integer rb = regionById.get(b);
                if (ra != null && rb != null && ra.intValue() == rb.intValue() && !a.equals(b)) {
                    String min = a.compareTo(b) < 0 ? a : b;
                    String max = a.compareTo(b) < 0 ? b : a;
                    outKey.set(min + "," + max);
                    outVal.set(a + "->" + b);
                    context.write(outKey, outVal);
                }
            }
        }
    }

    public static class AsymmetricReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String[] pair = CsvUtils.split(key.toString());
            if (pair.length < 2) {
                return;
            }
            String a = pair[0];
            String b = pair[1];
            boolean ab = false;
            boolean ba = false;
            for (Text t : values) {
                String v = t.toString();
                if ((a + "->" + b).equals(v)) {
                    ab = true;
                } else if ((b + "->" + a).equals(v)) {
                    ba = true;
                }
            }
            if (ab ^ ba) {
                String follower = ab ? a : b;
                context.write(new Text(follower), new Text("F"));
            }
        }
    }

    public static class FollowerMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private static final Text OUT = new Text("F");

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] f = CsvUtils.splitTab(value.toString());
            if (f.length >= 1) {
                outKey.set(f[0].trim());
                context.write(outKey, OUT);
            }
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

    public static class JoinReducer extends Reducer<Text, Text, NullWritable, Text> {
        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            boolean hasFollower = false;
            String nick = null;
            for (Text t : values) {
                String v = t.toString();
                if ("F".equals(v)) {
                    hasFollower = true;
                } else {
                    String[] p = CsvUtils.split(v);
                    if (p.length >= 2 && "P".equals(p[0])) {
                        nick = p[1];
                    }
                }
            }
            if (hasFollower && nick != null) {
                context.write(NullWritable.get(), new Text(key.toString() + "," + nick));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 4) {
            System.err.println("Usage: TaskHSimple <pages_file> <follows_in> <tmp_edges_out> <out>");
            System.exit(1);
        }
        Configuration conf = new Configuration();

        Job job1 = Job.getInstance(conf, "TaskH Simple - Same Region Asymmetry");
        job1.setJarByClass(TaskHSimple.class);
        job1.setMapperClass(SameRegionEdgeMapper.class);
        job1.setReducerClass(AsymmetricReducer.class);
        job1.setMapOutputKeyClass(Text.class);
        job1.setMapOutputValueClass(Text.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(Text.class);
        job1.addCacheFile(new Path(args[0]).toUri());
        FileInputFormat.addInputPath(job1, new Path(args[1]));
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        if (!JobTimer.run(job1, "TaskHSimple-job1-asymmetry")) {
            JobTimer.total("TaskHSimple", totalStart);
            System.exit(1);
        }

        Job job2 = Job.getInstance(conf, "TaskH Simple - Join Nickname");
        job2.setJarByClass(TaskHSimple.class);
        MultipleInputs.addInputPath(job2, new Path(args[2]), TextInputFormat.class, FollowerMapper.class);
        MultipleInputs.addInputPath(job2, new Path(args[0]), TextInputFormat.class, PageMapper.class);
        job2.setReducerClass(JoinReducer.class);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(Text.class);
        job2.setOutputKeyClass(NullWritable.class);
        job2.setOutputValueClass(Text.class);
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        boolean ok = JobTimer.run(job2, "TaskHSimple-job2-joinnick");
        JobTimer.total("TaskHSimple", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
