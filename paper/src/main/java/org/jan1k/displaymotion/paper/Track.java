package org.jan1k.displaymotion.paper;

import org.jan1k.displaymotion.Frames;

final class Track {

    final Frames frames;
    final Runnable onEnd;
    long step = -1; // first step lands on frame 0
    int frame;
    boolean running = true;
    boolean moved = true;
    // crossfade: the track it fades out of keeps playing until the fade is done
    Track from;
    int fadeSteps;
    int fadeStep;

    Track(Frames frames, Runnable onEnd) {
        this.frames = frames;
        this.onEnd = onEnd;
    }

    Track(Frames frames, Runnable onEnd, Track from, int fadeSteps) {
        this(frames, onEnd);
        this.from = from;
        this.fadeSteps = fadeSteps;
    }

    // how much of this track shows, the rest is still the old one
    float weight() {
        return from == null ? 1f : Math.min(1f, (fadeStep + 1) / (float) (fadeSteps + 1));
    }
}
