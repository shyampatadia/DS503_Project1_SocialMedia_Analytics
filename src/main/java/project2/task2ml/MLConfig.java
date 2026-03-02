package project2.task2ml;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class MLConfig {
    public String task2ConfigPath = "/home/ds503/project2_task2_config.properties";

    public boolean useScaler = true;
    public String scalerMethod = "zscore"; // zscore | minmax

    public String rawTrainInput = "/project2/task2/input/points_4d.csv";
    public String scaledTrainOutput = "/project2/task2ml/scaled/train";
    public String scalerStatsPath = "/project2/task2ml/scaler/stats";

    public String modelBase = "/project2/task2ml/model";
    public String modelCentersPath = ""; // optional override
    public String modelSsePath = "/project2/task2ml/model/metrics_sse";

    public String predictInput = "/project2/task2/input/points_4d.csv";
    public String scaledPredictOutput = "/project2/task2ml/scaled/predict";
    public String predictOutputBase = "/project2/task2ml/predict";

    public String fitMode = "OPT_CENTERS";

    public static MLConfig load(String path) throws IOException {
        MLConfig c = new MLConfig();
        if (path == null || path.trim().isEmpty()) return c;

        Properties p = new Properties();
        FileInputStream in = new FileInputStream(path);
        try {
            p.load(in);
        } finally {
            in.close();
        }

        c.task2ConfigPath = p.getProperty("task2.config.path", c.task2ConfigPath);
        c.useScaler = toBool(p.getProperty("scaler.use"), c.useScaler);
        c.scalerMethod = p.getProperty("scaler.method", c.scalerMethod);

        c.rawTrainInput = p.getProperty("train.input.raw", c.rawTrainInput);
        c.scaledTrainOutput = p.getProperty("train.input.scaled", c.scaledTrainOutput);
        c.scalerStatsPath = p.getProperty("scaler.stats.path", c.scalerStatsPath);

        c.modelBase = p.getProperty("model.base", c.modelBase);
        c.modelCentersPath = p.getProperty("model.centers.path", c.modelCentersPath);
        c.modelSsePath = p.getProperty("model.sse.path", c.modelSsePath);

        c.predictInput = p.getProperty("predict.input.raw", c.predictInput);
        c.scaledPredictOutput = p.getProperty("predict.input.scaled", c.scaledPredictOutput);
        c.predictOutputBase = p.getProperty("predict.output.base", c.predictOutputBase);

        c.fitMode = p.getProperty("fit.mode", c.fitMode);
        return c;
    }

    public void applyOverrides(String[] args, int start) {
        for (int i = start; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            if (kv.length != 2) continue;
            set(kv[0].trim(), kv[1].trim());
        }
    }

    private void set(String k, String v) {
        if ("task2.config.path".equalsIgnoreCase(k)) task2ConfigPath = v;
        else if ("scaler.use".equalsIgnoreCase(k)) useScaler = toBool(v, useScaler);
        else if ("scaler.method".equalsIgnoreCase(k)) scalerMethod = v;
        else if ("train.input.raw".equalsIgnoreCase(k)) rawTrainInput = v;
        else if ("train.input.scaled".equalsIgnoreCase(k)) scaledTrainOutput = v;
        else if ("scaler.stats.path".equalsIgnoreCase(k)) scalerStatsPath = v;
        else if ("model.base".equalsIgnoreCase(k)) modelBase = v;
        else if ("model.centers.path".equalsIgnoreCase(k)) modelCentersPath = v;
        else if ("model.sse.path".equalsIgnoreCase(k)) modelSsePath = v;
        else if ("predict.input.raw".equalsIgnoreCase(k)) predictInput = v;
        else if ("predict.input.scaled".equalsIgnoreCase(k)) scaledPredictOutput = v;
        else if ("predict.output.base".equalsIgnoreCase(k)) predictOutputBase = v;
        else if ("fit.mode".equalsIgnoreCase(k)) fitMode = v;
    }

    private static boolean toBool(String s, boolean d) {
        if (s == null) return d;
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }
}
