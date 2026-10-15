package org.jan1k.displaymotion.paper;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// the server never works out where a player body faces while walking, it only snaps the position.
// the client does, so to put a backpack on the right side we run the same steps here for every player,
// from the join on, because the client starts the body at 0 and it depends on everything after that.
// all from LivingEntity.tick and tickHeadTurn in 1.21.11, main thread only
final class Bodies {

    private static final double WALK_SQUARED = 0.0025;
    // more then this in one tick is a teleport, the client does not turn the body for those
    private static final double TELEPORT_SQUARED = 4.0;
    private static final float FOLLOW = 0.3f;
    private static final float HEAD_LIMIT = 50f;
    private static final float SHIELD_HEAD_LIMIT = 15f;
    private static final int SWING_TICKS = 6;

    private static final class Body {
        float yaw;
        double x;
        double z;
        long swingUntil;
    }

    private final Map<UUID, Body> bodies = new HashMap<>();
    private long tick;

    void tick(Iterable<? extends Player> players, Location scratch) {
        tick++;
        for (Player player : players) {
            player.getLocation(scratch);
            Body body = bodies.get(player.getUniqueId());
            if (body == null) {
                body = new Body();
                // the server body went thru the same head limit, so it is right as long as nobody walked yet
                body.yaw = player.getBodyYaw();
                body.x = scratch.getX();
                body.z = scratch.getZ();
                bodies.put(player.getUniqueId(), body);
            }
            turn(body, player, scratch);
        }
    }

    private void turn(Body body, Player player, Location at) {
        double dx = at.getX() - body.x;
        double dz = at.getZ() - body.z;
        body.x = at.getX();
        body.z = at.getZ();
        float head = at.getYaw();
        float target = body.yaw;
        double moved = dx * dx + dz * dz;
        if (moved > WALK_SQUARED && moved < TELEPORT_SQUARED) {
            target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            // walking backwards keeps the body facing forward
            float away = Math.abs(wrap(head) - target);
            if (away > 95f && away < 265f) {
                target -= 180f;
            }
        }
        if (tick < body.swingUntil) {
            target = head;
        }
        body.yaw += wrap(target - body.yaw) * FOLLOW;
        float limit = player.isBlocking() ? SHIELD_HEAD_LIMIT : HEAD_LIMIT;
        float headAway = wrap(head - body.yaw);
        if (Math.abs(headAway) > limit) {
            body.yaw += headAway - Math.signum(headAway) * limit;
        }
        body.yaw = wrap(body.yaw);
    }

    float yaw(Player player) {
        Body body = bodies.get(player.getUniqueId());
        return body == null ? player.getBodyYaw() : body.yaw;
    }

    void swing(Player player) {
        Body body = bodies.get(player.getUniqueId());
        if (body != null) {
            body.swingUntil = tick + SWING_TICKS;
        }
    }

    // respawn and a new world make a new player on the client, its body starts at 0 again
    void reset(Player player) {
        Body body = bodies.get(player.getUniqueId());
        if (body != null) {
            body.yaw = 0f;
            body.x = player.getLocation().getX();
            body.z = player.getLocation().getZ();
        }
    }

    void forget(UUID player) {
        bodies.remove(player);
    }

    static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}
