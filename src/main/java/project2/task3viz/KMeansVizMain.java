package project2.task3viz;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

/**
 * Task 3(d): visualizes K-means clustered 4D points.
 *
 * Expected input line format from OPT_POINTS:
 * w,x,y,z,clusterId,center_w,center_x,center_y,center_z
 */
public class KMeansVizMain {

    private static class P {
        final double[] v; // 4D point
        final int cluster;

        P(double[] v, int cluster) {
            this.v = v;
            this.cluster = cluster;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: KMeansVizMain <inputFileOrDir> <outputDir> [maxPoints]");
            System.exit(1);
        }

        String inputPath = args[0].trim();
        String outputDir = args[1].trim();
        int maxPoints = args.length >= 3 ? Integer.parseInt(args[2]) : 50000;

        File outDir = new File(outputDir);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new RuntimeException("Could not create output dir: " + outputDir);
        }

        List<P> points = readPoints(inputPath, maxPoints);
        if (points.isEmpty()) {
            throw new RuntimeException("No valid points were read from: " + inputPath);
        }

        writeClusterCounts(points, new File(outDir, "cluster_counts.csv"));
        writeSummary(points, new File(outDir, "summary.txt"), maxPoints, inputPath);

        createScatter(points, 0, 1, "Projection w vs x", new File(outDir, "scatter_w_x"));
        createScatter(points, 2, 3, "Projection y vs z", new File(outDir, "scatter_y_z"));
        createScatter(points, 0, 2, "Projection w vs y", new File(outDir, "scatter_w_y"));

        System.out.println("Visualization finished.");
        System.out.println("Input  : " + inputPath);
        System.out.println("Output : " + outDir.getAbsolutePath());
        System.out.println("Points : " + points.size());
    }

    private static List<P> readPoints(String inputPath, int maxPoints) throws Exception {
        List<String> lines = readAllLines(inputPath);
        List<P> reservoir = new ArrayList<P>(Math.min(maxPoints, 1024));
        Random rnd = new Random(42L);
        int seen = 0;

        for (String line : lines) {
            P p = parse(line);
            if (p == null) continue;
            seen++;
            if (reservoir.size() < maxPoints) {
                reservoir.add(p);
            } else {
                // reservoir sampling for stable memory use on large outputs
                int j = rnd.nextInt(seen);
                if (j < maxPoints) reservoir.set(j, p);
            }
        }
        return reservoir;
    }

    private static List<String> readAllLines(String inputPath) throws Exception {
        File f = new File(inputPath);
        if (f.exists()) {
            return readAllLinesLocal(f);
        }
        return readAllLinesHdfs(inputPath);
    }

    private static List<String> readAllLinesLocal(File fileOrDir) throws Exception {
        List<String> out = new ArrayList<String>();
        if (fileOrDir.isFile()) {
            readLocalFile(fileOrDir, out);
            return out;
        }

        File[] files = fileOrDir.listFiles();
        if (files == null) return out;

        List<File> partFiles = new ArrayList<File>();
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith("part-")) partFiles.add(f);
        }
        Collections.sort(partFiles);
        for (File pf : partFiles) readLocalFile(pf, out);
        return out;
    }

    private static void readLocalFile(File file, List<String> out) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) out.add(line);
        } finally {
            br.close();
        }
    }

    private static List<String> readAllLinesHdfs(String inputPath) throws Exception {
        List<String> out = new ArrayList<String>();
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);
        Path p = new Path(inputPath);
        FileStatus status = fs.getFileStatus(p);
        if (status.isFile()) {
            readHdfsFile(fs, p, out);
            return out;
        }

        FileStatus[] files = fs.listStatus(p);
        List<Path> partFiles = new ArrayList<Path>();
        for (FileStatus st : files) {
            if (st.isFile() && st.getPath().getName().startsWith("part-")) partFiles.add(st.getPath());
        }
        Collections.sort(partFiles);
        for (Path pf : partFiles) readHdfsFile(fs, pf, out);
        return out;
    }

    private static void readHdfsFile(FileSystem fs, Path p, List<String> out) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(p)));
        try {
            String line;
            while ((line = br.readLine()) != null) out.add(line);
        } finally {
            br.close();
        }
    }

    private static P parse(String line) {
        if (line == null) return null;
        String s = line.trim();
        if (s.isEmpty()) return null;

        String[] csv = s.split(",");
        if (csv.length < 5) return null;
        try {
            double[] v = new double[4];
            v[0] = Double.parseDouble(csv[0].trim());
            v[1] = Double.parseDouble(csv[1].trim());
            v[2] = Double.parseDouble(csv[2].trim());
            v[3] = Double.parseDouble(csv[3].trim());
            int cluster = Integer.parseInt(csv[4].trim());
            return new P(v, cluster);
        } catch (Exception e) {
            return null;
        }
    }

    private static void createScatter(List<P> points, int a, int b, String title, File outputWithoutExt) throws Exception {
        XYChart chart = new XYChartBuilder()
            .width(1200)
            .height(800)
            .title(title)
            .xAxisTitle(axisName(a))
            .yAxisTitle(axisName(b))
            .build();

        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(5);

        Map<Integer, List<Double>> xs = new HashMap<Integer, List<Double>>();
        Map<Integer, List<Double>> ys = new HashMap<Integer, List<Double>>();
        for (P p : points) {
            List<Double> lx = xs.get(p.cluster);
            List<Double> ly = ys.get(p.cluster);
            if (lx == null) {
                lx = new ArrayList<Double>();
                ly = new ArrayList<Double>();
                xs.put(p.cluster, lx);
                ys.put(p.cluster, ly);
            }
            lx.add(p.v[a]);
            ly.add(p.v[b]);
        }

        List<Integer> clusters = new ArrayList<Integer>(xs.keySet());
        Collections.sort(clusters);
        for (Integer c : clusters) {
            org.knowm.xchart.XYSeries series = chart.addSeries("cluster_" + c, xs.get(c), ys.get(c));
            series.setMarker(SeriesMarkers.CIRCLE);
        }

        BitmapEncoder.saveBitmap(chart, outputWithoutExt.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
    }

    private static void writeClusterCounts(List<P> points, File outFile) throws Exception {
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for (P p : points) {
            Integer c = counts.get(p.cluster);
            counts.put(p.cluster, c == null ? 1 : c + 1);
        }
        List<Integer> keys = new ArrayList<Integer>(counts.keySet());
        Collections.sort(keys);

        BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
        try {
            bw.write("cluster,count");
            bw.newLine();
            for (Integer k : keys) {
                bw.write(k + "," + counts.get(k));
                bw.newLine();
            }
        } finally {
            bw.close();
        }
    }

    private static void writeSummary(List<P> points, File outFile, int maxPoints, String inputPath) throws Exception {
        Map<Integer, Integer> counts = new LinkedHashMap<Integer, Integer>();
        for (P p : points) {
            Integer c = counts.get(p.cluster);
            counts.put(p.cluster, c == null ? 1 : c + 1);
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
        try {
            bw.write("input=" + inputPath); bw.newLine();
            bw.write("sampled_points=" + points.size()); bw.newLine();
            bw.write("max_points_arg=" + maxPoints); bw.newLine();
            bw.write("num_clusters_in_sample=" + counts.size()); bw.newLine();
        } finally {
            bw.close();
        }
    }

    private static String axisName(int i) {
        if (i == 0) return "w";
        if (i == 1) return "x";
        if (i == 2) return "y";
        return "z";
    }
}
