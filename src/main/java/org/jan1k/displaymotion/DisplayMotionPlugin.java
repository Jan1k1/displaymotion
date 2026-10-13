package org.jan1k.displaymotion;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DisplayMotionPlugin extends JavaPlugin {

    private static final int STEP_TICKS = 10;
    private static final int POP_TICKS = 20;
    private static final float STEP_DEGREES = 45f;

    private ItemDisplay display;
    private BukkitTask popTask;
    private BukkitTask spinTask;
    private float yaw;

    @Override
    public void onEnable() {
        getLogger().info("displaymotion");
    }

    @Override
    public void onDisable() {
        removeDisplay();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            removeDisplay();
            sender.sendPlainMessage("removed");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendPlainMessage("players only");
            return true;
        }

        removeDisplay();
        yaw = 0f;
        display = player.getWorld().spawn(player.getLocation().add(0, 1, 0), ItemDisplay.class, entity -> {
            entity.setItemStack(ItemStack.of(Material.DIAMOND_SWORD));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            // duration 0 never saves the tiny pose, so the grow starts at full size
            entity.setInterpolationDelay(-1);
            entity.setInterpolationDuration(1);
            entity.setTransformation(pose(0f, 0f, 0f));
        });
        popTask = getServer().getScheduler().runTaskLater(this, this::popIn, 3L);
        return true;
    }

    private void popIn() {
        popTask = null;
        if (display == null || !display.isValid()) {
            return;
        }

        show(0f, 0f, 1f, POP_TICKS);
        spinTask = getServer().getScheduler().runTaskTimer(this, this::spin, POP_TICKS, STEP_TICKS);
    }

    private void spin() {
        if (display == null || !display.isValid()) {
            removeDisplay();
            return;
        }

        yaw += STEP_DEGREES;
        if (yaw >= 360f) {
            yaw -= 360f;
        }

        float bob = (float) Math.sin(Math.toRadians(yaw)) * 0.3f;
        show(bob, yaw, 1f, STEP_TICKS);
    }

    private void show(float bob, float yawDegrees, float scale, int ticks) {
        // client fills the frames. delay must change or it will not send again
        display.setInterpolationDelay(-1);
        display.setInterpolationDuration(ticks);
        display.setInterpolationDelay(0);
        display.setTransformation(pose(bob, yawDegrees, scale));
    }

    private Transformation pose(float bob, float yawDegrees, float scale) {
        return new Transformation(
                new Vector3f(0f, bob, 0f),
                new Quaternionf().rotationY((float) Math.toRadians(yawDegrees)),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        );
    }

    private void removeDisplay() {
        if (popTask != null) {
            popTask.cancel();
            popTask = null;
        }
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
        if (display != null) {
            display.remove();
            display = null;
        }
    }
}
