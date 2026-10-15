package org.jan1k.displaymotion;

public final class Frames {

    public static final int STEP_TICKS = 2;

    public static final int TX = 0;
    public static final int TY = 1;
    public static final int TZ = 2;
    public static final int SX = 3;
    public static final int SY = 4;
    public static final int SZ = 5;
    public static final int QX = 6;
    public static final int QY = 7;
    public static final int QZ = 8;
    public static final int QW = 9;

    private static final int STRIDE = 10;

    private final float[] data;
    private final boolean[] changed;
    private final int introFrames;
    private final int loopFrames;
    private final int count;

    private Frames(float[] data, boolean[] changed, int introFrames, int loopFrames) {
        this.data = data;
        this.changed = changed;
        this.introFrames = introFrames;
        this.loopFrames = loopFrames;
        this.count = changed.length;
    }

    public static Frames bake(Animation animation, boolean centerBlock) {
        int intro = animation.introTicks();
        int loop = animation.loopTicks();
        int introFrames;
        int loopFrames;
        int count;
        if (loop > 0) {
            introFrames = intro == 0 ? 0 : Math.max(1, Math.round(intro / (float) STEP_TICKS));
            loopFrames = Math.max(1, Math.round(loop / (float) STEP_TICKS));
            count = introFrames + loopFrames;
        } else {
            introFrames = Math.max(1, Math.ceilDiv(intro, STEP_TICKS));
            loopFrames = 0;
            count = introFrames + 1;
        }

        float[] data = new float[count * STRIDE];
        for (int i = 0; i < count; i++) {
            float tick = i < introFrames || loop == 0
                    ? (float) i * intro / introFrames
                    : intro + (float) (i - introFrames) * loop / loopFrames;
            write(animation.poseAt(tick), centerBlock, data, i * STRIDE);
        }

        boolean[] changed = new boolean[count];
        changed[0] = true;
        for (int i = 1; i < count; i++) {
            changed[i] = !same(data, (i - 1) * STRIDE, i * STRIDE);
        }
        if (loopFrames > 0) {
            changed[introFrames] = true;
        }
        return new Frames(data, changed, introFrames, loopFrames);
    }

    public int frameAt(long step) {
        if (step <= 0) {
            return 0;
        }
        if (loopFrames == 0) {
            return step < count ? (int) step : -1;
        }
        if (step < introFrames) {
            return (int) step;
        }
        return introFrames + (int) ((step - introFrames) % loopFrames);
    }

    public boolean changed(int frame) {
        return changed[frame];
    }

    public float value(int frame, int index) {
        return data[frame * STRIDE + index];
    }

    public int count() {
        return count;
    }

    public boolean isEndless() {
        return loopFrames > 0;
    }

    private static void write(Pose pose, boolean centerBlock, float[] out, int o) {
        float hp = (float) Math.toRadians(pose.pitch()) * 0.5f;
        float hy = (float) Math.toRadians(pose.yaw()) * 0.5f;
        float hr = (float) Math.toRadians(pose.roll()) * 0.5f;
        float sx = (float) Math.sin(hp);
        float cx = (float) Math.cos(hp);
        float sy = (float) Math.sin(hy);
        float cy = (float) Math.cos(hy);
        float sz = (float) Math.sin(hr);
        float cz = (float) Math.cos(hr);

        float ax = cy * sx;
        float ay = sy * cx;
        float az = -sy * sx;
        float aw = cy * cx;
        float qx = ax * cz + ay * sz;
        float qy = ay * cz - ax * sz;
        float qz = aw * sz + az * cz;
        float qw = aw * cz - az * sz;

        float tx = pose.x();
        float ty = pose.y();
        float tz = pose.z();
        if (centerBlock) {
            float vx = pose.scaleX() * 0.5f;
            float vy = pose.scaleY() * 0.5f;
            float vz = pose.scaleZ() * 0.5f;
            float rx = 2f * (qy * vz - qz * vy);
            float ry = 2f * (qz * vx - qx * vz);
            float rz = 2f * (qx * vy - qy * vx);
            tx -= vx + qw * rx + (qy * rz - qz * ry);
            ty -= vy + qw * ry + (qz * rx - qx * rz);
            tz -= vz + qw * rz + (qx * ry - qy * rx);
        }

        out[o + TX] = tx;
        out[o + TY] = ty;
        out[o + TZ] = tz;
        out[o + SX] = pose.scaleX();
        out[o + SY] = pose.scaleY();
        out[o + SZ] = pose.scaleZ();
        out[o + QX] = qx;
        out[o + QY] = qy;
        out[o + QZ] = qz;
        out[o + QW] = qw;
    }

    private static boolean same(float[] data, int a, int b) {
        for (int i = 0; i < STRIDE; i++) {
            if (Float.compare(data[a + i], data[b + i]) != 0) {
                return false;
            }
        }
        return true;
    }
}
