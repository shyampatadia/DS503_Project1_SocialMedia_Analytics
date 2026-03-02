package project2.task2simple;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
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

public class KMeansMain {

    static class Center {
        final int id;
        final Point4D p;

        Center(int id, Point4D p) {
            this.id = id;
            this.p = p;
        }

        static Center parse(String line) {
            if (line == null) return null;
            String s = line.trim();
            if (s.isEmpty()) return null;

            String[] tab = s.split("\\t");
            if (tab.length == 2) {
                int id = Integer.parseInt(tab[0].trim());
                Point4D p = Point4D.parseCsv4(tab[1]);
                return new Center(id, p);
            }

            String[] csv = s.split(",");
            if (csv.length >= 5) {
                int id = Integer.parseInt(csv[0].trim());
                Point4D p = new Point4D(
                    Double.parseDouble(csv[1].trim()),
                    Double.parseDouble(csv[2].trim()),
                    Double.parseDouble(csv[3].trim()),
                    Double.parseDouble(csv[4].trim())
                );
                return new Center(id, p);
            }
            return null;
        }

        String toCsv() {
            return id + "," + p.toCsv();
        }
    }

    public static class AssignMapper extends Mapper<LongWritable, Text, IntWritable, SumCountWritable> {
        private final List<Center> centers = new ArrayList<Center>();
        private final IntWritable outKey = new IntWritable();

