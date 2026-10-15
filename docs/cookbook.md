# Cookbook

Short recipes. Each one is complete code you can copy.

## Floating item that spins

```java
static final Animation FLOAT = Animations.popIn(20).then(Animations.spin(80).with(Animations.bob(0.3f, 80)));

motion.item(location, ItemStack.of(Material.NETHER_STAR)).play(FLOAT);
```

## Remove with an animation

```java
display.play(Animations.popOut(10), display::remove);
```

## Follow a player like a pet

```java
MotionDisplay pet = motion.item(spot(player), ItemStack.of(Material.DIAMOND_SWORD)).play(FLOAT);
Bukkit.getScheduler().runTaskTimer(plugin, task -> {
    if (pet.isRemoved() || !player.isOnline()) {
        task.cancel();
        return;
    }
    pet.teleport(spot(player), 4);   // move every 2 ticks, glide over 4, so it trails behind
}, 2L, 2L);
```

## Only one player sees it

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

```java
motion.text(location, Component.text("Spawn", NamedTextColor.GOLD))
        .billboard(Display.Billboard.CENTER)
        .play(Animations.popIn(15));
```

## Glowing outline

```java
display.glow(Color.AQUA);   // null turns it off
```

## Always bright, even in a cave

```java
display.brightness(new Display.Brightness(15, 15));
```

## Mirror a part

```java
Pose.IDENTITY.withScale(-1f, 1f, 1f)
```

## A hop

```java
static final Animation HOP = Animations.slide(0f, 1f, 0f, 6, Ease.OUT)
        .then(Animations.slide(0f, -1f, 0f, 6, Ease.IN));
```

`then` starts each part where the last ended, so the second slide brings it back down.
