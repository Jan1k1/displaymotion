package org.jan1k.displaymotion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class SequenceAnimation implements Animation {

    private final Animation[] parts;
    private final Pose[] starts;
    private final Pose end;
    private final int introTicks;
    private final int loopTicks;
    private final List<AnimationEvent> events;

    SequenceAnimation(List<Animation> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("a sequence needs at least one animation");
        }
        int count = 0;
        int intro = 0;
        int loop = 0;
        Pose base = Pose.IDENTITY;
        Pose[] starts = new Pose[parts.size()];
        for (Animation part : parts) {
            starts[count++] = base;
            intro += part.introTicks();
            if (part.isEndless()) {
                loop = part.loopTicks();
                break;
            }
            // next part starts where this one ended. before this popOut then hold popped back
            // to full size, the the reviewer found that one
            base = base.combine(part.poseAt(part.introTicks()));
        }
        this.parts = parts.subList(0, count).toArray(Animation[]::new);
        this.starts = Arrays.copyOf(starts, count);
        this.end = base;
        this.introTicks = intro;
        this.loopTicks = loop;
        List<AnimationEvent> events = new ArrayList<>();
        int offset = 0;
        for (Animation part : this.parts) {
            for (AnimationEvent event : part.events()) {
                events.add(new AnimationEvent(offset + event.tick(), event.name()));
            }
            offset += part.introTicks();
        }
        this.events = List.copyOf(events);
    }

    @Override
    public List<AnimationEvent> events() {
        return events;
    }

    @Override
    public Pose poseAt(float tick) {
        for (int i = 0; i < parts.length; i++) {
            Animation part = parts[i];
            if (part.isEndless() || tick < part.introTicks()) {
                return starts[i].combine(part.poseAt(tick));
            }
            tick -= part.introTicks();
        }
        return end;
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