        @Override
        protected void setup(Context ctx) throws java.io.IOException {
            centers.addAll(readCentersFromCache(ctx.getConfiguration(), ctx.getCacheFiles()));
            if (centers.isEmpty()) throw new java.io.IOException("No centers loaded");
        }

        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws java.io.IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty()) return;
            Point4D p;
            try {
                p = Point4D.parseCsv4(line);
            } catch (Exception e) {
                return;
            }
            Nearest n = nearestWithDist(p, centers);
            int cid = n.id;
            outKey.set(cid);
            ctx.write(outKey, new SumCountWritable(p.w, p.x, p.y, p.z, n.dist2, 1L));
        }
    }

    public static class SumCombiner extends Reducer<IntWritable, SumCountWritable, IntWritable, SumCountWritable> {
        @Override
        protected void reduce(IntWritable key, Iterable<SumCountWritable> vals, Context ctx) throws java.io.IOException, InterruptedException {
            SumCountWritable acc = new SumCountWritable(0, 0, 0, 0, 0, 0);
            for (SumCountWritable v : vals) acc.add(v);
            ctx.write(key, acc);
        }
    }

    public static class RecomputeReducer extends Reducer<IntWritable, SumCountWritable, IntWritable, Text> {
        private final Text out = new Text();

        @Override
        protected void reduce(IntWritable key, Iterable<SumCountWritable> vals, Context ctx) throws java.io.IOException, InterruptedException {
            double sw = 0, sx = 0, sy = 0, sz = 0;
            double sse = 0;
            long c = 0;
            for (SumCountWritable v : vals) {
                sw += v.sw;
                sx += v.sx;
                sy += v.sy;
                sz += v.sz;
                sse += v.sse;
                c += v.c;
            }
            if (c == 0) return;
            out.set((sw / c) + "," + (sx / c) + "," + (sy / c) + "," + (sz / c) + "," + c + "," + sse);
            ctx.write(key, out);
        }
    }

    public static class FinalAssignMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
        private final List<Center> centers = new ArrayList<Center>();
        private final Text out = new Text();

        @Override
        protected void setup(Context ctx) throws java.io.IOException {
            centers.addAll(readCentersFromCache(ctx.getConfiguration(), ctx.getCacheFiles()));
            if (centers.isEmpty()) throw new java.io.IOException("No centers loaded");
        }

        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws java.io.IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty()) return;
            Point4D p;
            try {
                p = Point4D.parseCsv4(line);
            } catch (Exception e) {
                return;
            }
            int cid = nearest(p, centers);
            Center c = byId(cid, centers);
            if (c == null) return;
            out.set(p.toCsv() + "," + cid + "," + c.p.toCsv());
            ctx.write(NullWritable.get(), out);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: KMeansMain <config.properties> [key=value overrides]");
            System.exit(1);
        }

        Config cfg = Config.load(args[0]);
        cfg.applyOverrides(args, 1);

        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);

        Path points = new Path(cfg.pointsInput);
        Path seeds = new Path(cfg.seedsInput);
        Path base = new Path(cfg.outputBase);

        int rounds = cfg.effectiveRounds();
        boolean useCombiner = cfg.useCombiner();
        int reducers = useCombiner ? Math.max(1, Math.min(cfg.reducers, cfg.k)) : Math.max(1, cfg.reducers);

        fs.delete(base, true);
        fs.mkdirs(base);

        Path currentCenters = new Path(base, "centers_initial.csv");
        copy(fs, seeds, currentCenters);

        boolean converged = false;
        int executed = 0;
        double prevSse = Double.NaN;
        double finalSse = Double.NaN;
        StringBuilder metrics = new StringBuilder();
        metrics.append("iter,shift,sse,delta_sse,stop_shift,stop_sse,converged\n");

        for (int i = 1; i <= rounds; i++) {
            Path outIter = new Path(base, "iter_" + i);
            fs.delete(outIter, true);

            Job round = buildRoundJob(conf, points, currentCenters, outIter, reducers, useCombiner);
            if (!round.waitForCompletion(true)) {
                throw new RuntimeException("Iteration failed: " + i);
            }

            Map<Integer, Center> oldC = readCentersFile(fs, currentCenters);
            Map<Integer, Center> newC = readCentersFromParts(fs, outIter);
            Map<Integer, Center> merged = merge(oldC, newC);

            Path nextCenters = new Path(base, "centers_iter_" + i + ".csv");
            writeCenters(fs, nextCenters, merged);

            double shift = maxShift(oldC, merged);
            double sse = sumSseFromParts(fs, outIter);
            double deltaSse = Double.isNaN(prevSse) ? Double.NaN : Math.abs(prevSse - sse);
            boolean stopByShift = shift <= cfg.epsilon;
            boolean stopBySse = cfg.sseEpsilon > 0.0 && !Double.isNaN(deltaSse) && deltaSse <= cfg.sseEpsilon;
            converged = stopByShift || stopBySse;
            if (i < Math.max(1, cfg.minRounds)) converged = false;

            metrics.append(i).append(',')
                .append(shift).append(',')
                .append(sse).append(',')
                .append(Double.isNaN(deltaSse) ? "" : String.valueOf(deltaSse)).append(',')
                .append(stopByShift).append(',')
                .append(stopBySse).append(',')
                .append(converged).append('\n');

            executed = i;
            finalSse = sse;
            prevSse = sse;
            currentCenters = nextCenters;

            if ((cfg.mode == Config.Mode.EARLY || cfg.mode == Config.Mode.OPT_CENTERS || cfg.mode == Config.Mode.OPT_POINTS) && converged) {
                break;
            }
        }

        Path finalCenters = new Path(base, "final_centers.csv");
        copy(fs, currentCenters, finalCenters);

        if (cfg.mode == Config.Mode.OPT_POINTS) {
            Path finalOut = new Path(base, "final_points_with_centers");
            fs.delete(finalOut, true);
            Job assign = buildAssignJob(conf, points, finalCenters, finalOut);
            if (!assign.waitForCompletion(true)) throw new RuntimeException("Final assignment failed");
        }

        writeText(fs, new Path(base, "metrics.csv"), metrics.toString());
        writeSummary(fs, new Path(base, "summary.txt"), cfg, executed, converged, reducers, useCombiner, finalCenters.toString(), finalSse);
        System.out.println("Done. mode=" + cfg.mode + ", rounds=" + executed + ", converged=" + converged);
    }

    private static Job buildRoundJob(Configuration conf, Path points, Path centers, Path out, int reducers, boolean useCombiner) throws Exception {
        Job job = Job.getInstance(conf, "Task2SimpleKMeans-Round");
        job.setJarByClass(KMeansMain.class);
        job.setMapperClass(AssignMapper.class);
        if (useCombiner) job.setCombinerClass(SumCombiner.class);
        job.setReducerClass(RecomputeReducer.class);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(SumCountWritable.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(reducers);
        job.addCacheFile(centers.toUri());
        FileInputFormat.addInputPath(job, points);
        FileOutputFormat.setOutputPath(job, out);
        return job;
    }

    private static Job buildAssignJob(Configuration conf, Path points, Path centers, Path out) throws Exception {
        Job job = Job.getInstance(conf, "Task2SimpleKMeans-Assign");
        job.setJarByClass(KMeansMain.class);
        job.setMapperClass(FinalAssignMapper.class);
        job.setNumReduceTasks(0);
        job.setMapOutputKeyClass(NullWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.addCacheFile(centers.toUri());
        FileInputFormat.addInputPath(job, points);
        FileOutputFormat.setOutputPath(job, out);
        return job;
    }

    private static List<Center> readCentersFromCache(Configuration conf, URI[] files) throws java.io.IOException {
        if (files == null || files.length == 0) throw new java.io.IOException("No cache center file");
        FileSystem fs = FileSystem.get(conf);
        List<Center> out = new ArrayList<Center>();
        for (URI u : files) {
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(u.getPath()))));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    Center c = Center.parse(line);
                    if (c != null) out.add(c);
                }
            } finally {
                br.close();
            }
        }
        return out;
    }

    private static Map<Integer, Center> readCentersFile(FileSystem fs, Path file) throws Exception {
        Map<Integer, Center> out = new HashMap<Integer, Center>();
        BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(file)));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                Center c = Center.parse(line);
                if (c != null) out.put(c.id, c);
            }
        } finally {
            br.close();
        }
        return out;
    }

    private static Map<Integer, Center> readCentersFromParts(FileSystem fs, Path folder) throws Exception {
        Map<Integer, Center> out = new HashMap<Integer, Center>();
        FileStatus[] files = fs.listStatus(folder);
        for (FileStatus st : files) {
            if (!st.isFile() || !st.getPath().getName().startsWith("part-")) continue;
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(st.getPath())));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    Center c = Center.parse(line);
                    if (c != null) out.put(c.id, c);
                }
            } finally {
                br.close();
            }
        }
        return out;
    }

    private static Map<Integer, Center> merge(Map<Integer, Center> oldC, Map<Integer, Center> newC) {
        Map<Integer, Center> merged = new HashMap<Integer, Center>();
        for (Map.Entry<Integer, Center> e : oldC.entrySet()) {
            Center n = newC.get(e.getKey());
            merged.put(e.getKey(), n != null ? n : e.getValue());
        }
        for (Map.Entry<Integer, Center> e : newC.entrySet()) {
            if (!merged.containsKey(e.getKey())) merged.put(e.getKey(), e.getValue());
        }
        return merged;
    }

    private static double maxShift(Map<Integer, Center> oldC, Map<Integer, Center> newC) {
        double max = 0.0;
        for (Map.Entry<Integer, Center> e : oldC.entrySet()) {
            Center n = newC.get(e.getKey());
            if (n == null) continue;
            double d = Math.sqrt(e.getValue().p.dist2(n.p));
            if (d > max) max = d;
        }
        return max;
    }

    private static double sumSseFromParts(FileSystem fs, Path folder) throws Exception {
        double total = 0.0;
        FileStatus[] files = fs.listStatus(folder);
        for (FileStatus st : files) {
            if (!st.isFile() || !st.getPath().getName().startsWith("part-")) continue;
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(st.getPath())));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty()) continue;
                    String[] tab = s.split("\\t", 2);
                    String payload = tab.length == 2 ? tab[1] : s;
                    String[] csv = payload.split(",");
                    if (csv.length >= 6) {
                        try {
                            total += Double.parseDouble(csv[5].trim());
                        } catch (Exception ignored) {
                        }
                    }
                }
            } finally {
                br.close();
            }
        }
        return total;
    }

    private static void writeCenters(FileSystem fs, Path file, Map<Integer, Center> centers) throws Exception {
        fs.delete(file, true);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fs.create(file, true)));
        try {
            List<Integer> ids = new ArrayList<Integer>(centers.keySet());
            Collections.sort(ids);
            for (Integer id : ids) {
                bw.write(centers.get(id).toCsv());
                bw.newLine();
            }
        } finally {
            bw.close();
        }
    }

    private static void writeSummary(FileSystem fs, Path file, Config cfg, int rounds, boolean converged, int reducers, boolean combiner, String finalCenters, double finalSse) throws Exception {
        fs.delete(file, true);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fs.create(file, true)));
        try {
            bw.write("mode=" + cfg.mode); bw.newLine();
            bw.write("k=" + cfg.k); bw.newLine();
            bw.write("rounds_executed=" + rounds); bw.newLine();
            bw.write("converged=" + converged); bw.newLine();
            bw.write("epsilon=" + cfg.epsilon); bw.newLine();
            bw.write("sse_epsilon=" + cfg.sseEpsilon); bw.newLine();
            bw.write("min_rounds=" + Math.max(1, cfg.minRounds)); bw.newLine();
            bw.write("reducers=" + reducers); bw.newLine();
            bw.write("used_combiner=" + combiner); bw.newLine();
            bw.write("stop_rule=" + (cfg.sseEpsilon > 0.0 ? "shift_or_sse" : "shift_only")); bw.newLine();
            bw.write("final_sse=" + finalSse); bw.newLine();
            bw.write("final_centers_path=" + finalCenters); bw.newLine();
        } finally {
            bw.close();
        }
    }

    private static void writeText(FileSystem fs, Path file, String content) throws Exception {
        fs.delete(file, true);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fs.create(file, true)));
        try {
            bw.write(content);
        } finally {
            bw.close();
        }
    }

    private static int nearest(Point4D p, List<Center> centers) {
        double best = Double.MAX_VALUE;
        int bestId = centers.get(0).id;
        for (Center c : centers) {
            double d = p.dist2(c.p);
            if (d < best) {
                best = d;
                bestId = c.id;
            }
        }
        return bestId;
    }

    private static class Nearest {
        final int id;
        final double dist2;
        Nearest(int id, double dist2) {
            this.id = id;
            this.dist2 = dist2;
        }
    }

    private static Nearest nearestWithDist(Point4D p, List<Center> centers) {
        double best = Double.MAX_VALUE;
        int bestId = centers.get(0).id;
        for (Center c : centers) {
            double d = p.dist2(c.p);
            if (d < best) {
                best = d;
                bestId = c.id;
            }
        }
        return new Nearest(bestId, best);
    }

    private static Center byId(int id, List<Center> centers) {
        for (Center c : centers) if (c.id == id) return c;
        return null;
    }

    private static void copy(FileSystem fs, Path from, Path to) throws Exception {
        fs.delete(to, true);
        InputStream in = fs.open(from);
        OutputStream out = fs.create(to, true);
        try {
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) out.write(b, 0, r);
        } finally {
            in.close();
            out.close();
        }
    }
}
