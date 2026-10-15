package org.jan1k.displaymotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramesTest {

    @Test
    void oneShotHasAFrameEveryStepAndThenEnds() {
        Frames frames = Frames.bake(Animations.popIn(20));
        float[] m = new float[Affine.SIZE];

        assertEquals(11, frames.count());
        assertEquals(0, frames.frameAt(0));
        assertEquals(10, frames.frameAt(10));
        assertEquals(-1, frames.frameAt(11));
        frames.copyTo(0, m, 0);
        assertEquals(0f, m[0]);
        frames.copyTo(10, m, 0);
        assertEquals(1f, m[0]);
    }

    @Test
    void loopRepeatsAfterTheIntro() {
        Frames frames = Frames.bake(Animations.popIn(20).then(Animations.spin(80)));

        assertTrue(frames.isEndless());
        assertEquals(50, frames.count());
        assertEquals(10, frames.frameAt(10));
        assertEquals(10, frames.frameAt(50));
        assertEquals(49, frames.frameAt(1_000_049));
    }

    @Test
    void framesHoldTheBakedPose() {
        Frames frames = Frames.bake(Animations.slide(0f, 2f, 0f, 10, Ease.LINEAR));
        float[] m = new float[Affine.SIZE];

        frames.copyTo(5, m, 0);
        assertEquals(2f, m[10], 1e-6f);
        frames.copyTo(2, m, 0);
        assertEquals(0.8f, m[10], 1e-6f);
    }
}
