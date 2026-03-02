package project2.task2ml;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class KMeansSkMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: KMeansSkMain <action:fit|predict|fit_predict> <ml_config.properties> [key=value overrides]");
            System.exit(1);
        }

        String action = args[0].trim().toLowerCase();
        MLConfig cfg = MLConfig.load(args[1]);
        cfg.applyOverrides(args, 2);

        if ("fit".equals(action)) {
            doFit(cfg);
        } else if ("predict".equals(action)) {
            doPredict(cfg);
        } else if ("fit_predict".equals(action)) {
            doFit(cfg);
            doPredict(cfg);
        } else {
            throw new IllegalArgumentException("Unknown action: " + action);
        }
    }

    private static void doFit(MLConfig cfg) throws Exception {
        String trainInput = cfg.rawTrainInput;

        if (cfg.useScaler) {
            Configuration hconf = new Configuration();
            boolean ok = StandardScaleMR.runFitTransform(
                hconf,
                new Path(cfg.rawTrainInput),
                new Path(cfg.scalerStatsPath),
                new Path(cfg.scaledTrainOutput),
                cfg.scalerMethod
            );
            if (!ok) throw new RuntimeException("Scaling (fit_transform) failed");

            Path part = firstPartPath(new Path(cfg.scaledTrainOutput), hconf);
            if (part == null) throw new RuntimeException("No scaled train output part file found");
            trainInput = part.toString();
        }

        String[] kmeansArgs = new String[] {
            cfg.task2ConfigPath,
            "mode=" + cfg.fitMode,
            "points.input=" + trainInput,
            "output.base=" + cfg.modelBase
        };
        project2.task2simple.KMeansMain.main(kmeansArgs);

        String centers = (cfg.modelCentersPath == null || cfg.modelCentersPath.trim().isEmpty())
            ? cfg.modelBase + "/final_centers.csv"
            : cfg.modelCentersPath;

        Configuration conf = new Configuration();
        Path sseOut = new Path(cfg.modelSsePath);
        boolean okSse = KMeansSSEMR.run(conf, new Path(trainInput), new Path(centers), sseOut);
        if (!okSse) {
            throw new RuntimeException("SSE job failed");
        }
        double sse = KMeansSSEMR.readSSE(conf, sseOut);
        System.out.println("Training SSE written at: " + cfg.modelSsePath);
        System.out.println("Training SSE value     : " + sse);
    }

    private static void doPredict(MLConfig cfg) throws Exception {
        String centers = (cfg.modelCentersPath == null || cfg.modelCentersPath.trim().isEmpty())
            ? cfg.modelBase + "/final_centers.csv"
            : cfg.modelCentersPath;

        String predictInput = cfg.predictInput;

        if (cfg.useScaler) {
            Configuration hconf = new Configuration();
            boolean ok = StandardScaleMR.runTransform(
                hconf,
                new Path(cfg.predictInput),
                new Path(cfg.scalerStatsPath),
                new Path(cfg.scaledPredictOutput),
                cfg.scalerMethod
            );
            if (!ok) throw new RuntimeException("Scaling (transform) failed");

            Path part = firstPartPath(new Path(cfg.scaledPredictOutput), hconf);
            if (part == null) throw new RuntimeException("No scaled predict output part file found");
            predictInput = part.toString();
        }

        String[] kmeansArgs = new String[] {
            cfg.task2ConfigPath,
            "mode=OPT_POINTS",
            "rounds=0",
            "seeds.input=" + centers,
            "points.input=" + predictInput,
            "output.base=" + cfg.predictOutputBase
        };
        project2.task2simple.KMeansMain.main(kmeansArgs);
    }

    private static Path firstPartPath(Path dir, Configuration conf) throws Exception {
        FileSystem fs = FileSystem.get(conf);
        for (org.apache.hadoop.fs.FileStatus st : fs.listStatus(dir)) {
            if (st.isFile() && st.getPath().getName().startsWith("part-")) {
                return st.getPath();
            }
        }
        return null;
    }
}
