package org.jan1k.displaymotion;

import java.util.Arrays;

/** Internal. A baked animation, one matrix every 2 ticks. Used by the sender modules. */
public final class Frames {

    public static final int STEP_TICKS = 2;
    public static final int MAX_TICKS = 6000;

    private static final String[] NO_EVENTS = new String[0];

    private final float[] data;
    private final int introFrames;
    private final int loopFrames;
    private final int count;
    private final String[][] events;

    private Frames(float[] data, int introFrames, int loopFrames, int count, String[][] events) {
        this.data = data;
        this.introFrames = introFrames;
        this.loopFrames = loopFrames;
        this.count = count;
        this.events = events;
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
            // loops get rounded to whole frames so spin(81) is actualy 82 ticks, close enough
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
        return new Frames(data, introFrames, loopFrames, count, bakeEvents(animation, introFrames, loopFrames, count));
    }

    // same tick to frame mapping as the poses above, so an event lands on the frame that shows its tick
    private static String[][] bakeEvents(Animation animation, int introFrames, int loopFrames, int count) {
        if (animation.events().isEmpty()) {
            return null;
        }
        int intro = animation.introTicks();
        int loop = animation.loopTicks();
        String[][] events = new String[count][];
        for (AnimationEvent event : animation.events()) {
            int tick = event.tick();
            int frame;
            if (loop > 0 && tick >= intro) {
                frame = introFrames + Math.round((float) (tick - intro) * loopFrames / loop) % loopFrames;
            } else if (intro == 0) {
                frame = 0;
            } else {
                frame = Math.min(Math.round((float) tick * introFrames / intro), loop > 0 ? introFrames - 1 : count - 1);
            }
            String[] old = events[frame] == null ? NO_EVENTS : events[frame];
            String[] grown = Arrays.copyOf(old, old.length + 1);
            grown[old.length] = event.name();
            events[frame] = grown;
        }
        return events;
    }

    /** Names of the events on this frame, empty when there are none. */
    public String[] eventsAt(int frame) {
        if (events == null || events[frame] == null) {
            return NO_EVENTS;
        }
        return events[frame];
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
