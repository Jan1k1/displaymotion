package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.Ease;
import org.jan1k.displaymotion.Pose;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * A leaderboard that moves: rows glide to their new rank, new names pop in, scores count up.
 * Get one with {@link DisplayMotion#leaderboard(Location)}, then call {@link #update(List)} as often as you want.
 * The board faces where the location looks. Call every method on the main thread.
 */
public final class Leaderboard {

    public static final int DEFAULT_ROWS = 10;
    public static final int MAX_ROWS = 50;

    private static final float ROW_HEIGHT = 0.32f;
    private static final float TEXT_SCALE = 0.8f;
    private static final float TITLE_SCALE = 1.3f;
    // a space is 4 font pixels, so 32 of them make a panel 3.2 blocks wide, from -1.6 to 1.6
    private static final String PANEL = " ".repeat(32);
    private static final float RANK_X = -1.4f;
    private static final float ICON_X = -1.08f;
    private static final float NAME_LEFT = -0.88f;
    private static final float SCORE_X = 1.02f;
    private static final float ARROW_X = 1.46f;
    private static final float TEXT_Y = 0.02f;
    private static final int GLIDE_TICKS = 10;
    private static final int POP_TICKS = 8;
    private static final int STAGGER_TICKS = 2;
    private static final int COUNT_TICKS = 20;
    private static final int COUNT_STEP_TICKS = 2;
    private static final long ARROW_TICKS = 40L;
    private static final Color CLEAR = Color.fromARGB(0, 0, 0, 0);
    private static final Color[] PANELS = {
            Color.fromARGB(0x90, 0xB8, 0x86, 0x0B),
            Color.fromARGB(0x80, 0x8A, 0x92, 0xA0),
            Color.fromARGB(0x80, 0x8B, 0x55, 0x2E),
    };
    private static final Color PANEL_REST = Color.fromARGB(0x60, 0x10, 0x12, 0x18);
    private static final TextColor[] MEDALS = {TextColor.color(0xFFD54A), TextColor.color(0xE3E8F0), TextColor.color(0xE0955B)};
    private static final TextColor UP = TextColor.color(0x8EA6FF);
    private static final Animation BUMP = Animation.keyframes()
            .at(0, Pose.IDENTITY)
            .at(3, Pose.IDENTITY.withScale(1.25f), Ease.OUT)
            .at(8, Pose.IDENTITY, Ease.IN_OUT)
            .build();

    /**
     * One line of the board.
     * @param id what makes two updates the same row, for example the player UUID as text
     * @param name what the row shows
     * @param score the number it is sorted by, highest first
     * @param icon an item in front of the name, for example {@link #head(OfflinePlayer)}, or {@code null}
     */
    public record Entry(String id, Component name, long score, ItemStack icon) {

        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }

        public static Entry of(String id, Component name, long score) {
            return new Entry(id, name, score, null);
        }

        /** The same entry with this icon. */
        public Entry icon(ItemStack icon) {
            return new Entry(id, name, score, icon);
        }
    }

    private static final class Row {
        final MotionDisplay display;
        int rank;
        long shown;
        long from;
        long to;
        int countTick = -1;
        BukkitTask arrow;

        Row(MotionDisplay display, int rank, long score) {
            this.display = display;
            this.rank = rank;
            this.shown = score;
            this.to = score;
        }
    }

    private final DisplayMotion motion;
    private final Plugin plugin;
    private final Location origin;
    private final Map<String, Row> rows = new HashMap<>();
    private MotionDisplay title;
    private int size = DEFAULT_ROWS;
    private LongFunction<Component> format = score -> Component.text(String.format("%,d", score), NamedTextColor.WHITE);
    private BukkitTask counter;
    private boolean removed;

    Leaderboard(DisplayMotion motion, Plugin plugin, Location origin) {
        this.motion = motion;
        this.plugin = plugin;
        this.origin = origin.clone();
        this.origin.setPitch(0f);
    }

    /** The title above the rows. {@code null} removes it. */
    public Leaderboard title(Component text) {
        requireAlive();
        if (title != null) {
            title.remove();
            title = null;
        }
        if (text != null) {
            title = motion.model(rowSpot(-1).add(0, 0.08, 0))
                    .text("main", text, Pose.IDENTITY.withScale(TITLE_SCALE))
                    .spawn()
                    .background(Color.fromARGB(0xB0, 0x0A, 0x0B, 0x0D))
                    .play(Animations.popIn(POP_TICKS));
        }
        return this;
    }

    /** How many rows show, the default is 10. Fewer rows remove the ones at the bottom on the next update. */
    public Leaderboard rows(int rows) {
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("rows must be 1 to " + MAX_ROWS + ", got " + rows);
        }
        size = rows;
        return this;
    }

    /** How a score turns into text. The default is the number with thousands separators. */
    public Leaderboard format(LongFunction<Component> format) {
        this.format = Objects.requireNonNull(format, "format");
        return this;
    }

    /**
     * Shows these entries, highest score first. Rows that change rank glide to the new place, new ones
     * pop in, ones that drop off pop out, and a changed score counts up to the new value.
     */
    public void update(List<Entry> entries) {
        requireAlive();
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(Entry::score).reversed().thenComparing(Entry::id));
        Set<String> kept = new HashSet<>();
        int spawned = 0;
        for (int rank = 0; rank < Math.min(size, sorted.size()); rank++) {
            Entry entry = sorted.get(rank);
            kept.add(entry.id());
            Row row = rows.get(entry.id());
            if (row == null) {
                rows.put(entry.id(), spawn(entry, rank, spawned++));
                continue;
            }
            if (row.rank != rank) {
                move(row, entry, rank);
            }
            if (row.to != entry.score()) {
                count(row, entry.score());
            }
        }
        for (Iterator<Map.Entry<String, Row>> it = rows.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Row> old = it.next();
            if (!kept.contains(old.getKey())) {
                drop(old.getValue());
                it.remove();
            }
        }
    }

    /** Removes the whole board. */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        if (counter != null) {
            counter.cancel();
        }
        for (Row row : rows.values()) {
            drop(row);
        }
        rows.clear();
        if (title != null && !title.isRemoved()) {
            title.play(Animations.popOut(POP_TICKS), title::remove);
        }
    }

    /** True after {@link #remove()}. */
    public boolean isRemoved() {
        return removed || motion.isClosed();
    }

    /** A player head with the skin of this player, for {@link Entry#icon(ItemStack)}. */
    public static ItemStack head(OfflinePlayer player) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        return head;
    }

    private Row spawn(Entry entry, int rank, int order) {
        ModelBuilder model = motion.model(rowSpot(rank))
                .text("panel", Component.text(PANEL), Pose.IDENTITY.withOffset(0f, 0f, -0.02f))
                .text("rank", rankText(rank), Pose.IDENTITY.withOffset(RANK_X, TEXT_Y, 0f).withScale(TEXT_SCALE))
                .text("arrow", Component.empty(), Pose.IDENTITY.withOffset(ARROW_X, TEXT_Y, 0f).withScale(TEXT_SCALE))
                .text("name", entry.name(), leftAligned(entry.name(), entry.icon() == null ? ICON_X : NAME_LEFT))
                .text("score", format.apply(entry.score()), Pose.IDENTITY.withOffset(SCORE_X, TEXT_Y, 0f).withScale(TEXT_SCALE));
        if (entry.icon() != null) {
            model.item("icon", entry.icon(), ItemDisplay.ItemDisplayTransform.GUI,
                    Pose.IDENTITY.withOffset(ICON_X, 0.135f, 0f).withScale(0.22f));
        }
        MotionDisplay display = model.spawn()
                .background("panel", panelColor(rank))
                .background("rank", CLEAR)
                .background("arrow", CLEAR)
                .background("name", CLEAR)
                .background("score", CLEAR)
                .play(popInAfter(order * STAGGER_TICKS));
        if (entry.icon() != null) {
            // world light makes a head nearly black at night or under a roof
            display.brightness("icon", new Display.Brightness(15, 15));
        }
        if (entry.icon() != null && rank < MEDALS.length) {
            display.glow("icon", Color.fromRGB(MEDALS[rank].value()));
        }
        return new Row(display, rank, entry.score());
    }

    // the client glides it over the ticks by itself, so a rank change costs one packet per part
    private void move(Row row, Entry entry, int rank) {
        boolean up = rank < row.rank;
        row.rank = rank;
        row.display.teleport(rowSpot(rank), GLIDE_TICKS)
                .background("panel", panelColor(rank))
                .text("rank", rankText(rank))
                .text("arrow", Component.text(up ? "▲" : "▼", up ? UP : NamedTextColor.RED));
        if (entry.icon() != null) {
            row.display.glow("icon", rank < MEDALS.length ? Color.fromRGB(MEDALS[rank].value()) : null);
        }
        if (row.arrow != null) {
            row.arrow.cancel();
        }
        row.arrow = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!row.display.isRemoved()) {
                row.display.text("arrow", Component.empty());
            }
        }, ARROW_TICKS);
    }

    private void count(Row row, long score) {
        row.from = row.shown;
        row.to = score;
        row.countTick = 0;
        row.display.play("score", BUMP);
        if (counter == null) {
            counter = Bukkit.getScheduler().runTaskTimer(plugin, this::countStep, COUNT_STEP_TICKS, COUNT_STEP_TICKS);
        }
    }

    // only runs while a score counts, so a quiet board does nothing every tick
    private void countStep() {
        boolean busy = false;
        for (Row row : rows.values()) {
            if (row.countTick < 0) {
                continue;
            }
            row.countTick += COUNT_STEP_TICKS;
            float t = Math.min(1f, row.countTick / (float) COUNT_TICKS);
            float eased = 1f - (1f - t) * (1f - t);
            row.shown = row.from + Math.round((row.to - row.from) * (double) eased);
            if (!row.display.isRemoved()) {
                row.display.text("score", format.apply(row.shown));
            }
            if (t >= 1f) {
                row.countTick = -1;
            } else {
                busy = true;
            }
        }
        if (!busy && counter != null) {
            counter.cancel();
            counter = null;
        }
    }

    private void drop(Row row) {
        if (row.arrow != null) {
            row.arrow.cancel();
        }
        if (!row.display.isRemoved()) {
            row.display.play(Animations.popOut(POP_TICKS), row.display::remove);
        }
    }

    private Location rowSpot(int rank) {
        return origin.clone().add(0, -rank * ROW_HEIGHT, 0);
    }

    private static Color panelColor(int rank) {
        return rank < PANELS.length ? PANELS[rank] : PANEL_REST;
    }

    private static Component rankText(int rank) {
        TextColor color = rank < MEDALS.length ? MEDALS[rank] : NamedTextColor.GRAY;
        return Component.text("#" + (rank + 1), color).decorate(TextDecoration.BOLD);
    }

    private static Pose leftAligned(Component text, float left) {
        return Pose.IDENTITY.withOffset(left + TextWidth.blocks(text, TEXT_SCALE) / 2f, TEXT_Y, 0f).withScale(TEXT_SCALE);
    }


    private static Animation popInAfter(int delay) {
        if (delay == 0) {
            return Animations.popIn(POP_TICKS);
        }
        return Animation.keyframes()
                .at(0, Pose.IDENTITY.withScale(0f))
                .at(delay, Pose.IDENTITY.withScale(0f))
                .at(delay + POP_TICKS, Pose.IDENTITY, Ease.OUT)
                .build();
    }

    private void requireAlive() {
        if (isRemoved()) {
            throw new IllegalStateException("this leaderboard was removed");
        }
    }
}
