package circlenet.taskH;

import circlenet.common.CsvUtils;
import circlenet.common.JobTimer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TaskHOptimized {
    public static class SameRegionGraphMapper extends Mapper<LongWritable, Text, Text, Text> {
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
                    outKey.set(a);
                    outVal.set("O," + b);
                    context.write(outKey, outVal);

                    outKey.set(b);
                    outVal.set("I," + a);
                    context.write(outKey, outVal);
                }
            }
        }
    }

    public static class OneWayReducer extends Reducer<Text, Text, NullWritable, Text> {
        private final Map<String, String> nickById = new HashMap<String, String>();

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
                    if (p.length >= 2) {
                        nickById.put(p[0].trim(), p[1].trim());
                    }
                }
                br.close();
            }
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Set<String> outSet = new HashSet<String>();
            Set<String> inSet = new HashSet<String>();
            for (Text t : values) {
                String[] p = CsvUtils.split(t.toString());
                if (p.length >= 2 && "O".equals(p[0])) {
                    outSet.add(p[1]);
                } else if (p.length >= 2 && "I".equals(p[0])) {
                    inSet.add(p[1]);
                }
            }

            boolean oneWayFound = false;
            for (String followed : outSet) {
                if (!inSet.contains(followed)) {
                    oneWayFound = true;
                    break;
                }
            }

            if (oneWayFound) {
                String id = key.toString();
                String nick = nickById.containsKey(id) ? nickById.get(id) : "";
                context.write(NullWritable.get(), new Text(id + "," + nick));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();
        if (args.length != 3) {
            System.err.println("Usage: TaskHOptimized <pages_file> <follows_in> <out>");
            System.exit(1);
        }

        Job job = Job.getInstance(new Configuration(), "TaskHOptimized");
        job.setJarByClass(TaskHOptimized.class);
        job.setMapperClass(SameRegionGraphMapper.class);
        job.setReducerClass(OneWayReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.addCacheFile(new Path(args[0]).toUri());
        FileInputFormat.addInputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, new Path(args[2]));

        boolean ok = JobTimer.run(job, "TaskHOptimized");
        JobTimer.total("TaskHOptimized", totalStart);
        System.exit(ok ? 0 : 1);
    }
}
