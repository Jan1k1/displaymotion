package org.jan1k.displaymotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EaseTest {

    @Test
    void startsAtZeroAndEndsAtOne() {
        assertEquals(0f, Ease.out(0f));
        assertEquals(1f, Ease.out(1f));
    }

    @Test
    void halfwayIsPastHalf() {
        assertEquals(0.75f, Ease.out(0.5f), 1e-6f);
    }

    @Test
    void staysInsideZeroToOne() {
        assertEquals(0f, Ease.out(-1f));
        assertEquals(1f, Ease.out(2f));
    }

    @Test
    void neverGoesBack() {
        float last = 0f;
        for (int i = 1; i <= 100; i++) {
            float now = Ease.out(i / 100f);
            assertTrue(now >= last);
            last = now;
        }
    }
}
