package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// a text as a flat list of pieces with the style each one ends up with, so we can show only the first
// n chars and keep the colors. typing "Welcome to " white then "Spawn" gold needs this, plain substring loses it
final class TextCut {

    private record Piece(String text, Style style) {
    }

    private final List<Piece> pieces = new ArrayList<>();
    private final int length;

    TextCut(Component text) {
        Deque<Style> styles = new ArrayDeque<>();
        styles.push(Style.empty());
        ComponentFlattener.basic().flatten(text, new FlattenerListener() {
            @Override
            public void pushStyle(Style style) {
                styles.push(styles.peek().merge(style));
            }

            @Override
            public void component(String piece) {
                if (!piece.isEmpty()) {
                    pieces.add(new Piece(piece, styles.peek()));
                }
            }

            @Override
            public void popStyle(Style style) {
                styles.pop();
            }
        });
        int count = 0;
        for (Piece piece : pieces) {
            count += piece.text.codePointCount(0, piece.text.length());
        }
        length = count;
    }

    // chars, not java chars, so an emoji or a ▲ counts as one
    int length() {
        return length;
    }

    Component first(int chars) {
        TextComponent.Builder out = Component.text();
        int left = chars;
        for (Piece piece : pieces) {
            if (left <= 0) {
                break;
            }
            int count = piece.text.codePointCount(0, piece.text.length());
            String shown = count <= left ? piece.text : piece.text.substring(0, piece.text.offsetByCodePoints(0, left));
            out.append(Component.text(shown, piece.style));
            left -= count;
        }
        return out.build();
    }
}
