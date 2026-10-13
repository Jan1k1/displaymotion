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
    private static final float STEP_DEGREES = 45f;

    private ItemDisplay display;
    private BukkitTask spinTask;
    private float yaw;

    @Override
    public void onEnable() {
        getLogger().info("displaymotion");
    }

    @Override
    public void onDisable() {
        stopSpin();
        if (display != null) {
            display.remove();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendPlainMessage("players only");
            return true;
        }

        // last one stays in the world until we remove it
        stopSpin();
        if (display != null) {
            display.remove();
        }

        yaw = 0f;
        display = player.getWorld().spawn(player.getLocation().add(0, 1, 0), ItemDisplay.class, entity -> {
            entity.setItemStack(ItemStack.of(Material.DIAMOND_SWORD));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        });
        spinTask = getServer().getScheduler().runTaskTimer(this, this::spin, STEP_TICKS, STEP_TICKS);
        return true;
    }

    private void spin() {
        if (display == null || !display.isValid()) {
            stopSpin();
            return;
        }

        yaw += STEP_DEGREES;
        if (yaw >= 360f) {
            yaw -= 360f;
        }

        // client fills the frames. delay must change or it will not send again
        float bob = (float) Math.sin(Math.toRadians(yaw)) * 0.3f;
        display.setInterpolationDelay(-1);
        display.setInterpolationDuration(STEP_TICKS);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(
                new Vector3f(0f, bob, 0f),
                new Quaternionf().rotationY((float) Math.toRadians(yaw)),
                new Vector3f(1f, 1f, 1f),
                new Quaternionf()
        ));
    }

    private void stopSpin() {
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
    }
}
