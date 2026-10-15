package org.jan1k.displaymotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTest {

    @Test
    void keyframesBlendWithTheEaseOfTheNextKey() {
        Animation move = Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(10, Pose.IDENTITY.withOffset(0f, 2f, 0f), Ease.OUT)
                .build();

        assertEquals(0f, move.poseAt(0f).y());
        assertEquals(1.5f, move.poseAt(5f).y(), 1e-6f);
        assertEquals(2f, move.poseAt(10f).y());
        assertEquals(2f, move.poseAt(99f).y());
        assertEquals(10, move.introTicks());
        assertFalse(move.isEndless());
    }

    @Test
    void keyframeTicksMustGoUp() {
        assertThrows(IllegalArgumentException.class, () -> Animation.keyframes()
                .at(10, Pose.IDENTITY)
                .at(10, Pose.IDENTITY));
        assertThrows(IllegalStateException.class, () -> Animation.keyframes().build());
    }

    @Test
    void loopWraps() {
        Animation spin = Animations.spin(80);

        assertTrue(spin.isEndless());
        assertEquals(80, spin.loopTicks());
        assertEquals(90f, spin.poseAt(20f).yaw(), 1e-4f);
        assertEquals(90f, spin.poseAt(100f).yaw(), 1e-4f);
    }

    @Test
    void sequencePlaysPartsInOrder() {
        Animation both = Animations.popIn(20).then(Animations.spin(80));

        assertEquals(20, both.introTicks());
        assertEquals(80, both.loopTicks());
        assertEquals(0f, both.poseAt(0f).scaleX());
        assertEquals(1f, both.poseAt(20f).scaleX());
        assertEquals(90f, both.poseAt(40f).yaw(), 1e-4f);
        assertEquals(90f, both.poseAt(120f).yaw(), 1e-4f);
    }

    @Test
    void sequenceWithoutLoopEndsOnTheLastPose() {
        Animation upThenDown = Animations.slide(0f, 1f, 0f, 10, Ease.LINEAR).then(Animations.popOut(10));

        assertEquals(20, upThenDown.introTicks());
        assertFalse(upThenDown.isEndless());
        assertEquals(0f, upThenDown.poseAt(50f).scaleX());
    }

    @Test
    void thenStartsWhereTheLastPartEnded() {
        Animation shrinkAndStay = Animations.popOut(10).then(Animations.hold(20));
        assertEquals(0f, shrinkAndStay.poseAt(15f).scaleX());
        assertEquals(0f, shrinkAndStay.poseAt(99f).scaleX());

        Animation twoSteps = Animations.slide(1f, 0f, 0f, 10, Ease.LINEAR).then(Animations.slide(1f, 0f, 0f, 10, Ease.LINEAR));
        assertEquals(1f, twoSteps.poseAt(10f).x(), 1e-6f);
        assertEquals(1.5f, twoSteps.poseAt(15f).x(), 1e-6f);
        assertEquals(2f, twoSteps.poseAt(99f).x(), 1e-6f);
    }

    @Test
    void framesRefuseTooLongOrTooShortAnimations() {
        assertThrows(IllegalArgumentException.class, () -> Frames.check(Animations.hold(Integer.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> Frames.check(Animations.turn(0f, 90f, 0f, 2).loop()));
        assertThrows(IllegalArgumentException.class, () -> Animations.spin(4));
        Frames.check(Animations.spin(Animations.MIN_TICKS_PER_TURN));
    }

    @Test
    void togetherAddsOffsetsAndRotationsAndMultipliesScale() {
        Animation idle = Animations.spin(80).with(Animations.bob(0.4f, 40));

        assertEquals(80, idle.loopTicks());
        Pose pose = idle.poseAt(20f);
        assertEquals(90f, pose.yaw(), 1e-4f);
        assertEquals(0.4f, pose.y(), 1e-6f);
        assertEquals(1f, pose.scaleY());
    }

    @Test
    void togetherRefusesLoopsThatNeverLineUp() {
        assertThrows(IllegalArgumentException.class,
                () -> Animations.spin(9973).with(Animations.spin(9967)));
    }

    @Test
    void presetsRefuseZeroTicks() {
        assertThrows(IllegalArgumentException.class, () -> Animations.popIn(0));
        assertThrows(IllegalArgumentException.class, () -> Animations.spin(-5));
        assertThrows(IllegalArgumentException.class, () -> Animations.bob(1f, 1));
    }
}
