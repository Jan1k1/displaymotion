package org.jan1k.displaymotion.example;

import org.bukkit.Material;
import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Frames;
import org.jan1k.displaymotion.paper.ModelBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void everyBoneHasARidePose() {
        for (Dragon.Piece piece : Dragon.design(Dragon.Kind.FIRE, false, 0, 1).pieces()) {
            if (piece.isBone()) {
                DragonRide.pose(piece.name());
            }
        }
    }

    // the dragon turns around you, so the seat point has to stay where it is
    @Test
    void theSeatStaysPutWhenItTurns() {
        DragonRide.Flight flight = new DragonRide.Flight(0f, 0f, 0f, 1f, 0f, 1f, 0.5f, -20f, -25f, 130f, 2f, 10f, 5f, 0f, 0f, 1000f);
        float[] m = new float[Affine.SIZE];
        Affine.fromPose(DragonRide.root(flight), m, 0);
        Dragon.Vec s = DragonRide.SEAT;
        assertEquals(0f, m[0] * s.x() + m[3] * s.y() + m[6] * s.z() + m[9], 1e-4f);
        assertEquals(0f, m[1] * s.x() + m[4] * s.y() + m[7] * s.z() + m[10], 1e-4f);
        assertEquals(0f, m[2] * s.x() + m[5] * s.y() + m[8] * s.z() + m[11], 1e-4f);
    }

    @Test
    void landingGroundAndBoostCooldown() {
        assertTrue(DragonRide.solidGround(Material.STONE, true, false));
        assertFalse(DragonRide.solidGround(Material.AIR, false, false));
        assertFalse(DragonRide.solidGround(Material.SHORT_GRASS, false, false));
        assertFalse(DragonRide.solidGround(Material.OAK_LEAVES, true, false));
        assertFalse(DragonRide.solidGround(Material.WATER, false, true));
        assertFalse(DragonRide.solidGround(Material.LAVA, false, true));
        assertTrue(DragonRide.unlandable(Material.OAK_LEAVES, false));
        assertTrue(DragonRide.unlandable(Material.WATER, true));
        assertEquals("boost: 4.0s", DragonRide.cooldownText(80));
        assertEquals("boost: 1.5s", DragonRide.cooldownText(30));
    }
}
