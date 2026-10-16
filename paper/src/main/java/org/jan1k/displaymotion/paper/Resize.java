package org.jan1k.displaymotion.paper;

// the size of a whole display or one part on top of its animation. it grows to a new size over some steps,
// eased in and out so it does not start or stop with a jerk. engine thread only
final class Resize {

    float now = 1f;
    boolean moved;
    private float from = 1f;
    private float to = 1f;
    private int steps;
    private int step;

    void to(float size, int steps) {
        from = now;
        to = size;
        this.steps = steps;
        step = 0;
    }

    boolean busy() {
        return step < steps;
    }

    void step() {
        if (step >= steps) {
            return;
        }
        step++;
        float t = step / (float) steps;
        now = step == steps ? to : from + (to - from) * t * t * (3f - 2f * t);
        moved = true;
    }
}
