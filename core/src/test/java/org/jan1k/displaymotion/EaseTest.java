package org.jan1k.displaymotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EaseTest {

    @Test
    void startsAtZeroAndEndsAtOne() {
        for (Ease ease : Ease.values()) {
            assertEquals(0f, ease.apply(0f), ease.name());
            assertEquals(1f, ease.apply(1f), ease.name());
        }
    }

    @Test
    void outIsPastHalfAtTheHalf() {
        assertEquals(0.75f, Ease.OUT.apply(0.5f), 1e-6f);
        assertEquals(0.25f, Ease.IN.apply(0.5f), 1e-6f);
        assertEquals(0.5f, Ease.IN_OUT.apply(0.5f), 1e-6f);
    }

    @Test
    void staysInsideZeroToOne() {
        for (Ease ease : Ease.values()) {
            assertEquals(0f, ease.apply(-1f), ease.name());
            assertEquals(1f, ease.apply(2f), ease.name());
        }
    }

    @Test
    void neverGoesBack() {
        for (Ease ease : Ease.values()) {
            float last = 0f;
            for (int i = 1; i <= 100; i++) {
                float now = ease.apply(i / 100f);
                assertTrue(now >= last, ease.name());
                last = now;
            }
        }
    }
}
