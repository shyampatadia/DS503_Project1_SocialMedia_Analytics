package project2.task2ml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class KMeansSSEMR {

    static class P {
        final double a, b, c, d;
        P(double a, double b, double c, double d) { this.a = a; this.b = b; this.c = c; this.d = d; }
        static P parse4(String line) {
            String[] x = line.split(",");
            if (x.length < 4) throw new IllegalArgumentException("Bad point: " + line);
            return new P(
                Double.parseDouble(x[0].trim()),
                Double.parseDouble(x[1].trim()),
                Double.parseDouble(x[2].trim()),
                Double.parseDouble(x[3].trim())
            );
        }
        double dist2(P o) {
            double da = a - o.a, db = b - o.b, dc = c - o.c, dd = d - o.d;
            return da*da + db*db + dc*dc + dd*dd;
        }
    }

    static class C {
        final int id;
        final P p;
        C(int id, P p) { this.id = id; this.p = p; }

        static C parse(String line) {
            if (line == null) return null;
            String s = line.trim();
            if (s.isEmpty()) return null;
            String[] t = s.split("\\t");
            if (t.length == 2) {
                int id = Integer.parseInt(t[0].trim());
                return new C(id, P.parse4(t[1]));
            }
            String[] c = s.split(",");
            if (c.length >= 5) {
                int id = Integer.parseInt(c[0].trim());
                return new C(id, new P(
                    Double.parseDouble(c[1].trim()),
                    Double.parseDouble(c[2].trim()),
                    Double.parseDouble(c[3].trim()),
                    Double.parseDouble(c[4].trim())
                ));
            }
            return null;
        }
    }

    public static class SSEMapper extends Mapper<LongWritable, Text, IntWritable, DoubleWritable> {
        private final List<C> centers = new ArrayList<C>();
        private static final IntWritable K = new IntWritable(0);

        @Override
        protected void setup(Context ctx) throws IOException {
            URI[] files = ctx.getCacheFiles();
            if (files == null || files.length == 0) throw new IOException("No centers file in cache");
            FileSystem fs = FileSystem.get(ctx.getConfiguration());
            for (URI u : files) {
                BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(u.getPath()))));
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        C c = C.parse(line);
                        if (c != null) centers.add(c);
                    }
                } finally {
                    br.close();
                }
            }
            if (centers.isEmpty()) throw new IOException("No centers loaded");
        }

        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty()) return;
            P p;
            try {
                p = P.parse4(line);
            } catch (Exception e) {
                return;
            }
            double best = Double.MAX_VALUE;
            for (C c : centers) {
                double d = p.dist2(c.p);
                if (d < best) best = d;
            }
            ctx.write(K, new DoubleWritable(best));
        }
    }

    public static class SSEReducer extends Reducer<IntWritable, DoubleWritable, NullWritable, DoubleWritable> {
        @Override
        protected void reduce(IntWritable key, Iterable<DoubleWritable> vals, Context ctx) throws IOException, InterruptedException {
            double sum = 0.0;
            for (DoubleWritable v : vals) sum += v.get();
            ctx.write(NullWritable.get(), new DoubleWritable(sum));
        }
    }

    public static boolean run(Configuration conf, Path pointsInput, Path centersFile, Path outPath) throws Exception {
        FileSystem fs = FileSystem.get(conf);
        fs.delete(outPath, true);

        Job job = Job.getInstance(conf, "KMeans-SSE");
        job.setJarByClass(KMeansSSEMR.class);

        job.setMapperClass(SSEMapper.class);
        job.setReducerClass(SSEReducer.class);
        job.setNumReduceTasks(1);

        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(DoubleWritable.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(DoubleWritable.class);

        job.addCacheFile(centersFile.toUri());
        FileInputFormat.addInputPath(job, pointsInput);
        FileOutputFormat.setOutputPath(job, outPath);
        return job.waitForCompletion(true);
    }

    public static double readSSE(Configuration conf, Path outPath) throws Exception {
        FileSystem fs = FileSystem.get(conf);
        FileStatus[] files = fs.listStatus(outPath);
        for (FileStatus st : files) {
            if (st.isFile() && st.getPath().getName().startsWith("part-")) {
                BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(st.getPath())));
                try {
                    String line = br.readLine();
                    if (line == null) return Double.NaN;
                    String[] t = line.trim().split("\\t");
                    String last = t[t.length - 1];
                    return Double.parseDouble(last.trim());
                } finally {
                    br.close();
                }
            }
        }
        return Double.NaN;
    }
}
