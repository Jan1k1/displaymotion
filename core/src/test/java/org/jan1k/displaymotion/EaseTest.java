package org.jan1k.displaymotion;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EaseTest {

    private static final Set<Ease> CALM = EnumSet.of(Ease.LINEAR, Ease.IN, Ease.OUT, Ease.IN_OUT);

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
        for (Ease ease : CALM) {
            assertEquals(0f, ease.apply(-1f), ease.name());
            assertEquals(1f, ease.apply(2f), ease.name());
        }
    }

    @Test
    void clampsTimeOutsideZeroToOne() {
        for (Ease ease : Ease.values()) {
            assertEquals(ease.apply(0f), ease.apply(-1f), ease.name());
            assertEquals(ease.apply(1f), ease.apply(2f), ease.name());
        }
    }

    @Test
    void backAndElasticGoPastTheEnd() {
        float most = 0f;
        float elastic = 0f;
        for (int i = 1; i < 100; i++) {
            most = Math.max(most, Ease.BACK_OUT.apply(i / 100f));
            elastic = Math.max(elastic, Ease.ELASTIC_OUT.apply(i / 100f));
        }
        assertTrue(most > 1.05f && most < 1.15f, "back goes about 10% past, got " + most);
        assertTrue(elastic > 1.2f, "elastic shoots past, got " + elastic);
        assertTrue(Ease.BACK_IN.apply(0.3f) < 0f);
    }

    @Test
    void bounceTouchesTheEndBeforeItSettles() {
        // the ball lands at about 36% of the time, then bounces up again
        assertEquals(1f, Ease.BOUNCE_OUT.apply(1f / 2.75f), 1e-5f);
        assertTrue(Ease.BOUNCE_OUT.apply(0.5f) < 1f);
        assertEquals(1f - Ease.BOUNCE_OUT.apply(0.7f), Ease.BOUNCE_IN.apply(0.3f), 1e-6f);
    }

    @Test
    void neverGoesBack() {
        for (Ease ease : CALM) {
            float last = 0f;
            for (int i = 1; i <= 100; i++) {
                float now = ease.apply(i / 100f);
                assertTrue(now >= last, ease.name());
                last = now;
            }
        }
    }
}
