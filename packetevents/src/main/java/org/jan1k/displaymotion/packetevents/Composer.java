package org.jan1k.displaymotion.packetevents;

import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Decomposer;

final class Composer {

    private final Decomposer decomposer = new Decomposer();
    private final float[] root = new float[Affine.SIZE];
    private final float[] frame = new float[Affine.SIZE];
    private final float[] local = new float[Affine.SIZE];
    private final float[] current = new float[Decomposer.SIZE];
    private final boolean[] dirty = new boolean[ModelBuilder.MAX_PARTS];

    void compose(DisplayState display, boolean everything) {
        Track whole = display.tracks[0];
        boolean rootMoved = everything || whole != null && whole.moved;
        if (whole != null) {
            whole.frames.copyTo(whole.frame, root, 0);
        } else {
            Affine.identity(root, 0);
        }

        PartState[] parts = display.parts;
        for (int i = 0; i < parts.length; i++) {
            PartState part = parts[i];
            Track track = display.tracks[i + 1];
            boolean needed = part.parent < 0 ? rootMoved : dirty[part.parent];
            needed |= everything || track != null && track.moved;
            dirty[i] = needed;
            part.changes = 0;
            if (!needed) {
                continue;
            }

            float[] base = part.parent < 0 ? root : parts[part.parent].world;
            Affine.multiply(base, 0, part.rest, 0, part.world, 0);
            if (track != null) {
                track.frames.copyTo(track.frame, frame, 0);
                Affine.multiply(part.world, 0, frame, 0, part.world, 0);
            }
            System.arraycopy(part.world, 0, local, 0, Affine.SIZE);
            Affine.scale(local, 0, part.sizeX, part.sizeY, part.sizeZ);
            if (part.isBlock()) {
                Affine.translate(local, 0, -0.5f, -0.5f, -0.5f);
            }
            decomposer.decompose(local, 0, current, 0);
            part.changes = changes(part.sent, current);
            if (part.changes != 0) {
                System.arraycopy(current, 0, part.sent, 0, Decomposer.SIZE);
            }
        }
        for (Track track : display.tracks) {
            if (track != null) {
                track.moved = false;
            }
        }
    }

    private static int changes(float[] sent, float[] now) {
        int changes = 0;
        if (differs(sent, now, Decomposer.TX, Decomposer.TZ)) {
            changes |= DisplayPacket.TRANSLATION;
        }
        if (differs(sent, now, Decomposer.LX, Decomposer.LW)) {
            changes |= DisplayPacket.LEFT_ROTATION;
        }
        if (differs(sent, now, Decomposer.SX, Decomposer.SZ)) {
            changes |= DisplayPacket.SCALE;
        }
        if (differs(sent, now, Decomposer.RX, Decomposer.RW)) {
            changes |= DisplayPacket.RIGHT_ROTATION;
        }
        return changes;
    }

    private static boolean differs(float[] a, float[] b, int from, int to) {
        for (int i = from; i <= to; i++) {
            if (Float.compare(a[i], b[i]) != 0) {
                return true;
            }
        }
        return false;
    }
}
