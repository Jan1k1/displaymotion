package org.jan1k.displaymotion;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public final class DisplayMotionPlugin extends JavaPlugin implements Listener {

    private static final int SAMPLE_TICKS = 2;
    private static final int POP_TICKS = 20;
    // 9 degrees each step keeps the old spin speed
    private static final float STEP_DEGREES = 9f;

    private ItemDisplay display;
    private UUID owner;
    private BukkitTask popTask;
    private BukkitTask spinTask;
    private float yaw;
    private int popAge;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("displaymotion");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(owner)) {
            removeDisplay();
        }
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
        owner = player.getUniqueId();
        display = player.getWorld().spawn(player.getLocation().add(0, 1, 0), ItemDisplay.class, entity -> {
            entity.setItemStack(ItemStack.of(Material.DIAMOND_SWORD));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            // a restart should not bring it back
            entity.setPersistent(false);
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

        popAge = 0;
        popTask = getServer().getScheduler().runTaskTimer(this, this::popStep, 0L, SAMPLE_TICKS);
    }

    private void popStep() {
        if (display == null || !display.isValid()) {
            removeDisplay();
            return;
        }

        popAge += SAMPLE_TICKS;
        float t = popAge / (float) POP_TICKS;
        // client blend is a straight line, so the pop curve is sent in steps
        show(0f, 0f, Ease.out(t), SAMPLE_TICKS);

        if (t < 1f) {
            return;
        }

        popTask.cancel();
        popTask = null;
        spinTask = getServer().getScheduler().runTaskTimer(this, this::spin, SAMPLE_TICKS, SAMPLE_TICKS);
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
        show(bob, yaw, 1f, SAMPLE_TICKS);
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
        owner = null;
    }
}
