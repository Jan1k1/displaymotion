package org.jan1k.displaymotion;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramesTest {

    @Test
    void oneShotHasAFrameEveryStepAndThenEnds() {
        Frames frames = Frames.bake(Animations.popIn(20), false);

        assertEquals(11, frames.count());
        assertEquals(0, frames.frameAt(0));
        assertEquals(10, frames.frameAt(10));
        assertEquals(-1, frames.frameAt(11));
        assertEquals(0f, frames.value(0, Frames.SX));
        assertEquals(1f, frames.value(10, Frames.SX));
    }

    @Test
    void loopRepeatsAfterTheIntro() {
        Frames frames = Frames.bake(Animations.popIn(20).then(Animations.spin(80)), false);

        assertTrue(frames.isEndless());
        assertEquals(50, frames.count());
        assertEquals(10, frames.frameAt(10));
        assertEquals(10, frames.frameAt(50));
        assertEquals(49, frames.frameAt(1_000_049));
    }

    @Test
    void framesThatDoNotMoveAreNotSentAgain() {
        Frames frames = Frames.bake(Animations.hold(10), false);

        assertTrue(frames.changed(0));
        for (int i = 1; i < frames.count(); i++) {
            assertFalse(frames.changed(i));
        }
    }

    @Test
    void rotationMatchesJoml() {
        Pose pose = Pose.IDENTITY.withRotation(30f, 75f, -20f);
        Frames frames = Frames.bake(Animation.keyframes().at(0, pose).build(), false);
        Quaternionf expected = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(75f), (float) Math.toRadians(30f), (float) Math.toRadians(-20f));

        assertEquals(expected.x, frames.value(0, Frames.QX), 1e-6f);
        assertEquals(expected.y, frames.value(0, Frames.QY), 1e-6f);
        assertEquals(expected.z, frames.value(0, Frames.QZ), 1e-6f);
        assertEquals(expected.w, frames.value(0, Frames.QW), 1e-6f);
    }

    @Test
    void blockTurnsAroundItsCenter() {
        Pose pose = Pose.IDENTITY.withOffset(1f, 2f, 3f).withRotation(10f, 45f, 5f).withScale(2f, 0.5f, 1f);
        Frames frames = Frames.bake(Animation.keyframes().at(0, pose).build(), true);

        Vector3f center = new Vector3f(0.5f, 0.5f, 0.5f)
                .mul(pose.scaleX(), pose.scaleY(), pose.scaleZ())
                .rotate(new Quaternionf(
                        frames.value(0, Frames.QX), frames.value(0, Frames.QY),
                        frames.value(0, Frames.QZ), frames.value(0, Frames.QW)))
                .add(frames.value(0, Frames.TX), frames.value(0, Frames.TY), frames.value(0, Frames.TZ));

        assertEquals(1f, center.x, 1e-5f);
        assertEquals(2f, center.y, 1e-5f);
        assertEquals(3f, center.z, 1e-5f);
    }
}
