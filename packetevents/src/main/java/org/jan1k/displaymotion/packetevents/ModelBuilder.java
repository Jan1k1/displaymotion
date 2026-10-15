package org.jan1k.displaymotion.packetevents;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.jan1k.displaymotion.Pose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModelBuilder {

    public static final int MAX_PARTS = 256;

    private final DisplayMotion motion;
    private final Location location;
    private final World world;
    private final List<PartState> parts = new ArrayList<>();
    private final Map<String, Integer> names = new HashMap<>();
    private boolean spawned;

    ModelBuilder(DisplayMotion motion, Location location, World world) {
        this.motion = motion;
        this.location = location;
        this.world = world;
    }

    public ModelBuilder block(String name, BlockData block, Pose pose) {
        return block(name, block, pose, null);
    }

    public ModelBuilder block(String name, BlockData block, Pose pose, String parent) {
        Objects.requireNonNull(block, "block");
        return add(name, null, 0, SpigotConversionUtil.fromBukkitBlockData(block).getGlobalId(), pose, parent);
    }

    public ModelBuilder item(String name, ItemStack item, Pose pose) {
        return item(name, item, ItemDisplay.ItemDisplayTransform.NONE, pose, null);
    }

    public ModelBuilder item(String name, ItemStack item, Pose pose, String parent) {
        return item(name, item, ItemDisplay.ItemDisplayTransform.NONE, pose, parent);
    }

    public ModelBuilder item(String name, ItemStack item, ItemDisplay.ItemDisplayTransform transform, Pose pose) {
        return item(name, item, transform, pose, null);
    }

    public ModelBuilder item(String name, ItemStack item, ItemDisplay.ItemDisplayTransform transform, Pose pose, String parent) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(transform, "transform");
        return add(name, SpigotConversionUtil.fromBukkitItemStack(item), transform.ordinal(), 0, pose, parent);
    }

    public MotionDisplay spawn() {
        if (spawned) {
            throw new IllegalStateException("this model was already spawned, make a new one with motion.model(location)");
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException("add at least one part with block(...) or item(...)");
        }
        spawned = true;
        return motion.spawn(location, parts.toArray(PartState[]::new));
    }

    private ModelBuilder add(String name, com.github.retrooper.packetevents.protocol.item.ItemStack item,
                             int itemTransform, int blockState, Pose pose, String parent) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pose, "pose");
        if (spawned) {
            throw new IllegalStateException("this model was already spawned");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("part name must not be blank");
        }
        if (names.containsKey(name)) {
            throw new IllegalArgumentException("there is already a part named '" + name + "'");
        }
        if (parts.size() >= MAX_PARTS) {
            throw new IllegalArgumentException("a model can have at most " + MAX_PARTS + " parts");
        }
        int parentIndex = -1;
        if (parent != null) {
            Integer index = names.get(parent);
            if (index == null) {
                throw new IllegalArgumentException("parent '" + parent + "' must be added before '" + name + "'");
            }
            parentIndex = index;
        }
        int entityId = Bukkit.getUnsafe().nextEntityId(world);
        names.put(name, parts.size());
        parts.add(new PartState(name, entityId, parentIndex, item, itemTransform, blockState, pose));
        return this;
    }
}
