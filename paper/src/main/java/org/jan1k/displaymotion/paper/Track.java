package org.jan1k.displaymotion.paper;

import org.jan1k.displaymotion.Frames;

final class Track {

    final Frames frames;
    final Runnable onEnd;
    long step = -1; // first step lands on frame 0
    int frame;
    boolean running = true;
    boolean moved = true;

    Track(Frames frames, Runnable onEnd) {
        this.frames = frames;
        this.onEnd = onEnd;
    }
}
