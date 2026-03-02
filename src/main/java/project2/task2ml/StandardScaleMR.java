package project2.task2ml;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class StandardScaleMR {

    public static class FeatureStatsWritable implements Writable {
        double sum;
        double sumSq;
        double min;
        double max;
        long count;

        public FeatureStatsWritable() {
            this(0.0, 0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0L);
        }

        public FeatureStatsWritable(double sum, double sumSq, double min, double max, long count) {
            this.sum = sum;
            this.sumSq = sumSq;
            this.min = min;
            this.max = max;
            this.count = count;
        }

        void add(FeatureStatsWritable o) {
            sum += o.sum;
            sumSq += o.sumSq;
            min = Math.min(min, o.min);
            max = Math.max(max, o.max);
            count += o.count;
        }

        @Override
        public void write(DataOutput out) throws IOException {
            out.writeDouble(sum);
            out.writeDouble(sumSq);
            out.writeDouble(min);
            out.writeDouble(max);
            out.writeLong(count);
        }

        @Override
        public void readFields(DataInput in) throws IOException {
            sum = in.readDouble();
            sumSq = in.readDouble();
            min = in.readDouble();
            max = in.readDouble();
            count = in.readLong();
        }
    }

    public static class StatsMapper extends Mapper<LongWritable, Text, IntWritable, FeatureStatsWritable> {
        private final IntWritable outKey = new IntWritable();

        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            String[] p = value.toString().split(",");
            if (p.length < 4) return;

            for (int i = 0; i < 4; i++) {
                double v;
                try {
                    v = Double.parseDouble(p[i].trim());
                } catch (Exception e) {
                    return;
                }
                outKey.set(i);
                ctx.write(outKey, new FeatureStatsWritable(v, v * v, v, v, 1L));
            }
        }
    }

    public static class StatsCombiner extends Reducer<IntWritable, FeatureStatsWritable, IntWritable, FeatureStatsWritable> {
        @Override
        protected void reduce(IntWritable key, Iterable<FeatureStatsWritable> vals, Context ctx) throws IOException, InterruptedException {
            FeatureStatsWritable acc = new FeatureStatsWritable();
            for (FeatureStatsWritable v : vals) acc.add(v);
            ctx.write(key, acc);
        }
    }

    public static class StatsReducer extends Reducer<IntWritable, FeatureStatsWritable, IntWritable, Text> {
        private final Text outVal = new Text();

        @Override
        protected void reduce(IntWritable key, Iterable<FeatureStatsWritable> vals, Context ctx) throws IOException, InterruptedException {
            FeatureStatsWritable acc = new FeatureStatsWritable();
            for (FeatureStatsWritable v : vals) acc.add(v);
            outVal.set(acc.sum + "," + acc.sumSq + "," + acc.min + "," + acc.max + "," + acc.count);
            ctx.write(key, outVal);
        }
    }

    public static class ScaleMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
        private final Text out = new Text();
        private final Map<Integer, double[]> stats = new HashMap<Integer, double[]>();
        private String method;

        @Override
        protected void setup(Context ctx) throws IOException {
            method = ctx.getConfiguration().get("scale.method", "zscore").trim().toLowerCase();
            URI[] files = ctx.getCacheFiles();
            if (files == null || files.length == 0) throw new IOException("No stats file in cache");

            FileSystem fs = FileSystem.get(ctx.getConfiguration());
            for (URI u : files) {
                Path p = new Path(u.getPath());
                if (p.getName().startsWith("_")) continue;
                BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(p)));
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] tab = line.split("\\t");
                        if (tab.length != 2) continue;
                        int idx = Integer.parseInt(tab[0].trim());
                        String[] s = tab[1].split(",");
                        if (s.length < 5) continue;
                        double sum = Double.parseDouble(s[0].trim());
                        double sumSq = Double.parseDouble(s[1].trim());
                        double min = Double.parseDouble(s[2].trim());
                        double max = Double.parseDouble(s[3].trim());
                        long c = Long.parseLong(s[4].trim());
                        double mean = c == 0 ? 0.0 : sum / c;
                        double var = c == 0 ? 0.0 : (sumSq / c) - (mean * mean);
                        if (var < 0.0) var = 0.0;
                        double std = Math.sqrt(var);
                        stats.put(idx, new double[] { mean, std, min, max });
                    }
                } finally {
                    br.close();
                }
            }
            if (stats.size() < 4) {
                throw new IOException("Could not load 4 feature stats from cache");
            }
        }

        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            String[] p = value.toString().split(",");
            if (p.length < 4) return;

            double[] outVals = new double[4];
            for (int i = 0; i < 4; i++) {
                double v;
                try {
                    v = Double.parseDouble(p[i].trim());
                } catch (Exception e) {
                    return;
                }
                double[] st = stats.get(i);
                if (st == null) return;

                if ("minmax".equals(method)) {
                    double min = st[2], max = st[3];
                    outVals[i] = (max == min) ? 0.0 : (v - min) / (max - min);
                } else {
                    double mean = st[0], std = st[1];
                    outVals[i] = (std == 0.0) ? 0.0 : (v - mean) / std;
                }
            }
            out.set(outVals[0] + "," + outVals[1] + "," + outVals[2] + "," + outVals[3]);
            ctx.write(NullWritable.get(), out);
        }
    }

    public static boolean runStats(Configuration conf, Path input, Path statsOut) throws Exception {
        FileSystem fs = FileSystem.get(conf);
        fs.delete(statsOut, true);

        Job job = Job.getInstance(conf, "Scale-Stats");
        job.setJarByClass(StandardScaleMR.class);
        job.setMapperClass(StatsMapper.class);
        job.setCombinerClass(StatsCombiner.class);
        job.setReducerClass(StatsReducer.class);

        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(FeatureStatsWritable.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(Text.class);

        job.setNumReduceTasks(1);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, statsOut);
        return job.waitForCompletion(true);
    }

    public static boolean runTransform(Configuration conf, Path input, Path statsOut, Path output, String method) throws Exception {
        FileSystem fs = FileSystem.get(conf);
        fs.delete(output, true);

        Job job = Job.getInstance(conf, "Scale-Transform-" + method);
        job.setJarByClass(StandardScaleMR.class);
        job.setMapperClass(ScaleMapper.class);
        job.setNumReduceTasks(0);
        job.getConfiguration().set("scale.method", method);

        FileStatus[] files = fs.listStatus(statsOut);
        for (FileStatus st : files) {
            if (st.isFile() && st.getPath().getName().startsWith("part-")) {
                job.addCacheFile(st.getPath().toUri());
            }
        }

        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true);
    }

    public static boolean runFitTransform(Configuration conf, Path input, Path statsOut, Path output, String method) throws Exception {
        return runStats(conf, input, statsOut) && runTransform(conf, input, statsOut, output, method);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: StandardScaleMR <action:stats|transform|fit_transform> <input> <statsOut> <outputOrDash> <method:zscore|minmax>");
            System.exit(1);
        }

        String action = args[0].trim().toLowerCase();
        Path input = new Path(args[1]);
        Path statsOut = new Path(args[2]);
        String outputArg = args[3];
        String method = args[4].trim().toLowerCase();

        Configuration conf = new Configuration();
        boolean ok;
        if ("stats".equals(action)) {
            ok = runStats(conf, input, statsOut);
        } else if ("transform".equals(action)) {
            if ("-".equals(outputArg)) throw new IllegalArgumentException("output required for transform");
            ok = runTransform(conf, input, statsOut, new Path(outputArg), method);
        } else if ("fit_transform".equals(action)) {
            if ("-".equals(outputArg)) throw new IllegalArgumentException("output required for fit_transform");
            ok = runFitTransform(conf, input, statsOut, new Path(outputArg), method);
        } else {
            throw new IllegalArgumentException("Unknown action: " + action);
        }

        System.exit(ok ? 0 : 1);
    }
}
