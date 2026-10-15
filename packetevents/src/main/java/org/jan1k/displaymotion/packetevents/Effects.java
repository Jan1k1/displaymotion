package org.jan1k.displaymotion.packetevents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
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

    private static final Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);
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

    /** A gravestone with this name rises out of the ground, stays 3 seconds and sinks back. */
    public MotionDisplay tombstone(Location at, Component name) {
        Objects.requireNonNull(name, "name");
        BlockData stone = Material.POLISHED_ANDESITE.createBlockData();
        MotionDisplay display = motion.model(at)
                .block("base", stone, Pose.IDENTITY.withOffset(0f, 0.08f, 0f).withScale(0.9f, 0.16f, 0.55f))
                .block("stone", stone, Pose.IDENTITY.withOffset(0f, 0.62f, 0f).withScale(0.62f, 0.95f, 0.16f))
                .block("cap", stone, Pose.IDENTITY.withOffset(0f, 1.12f, 0f).withScale(0.72f, 0.1f, 0.2f))
                .block("moss", Material.MOSS_BLOCK.createBlockData(), Pose.IDENTITY
                        .withOffset(-0.2f, 0.22f, 0.02f).withScale(0.26f, 0.12f, 0.18f))
                .text("rip", Component.text("RIP", NamedTextColor.DARK_GRAY, TextDecoration.BOLD), Pose.IDENTITY
                        .withOffset(0f, 0.8f, 0.085f).withScale(0.7f))
                .text("name", name, Pose.IDENTITY.withOffset(0f, 0.5f, 0.085f).withScale(0.4f))
                .spawn()
                .background(TRANSPARENT);

        // starts under the ground, so the ground hides it while it comes up
        Animation rise = Animation.keyframes()
                .at(0, Pose.IDENTITY.withOffset(0f, -1.3f, 0f).withRotation(0f, 0f, 6f))
                .at(14, Pose.IDENTITY, Ease.OUT)
                .at(74, Pose.IDENTITY)
                .at(90, Pose.IDENTITY.withOffset(0f, -1.3f, 0f), Ease.IN)
                .build();
        return display.play(rise, display::remove);
    }

    private static float spread(ThreadLocalRandom random, float amount) {
        return (random.nextFloat() * 2f - 1f) * amount;
    }
}
