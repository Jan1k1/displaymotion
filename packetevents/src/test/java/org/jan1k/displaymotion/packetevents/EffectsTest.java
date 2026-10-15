package org.jan1k.displaymotion.packetevents;

import org.jan1k.displaymotion.Frames;
import org.junit.jupiter.api.Test;

class EffectsTest {

    @Test
    void everySwordFallBuilds() {
        for (int i = 0; i < Effects.SWORDS; i++) {
            Frames.check(Effects.swordFall(i * 3, i * 45f));
        }
    }
}
