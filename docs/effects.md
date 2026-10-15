# Effects

Ready-made effects, one line each. Every effect spawns, plays and removes itself.

```java
motion.effects().shatter(location, Material.REDSTONE_BLOCK.createBlockData());
motion.effects().tombstone(location, Component.text("Steve"));
motion.effects().swordRain(location, ItemStack.of(Material.IRON_SWORD));
motion.effects().soul(location);
```

| Effect | What you see |
| --- | --- |
| `shatter(location, block)` | The block breaks into 10 pieces. They fly out, spin, fall and shrink. |
| `tombstone(location, name)` | A gravestone with "RIP" and the name rises out of the ground, stays 3 seconds, sinks back. |
| `swordRain(location, sword)` | 6 swords fall from the sky one after another, stick in the ground, then vanish. |
| `soul(location)` | A glowing skull rises, spins and fades. |

Each call returns the `MotionDisplay`, so you can still `hide(player)` or `remove()` it early.

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

The front of an effect is the direction of the location's yaw. Turn the location to the player to make text readable.

## Try them

In the example plugin: `/dm effect shatter|tombstone|swords|soul`, or `/dm killeffect tombstone` and kill a mob.
