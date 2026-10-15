package org.jan1k.displaymotion.packetevents;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
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

/**
 * Builds a model from parts. A part can have a parent, which must be added first.
 * Offset and rotation of a pose pass to the children. Scale is the size of that part only.
 */
public final class ModelBuilder {

    public static final int MAX_PARTS = 256;
    public static final int MAX_TEXT_LENGTH = 32_000;

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

    /** Adds a block part. It turns and scales around its center. */
    public ModelBuilder block(String name, BlockData block, Pose pose) {
        return block(name, block, pose, null);
    }

    /** Adds a block part that moves with its parent. */
    public ModelBuilder block(String name, BlockData block, Pose pose, String parent) {
        Objects.requireNonNull(block, "block");
        int blockState = SpigotConversionUtil.fromBukkitBlockData(block).getGlobalId();
        return add(name, pose, parent, (id, parentIndex) -> PartState.block(name, id, parentIndex, blockState, pose));
    }

    /** Adds a text part. The text grows up from its pose. */
    public ModelBuilder text(String name, Component text, Pose pose) {
        return text(name, text, pose, null);
    }

    /** Adds a text part that moves with its parent. */
    public ModelBuilder text(String name, Component text, Pose pose, String parent) {
        checkText(text);
        return add(name, pose, parent, (id, parentIndex) -> PartState.text(name, id, parentIndex, text, pose));
    }

    /** Adds an item part. It turns around the item center. */
    public ModelBuilder item(String name, ItemStack item, Pose pose) {
        return item(name, item, ItemDisplay.ItemDisplayTransform.NONE, pose, null);
    }

    /** Adds an item part that moves with its parent. */
    public ModelBuilder item(String name, ItemStack item, Pose pose, String parent) {
        return item(name, item, ItemDisplay.ItemDisplayTransform.NONE, pose, parent);
    }

    /** Adds an item part drawn with this item transform. */
    public ModelBuilder item(String name, ItemStack item, ItemDisplay.ItemDisplayTransform transform, Pose pose) {
        return item(name, item, transform, pose, null);
    }

    /** Adds an item part with an item transform and a parent. */
    public ModelBuilder item(String name, ItemStack item, ItemDisplay.ItemDisplayTransform transform, Pose pose, String parent) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(transform, "transform");
        var converted = SpigotConversionUtil.fromBukkitItemStack(item);
        // bukkits enum is in the same order as the network ids, it just doesnt have ON_SHELF
        int itemTransform = transform.ordinal();
        return add(name, pose, parent, (id, parentIndex) -> PartState.item(name, id, parentIndex, converted, itemTransform, pose));
    }

    static void checkText(Component text) {
        Objects.requireNonNull(text, "text");
        // viaversion kicks old clients on huge text displays so keep it sane
        int length = GsonComponentSerializer.gson().serialize(text).length();
        if (length > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text is too big, " + length + " chars of json, the limit is " + MAX_TEXT_LENGTH);
        }
    }

    /** Spawns the model. Call {@code play} on the result in the same tick to avoid a frame at the rest pose. */
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

    private interface PartFactory {
        PartState create(int entityId, int parentIndex);
    }

    private ModelBuilder add(String name, Pose pose, String parent, PartFactory factory) {
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
        parts.add(factory.create(entityId, parentIndex));
        return this;
    }
}
