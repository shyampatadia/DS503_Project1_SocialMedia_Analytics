package project2.task2simple;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.io.Writable;

public class SumCountWritable implements Writable {
    public double sw;
    public double sx;
    public double sy;
    public double sz;
    public double sse;
    public long c;

    public SumCountWritable() {
    }

    public SumCountWritable(double w, double x, double y, double z, long c) {
        this.sw = w;
        this.sx = x;
        this.sy = y;
        this.sz = z;
        this.sse = 0.0;
        this.c = c;
    }

    public SumCountWritable(double w, double x, double y, double z, double sse, long c) {
        this.sw = w;
        this.sx = x;
        this.sy = y;
        this.sz = z;
        this.sse = sse;
        this.c = c;
    }

    public void add(SumCountWritable other) {
        sw += other.sw;
        sx += other.sx;
        sy += other.sy;
        sz += other.sz;
        sse += other.sse;
        c += other.c;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeDouble(sw);
        out.writeDouble(sx);
        out.writeDouble(sy);
        out.writeDouble(sz);
        out.writeDouble(sse);
        out.writeLong(c);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        sw = in.readDouble();
        sx = in.readDouble();
        sy = in.readDouble();
        sz = in.readDouble();
        sse = in.readDouble();
        c = in.readLong();
    }
}
