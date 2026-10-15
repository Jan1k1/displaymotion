package org.jan1k.displaymotion.packetevents;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.Ease;
import org.jan1k.displaymotion.Pose;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ready-made effects, for example kill effects. Each one spawns, plays and removes itself.
 * Get it with {@link DisplayMotion#effects()}.
 */
public final class Effects {

    private static final int SHATTER_PIECES = 10;
    private static final int SHATTER_TICKS = 34;

    private final DisplayMotion motion;

    Effects(DisplayMotion motion) {
        this.motion = motion;
    }

    /** The block breaks into small pieces that fly out, spin, fall and shrink away. */
    public MotionDisplay shatter(Location at, BlockData block) {
        Objects.requireNonNull(block, "block");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ModelBuilder model = motion.model(at);
        Animation[] paths = new Animation[SHATTER_PIECES];
        for (int i = 0; i < SHATTER_PIECES; i++) {
            float size = 0.22f + random.nextFloat() * 0.14f;
            model.block("piece_" + i, block, Pose.IDENTITY
                    .withOffset(spread(random, 0.25f), 0.5f + spread(random, 0.25f), spread(random, 0.25f))
                    .withRotation(random.nextFloat() * 90f, random.nextFloat() * 90f, 0f)
                    .withScale(size));

            // fly up and out fast, then drop, then shrink where it landed
            double angle = random.nextDouble() * Math.PI * 2;
            float distance = 0.8f + random.nextFloat() * 0.9f;
            float x = (float) Math.cos(angle) * distance;
            float z = (float) Math.sin(angle) * distance;
            float spinX = spread(random, 540f);
            float spinZ = spread(random, 540f);
            paths[i] = Animation.keyframes()
                    .at(0, Pose.IDENTITY)
                    .at(9, Pose.IDENTITY.withOffset(x * 0.6f, 0.7f + random.nextFloat() * 0.5f, z * 0.6f)
                            .withRotation(spinX * 0.4f, 0f, spinZ * 0.4f), Ease.OUT)
                    .at(24, Pose.IDENTITY.withOffset(x, -0.45f, z).withRotation(spinX, 0f, spinZ), Ease.IN)
                    .at(SHATTER_TICKS, Pose.IDENTITY.withOffset(x, -0.45f, z).withRotation(spinX, 0f, spinZ).withScale(0f), Ease.IN)
                    .build();
        }
        MotionDisplay display = model.spawn();
        for (int i = 0; i < SHATTER_PIECES; i++) {
            display.play("piece_" + i, paths[i]);
        }
        return display.play(Animations.hold(SHATTER_TICKS + 2), display::remove);
    }

    private static float spread(ThreadLocalRandom random, float amount) {
        return (random.nextFloat() * 2f - 1f) * amount;
    }
}
