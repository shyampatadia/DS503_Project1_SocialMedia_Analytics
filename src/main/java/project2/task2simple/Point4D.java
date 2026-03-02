package project2.task2simple;

public class Point4D {
    public final double w;
    public final double x;
    public final double y;
    public final double z;

    public Point4D(double w, double x, double y, double z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Point4D parseCsv4(String line) {
        String[] p = line.split(",");
        if (p.length < 4) {
            throw new IllegalArgumentException("Bad point row: " + line);
        }
        return new Point4D(
            Double.parseDouble(p[0].trim()),
            Double.parseDouble(p[1].trim()),
            Double.parseDouble(p[2].trim()),
            Double.parseDouble(p[3].trim())
        );
    }

    public String toCsv() {
        return w + "," + x + "," + y + "," + z;
    }

    public double dist2(Point4D other) {
        double dw = w - other.w;
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dw * dw + dx * dx + dy * dy + dz * dz;
    }
}
