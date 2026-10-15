package org.jan1k.displaymotion;

import java.util.List;

final class TogetherAnimation implements Animation {

    static final int MAX_LOOP_TICKS = Frames.MAX_TICKS;

    private final Animation[] parts;
    private final int introTicks;
    private final int loopTicks;

    TogetherAnimation(List<Animation> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("together needs at least one animation");
        }
        this.parts = parts.toArray(Animation[]::new);
        int intro = 0;
        long loop = 0;
        for (Animation part : this.parts) {
            intro = Math.max(intro, part.introTicks());
            if (part.isEndless()) {
                // two loops only line up again after the lcm of they're lengths, 80 and 60 is 240
                loop = loop == 0 ? part.loopTicks() : lcm(loop, part.loopTicks());
                if (loop > MAX_LOOP_TICKS) {
                    throw new IllegalArgumentException("these loops only line up after " + loop
                            + " ticks, the limit is " + MAX_LOOP_TICKS + ". Use loop lengths that divide each other");
                }
            }
        }
        this.introTicks = intro;
        this.loopTicks = (int) loop;
    }

    @Override
    public Pose poseAt(float tick) {
        Pose pose = parts[0].poseAt(tick);
        for (int i = 1; i < parts.length; i++) {
            pose = pose.combine(parts[i].poseAt(tick));
        }
        return pose;
    }

    @Override
    public int introTicks() {
        return introTicks;
    }

    @Override
    public int loopTicks() {
        return loopTicks;
    }

    private static long lcm(long a, long b) {
        long x = a;
        long y = b;
        while (y != 0) {
            long r = x % y;
            x = y;
            y = r;
        }
        return a / x * b;
    }
}
