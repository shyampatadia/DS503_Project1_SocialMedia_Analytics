package circlenet.common;

public final class CsvUtils {
    private CsvUtils() {
    }

    public static String[] split(String line) {
        return line.split(",", -1);
    }

    public static String[] splitTab(String line) {
        return line.split("\t", -1);
    }

    public static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
