# displaymotion

Animate vanilla item and block displays from Java code.
No resource pack. No real entities. Packets only, through PacketEvents.

Java Edition only. Paper 26.3, Java 25, PacketEvents 2.14.0.

## Example

```java
DisplayMotion motion = DisplayMotion.create(plugin); // once, in onEnable

Animation idle = Animations.spin(80).with(Animations.bob(0.3f, 80));

MotionDisplay sword = motion.item(location, ItemStack.of(Material.DIAMOND_SWORD));
sword.play(Animations.popIn(20).then(idle));

sword.play(Animations.popOut(10)).onEnd(sword::remove); // later
```

Your `plugin.yml` needs `depend: [packetevents]`. The server installs PacketEvents. Do not shade it.

## API

| Call | What it does |
| --- | --- |
| `DisplayMotion.create(plugin)` | Start the library. Call once on the main thread. It closes itself when your plugin disables. |
| `motion.item(location, itemStack)` | Spawn an item display. Optional third argument: `ItemDisplay.ItemDisplayTransform`. |
| `motion.block(location, blockData)` | Spawn a block display. It turns and scales around the block center, not the corner. |
| `display.play(animation)` | Start an animation from its first pose. Replaces the one that plays now. |
| `display.stop()` | Freeze on the current pose. |
| `display.onEnd(runnable)` | Run code on the main thread when a non-looping animation ends. `stop()` does not call it. |
| `display.viewRange(blocks)` | Who sees it. Default 48 blocks, same world. |
| `display.remove()` | Remove it for every viewer. |
| `motion.close()` | Remove every display. Happens by itself on plugin disable. |

Every `MotionDisplay` method is safe from any thread.

### Animations

| Call | Result |
| --- | --- |
| `Animations.popIn(ticks)` | Scale 0 to 1, ease out. |
| `Animations.popOut(ticks)` | Scale 1 to 0, ease in. |
| `Animations.spin(ticksPerTurn)` | Turn around Y forever. |
| `Animations.bob(height, ticks)` | Move up and down forever. |
| `Animations.slide(x, y, z, ticks, ease)` | Move by an offset. |
| `Animations.hold(ticks)` | Do nothing for a time. |
| `a.then(b)` | Play `a`, then `b`. |
| `a.with(b)` | Play both at once. Offsets and turns add, scales multiply. |
| `a.loop()` | Repeat forever. |
| `Animation.keyframes().at(tick, pose, ease)...build()` | Your own animation. |

`Pose.IDENTITY.withOffset(x, y, z).withRotation(pitch, yaw, roll).withScale(s)` builds a pose. Angles are degrees.
`Ease` is `LINEAR`, `IN`, `OUT` or `IN_OUT`.

An animation that never ends (it has a `loop()` or `spin` or `bob` in it) never calls `onEnd`. Anything after it in `then` never plays.

## How it stays fast

- No work on the main thread. It copies player positions every 10 ticks, nothing more.
- One motion thread per plugin does the rest. It sends straight to the network threads.
- Each animation is baked once into a frame table. A tick reads numbers from the table. It does no math.
- Each packet is encoded once and shared by every viewer. Frames that do not move are not sent.
- Each viewer gets one network task and one bundle per tick.
- Measured: 1000 displays with 10 viewers each take about 0.34 ms per frame step (one step every 2 ticks), on the motion thread.

The client blends between frames. Curves are sent as a frame every 2 ticks.

## Modules

| Module | What | License |
| --- | --- | --- |
| `core` | Animations, poses, easing, frame baking. No Bukkit, no PacketEvents. | MIT |
| `packetevents` | Sends the frames to players. | GPL-3.0-only |
| `example` | Test plugin: `/displaymotion`, `/displaymotion block`, `/displaymotion stop`. | GPL-3.0-only |

PacketEvents is GPL-3.0, so the module that uses it is GPL-3.0 too. `core` stays MIT, so you can write your own sender. This is not legal advice.

## Build

```
./gradlew build
```

The test plugin is `example/build/libs/displaymotion-example-<version>.jar`.
It is not on a Maven repository yet. Use `./gradlew publishToMavenLocal` to try it in your own plugin.
