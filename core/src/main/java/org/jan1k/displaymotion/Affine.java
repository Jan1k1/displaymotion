package org.jan1k.displaymotion;

/** Internal. 3x4 matrix math on flat float arrays, no objects. */
public final class Affine {

    public static final int SIZE = 12;

    private Affine() {
    }

    public static void identity(float[] out, int o) {
        out[o] = 1f;
        out[o + 1] = 0f;
        out[o + 2] = 0f;
        out[o + 3] = 0f;
        out[o + 4] = 1f;
        out[o + 5] = 0f;
        out[o + 6] = 0f;
        out[o + 7] = 0f;
        out[o + 8] = 1f;
        out[o + 9] = 0f;
        out[o + 10] = 0f;
        out[o + 11] = 0f;
    }

    public static void fromPose(Pose pose, float[] out, int o) {
        fromPose(pose.x(), pose.y(), pose.z(), pose.pitch(), pose.yaw(), pose.roll(),
                pose.scaleX(), pose.scaleY(), pose.scaleZ(), out, o);
    }

    public static void fromPose(float x, float y, float z, float pitch, float yaw, float roll,
                                float scaleX, float scaleY, float scaleZ, float[] out, int o) {
        double hp = Math.toRadians(pitch) * 0.5;
        double hy = Math.toRadians(yaw) * 0.5;
        double hr = Math.toRadians(roll) * 0.5;
        double sx = Math.sin(hp);
        double cx = Math.cos(hp);
        double sy = Math.sin(hy);
        double cy = Math.cos(hy);
        double sz = Math.sin(hr);
        double cz = Math.cos(hr);

        double ax = cy * sx;
        double ay = sy * cx;
        double az = -sy * sx;
        double aw = cy * cx;
        double qx = ax * cz + ay * sz;
        double qy = ay * cz - ax * sz;
        double qz = aw * sz + az * cz;
        double qw = aw * cz - az * sz;

        out[o] = (float) ((1 - 2 * (qy * qy + qz * qz)) * scaleX);
        out[o + 1] = (float) (2 * (qx * qy + qw * qz) * scaleX);
        out[o + 2] = (float) (2 * (qx * qz - qw * qy) * scaleX);
        out[o + 3] = (float) (2 * (qx * qy - qw * qz) * scaleY);
        out[o + 4] = (float) ((1 - 2 * (qx * qx + qz * qz)) * scaleY);
        out[o + 5] = (float) (2 * (qy * qz + qw * qx) * scaleY);
        out[o + 6] = (float) (2 * (qx * qz + qw * qy) * scaleZ);
        out[o + 7] = (float) (2 * (qy * qz - qw * qx) * scaleZ);
        out[o + 8] = (float) ((1 - 2 * (qx * qx + qy * qy)) * scaleZ);
        out[o + 9] = x;
        out[o + 10] = y;
        out[o + 11] = z;
    }

    public static void multiply(float[] a, int ao, float[] b, int bo, float[] out, int oo) {
        float a00 = a[ao], a10 = a[ao + 1], a20 = a[ao + 2];
        float a01 = a[ao + 3], a11 = a[ao + 4], a21 = a[ao + 5];
        float a02 = a[ao + 6], a12 = a[ao + 7], a22 = a[ao + 8];
        float ax = a[ao + 9], ay = a[ao + 10], az = a[ao + 11];
        float b00 = b[bo], b10 = b[bo + 1], b20 = b[bo + 2];
        float b01 = b[bo + 3], b11 = b[bo + 4], b21 = b[bo + 5];
        float b02 = b[bo + 6], b12 = b[bo + 7], b22 = b[bo + 8];
        float bx = b[bo + 9], by = b[bo + 10], bz = b[bo + 11];

        out[oo] = a00 * b00 + a01 * b10 + a02 * b20;
        out[oo + 1] = a10 * b00 + a11 * b10 + a12 * b20;
        out[oo + 2] = a20 * b00 + a21 * b10 + a22 * b20;
        out[oo + 3] = a00 * b01 + a01 * b11 + a02 * b21;
        out[oo + 4] = a10 * b01 + a11 * b11 + a12 * b21;
        out[oo + 5] = a20 * b01 + a21 * b11 + a22 * b21;
        out[oo + 6] = a00 * b02 + a01 * b12 + a02 * b22;
        out[oo + 7] = a10 * b02 + a11 * b12 + a12 * b22;
        out[oo + 8] = a20 * b02 + a21 * b12 + a22 * b22;
        out[oo + 9] = a00 * bx + a01 * by + a02 * bz + ax;
        out[oo + 10] = a10 * bx + a11 * by + a12 * bz + ay;
        out[oo + 11] = a20 * bx + a21 * by + a22 * bz + az;
    }

    public static void scale(float[] m, int o, float x, float y, float z) {
        m[o] *= x;
        m[o + 1] *= x;
        m[o + 2] *= x;
        m[o + 3] *= y;
        m[o + 4] *= y;
        m[o + 5] *= y;
        m[o + 6] *= z;
        m[o + 7] *= z;
        m[o + 8] *= z;
    }

    public static void translate(float[] m, int o, float x, float y, float z) {
        m[o + 9] += m[o] * x + m[o + 3] * y + m[o + 6] * z;
        m[o + 10] += m[o + 1] * x + m[o + 4] * y + m[o + 7] * z;
        m[o + 11] += m[o + 2] * x + m[o + 5] * y + m[o + 8] * z;
    }
}
