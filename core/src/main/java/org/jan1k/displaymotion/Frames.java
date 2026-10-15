package org.jan1k.displaymotion;

public final class Frames {

    public static final int STEP_TICKS = 2;
    public static final int MAX_TICKS = 6000;

    private final float[] data;
    private final int introFrames;
    private final int loopFrames;
    private final int count;

    private Frames(float[] data, int introFrames, int loopFrames, int count) {
        this.data = data;
        this.introFrames = introFrames;
        this.loopFrames = loopFrames;
        this.count = count;
    }

    public static void check(Animation animation) {
        long total = (long) animation.introTicks() + animation.loopTicks();
        if (animation.introTicks() < 0 || animation.loopTicks() < 0 || total > MAX_TICKS) {
            throw new IllegalArgumentException("an animation can be at most " + MAX_TICKS
                    + " ticks long (intro plus one loop), got " + total);
        }
        if (animation.loopTicks() > 0 && Math.round(animation.loopTicks() / (float) STEP_TICKS) < 2) {
            throw new IllegalArgumentException("a loop must be at least " + 2 * STEP_TICKS
                    + " ticks long, got " + animation.loopTicks());
        }
    }

    public static Frames bake(Animation animation) {
        check(animation);
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

        float[] data = new float[count * Affine.SIZE];
        for (int i = 0; i < count; i++) {
            float tick = i < introFrames || loop == 0
                    ? (float) i * intro / introFrames
                    : intro + (float) (i - introFrames) * loop / loopFrames;
            Affine.fromPose(animation.poseAt(tick), data, i * Affine.SIZE);
        }
        return new Frames(data, introFrames, loopFrames, count);
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

    public void copyTo(int frame, float[] out, int o) {
        System.arraycopy(data, frame * Affine.SIZE, out, o, Affine.SIZE);
    }

    public int count() {
        return count;
    }

    public boolean isEndless() {
        return loopFrames > 0;
    }
}
