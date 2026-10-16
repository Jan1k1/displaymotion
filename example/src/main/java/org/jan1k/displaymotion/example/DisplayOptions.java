package org.jan1k.displaymotion.example;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.jan1k.displaymotion.paper.MotionDisplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// the key:value words after a spawn command, like "size:3 speed:0.5 glow:gold"
final class DisplayOptions {

    // what a display has in it, a sword has an item, a text has text
    enum Part { ITEM, BLOCK, TEXT }

    record Parsed(DisplayOptions options, List<String> errors) {
    }

    static final DisplayOptions NONE = new DisplayOptions();

    private static final Map<String, Integer> COLORS = new LinkedHashMap<>();
    private static final List<String> BILLBOARDS = List.of("fixed", "center", "vertical", "horizontal");
    private static final List<String> ALIGNS = List.of("left", "center", "right");
    private static final List<String> BOOLEANS = List.of("true", "false");
    private static final Map<String, List<String>> VALUES = new LinkedHashMap<>();
    private static final Map<String, Part> NEEDS = Map.of(
            "item", Part.ITEM, "block", Part.BLOCK, "text", Part.TEXT, "align", Part.TEXT,
            "textshadow", Part.TEXT, "seethrough", Part.TEXT, "width", Part.TEXT);

    static {
        COLORS.put("white", 0xFFFFFF);
        COLORS.put("silver", 0xC0C0C0);
        COLORS.put("gray", 0x808080);
        COLORS.put("black", 0x000000);
        COLORS.put("red", 0xFF0000);
        COLORS.put("maroon", 0x800000);
        COLORS.put("yellow", 0xFFFF00);
        COLORS.put("olive", 0x808000);
        COLORS.put("lime", 0x00FF00);
        COLORS.put("green", 0x008000);
        COLORS.put("aqua", 0x00FFFF);
        COLORS.put("teal", 0x008080);
        COLORS.put("blue", 0x0000FF);
        COLORS.put("navy", 0x000080);
        COLORS.put("fuchsia", 0xFF00FF);
        COLORS.put("purple", 0x800080);
        COLORS.put("orange", 0xFFA500);
        COLORS.put("gold", 0xFFAA00);

        List<String> glow = new ArrayList<>(List.of("off"));
        glow.addAll(COLORS.keySet());
        VALUES.put("size", List.of("0.5", "1", "2", "4"));
        VALUES.put("grow", List.of("10", "20", "40"));
        VALUES.put("speed", List.of("0.5", "1", "2"));
        VALUES.put("glow", glow);
        VALUES.put("bright", List.of("0", "8", "15"));
        VALUES.put("billboard", BILLBOARDS);
        VALUES.put("shadow", List.of("0.5", "1", "2"));
        VALUES.put("item", List.of());
        VALUES.put("block", List.of());
        VALUES.put("text", List.of());
        VALUES.put("align", ALIGNS);
        VALUES.put("textshadow", BOOLEANS);
        VALUES.put("seethrough", BOOLEANS);
        VALUES.put("width", List.of("100", "200", "400"));
        VALUES.put("range", List.of("16", "32", "64", "128"));
    }

    private Float size;
    private Integer grow;
    private Double speed;
    private boolean glowSet;
    private Integer glow;
    private Integer bright;
    private String billboard;
    private Float shadow;
    private String item;
    private String block;
    private String text;
    private String align;
    private Boolean textShadow;
    private Boolean seeThrough;
    private Integer width;
    private Double range;

    private DisplayOptions() {
    }

    // never throws, a bad word goes in the errors and the good ones still count
    static Parsed parse(String input) {
        DisplayOptions options = new DisplayOptions();
        List<String> errors = new ArrayList<>();
        String rest = input == null ? "" : input.strip();
        while (!rest.isEmpty()) {
            int end = indexOfSpace(rest);
            String token = end < 0 ? rest : rest.substring(0, end);
            int colon = token.indexOf(':');
            String key = colon < 0 ? token : token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = colon < 0 ? null : token.substring(colon + 1);
            // text takes the rest of the line, spaces and all
            if (key.equals("text") && value != null) {
                String all = rest.substring(colon + 1);
                if (all.isBlank()) {
                    errors.add("text: write some words after text:");
                } else {
                    options.text = all;
                }
                break;
            }
            rest = end < 0 ? "" : rest.substring(end).stripLeading();
            String error = value == null || value.isEmpty()
                    ? "'" + token + "' needs a value, write key:value. keys: " + String.join(", ", VALUES.keySet())
                    : options.set(key, value);
            if (error != null) {
                errors.add(error);
            }
        }
        return new Parsed(options, errors);
    }

