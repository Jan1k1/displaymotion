package org.jan1k.displaymotion.packetevents;

import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Decomposer;

final class Composer {

    private static final float BOX_MARGIN = 0.25f;
    // grow with some room so we dont send a new box every single frame while it pops in
    private static final float BOX_GROWTH = 1.25f;
    private static final float BELOW_TOLERANCE = 0.001f;

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
            if (!part.hasEntity()) {
                continue;
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
            if (cullBox(part, display.pitch == 0f)) {
                part.changes |= DisplayPacket.BOX;
            }
        }
        for (Track track : display.tracks) {
            if (track != null) {
                track.moved = false;
            }
        }
    }

    // client only culls a display when width and height arent 0 and the box goes from the feet up
    // so a part only gets a box while all of it is above the spawn point. box never shrinks
    private boolean cullBox(PartState part, boolean upright) {
        if (!upright || part.kind == PartState.TEXT || part.billboard != 0) {
            return noBox(part);
        }
        float low = part.isBlock() ? 0f : -0.5f;
        float high = low + 1f;
        float radius = 0f;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            float cx = (corner & 1) == 0 ? low : high;
            float cy = (corner & 2) == 0 ? low : high;
            float cz = (corner & 4) == 0 ? low : high;
            float x = local[0] * cx + local[3] * cy + local[6] * cz + local[9];
            float y = local[1] * cx + local[4] * cy + local[7] * cz + local[10];
            float z = local[2] * cx + local[5] * cy + local[8] * cz + local[11];
            radius = Math.max(radius, x * x + z * z);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (minY < -BELOW_TOLERANCE) {
            return noBox(part);
        }
        float width = 2f * (float) Math.sqrt(radius) + BOX_MARGIN;
        float height = maxY + BOX_MARGIN;
        if (width <= part.cullWidth && height <= part.cullHeight) {
            return false;
        }
        part.cullWidth = Math.max(part.cullWidth, width * BOX_GROWTH);
        part.cullHeight = Math.max(part.cullHeight, height * BOX_GROWTH);
        return true;
    }

    private static boolean noBox(PartState part) {
        if (part.cullWidth == 0f && part.cullHeight == 0f) {
            return false;
        }
        part.cullWidth = 0f;
        part.cullHeight = 0f;
        return true;
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
