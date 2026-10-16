package org.jan1k.displaymotion.example;

import org.jan1k.displaymotion.example.DisplayOptions.Part;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayOptionsTest {

    private static final Set<Part> ALL = EnumSet.allOf(Part.class);

    @Test
    void emptyInputHasNoErrors() {
        assertTrue(DisplayOptions.parse("").errors().isEmpty());
        assertTrue(DisplayOptions.parse(null).errors().isEmpty());
        assertTrue(DisplayOptions.parse("   ").errors().isEmpty());
    }

    @Test
    void goodOptionsParse() {
        DisplayOptions.Parsed parsed = DisplayOptions.parse(
                "size:3 grow:20 speed:0.5 glow:gold bright:15 billboard:center shadow:2 range:64 align:left "
                        + "textshadow:true seethrough:false width:120 item:diamond block:gold_block");
        assertEquals(List.of(), parsed.errors());
    }

    @Test
    void textTakesTheRestOfTheLine() {
        DisplayOptions.Parsed parsed = DisplayOptions.parse("size:2 text:Hello there size:9");
        assertEquals(List.of(), parsed.errors());
        assertEquals("Hello there size:9", parsed.options().text());
    }

    @Test
    void textKeepsOptionsBeforeIt() {
        DisplayOptions.Parsed parsed = DisplayOptions.parse("align:left text:Hello");
        assertEquals(List.of(), parsed.errors());
        assertEquals("Hello", parsed.options().text());
        assertNull(parsed.options().withoutText().text());
    }

    @Test
    void emptyTextIsAnError() {
        assertEquals(1, DisplayOptions.parse("text:").errors().size());
        assertEquals(1, DisplayOptions.parse("text:   ").errors().size());
    }

    @Test
    void unknownKeyIsReportedAndOthersStay() {
        DisplayOptions.Parsed parsed = DisplayOptions.parse("colour:red size:2");
        assertEquals(1, parsed.errors().size());
        assertTrue(parsed.errors().getFirst().contains("colour"));
    }

    @Test
    void badValuesNameTheKeyAndTheAllowedValues() {
        assertTrue(DisplayOptions.parse("size:99").errors().getFirst().contains("0 to 32"));
        assertTrue(DisplayOptions.parse("size:abc").errors().getFirst().contains("abc"));
        assertTrue(DisplayOptions.parse("size:NaN").errors().getFirst().startsWith("size"));
        assertTrue(DisplayOptions.parse("speed:11").errors().getFirst().contains("0 to 10"));
        assertTrue(DisplayOptions.parse("grow:5.5").errors().getFirst().startsWith("grow"));
        assertTrue(DisplayOptions.parse("grow:201").errors().getFirst().contains("200"));
        assertTrue(DisplayOptions.parse("bright:16").errors().getFirst().contains("0 to 15"));
        assertTrue(DisplayOptions.parse("billboard:up").errors().getFirst().contains("fixed, center, vertical, horizontal"));
        assertTrue(DisplayOptions.parse("align:top").errors().getFirst().contains("left, center, right"));
        assertTrue(DisplayOptions.parse("textshadow:maybe").errors().getFirst().contains("true or false"));
        assertTrue(DisplayOptions.parse("glow:pinkish").errors().getFirst().contains("gold"));
        assertTrue(DisplayOptions.parse("glow:#12345").errors().getFirst().startsWith("glow"));
        assertTrue(DisplayOptions.parse("range:0").errors().getFirst().startsWith("range"));
        assertTrue(DisplayOptions.parse("width:0").errors().getFirst().startsWith("width"));
    }

    @Test
    void keyWithoutValueIsAnError() {
        assertEquals(1, DisplayOptions.parse("size").errors().size());
        assertEquals(1, DisplayOptions.parse("size:").errors().size());
    }

    @Test
    void oneErrorPerBadWord() {
        assertEquals(3, DisplayOptions.parse("size:x speed:y nope:1").errors().size());
    }

    @Test
    void colors() {
        assertEquals(0xFFAA00, DisplayOptions.color("GOLD"));
        assertEquals(0xFF8800, DisplayOptions.color("#ff8800"));
        assertNull(DisplayOptions.color("#gg0000"));
        assertNull(DisplayOptions.color("nothing"));
    }

    @Test
    void lastTokenStart() {
        assertEquals(0, DisplayOptions.lastTokenStart(""));
        assertEquals(0, DisplayOptions.lastTokenStart("siz"));
        assertEquals(7, DisplayOptions.lastTokenStart("size:2 sp"));
        assertEquals(7, DisplayOptions.lastTokenStart("size:2 "));
    }

    @Test
    void suggestsKeys() {
        List<String> keys = DisplayOptions.suggest("", ALL, part -> List.of());
        assertTrue(keys.contains("size:"));
        assertTrue(keys.contains("text:"));
        assertEquals(List.of("size:", "shadow:", "speed:", "seethrough:").stream().sorted().toList(),
                DisplayOptions.suggest("s", ALL, part -> List.of()).stream().sorted().toList());
    }

    @Test
    void doesNotSuggestAKeyTwice() {
        assertTrue(!DisplayOptions.suggest("size:2 ", ALL, part -> List.of()).contains("size:"));
    }

    @Test
    void keysFollowWhatTheDisplayHas() {
        List<String> keys = DisplayOptions.suggest("", EnumSet.of(Part.ITEM), part -> List.of());
        assertTrue(keys.contains("item:"));
        assertTrue(!keys.contains("block:"));
        assertTrue(!keys.contains("text:"));
        assertTrue(!keys.contains("align:"));
    }

    @Test
    void suggestsValuesByPrefix() {
        assertEquals(List.of("glow:gray", "glow:green", "glow:gold"),
                DisplayOptions.suggest("size:2 glow:g", ALL, part -> List.of()));
        assertEquals(List.of("billboard:center"), DisplayOptions.suggest("billboard:ce", ALL, part -> List.of()));
        assertEquals(List.of("size:0.5", "size:1", "size:2", "size:4"), DisplayOptions.suggest("size:", ALL, part -> List.of()));
    }

    @Test
    void suggestsMaterialsByKind() {
        List<String> items = List.of("diamond", "diamond_sword", "stick");
        List<String> blocks = List.of("diamond_block", "stone");
        assertEquals(List.of("item:diamond", "item:diamond_sword"),
                DisplayOptions.suggest("item:dia", ALL, part -> part == Part.ITEM ? items : blocks));
        assertEquals(List.of("block:diamond_block"),
                DisplayOptions.suggest("block:dia", ALL, part -> part == Part.ITEM ? items : blocks));
    }

    @Test
    void noSuggestionsAfterText() {
        assertEquals(List.of(), DisplayOptions.suggest("text:hello si", ALL, part -> List.of()));
        assertEquals(List.of(), DisplayOptions.suggest("text:", ALL, part -> List.of()));
    }
}