    private static int indexOfSpace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private String set(String key, String value) {
        switch (key) {
            case "size" -> {
                Double number = number(value);
                if (number == null || number < 0 || number > MotionDisplay.MAX_SCALE) {
                    return "size: use 0 to " + (int) MotionDisplay.MAX_SCALE + ", got " + value;
                }
                size = number.floatValue();
            }
            case "grow" -> {
                Double number = number(value);
                if (number == null || number < 0 || number > MotionDisplay.MAX_SCALE_TICKS || number != Math.floor(number)) {
                    return "grow: use whole ticks, 0 to " + MotionDisplay.MAX_SCALE_TICKS + ", got " + value;
                }
                grow = number.intValue();
            }
            case "speed" -> {
                Double number = number(value);
                if (number == null || number < 0 || number > MotionDisplay.MAX_SPEED) {
                    return "speed: use 0 to " + (int) MotionDisplay.MAX_SPEED + ", got " + value;
                }
                speed = number;
            }
            case "glow" -> {
                if (value.equalsIgnoreCase("off")) {
                    glow = null;
                } else {
                    Integer rgb = color(value);
                    if (rgb == null) {
                        return "glow: use off, #rrggbb or " + String.join(", ", COLORS.keySet()) + ", got " + value;
                    }
                    glow = rgb;
                }
                glowSet = true;
            }
            case "bright" -> {
                Double number = number(value);
                if (number == null || number < 0 || number > 15 || number != Math.floor(number)) {
                    return "bright: use a whole number 0 to 15, got " + value;
                }
                bright = number.intValue();
            }
            case "billboard" -> {
                if (!BILLBOARDS.contains(value.toLowerCase(Locale.ROOT))) {
                    return "billboard: use " + String.join(", ", BILLBOARDS) + ", got " + value;
                }
                billboard = value.toLowerCase(Locale.ROOT);
            }
            case "shadow" -> {
                Double number = number(value);
                if (number == null || number < 0 || number > 64) {
                    return "shadow: use a radius 0 to 64, got " + value;
                }
                shadow = number.floatValue();
            }
            case "item" -> item = value;
            case "block" -> block = value;
            case "align" -> {
                if (!ALIGNS.contains(value.toLowerCase(Locale.ROOT))) {
                    return "align: use " + String.join(", ", ALIGNS) + ", got " + value;
                }
                align = value.toLowerCase(Locale.ROOT);
            }
            case "textshadow", "seethrough" -> {
                Boolean flag = value.equalsIgnoreCase("true") ? Boolean.TRUE : value.equalsIgnoreCase("false") ? Boolean.FALSE : null;
                if (flag == null) {
                    return key + ": use true or false, got " + value;
                }
                if (key.equals("textshadow")) {
                    textShadow = flag;
                } else {
                    seeThrough = flag;
                }
            }
            case "width" -> {
                Double number = number(value);
                if (number == null || number < 1 || number > 10000 || number != Math.floor(number)) {
                    return "width: use a whole number of 1 or more, got " + value;
                }
                width = number.intValue();
            }
            case "range" -> {
                Double number = number(value);
                if (number == null || number <= 0 || number > 10000) {
                    return "range: use blocks, more than 0, got " + value;
                }
                range = number;
            }
            default -> {
                return "unknown option '" + key + "'. keys: " + String.join(", ", VALUES.keySet());
            }
        }
        return null;
    }

