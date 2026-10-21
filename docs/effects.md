# Effects

Effects are short, ready-made animations. You call one line, the effect plays, and it removes itself when it is done. You do not need to build or clean up anything.

They are made for moments like a kill, a death, a win or a drop.

```java
motion.effects().shatter(location, Material.REDSTONE_BLOCK.createBlockData());
motion.effects().tombstone(location, Component.text("Steve"));
motion.effects().swordRain(location, ItemStack.of(Material.IRON_SWORD));
motion.effects().soul(location);
```

The location is the ground point where the effect happens. For a mob that died, use the location of the mob.

| Effect | What you see | Time |
| --- | --- | --- |
| `shatter(location, block)` | The block breaks into 10 small pieces. They fly out, spin, fall and shrink. | about 2 seconds |
| `tombstone(location, name)` | A gravestone with "RIP" and the name rises out of the ground, stays 3 seconds, and sinks back. | about 4.5 seconds |
| `swordRain(location, sword)` | 6 swords fall from the sky one after another, stick in the ground, then vanish. | about 3.5 seconds |
| `soul(location)` | A glowing skull rises, spins and fades away. | about 2.5 seconds |

What you give to each effect:

- `shatter` takes the block to break, for example `Material.REDSTONE_BLOCK.createBlockData()`. Pick the block of the thing that died, or a color you like.
- `tombstone` takes a `Component` for the name. `Component.text("Steve")` works. For a mob, use `event.getEntity().name()`.
- `swordRain` takes any `ItemStack`. It does not have to be a sword.
- `soul` takes nothing else.

Each call returns the `MotionDisplay`, so you can still `hide(player)` or `remove()` it before it ends.

## As a kill effect

```java
@EventHandler
public void onKill(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null) {
        return;
    }
    Location at = event.getEntity().getLocation();
    // face the killer so the tombstone text is readable
    at.setDirection(killer.getLocation().toVector().subtract(at.toVector()));
    at.setPitch(0f);
    motion.effects().tombstone(at, event.getEntity().name());
}
```

Remember to register the listener with `getServer().getPluginManager().registerEvents(this, this)`.

The front of an effect is the direction of the location's yaw. The text of the tombstone is on the front. Turn the location to the player to make the text readable. For `shatter`, `swordRain` and `soul` the direction does not matter.

## Who sees it

Every player within 48 blocks of the location sees the effect. If you want only one player to see it, call `hide(player)` for the others, or use [`visibleByDefault(false)` and `show(player)`](cookbook.md).

## Try them

In the example plugin: `/dm effect shatter|tombstone|swords|soul`, or `/dm killeffect tombstone` and kill a mob.

## Make your own

An effect is only a model with an animation and `display::remove` at the end. The source of [`Effects.java`](https://github.com/Jan1k1/displaymotion/blob/main/paper/src/main/java/org/jan1k/displaymotion/paper/Effects.java) is a good place to learn. See [Models](models.md) and [Animations](animations.md).
