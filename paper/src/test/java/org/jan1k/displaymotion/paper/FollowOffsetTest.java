package org.jan1k.displaymotion.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowOffsetTest {

    // follow takes x to the right, the turn works like a display, so right goes in as minus x
    @Test
    void rightAndBehindForSomeoneLookingSouth() {
        double[] right = Hitbox.turn(-1, 0, 0, 0f, 0f);
        double[] behind = Hitbox.turn(0, 0, -1, 0f, 0f);

        assertEquals(-1, right[0], 1e-9);   // looking south (+z) the right hand is west (-x)
        assertEquals(-1, behind[2], 1e-9);  // behind is north (-z)
    }

    @Test
    void rightAndBehindForSomeoneLookingWest() {
        double[] right = Hitbox.turn(-1, 0, 0, 90f, 0f);
        double[] behind = Hitbox.turn(0, 0, -1, 90f, 0f);

        assertEquals(-1, right[2], 1e-9);   // looking west (-x) the right hand is north (-z)
        assertEquals(1, behind[0], 1e-9);   // behind is east (+x)
    }
}
