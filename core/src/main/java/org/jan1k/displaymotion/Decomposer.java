package org.jan1k.displaymotion;

public final class Decomposer {

    public static final int SIZE = 14;

    public static final int TX = 0;
    public static final int TY = 1;
    public static final int TZ = 2;
    public static final int LX = 3;
    public static final int LY = 4;
    public static final int LZ = 5;
    public static final int LW = 6;
    public static final int SX = 7;
    public static final int SY = 8;
    public static final int SZ = 9;
    public static final int RX = 10;
    public static final int RY = 11;
    public static final int RZ = 12;
    public static final int RW = 13;

    private static final double TINY = 1e-6;
    private static final double ORTHOGONAL = 1e-5;
    private static final int MAX_SWEEPS = 10;

    private final double[] a = new double[9];
    private final double[] b = new double[9];
    private final double[] v = new double[9];
    private final double[] u = new double[9];
    private final double[] s = new double[3];

    public void decompose(float[] m, int o, float[] out, int r) {
        out[r + TX] = m[o + 9];
        out[r + TY] = m[o + 10];
        out[r + TZ] = m[o + 11];

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                a[row * 3 + col] = m[o + col * 3 + row];
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                b[i * 3 + j] = a[i] * a[j] + a[3 + i] * a[3 + j] + a[6 + i] * a[6 + j];
                v[i * 3 + j] = i == j ? 1 : 0;
            }
        }
        if (!orthogonal()) {
            jacobi();
        }

        if (det(v) < 0) {
            v[2] = -v[2];
            v[5] = -v[5];
            v[8] = -v[8];
        }

        int zeros = 0;
        for (int i = 0; i < 3; i++) {
            s[i] = Math.sqrt(Math.max(b[i * 4], 0));
            if (s[i] < TINY) {
                zeros++;
                continue;
            }
            for (int row = 0; row < 3; row++) {
                u[row * 3 + i] = (a[row * 3] * v[i] + a[row * 3 + 1] * v[3 + i] + a[row * 3 + 2] * v[6 + i]) / s[i];
            }
        }

        if (zeros == 3) {
            for (int i = 0; i < 9; i++) {
                u[i] = i % 4 == 0 ? 1 : 0;
                v[i] = u[i];
            }
            s[0] = 0;
            s[1] = 0;
            s[2] = 0;
        } else if (zeros == 2) {
            int k = s[0] >= TINY ? 0 : s[1] >= TINY ? 1 : 2;
            perpendicular(k, (k + 1) % 3);
            cross(k, (k + 1) % 3, (k + 2) % 3);
        } else if (zeros == 1) {
            int k = s[0] < TINY ? 0 : s[1] < TINY ? 1 : 2;
            cross((k + 1) % 3, (k + 2) % 3, k);
        } else if (det(u) < 0) {
            u[2] = -u[2];
            u[5] = -u[5];
            u[8] = -u[8];
            s[2] = -s[2];
        }

        quaternion(u, false, out, r + LX);
        out[r + SX] = (float) s[0];
        out[r + SY] = (float) s[1];
        out[r + SZ] = (float) s[2];
        quaternion(v, true, out, r + RX);
    }

    private boolean orthogonal() {
        return nearZero(b[1], b[0], b[4]) && nearZero(b[2], b[0], b[8]) && nearZero(b[5], b[4], b[8]);
    }

    private static boolean nearZero(double dot, double lengthA, double lengthB) {
        return Math.abs(dot) <= ORTHOGONAL * Math.sqrt(lengthA * lengthB) + 1e-30;
    }

    private void jacobi() {
        for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {
            double off = b[1] * b[1] + b[2] * b[2] + b[5] * b[5];
            double on = b[0] * b[0] + b[4] * b[4] + b[8] * b[8];
            if (off <= 1e-24 * on + 1e-40) {
                return;
            }
            rotate(0, 1);
            rotate(0, 2);
            rotate(1, 2);
        }
    }

    private void rotate(int p, int q) {
        double bpq = b[p * 3 + q];
        if (Math.abs(bpq) < 1e-40) {
            return;
        }
        double bpp = b[p * 3 + p];
        double bqq = b[q * 3 + q];
        double theta = (bqq - bpp) / (2 * bpq);
        double t = theta == 0 ? 1 : Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
        double c = 1 / Math.sqrt(t * t + 1);
        double sn = t * c;
        for (int k = 0; k < 3; k++) {
            if (k == p || k == q) {
                continue;
            }
            double bkp = b[k * 3 + p];
            double bkq = b[k * 3 + q];
            b[k * 3 + p] = b[p * 3 + k] = c * bkp - sn * bkq;
            b[k * 3 + q] = b[q * 3 + k] = sn * bkp + c * bkq;
        }
        b[p * 3 + p] = bpp - t * bpq;
        b[q * 3 + q] = bqq + t * bpq;
        b[p * 3 + q] = 0;
        b[q * 3 + p] = 0;
        for (int k = 0; k < 3; k++) {
            double vkp = v[k * 3 + p];
            double vkq = v[k * 3 + q];
            v[k * 3 + p] = c * vkp - sn * vkq;
            v[k * 3 + q] = sn * vkp + c * vkq;
        }
    }

    private void perpendicular(int from, int to) {
        double x = u[from];
        double y = u[3 + from];
        double z = u[6 + from];
        double px;
        double py;
        double pz;
        if (Math.abs(x) < 0.9) {
            px = 0;
            py = z;
            pz = -y;
        } else {
            px = -z;
            py = 0;
            pz = x;
        }
        double length = Math.sqrt(px * px + py * py + pz * pz);
        u[to] = px / length;
        u[3 + to] = py / length;
        u[6 + to] = pz / length;
    }

    private void cross(int first, int second, int to) {
        double x1 = u[first], y1 = u[3 + first], z1 = u[6 + first];
        double x2 = u[second], y2 = u[3 + second], z2 = u[6 + second];
        u[to] = y1 * z2 - z1 * y2;
        u[3 + to] = z1 * x2 - x1 * z2;
        u[6 + to] = x1 * y2 - y1 * x2;
    }

    private static double det(double[] m) {
        return m[0] * (m[4] * m[8] - m[5] * m[7])
                - m[1] * (m[3] * m[8] - m[5] * m[6])
                + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    private static void quaternion(double[] m, boolean inverse, float[] out, int o) {
        double m00 = m[0], m01 = m[1], m02 = m[2];
        double m10 = m[3], m11 = m[4], m12 = m[5];
        double m20 = m[6], m21 = m[7], m22 = m[8];
        double x;
        double y;
        double z;
        double w;
        double trace = m00 + m11 + m22;
        if (trace > 0) {
            double k = Math.sqrt(trace + 1) * 2;
            w = 0.25 * k;
            x = (m21 - m12) / k;
            y = (m02 - m20) / k;
            z = (m10 - m01) / k;
        } else if (m00 > m11 && m00 > m22) {
            double k = Math.sqrt(1 + m00 - m11 - m22) * 2;
            w = (m21 - m12) / k;
            x = 0.25 * k;
            y = (m01 + m10) / k;
            z = (m02 + m20) / k;
        } else if (m11 > m22) {
            double k = Math.sqrt(1 + m11 - m00 - m22) * 2;
            w = (m02 - m20) / k;
            x = (m01 + m10) / k;
            y = 0.25 * k;
            z = (m12 + m21) / k;
        } else {
            double k = Math.sqrt(1 + m22 - m00 - m11) * 2;
            w = (m10 - m01) / k;
            x = (m02 + m20) / k;
            y = (m12 + m21) / k;
            z = 0.25 * k;
        }
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        double sign = inverse ? -1 : 1;
        out[o] = (float) (sign * x / length);
        out[o + 1] = (float) (sign * y / length);
        out[o + 2] = (float) (sign * z / length);
        out[o + 3] = (float) (w / length);
    }
}
