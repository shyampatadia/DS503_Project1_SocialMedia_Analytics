package circlenet.common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import org.apache.hadoop.mapreduce.Job;

public final class JobTimer {
    private JobTimer() {
    }

    public static boolean run(Job job, String label) throws Exception {
        long start = System.currentTimeMillis();
        boolean ok = job.waitForCompletion(true);
        long end = System.currentTimeMillis();
        long ms = end - start;
        System.out.println(label + " time_ms=" + ms + " time_s=" + (ms / 1000.0));
        appendCsv(label, "job", ms, ok);
        return ok;
    }

    public static void total(String label, long totalStartMs) {
        long ms = System.currentTimeMillis() - totalStartMs;
        System.out.println(label + " total_time_ms=" + ms + " total_time_s=" + (ms / 1000.0));
        appendCsv(label, "total", ms, true);
    }

    private static synchronized void appendCsv(String label, String phase, long timeMs, boolean success) {
        String path = System.getenv("CIRCLENET_TIMING_FILE");
        if (path == null || path.trim().isEmpty()) {
            path = "task_times.csv";
        }
        File file = new File(path);
        boolean newFile = !file.exists();
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
            if (newFile) {
                bw.write("timestamp,label,phase,time_ms,time_s,success");
                bw.newLine();
            }
            bw.write(LocalDateTime.now().toString());
            bw.write(",");
            bw.write(label);
            bw.write(",");
            bw.write(phase);
            bw.write(",");
            bw.write(String.valueOf(timeMs));
            bw.write(",");
            bw.write(String.valueOf(timeMs / 1000.0));
            bw.write(",");
            bw.write(success ? "1" : "0");
            bw.newLine();
            bw.close();
        } catch (Exception e) {
            System.err.println("Timing CSV write skipped: " + e.getMessage());
        }
    }
}
