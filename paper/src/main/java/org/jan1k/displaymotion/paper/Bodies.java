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
        // what other players see
        float yaw;
        // what the player sees on itself in F5. LocalPlayer never gets a body turn from the server,
        // it starts at 0 and only these steps move it, so it can be up to 50 off from the others
        float own;
        // a new player entity for the others, their game starts its body at the head turn (LivingEntity.recreateFromPacket)
        boolean spawned;
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
                body.own = 0f;
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
        if (body.spawned) {
            body.spawned = false;
            body.yaw = head;
        }
        float target = Float.NaN;
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
        float limit = player.isBlocking() ? SHIELD_HEAD_LIMIT : HEAD_LIMIT;
        body.yaw = step(body.yaw, target, head, limit);
        body.own = step(body.own, target, head, limit);
    }

    // standing still the target is the body itself, so NaN means stay
    static float step(float yaw, float target, float head, float limit) {
        if (!Float.isNaN(target)) {
            yaw += wrap(target - yaw) * FOLLOW;
        }
        float headAway = wrap(head - yaw);
        if (Math.abs(headAway) > limit) {
            yaw += headAway - Math.signum(headAway) * limit;
        }
        return wrap(yaw);
    }

    float yaw(Player player) {
        Body body = bodies.get(player.getUniqueId());
        return body == null ? player.getBodyYaw() : body.yaw;
    }

    // the body turn the player sees on itself
    float own(Player player) {
        Body body = bodies.get(player.getUniqueId());
        return body == null ? player.getBodyYaw() : body.own;
    }

    void swing(Player player) {
        Body body = bodies.get(player.getUniqueId());
        if (body != null) {
            body.swingUntil = tick + SWING_TICKS;
        }
    }

    // respawn and a new world make a new player on every client. its own body starts at 0 again,
    // the others start it at the head turn it has when it shows up
    void reset(Player player) {
        Body body = bodies.get(player.getUniqueId());
        if (body != null) {
            body.own = 0f;
            body.spawned = true;
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
