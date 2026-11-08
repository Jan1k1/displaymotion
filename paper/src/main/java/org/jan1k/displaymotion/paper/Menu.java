package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.Ease;
import org.jan1k.displaymotion.Pose;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A menu that stands in the world: buttons in a grid, each one an item with a label, and players click them.
 * Get one with {@link DisplayMotion#menu(Location)}, add buttons, then {@link #open()} it for everybody or
 * {@link #open(Player)} for one player only. It faces where the location looks, the location is its bottom center.
 * Call every method on the main thread.
 */
public final class Menu {

    public static final int DEFAULT_COLUMNS = 3;
    public static final int MAX_COLUMNS = 9;
    public static final int MAX_BUTTONS = 54;

    private static final float CELL = 1f;
    private static final float PANEL_SIZE = 0.84f;
    private static final float PANEL_DEPTH = 0.04f;
    private static final float PANEL_Z = -0.06f;
    private static final float ICON_SCALE = 0.5f;
    private static final float ICON_Y = 0.1f;
    private static final float LABEL_SCALE = 0.4f;
    private static final float LABEL_Y = -0.38f;
    // a font pixel is 1/40 of a block at scale 1, so the label wraps before it gets wider then the panel
    private static final int LABEL_WIDTH = Math.round(PANEL_SIZE / (LABEL_SCALE / 40f)) - 4;
    private static final float TITLE_SCALE = 0.8f;
    // narrower then the cell, the boxes stay upright and would overlap when the menu is turned 45 degrees
    private static final float HITBOX_WIDTH = 0.7f;
    private static final float HITBOX_HEIGHT = 0.9f;
    private static final int POP_TICKS = 8;
    private static final Color CLEAR = Color.fromARGB(0, 0, 0, 0);
    private static final Animation PRESS = Animation.keyframes()
            .at(0, Pose.IDENTITY)
            .at(2, Pose.IDENTITY.withScale(0.85f), Ease.OUT)
            .at(7, Pose.IDENTITY, Ease.BACK_OUT)
            .build();

    private record Button(String id, ItemStack item, Component label, Consumer<MenuClick> onClick) {
    }

    private final DisplayMotion motion;
    private final Location location;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private Component title;
    private int columns = DEFAULT_COLUMNS;
    private BlockData panel = Material.BLACK_STAINED_GLASS.createBlockData();
    private MotionDisplay display;
    private boolean closed;

    Menu(DisplayMotion motion, Location location) {
        this.motion = motion;
        this.location = location.clone();
        this.location.setPitch(0f);
    }

    /** A title above the buttons. {@code null} is no title, that is the default. Before {@link #open()}. */
    public Menu title(Component title) {
        requireNotOpen();
        this.title = title;
        return this;
    }

    /** How many buttons go in one row, 1 to 9. The default is 3. A last row that is not full is centered. */
    public Menu columns(int columns) {
        requireNotOpen();
        if (columns < 1 || columns > MAX_COLUMNS) {
            throw new IllegalArgumentException("columns is 1 to " + MAX_COLUMNS + ", got " + columns);
        }
        this.columns = columns;
        return this;
    }

    /** The block behind each button, a thin square. {@code null} is no panel. The default is black stained glass. */
    public Menu panel(BlockData panel) {
        requireNotOpen();
        this.panel = panel;
        return this;
    }

    /**
     * Adds a button. Buttons fill the rows from the top left. {@code onClick} runs on the main thread when a player
     * who can see the menu left or right clicks it, the button bumps too.
     * @throws IllegalArgumentException if the id is taken or blank, or there are already {@link #MAX_BUTTONS}
     */
    public Menu button(String id, ItemStack item, Component label, Consumer<MenuClick> onClick) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(onClick, "onClick");
        requireNotOpen();
        ModelBuilder.checkText(label);
        if (id.isBlank()) {
            throw new IllegalArgumentException("button id must not be blank");
        }
        if (buttons.containsKey(id)) {
            throw new IllegalArgumentException("there is already a button '" + id + "'");
        }
        if (buttons.size() >= MAX_BUTTONS) {
            throw new IllegalArgumentException("a menu has at most " + MAX_BUTTONS + " buttons");
        }
        buttons.put(id, new Button(id, item.clone(), label, onClick));
        return this;
    }

    /** Spawns the menu for every player. */
    public Menu open() {
        return open(null);
    }

    /** Spawns the menu that only this player sees and can click. */
    public Menu open(Player player) {
        requireNotOpen();
        if (buttons.isEmpty()) {
            throw new IllegalStateException("add a button first");
        }
        ModelBuilder model = motion.model(location);
        int index = 0;
        int rows = 0;
        for (Button button : buttons.values()) {
            float[] cell = cell(index++, buttons.size(), columns);
            rows = Math.max(rows, Math.round(cell[1] / CELL) + 1);
            String group = part(button.id);
            model.group(group, Pose.IDENTITY.withOffset(cell[0], cell[1] + CELL / 2f, 0f));
            if (panel != null) {
                model.block(group + ":panel", panel, Pose.IDENTITY.withOffset(0f, 0f, PANEL_Z)
                        .withScale(PANEL_SIZE, PANEL_SIZE, PANEL_DEPTH), group);
            }
            model.item(group + ":item", button.item, ItemDisplay.ItemDisplayTransform.GUI,
                    Pose.IDENTITY.withOffset(0f, ICON_Y, 0f).withScale(ICON_SCALE), group);
            model.text(group + ":label", button.label, Pose.IDENTITY.withOffset(0f, LABEL_Y, 0f).withScale(LABEL_SCALE), group);
        }
        if (title != null) {
            model.text("title", title, Pose.IDENTITY.withOffset(0f, rows * CELL + 0.1f, 0f).withScale(TITLE_SCALE));
        }
        display = model.spawn();
        if (player != null) {
            display.visibleByDefault(false).show(player);
        }
        index = 0;
        for (Button button : buttons.values()) {
            float[] cell = cell(index++, buttons.size(), columns);
            String label = part(button.id) + ":label";
            display.background(label, CLEAR)
                    .lineWidth(label, LABEL_WIDTH)
                    .hitbox(button.id, HITBOX_WIDTH, HITBOX_HEIGHT, cell[0], cell[1] + (CELL - HITBOX_HEIGHT) / 2f, 0);
        }
        display.onClick(this::clicked).play(Animations.popIn(POP_TICKS));
        return this;
    }

    /** Changes the label of a button for everybody. */
    public Menu label(String id, Component label) {
        display().text(part(require(id)) + ":label", label);
        return this;
    }

    /** Changes the label of a button for one player only, the others keep theirs. */
    public Menu label(Player player, String id, Component label) {
        display().text(player, part(require(id)) + ":label", label);
        return this;
    }

    /** Swaps the item of a button. */
    public Menu item(String id, ItemStack item) {
        display().item(part(require(id)) + ":item", item);
        return this;
    }

    /** Pops the menu out and removes it. Safe to call more than once. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (display != null && !display.isRemoved()) {
            display.play(Animations.popOut(POP_TICKS), display::remove);
        }
    }

    /** True after {@link #open()} and before {@link #close()}. */
    public boolean isOpen() {
        return display != null && !closed && !display.isRemoved();
    }

    /** The button ids, in the order they were added. */
    public List<String> buttons() {
        return List.copyOf(buttons.keySet());
    }

    /**
     * The display the menu is made of, for glow, hide, show and the like. Its parts are named
     * {@code button:<id>} with {@code :panel}, {@code :item} and {@code :label} under it, and {@code title}.
     * @throws IllegalStateException before {@link #open()}
     */
    public MotionDisplay display() {
        if (display == null) {
            throw new IllegalStateException("open the menu first");
        }
        return display;
    }

    // main thread, from the display
    private void clicked(Click click) {
        Button button = buttons.get(click.hitbox());
        if (button == null || closed || !click.display().canSee(click.player())) {
            return;
        }
        display.play(part(button.id), PRESS);
        button.onClick.accept(new MenuClick(click.player(), this, button.id, click.leftClick()));
    }

    // x and the bottom y of a button, rows go down from the top and a last row that is not full is centered
    static float[] cell(int index, int count, int columns) {
        int perRow = Math.min(columns, count);
        int rows = Math.ceilDiv(count, perRow);
        int row = index / perRow;
        int inRow = row == rows - 1 ? count - row * perRow : perRow;
        float x = (index % perRow - (inRow - 1) / 2f) * CELL;
        float y = (rows - 1 - row) * CELL;
        return new float[]{x, y};
    }

    private static String part(String id) {
        return "button:" + id;
    }

    private String require(String id) {
        if (!buttons.containsKey(Objects.requireNonNull(id, "id"))) {
            throw new IllegalArgumentException("no button '" + id + "', buttons are " + buttons.keySet());
        }
        return id;
    }

    private void requireNotOpen() {
        if (display != null) {
            throw new IllegalStateException("the menu is already open, add buttons and set it up before open()");
        }
    }
}
