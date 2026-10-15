package org.jan1k.displaymotion;

import java.util.List;

final class SequenceAnimation implements Animation {

    private final Animation[] parts;
    private final int introTicks;
    private final int loopTicks;

    SequenceAnimation(List<Animation> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("a sequence needs at least one animation");
        }
        this.parts = parts.toArray(Animation[]::new);
        int intro = 0;
        int loop = 0;
        for (Animation part : this.parts) {
            intro += part.introTicks();
            if (part.isEndless()) {
                loop = part.loopTicks();
                break;
            }
        }
        this.introTicks = intro;
        this.loopTicks = loop;
    }

    @Override
    public Pose poseAt(float tick) {
        for (Animation part : parts) {
            if (part.isEndless() || tick < part.introTicks()) {
                return part.poseAt(tick);
            }
            tick -= part.introTicks();
        }
        Animation last = parts[parts.length - 1];
        return last.poseAt(last.introTicks());
    }

    @Override
    public int introTicks() {
        return introTicks;
    }

    @Override
    public int loopTicks() {
        return loopTicks;
    }
}
