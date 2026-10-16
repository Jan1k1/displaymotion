package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Controls a mannequin part of a display, get it with {@link MotionDisplay#npc()} or {@link MotionDisplay#npc(String)}.
 * A mannequin is the vanilla player shaped entity from 1.21.9, sent as packets like the displays.
 * Every method is safe from any thread and returns this, so calls chain.
 */
public final class Npc {

    /** How far players can be for {@link #lookAtPlayers(double)} by default. */
    public static final double DEFAULT_LOOK_RANGE = 8.0;

    private final MotionDisplay display;
    private final MotionEngine engine;
    private final DisplayState state;
    private final int part;

    Npc(MotionDisplay display, MotionEngine engine, DisplayState state, int part) {
        this.display = display;
        this.engine = engine;
        this.state = state;
        this.part = part;
    }

    /** The name tag over its head. {@code null} hides it. */
    public Npc name(Component name) {
        submit(() -> engine.npc(state, part, npc -> npc.npcName = name, false));
        return this;
    }

    /** The small line under the name, vanilla shows "NPC" there. {@code null} hides it, that is the default. */
    public Npc description(Component description) {
        submit(() -> engine.npc(state, part, npc -> npc.description = description, false));
        return this;
    }

    public Npc pose(NpcPose pose) {
        Objects.requireNonNull(pose, "pose");
        submit(() -> engine.npc(state, part, npc -> npc.npcPose = pose, false));
        return this;
    }

    /** Puts an item in a hand or an armor slot. {@code null} empties it. */
    public Npc equip(EquipmentSlot slot, ItemStack item) {
        Objects.requireNonNull(slot, "slot");
        var packetSlot = NpcPackets.slot(slot);
        var packetItem = NpcPackets.item(item);
        submit(() -> engine.npc(state, part, npc -> npc.equipment.put(packetSlot, packetItem), true));
        return this;
    }

    /** Swings the main hand once, like a hit. */
    public Npc swing() {
        submit(() -> engine.npcSwing(state, part, false));
        return this;
    }

    /** Swings the off hand once. */
    public Npc swingOffHand() {
        submit(() -> engine.npcSwing(state, part, true));
        return this;
    }

    /**
     * Turns the head to every player closer than this, each player sees it look at them.
     * Farther players see it look ahead. {@code 0} stops it.
     */
    public Npc lookAtPlayers(double range) {
        if (!(range >= 0)) {
            throw new IllegalArgumentException("range must be 0 or more, got " + range);
        }
        submit(() -> engine.npcLook(state, part, range));
        return this;
    }

    /** {@link #lookAtPlayers(double)} with the default range of 8 blocks. */
    public Npc lookAtPlayers() {
        return lookAtPlayers(DEFAULT_LOOK_RANGE);
    }

    /** The display this mannequin is part of. */
    public MotionDisplay display() {
        return display;
    }

    private void submit(Runnable command) {
        if (display.isRemoved()) {
            throw new IllegalStateException("this display was removed");
        }
        engine.submit(command);
    }
}
