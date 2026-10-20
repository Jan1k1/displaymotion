package org.jan1k.displaymotion.paper;

import java.util.Arrays;

// a smooth line thru points, centripetal catmull rom. the plain one makes loops and overshoots when the
// points are far apart on one side and close on the other, this one does not. it is cut into short straight
// bits once, so a display can go along it at the same speed the whole way
final class Curve {

    private static final int SAMPLES = 16;
    private static final double SAME_SPOT = 1e-6;

    private final double[] xyz;
    private final double[] length;

    private Curve(double[] xyz, double[] length) {
        this.xyz = xyz;
        this.length = length;
    }

    // x y z of each point after each other, the first one is the start
    static Curve through(double[] points) {
        double[] p = withoutRepeats(points);
        int count = p.length / 3;
        if (count < 2) {
            return new Curve(Arrays.copyOf(p, 3), new double[]{0});
        }
        int samples = (count - 1) * SAMPLES + 1;
        double[] xyz = new double[samples * 3];
        double[] length = new double[samples];
        System.arraycopy(p, 0, xyz, 0, 3);
        int s = 1;
        for (int seg = 0; seg < count - 1; seg++) {
            double[] p0 = control(p, count, seg - 1);
            double[] p1 = control(p, count, seg);
            double[] p2 = control(p, count, seg + 1);
            double[] p3 = control(p, count, seg + 2);
            double t1 = Math.sqrt(distance(p0, p1));
            double t2 = t1 + Math.sqrt(distance(p1, p2));
            double t3 = t2 + Math.sqrt(distance(p2, p3));
            for (int k = 1; k <= SAMPLES; k++, s++) {
                for (int axis = 0; axis < 3; axis++) {
                    xyz[s * 3 + axis] = k == SAMPLES ? p2[axis]
                            : point(p0[axis], p1[axis], p2[axis], p3[axis], t1, t2, t3, t1 + (t2 - t1) * k / SAMPLES);
                }
                length[s] = length[s - 1] + Math.sqrt(squared(xyz, s - 1, s));
            }
        }
        return new Curve(xyz, length);
    }

    double length() {
        return length[length.length - 1];
    }

    double[] end() {
        int last = length.length - 1;
        return new double[]{xyz[last * 3], xyz[last * 3 + 1], xyz[last * 3 + 2]};
    }

    // 0 is the start, 1 the end, by distance along the curve. back and elastic eases go a bit past the ends,
    // there it just goes on straight
    double[] at(double fraction) {
        int last = length.length - 1;
        if (last == 0) {
            return end();
        }
        double want = fraction * length();
        int i;
        if (want <= 0) {
            i = 0;
        } else if (want >= length()) {
            i = last - 1;
        } else {
            int found = Arrays.binarySearch(length, want);
            i = Math.min(last - 1, found >= 0 ? found : -found - 2);
        }
        double bit = length[i + 1] - length[i];
        double f = bit <= 0 ? 0 : (want - length[i]) / bit;
        double[] out = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double from = xyz[i * 3 + axis];
            out[axis] = from + (xyz[(i + 1) * 3 + axis] - from) * f;
        }
        return out;
    }

    // barry and goldman, knots spaced by the square root of the distance
    private static double point(double p0, double p1, double p2, double p3, double t1, double t2, double t3, double t) {
        double a1 = ((t1 - t) * p0 + t * p1) / t1;
        double a2 = ((t2 - t) * p1 + (t - t1) * p2) / (t2 - t1);
        double a3 = ((t3 - t) * p2 + (t - t2) * p3) / (t3 - t2);
        double b1 = ((t2 - t) * a1 + t * a2) / t2;
        double b2 = ((t3 - t) * a2 + (t - t1) * a3) / (t3 - t1);
        return ((t2 - t) * b1 + (t - t1) * b2) / (t2 - t1);
    }

    // before the first and after the last point there is nothing, so mirror the next one
    private static double[] control(double[] p, int count, int i) {
        if (i < 0) {
            return new double[]{2 * p[0] - p[3], 2 * p[1] - p[4], 2 * p[2] - p[5]};
        }
        if (i >= count) {
            int a = (count - 1) * 3;
            int b = (count - 2) * 3;
            return new double[]{2 * p[a] - p[b], 2 * p[a + 1] - p[b + 1], 2 * p[a + 2] - p[b + 2]};
        }
        return new double[]{p[i * 3], p[i * 3 + 1], p[i * 3 + 2]};
    }

    // two points on the same spot give a zero knot gap and the math divides by it
    private static double[] withoutRepeats(double[] points) {
        double[] out = new double[points.length];
        int n = 0;
        for (int i = 0; i < points.length; i += 3) {
            if (n > 0 && squared(points, i / 3, out, n / 3 - 1) < SAME_SPOT * SAME_SPOT) {
                continue;
            }
            System.arraycopy(points, i, out, n, 3);
            n += 3;
        }
        return Arrays.copyOf(out, n);
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double squared(double[] xyz, int a, int b) {
        return squared(xyz, a, xyz, b);
    }

    private static double squared(double[] first, int a, double[] second, int b) {
        double dx = first[a * 3] - second[b * 3];
        double dy = first[a * 3 + 1] - second[b * 3 + 1];
        double dz = first[a * 3 + 2] - second[b * 3 + 2];
        return dx * dx + dy * dy + dz * dz;
    }
}
