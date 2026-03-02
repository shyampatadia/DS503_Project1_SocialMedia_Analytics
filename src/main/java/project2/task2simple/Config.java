package project2.task2simple;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    public enum Mode { SINGLE, BASIC, EARLY, OPT_CENTERS, OPT_POINTS }

    public String pointsInput = "/project2/task2/input/points_4d.csv";
    public String seedsInput = "/project2/task2/input/seeds_k5.csv";
    public String outputBase = "/project2/task2/output";

    public String localPointsFile = "points_4d.csv";
    public String localSeedsFile = "seeds_k5.csv";

    public int numPoints = 10000;
    public int k = 5;
    public int rounds = 10;
    public double epsilon = 0.001;
    public double sseEpsilon = -1.0;
    public int minRounds = 1;
    public int reducers = 4;
    public long randomSeed = 42L;

    public Mode mode = Mode.BASIC;

    public static Config load(String path) throws IOException {
        Config c = new Config();
        if (path == null || path.trim().isEmpty()) {
            return c;
        }
        Properties p = new Properties();
        FileInputStream in = new FileInputStream(path);
        try {
            p.load(in);
        } finally {
            in.close();
        }

        c.pointsInput = p.getProperty("points.input", c.pointsInput);
        c.seedsInput = p.getProperty("seeds.input", c.seedsInput);
        c.outputBase = p.getProperty("output.base", c.outputBase);

        c.localPointsFile = p.getProperty("local.points.file", c.localPointsFile);
        c.localSeedsFile = p.getProperty("local.seeds.file", c.localSeedsFile);

        c.numPoints = toInt(p.getProperty("num.points"), c.numPoints);
        c.k = toInt(p.getProperty("k"), c.k);
        c.rounds = toInt(p.getProperty("rounds"), c.rounds);
        c.epsilon = toDouble(p.getProperty("epsilon"), c.epsilon);
        c.sseEpsilon = toDouble(p.getProperty("sse.epsilon"), c.sseEpsilon);
        c.minRounds = toInt(p.getProperty("min.rounds"), c.minRounds);
        c.reducers = toInt(p.getProperty("reducers"), c.reducers);
        c.randomSeed = toLong(p.getProperty("random.seed"), c.randomSeed);

        String m = p.getProperty("mode");
        if (m != null && !m.trim().isEmpty()) {
            c.mode = Mode.valueOf(m.trim().toUpperCase());
        }
        return c;
    }

    public void applyOverrides(String[] args, int start) {
        for (int i = start; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            if (kv.length != 2) continue;
            set(kv[0].trim(), kv[1].trim());
        }
    }

    private void set(String key, String val) {
        if ("mode".equalsIgnoreCase(key)) mode = Mode.valueOf(val.toUpperCase());
        else if ("points.input".equalsIgnoreCase(key)) pointsInput = val;
        else if ("seeds.input".equalsIgnoreCase(key)) seedsInput = val;
        else if ("output.base".equalsIgnoreCase(key)) outputBase = val;
        else if ("local.points.file".equalsIgnoreCase(key)) localPointsFile = val;
        else if ("local.seeds.file".equalsIgnoreCase(key)) localSeedsFile = val;
        else if ("num.points".equalsIgnoreCase(key)) numPoints = toInt(val, numPoints);
        else if ("k".equalsIgnoreCase(key)) k = toInt(val, k);
        else if ("rounds".equalsIgnoreCase(key)) rounds = toInt(val, rounds);
        else if ("epsilon".equalsIgnoreCase(key)) epsilon = toDouble(val, epsilon);
        else if ("sse.epsilon".equalsIgnoreCase(key)) sseEpsilon = toDouble(val, sseEpsilon);
        else if ("min.rounds".equalsIgnoreCase(key)) minRounds = toInt(val, minRounds);
        else if ("reducers".equalsIgnoreCase(key)) reducers = toInt(val, reducers);
        else if ("random.seed".equalsIgnoreCase(key)) randomSeed = toLong(val, randomSeed);
    }

    public int effectiveRounds() { return mode == Mode.SINGLE ? 1 : rounds; }
    public boolean useCombiner() { return mode == Mode.OPT_CENTERS || mode == Mode.OPT_POINTS; }

    private static int toInt(String s, int d) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; } }
    private static long toLong(String s, long d) { try { return Long.parseLong(s.trim()); } catch (Exception e) { return d; } }
    private static double toDouble(String s, double d) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return d; } }
}
