package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class Viewer {

    // vanilla allows 4096 packets in one bundle
    private static final int MAX_BUNDLE = 4000;

    final UUID id;
    final Set<DisplayState> visible = new HashSet<>();
    private final List<Object> pending = new ArrayList<>();

    Object channel;
    UUID world;
    double x;
    double y;
    double z;
    long seen;
    long waitFor;
    boolean waiting;

    Viewer(UUID id) {
        this.id = id;
    }

    void update(PlayerView view, long tick, long sequence) {
        channel = view.channel();
        world = view.world();
        x = view.x();
        y = view.y();
        z = view.z();
        seen = tick;
        waiting = sequence < waitFor;
    }

    boolean queue(Object buffer) {
        pending.add(buffer);
        return pending.size() == 1;
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    Object[] drain(Object bundle) {
        int count = pending.size();
        Object[] out;
        if (count == 1) {
            out = new Object[]{pending.getFirst()};
        } else {
            out = new Object[count + Math.ceilDiv(count, MAX_BUNDLE) * 2];
            int o = 0;
            for (int i = 0; i < count; i++) {
                if (i % MAX_BUNDLE == 0) {
                    out[o++] = ByteBufHelper.retainedDuplicate(bundle);
                }
                out[o++] = pending.get(i);
                if (i % MAX_BUNDLE == MAX_BUNDLE - 1 || i == count - 1) {
                    out[o++] = ByteBufHelper.retainedDuplicate(bundle);
                }
            }
        }
        pending.clear();
        return out;
    }

    void drop() {
        for (Object buffer : pending) {
            ByteBufHelper.release(buffer);
        }
        pending.clear();
    }
}
