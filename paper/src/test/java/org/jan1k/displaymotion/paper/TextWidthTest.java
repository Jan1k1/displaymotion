package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextWidthTest {

    @Test
    void widthFollowsTheVanillaFont() {
        // N=6 o=6 t=4 c=6 h=6, 28 pixels
        assertEquals(28 * 0.025f, TextWidth.blocks(Component.text("Notch"), 1f), 1e-6f);
        // j=6 e=6 b=6 _=6
        assertEquals(24 * 0.025f * 0.8f, TextWidth.blocks(Component.text("jeb_"), 0.8f), 1e-6f);
    }

    @Test
    void boldIsOnePixelWiderPerChar() {
        Component plain = Component.text("#10");
        Component bold = plain.decorate(TextDecoration.BOLD);

        assertEquals(TextWidth.blocks(plain, 1f) + 3 * 0.025f, TextWidth.blocks(bold, 1f), 1e-6f);
    }
}
