package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextCutTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void cutsAcrossPiecesAndKeepsTheColor() {
        Component text = Component.text("Hi ", NamedTextColor.WHITE).append(Component.text("there", NamedTextColor.GOLD));
        TextCut cut = new TextCut(text);

        Component first = cut.first(4);

        assertEquals(8, cut.length());
        assertEquals("Hi t", PLAIN.serialize(first));
        assertEquals(NamedTextColor.GOLD, first.children().get(1).color());
        assertEquals(NamedTextColor.WHITE, first.children().getFirst().color());
    }

    @Test
    void anArrowCountsAsOneChar() {
        TextCut cut = new TextCut(Component.text("▲ up"));

        assertEquals(4, cut.length());
        assertEquals("▲", PLAIN.serialize(cut.first(1)));
    }
}
