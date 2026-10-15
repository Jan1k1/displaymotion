package org.jan1k.displaymotion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTest {

    @Test
    void eventLandsOnTheFrameOfItsTick() {
        Frames frames = Frames.bake(Animations.popIn(20).event(10, "half").event(20, "end"));

        assertArrayEquals(new String[]{"half"}, frames.eventsAt(5));
        assertArrayEquals(new String[]{"end"}, frames.eventsAt(10));
        assertEquals(0, frames.eventsAt(4).length);
    }

    @Test
    void thenMovesTheEventsOfTheSecondPart() {
        Animation animation = Animations.popIn(20).then(Animations.hold(10).event(4, "late"));

        assertEquals(List.of(new AnimationEvent(24, "late")), animation.events());
        assertArrayEquals(new String[]{"late"}, Frames.bake(animation).eventsAt(12));
    }

    @Test
    void loopEventsComeEveryRound() {
        Frames frames = Frames.bake(Animations.spin(40).event(20, "half"));

        assertEquals(10, frames.frameAt(10));
        assertEquals(10, frames.frameAt(30));
        assertArrayEquals(new String[]{"half"}, frames.eventsAt(10));
    }

    @Test
    void shortLoopInsideALongOneRepeatsItsEvents() {
        Animation animation = Animations.spin(20).event(0, "turn").with(Animations.bob(0.1f, 40));

        assertEquals(List.of(new AnimationEvent(0, "turn"), new AnimationEvent(20, "turn")), animation.events());
    }

    @Test
    void lastTickOfALoopIsTheFirstOfTheNextRound() {
        Animation animation = Animations.popIn(20).event(20, "end").loop();

        assertEquals(List.of(new AnimationEvent(0, "end")), animation.events());
    }

    @Test
    void eventOutsideTheAnimationThrows() {
        assertThrows(IllegalArgumentException.class, () -> Animations.popIn(20).event(21, "late"));
        assertThrows(IllegalArgumentException.class, () -> Animations.spin(40).event(40, "late"));
        assertThrows(IllegalArgumentException.class, () -> Animations.popIn(20).event(-1, "early"));
    }
}
