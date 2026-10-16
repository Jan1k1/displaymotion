package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

// a text display is always centered on its spot, so to line text up on its left or right edge we need
// its width. these are the advance widths of the vanilla font in pixels, with the 1 px gap after each char
final class TextWidth {

    // one font pixel is 1/40 of a block on a text display at scale 1
    static final float BLOCKS_PER_PIXEL = 0.025f;
    private static final int NORMAL = 6;

    private TextWidth() {
    }

    static float blocks(Component text, float scale) {
        String plain = PlainTextComponentSerializer.plainText().serialize(text);
        int pixels = 0;
        for (int i = 0; i < plain.length(); i++) {
            pixels += pixels(plain.charAt(i));
        }
        // bold draws every char one pixel wider
        if (text.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
            pixels += plain.length();
        }
        return pixels * BLOCKS_PER_PIXEL * scale;
    }

    static int pixels(char c) {
        return switch (c) {
            case 'i', '.', ',', ':', ';', '!', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', 't', '[', ']', '(', ')', '*', '"', ' ' -> 4;
            case 'f', 'k', '<', '>', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> NORMAL;
        };
    }
}
