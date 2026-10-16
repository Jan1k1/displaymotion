package org.jan1k.displaymotion.example;

import org.jan1k.displaymotion.Frames;
import org.jan1k.displaymotion.paper.ModelBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonTest {

    @Test
    void everyDragonFitsInTwoModels() {
        for (Dragon.Kind kind : Dragon.Kind.values()) {
            for (boolean build : new boolean[]{false, true}) {
                Dragon.Design design = Dragon.design(kind, build, 0, 1);
                for (int model = 0; model < 2; model++) {
                    List<Dragon.Piece> pieces = design.model(model);
                    assertTrue(pieces.size() <= ModelBuilder.MAX_PARTS, kind + " model " + model + " has " + pieces.size() + " parts");
                    Set<String> names = new HashSet<>();
                    for (Dragon.Piece piece : pieces) {
                        assertTrue(names.add(piece.name()), "two parts named " + piece.name());
                        if (piece.parent() != null) {
                            assertTrue(names.contains(piece.parent()), piece.name() + " comes before its parent");
                        }
                        if (piece.animation() != null) {
                            Frames.check(piece.animation());
                        }
                    }
                }
                Frames.check(design.root());
            }
        }
    }

    @Test
    void itsLifeLoopsWithoutAJump() {
        Dragon.Design design = Dragon.design(Dragon.Kind.FIRE, false, 0, 1);
        for (Dragon.Piece piece : design.pieces()) {
            if (piece.animation() == null) {
                continue;
            }
            var start = piece.animation().poseAt(0f);
            var end = piece.animation().poseAt(Dragon.CYCLE - 0.001f);
            assertEquals(start.pitch(), end.pitch(), 0.5f, piece.name());
            assertEquals(start.roll(), end.roll(), 0.5f, piece.name());
            assertEquals(start.y(), end.y(), 0.05f, piece.name());
        }
    }
}
