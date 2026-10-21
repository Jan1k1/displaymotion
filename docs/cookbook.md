# Cookbook

Short recipes. Each one is code you can copy. They use `motion` for your `DisplayMotion` and `location` for a `Location`. See [Getting started](getting-started.md) for how to make them.

## Floating item that spins

Good for a reward, a drop or a shop item. It grows in, then spins and floats.

```java
static final Animation FLOAT = Animations.popIn(20).then(Animations.spin(80).with(Animations.bob(0.3f, 80)));

motion.item(location, ItemStack.of(Material.NETHER_STAR)).play(FLOAT);
```

## Remove with an animation

A display that just disappears looks cheap. Shrink it first. The code after the comma runs when the animation ends.

```java
display.play(Animations.popOut(10), display::remove);
```

## Follow a player like a pet

`follow` moves the display every 2 ticks and the client glides it over 4 ticks, so the pet trails a bit behind the player.

```java
MotionDisplay pet = motion.item(player.getLocation(), ItemStack.of(Material.DIAMOND_SWORD)).play(FLOAT).follow(player);
```

To pick the spot yourself, use `follow(player, new Vector(0.7, 1.7, -0.6), 5)`: x to the right, y up, z forward. `unfollow()` stops it. See [Models](models.md).

## Only one player sees it

Use this for quest markers or private hints. Nobody sees the display until you call `show`.

```java
motion.item(location, ItemStack.of(Material.EMERALD))
        .visibleByDefault(false)
        .show(player);
```

## Hide from one player

```java
display.hide(player);
display.show(player);   // back again
```

## Label that faces the player

A billboard text turns to look at every player, so it is always readable.

```java
motion.text(location, Component.text("Spawn", NamedTextColor.GOLD))
        .billboard(Display.Billboard.CENTER)
        .play(Animations.popIn(15));
```

## Change a text later

```java
label.text(Component.text("Open now", NamedTextColor.AQUA));   // label is a text display you spawned before
```

## Glowing outline

```java
display.glow(Color.AQUA);   // null turns it off
```

## Always bright, even in a cave

By default a display is lit by the world, so it is dark in a cave. A fixed light level fixes this.

```java
display.brightness(new Display.Brightness(15, 15));
```

## Mirror a part

A negative scale flips a part on that axis.

```java
Pose.IDENTITY.withScale(-1f, 1f, 1f)
```

## A hop

```java
static final Animation HOP = Animations.slide(0f, 1f, 0f, 6, Ease.OUT)
        .then(Animations.slide(0f, -1f, 0f, 6, Ease.IN));
```

`then` starts each part where the last ended, so the second slide brings it back down.

## A button you can click

Right click presses it and counts. Left click breaks it.

```java
static final Animation PRESS = Animation.keyframes()
        .at(0, Pose.IDENTITY)
        .at(2, Pose.IDENTITY.withOffset(0f, -0.08f, 0f))
        .at(6, Pose.IDENTITY)
        .build();

int[] clicks = {0};
MotionDisplay button = motion.model(location)
        .block("base", Material.POLISHED_BLACKSTONE.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.1f, 0f).withScale(0.9f, 0.2f, 0.9f))
        .block("cap", Material.RED_CONCRETE.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.27f, 0f).withScale(0.6f, 0.14f, 0.6f))
        .text("label", Component.text("Click me"), Pose.IDENTITY.withOffset(0f, 0.8f, 0f))
        .spawn()
        .billboard("label", Display.Billboard.CENTER)
        .hitbox(1f, 0.5f);

button.onClick(click -> {
    if (click.leftClick()) {
        motion.effects().shatter(location, Material.RED_CONCRETE.createBlockData());
        button.remove();
        return;
    }
    clicks[0]++;
    button.play("cap", PRESS).text("label", Component.text("Clicks: " + clicks[0]));
});
```

Try it in game with `/dm button` from the example plugin.

## Text that types and counts

```java
MotionDisplay sign = motion.text(location, Component.empty());
sign.type(Component.text("Welcome to ").append(Component.text("Spawn", NamedTextColor.GOLD)), 2);   // a char every 2 ticks

MotionDisplay coins = motion.text(location, Component.empty());
coins.count("main", 0, 1500, 40, n -> Component.text(n + " coins", NamedTextColor.YELLOW));       // 0 to 1500 in 2 seconds
```

- Colors and styles stay while it types. A model with more text parts takes the part name first: `type("title", text, 2)`.
- Counting starts fast and slows down at the end. It can count down too.
- Both run on the motion thread, not the main thread. Setting the text by hand stops them.
- `type(part, text, ticksPerChar, onEnd)` runs code on the main thread when the last char shows.

## A leaderboard that moves

Rows glide to their new rank, new names pop in, names that drop off pop out, and changed scores count up. The top 3 get gold, silver and bronze.

```java
Leaderboard board = motion.leaderboard(location)       // faces where the location looks, rows go down
        .rows(10)
        .title(Component.text("Top kills", NamedTextColor.GOLD));

// call it again whenever the scores change, it works out what moved
board.update(players.stream()
        .map(p -> Leaderboard.Entry.of(p.getUniqueId().toString(), p.name(), kills(p))
                .icon(Leaderboard.head(p)))
        .toList());

board.remove();                                        // when you are done
```

- The board sorts by score, highest first. The id says which row is which between updates.
- A rank change costs one move per part: the players' games glide it there by themselves.
- `format(score -> Component.text(score + " kills"))` changes how a score shows.
- Call it on the main thread. Try it with `/dm leaderboard`.

## Spawn an imported model

Load the file once. Spawn it every time you need it. `load` throws an `IOException` when the file is missing or broken, so catch it. See [Importing](importing.md) for the full steps.

```java
ImportedModel robot = ImportedModel.load(getDataFolder().toPath().resolve("models/robot.bbmodel"));

MotionDisplay display = robot.spawn(motion, location, 0.5f);
robot.play(display, "walk");
```

## Remove everything when the plugin stops

You do not need to do anything. The library removes all displays when your plugin disables. To do it by hand, call `motion.close()`.
