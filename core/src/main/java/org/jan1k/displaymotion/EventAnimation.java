package org.jan1k.displaymotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class EventAnimation implements Animation {

    private final Animation inner;
    private final List<AnimationEvent> events;

    EventAnimation(Animation inner, int tick, String name) {
        Objects.requireNonNull(name, "name");
        int end = inner.introTicks() + inner.loopTicks();
        boolean fits = inner.isEndless() ? tick < end : tick <= end;
        if (tick < 0 || !fits) {
            throw new IllegalArgumentException("event tick must be 0 to " + (inner.isEndless() ? end - 1 : end) + ", got " + tick);
        }
        this.inner = inner;
        List<AnimationEvent> all = new ArrayList<>(inner.events());
        all.add(new AnimationEvent(tick, name));
        this.events = List.copyOf(all);
    }

    @Override
    public Pose poseAt(float tick) {
        return inner.poseAt(tick);
    }

    @Override
    public int introTicks() {
        return inner.introTicks();
    }

    @Override
    public int loopTicks() {
        return inner.loopTicks();
    }

    @Override
    public List<AnimationEvent> events() {
        return events;
    }
}
