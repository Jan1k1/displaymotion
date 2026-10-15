# displaymotion

Animate vanilla item and block displays from Java code.
No resource pack. No real entities. Packets only, through PacketEvents.

Java Edition only. Paper 26.3, Java 25, PacketEvents 2.14.0.

## Example

```java
DisplayMotion motion = DisplayMotion.create(plugin); // once, in onEnable

static final Animation IDLE = Animations.spin(80).with(Animations.bob(0.3f, 80));
static final Animation SHOW = Animations.popIn(20).then(IDLE);

MotionDisplay sword = motion.item(location, ItemStack.of(Material.DIAMOND_SWORD));
sword.play(SHOW);

sword.play(Animations.popOut(10), sword::remove); // later: shrink, then remove
```

Your `plugin.yml` needs `depend: [packetevents]`. The server installs PacketEvents. Do not shade it.

## Models

A model is many displays that move as one. A part can have a parent. A child moves and turns with its parent.

```java
MotionDisplay windmill = motion.model(location)
        .block("tower", Material.OAK_PLANKS.createBlockData(), Pose.IDENTITY.withOffset(0f, 1f, 0f).withScale(0.6f, 2f, 0.6f))
        .block("hub", Material.DARK_OAK_LOG.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.7f, 0.4f).withScale(0.2f), "tower")
        .block("blade", Material.WHITE_WOOL.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.6f, 0.05f).withScale(0.2f, 1f, 0.05f), "hub")
        .spawn();

windmill.play(Animations.popIn(20));                                   // the whole model
windmill.play("hub", Animations.turn(0f, 0f, 360f, 60).loop());        // one part and its children
```

Rules for a part's pose:

- Offset and rotation pass to the children.
- Scale is the size of this part only. Children do not get it.
- Animation poses pass everything to the children, scale too.
- Blocks turn and scale around their center. Items turn around their model center.

## API

| Call | What it does |
| --- | --- |
| `DisplayMotion.create(plugin)` | Start the library. Call once on the main thread. It closes itself when your plugin disables. |
| `motion.item(location, itemStack)` | Spawn one item display. Optional third argument: `ItemDisplay.ItemDisplayTransform`. |
| `motion.block(location, blockData)` | Spawn one block display. |
| `motion.model(location)` | Start a model. Add parts with `.block(...)` and `.item(...)`, then `.spawn()`. At most 256 parts. |
| `display.play(animation)` | Play on the whole display. Replaces what plays there now. |
| `display.play(animation, onEnd)` | Same, and run `onEnd` on the main thread once, when it reaches its end. |
| `display.play("part", animation)` | Play on one part and its children. Also with `onEnd`. |
| `display.stop()` / `display.stop("part")` | Freeze on the current pose. `onEnd` does not run. |
| `display.viewRange(blocks)` | Who sees it. Default 48 blocks, same world. |
| `display.remove()` | Remove it for every viewer. |
| `display.parts()` | The part names. A single item or block has one part, `main`. |
| `motion.close()` | Remove every display. Happens by itself on plugin disable. |

`MotionDisplay` methods are safe from any thread. After `remove()` they throw `IllegalStateException`, except `remove()` itself.

The display faces the yaw and pitch of the spawn location. It never moves from that location. Motion comes from the transform only.

### Animations

| Call | Result |
| --- | --- |
| `Animations.popIn(ticks)` | Scale 0 to 1, ease out. |
| `Animations.popOut(ticks)` | Scale 1 to 0, ease in. |
| `Animations.spin(ticksPerTurn)` | Turn around Y forever. At least 6 ticks per turn. |
| `Animations.turn(pitch, yaw, roll, ticks)` | Turn by these degrees once. Add `.loop()` to repeat. |
| `Animations.bob(height, ticks)` | Move up and down forever. |
| `Animations.slide(x, y, z, ticks, ease)` | Move by an offset. |
| `Animations.hold(ticks)` | Stay on the current pose for a time. |
| `a.then(b)` | Play `a`, then `b`. `b` starts where `a` ended. |
| `a.with(b)` | Play both at once. Offsets and angles add, scales multiply. |
| `a.loop()` | Repeat forever. |
| `Animation.keyframes().at(tick, pose, ease)...build()` | Your own animation. |

`Pose.IDENTITY.withOffset(x, y, z).withRotation(pitch, yaw, roll).withScale(s)` builds a pose.
Angles are degrees. They apply in the order yaw, then pitch, then roll (Y, X, Z).
`Ease` is `LINEAR`, `IN`, `OUT` or `IN_OUT`.

Timing:

- Frames are sent every 2 ticks. The client blends between them. Lengths round to whole frames, so `spin(81)` takes 82 ticks.
- An animation can be at most 6000 ticks (intro plus one loop).
- An animation that never ends (`loop`, `spin`, `bob`) never calls `onEnd`. Anything after it in `then` never plays.
- A new `play` blends from the current pose to the first frame over 2 ticks.
- Keep animations in `static final` fields. Each animation object is baked once and then reused.

## How it stays light

- The main thread copies player positions every 10 ticks. It does nothing else.
- One motion thread per plugin does the rest, then hands packets to the network threads.
- Each animation is baked once into a matrix per frame. A step only combines the parts that moved.
- Each packet is encoded once and shared by every viewer. Only the changed transform fields are sent.
- A display that nobody sees is not computed.
- Each viewer gets one network task and one bundle per tick. A slow connection skips frames and gets the full state when it catches up.
- Big animations are baked off the motion thread.

There is no benchmark in this repo yet. Measure on your server with spark.

## Modules

| Module | What | License |
| --- | --- | --- |
| `core` | Animations, poses, easing, matrix math. No Bukkit, no PacketEvents. Java 21. | MIT |
| `packetevents` | Sends the frames to players. | GPL-3.0-only |
| `example` | Test plugin: `/displaymotion`, `/displaymotion block`, `/displaymotion windmill`, `/displaymotion stop`. | GPL-3.0-only |

PacketEvents is GPL-3.0, so the module that uses it is GPL-3.0 too. `core` stays MIT, so you can write your own sender. This is not legal advice.

## Build

```
./gradlew build
```

The test plugin is `example/build/libs/displaymotion-example-<version>.jar`.
It is not on a Maven repository yet. Use `./gradlew publishToMavenLocal` to try it in your own plugin.