    private static Double number(String value) {
        try {
            double number = Double.parseDouble(value);
            return Double.isNaN(number) || Double.isInfinite(number) ? null : number;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer color(String value) {
        Integer named = COLORS.get(value.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        if (value.length() == 7 && value.charAt(0) == '#') {
            try {
                return Integer.parseInt(value.substring(1), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    DisplayOptions withoutText() {
        DisplayOptions copy = new DisplayOptions();
        copy.size = size;
        copy.grow = grow;
        copy.speed = speed;
        copy.glowSet = glowSet;
        copy.glow = glow;
        copy.bright = bright;
        copy.billboard = billboard;
        copy.shadow = shadow;
        copy.item = item;
        copy.block = block;
        copy.align = align;
        copy.textShadow = textShadow;
        copy.seeThrough = seeThrough;
        copy.width = width;
        copy.range = range;
        return copy;
    }

    String text() {
        return text;
    }

    // returns the problems it could not apply, the rest is done
    List<String> apply(MotionDisplay display, Set<Part> parts) {
        List<String> errors = new ArrayList<>();
        if (grow != null && size == null) {
            errors.add("grow: needs size, like size:3 grow:20");
        }
        run(errors, () -> {
            if (size != null) {
                display.scale(size, grow == null ? 0 : grow);
            }
        });
        run(errors, () -> {
            if (speed != null) {
                display.speed(speed);
            }
        });
        run(errors, () -> {
            if (glowSet) {
                display.glow(glow == null ? null : Color.fromRGB(glow));
            }
        });
        run(errors, () -> {
            if (bright != null) {
                display.brightness(new Display.Brightness(bright, bright));
            }
        });
        run(errors, () -> {
            if (billboard != null) {
                display.billboard(Display.Billboard.valueOf(billboard.toUpperCase(Locale.ROOT)));
            }
        });
        run(errors, () -> {
            if (shadow != null) {
                display.shadow(shadow, 1f);
            }
        });
        run(errors, () -> {
            if (range != null) {
                display.viewRange(range);
            }
        });
        if (item != null) {
            if (!parts.contains(Part.ITEM)) {
                errors.add("item: this display has no item to swap");
            } else {
                Material material = Material.matchMaterial(item);
                if (material == null || material.isLegacy() || !material.isItem()) {
                    errors.add("item: use the name of an item, like diamond_sword, got " + item);
                } else {
                    run(errors, () -> display.item(new ItemStack(material)));
                }
            }
        }
        if (block != null) {
            if (!parts.contains(Part.BLOCK)) {
                errors.add("block: this display has no block to swap");
            } else {
                Material material = Material.matchMaterial(block);
                if (material == null || material.isLegacy() || !material.isBlock()) {
                    errors.add("block: use the name of a block, like gold_block, got " + block);
                } else {
                    run(errors, () -> display.block(material.createBlockData()));
                }
            }
        }
        boolean styled = text != null || align != null || textShadow != null || seeThrough != null || width != null;
        if (styled && !parts.contains(Part.TEXT)) {
            errors.add("text, align, textshadow, seethrough and width only work on a display with text, try /dm text");
        } else {
            run(errors, () -> {
                if (text != null) {
                    display.text(Component.text(text));
                }
                if (align != null) {
                    display.alignment(TextDisplay.TextAlignment.valueOf(align.toUpperCase(Locale.ROOT)));
                }
                if (textShadow != null) {
                    display.textShadow(textShadow);
                }
                if (seeThrough != null) {
                    display.seeThrough(seeThrough);
                }
                if (width != null) {
                    display.lineWidth(width);
                }
            });
        }
        return errors;
    }

    private static void run(List<String> errors, Runnable change) {
        try {
            change.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            errors.add(e.getMessage());
        }
    }

    // where the word that is being typed starts
    static int lastTokenStart(String typed) {
        int start = 0;
        for (int i = 0; i < typed.length(); i++) {
            if (Character.isWhitespace(typed.charAt(i))) {
                start = i + 1;
            }
        }
        return start;
    }

    // whole words for the last token: a key, or key:value, that starts like what is typed
    static List<String> suggest(String typed, Set<Part> parts, Function<Part, List<String>> materials) {
        int start = lastTokenStart(typed);
        String before = typed.substring(0, start).toLowerCase(Locale.ROOT);
        String last = typed.substring(start).toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        // after text: the rest of the line is the text
        for (String token : before.split("\\s+")) {
            if (token.startsWith("text:")) {
                return out;
            }
        }
        int colon = last.indexOf(':');
        if (colon < 0) {
            for (String key : VALUES.keySet()) {
                Part needs = NEEDS.get(key);
                boolean used = before.matches("(?s)(.*\\s)?" + key + ":.*");
                if (key.startsWith(last) && !used && (needs == null || parts.contains(needs))) {
                    out.add(key + ":");
                }
            }
            return out;
        }
        String key = last.substring(0, colon);
        String prefix = last.substring(colon + 1);
        Part needs = NEEDS.get(key);
        if (!VALUES.containsKey(key) || needs != null && !parts.contains(needs)) {
            return out;
        }
        List<String> values = key.equals("item") ? materials.apply(Part.ITEM)
                : key.equals("block") ? materials.apply(Part.BLOCK)
                : VALUES.get(key);
        for (String value : values) {
            if (value.startsWith(prefix)) {
                out.add(key + ":" + value);
            }
        }
        return out;
    }
}
