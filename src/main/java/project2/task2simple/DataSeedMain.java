package project2.task2simple;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DataSeedMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: DataSeedMain <config.properties> [key=value overrides]");
            System.exit(1);
        }
        Config cfg = Config.load(args[0]);
        cfg.applyOverrides(args, 1);

        if (cfg.numPoints < 6000) throw new IllegalArgumentException("num.points must be >= 6000");
        if (cfg.k <= 0 || cfg.k > cfg.numPoints) throw new IllegalArgumentException("k must be > 0 and <= num.points");

        Random rnd = new Random(cfg.randomSeed);
        List<Point4D> pts = new ArrayList<Point4D>(cfg.numPoints);
        for (int i = 0; i < cfg.numPoints; i++) {
            pts.add(new Point4D(
                rand(rnd, 0.0, 10000.0),
                rand(rnd, 20000.0, 1000000.0),
                rand(rnd, -50000.0, 50000.0),
                rand(rnd, 0.0, 100000.0)
            ));
        }

        BufferedWriter pOut = new BufferedWriter(new FileWriter(cfg.localPointsFile));
        try {
            for (Point4D p : pts) {
                pOut.write(p.toCsv());
                pOut.newLine();
            }
        } finally {
            pOut.close();
        }

        List<Integer> ids = new ArrayList<Integer>(pts.size());
        for (int i = 0; i < pts.size(); i++) ids.add(i);
        Collections.shuffle(ids, rnd);

        BufferedWriter sOut = new BufferedWriter(new FileWriter(cfg.localSeedsFile));
        try {
            for (int i = 0; i < cfg.k; i++) {
                sOut.write(i + "," + pts.get(ids.get(i)).toCsv());
                sOut.newLine();
            }
        } finally {
            sOut.close();
        }

        System.out.println("Generated: " + cfg.localPointsFile + " and " + cfg.localSeedsFile);
    }

    private static double rand(Random r, double min, double max) {
        return min + (max - min) * r.nextDouble();
    }
}
