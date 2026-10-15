# displaymotion

Animate vanilla item, block and text displays from Java code.
No resource pack. No real entities. Packets only, through PacketEvents. No work on the main thread.

Java Edition only. Paper 26.3, Java 25, PacketEvents 2.14.0.

```java
DisplayMotion motion = DisplayMotion.create(plugin); // once, in onEnable

static final Animation SHOW = Animations.popIn(20)
        .then(Animations.spin(80).with(Animations.bob(0.3f, 80)));

MotionDisplay sword = motion.item(location, ItemStack.of(Material.DIAMOND_SWORD)).play(SHOW);

sword.play(Animations.popOut(10), sword::remove); // later: shrink, then remove
```

## What it does

- **Models** from many blocks, items and texts. Parts have parents and move as one.
- **Import** models and animations from Blockbench (`.bbmodel`) and BDEngine (`.bdengine`).
- **Effects** ready to use: shatter, tombstone, sword rain, soul. One line each.
- **Animations** with easing, built from presets or keyframes, combined with `then`, `with` and `loop`.
- **Looks:** glow color, brightness, billboard, live text.
- **Move** whole models with a smooth glide. Show them to everyone or only to some players.
- **Light:** one motion thread does the work. Packets are encoded once and shared. Parts that do not move are not sent. The client culls parts that are off screen.

## Docs

- [Getting started](docs/getting-started.md): install with JitPack, first display.
- [Animations](docs/animations.md)
- [Models](docs/models.md)
- [Effects](docs/effects.md)
- [Importing](docs/importing.md)
- [Cookbook](docs/cookbook.md)
- [How it works](docs/how-it-works.md)
- AI tools: [llms.txt](llms.txt)

## Try it

Build the example plugin and put it next to PacketEvents on a Paper 26.3 server:

```
./gradlew build
```

The jar is `example/build/libs/displaymotion-example-<version>.jar`. Commands: `/dm showcase` (everything at once), `/dm sword`, `/dm block`, `/dm windmill`, `/dm text`, `/dm pet`, `/dm hat`, `/dm backpack`, `/dm selfview`, `/dm bench`, `/dm button`, `/dm effect <name>`, `/dm killeffect <name>`, `/dm model <file> [animation]`, `/dm debug`, `/dm stop`.

## Modules

| Module | What | License |
| --- | --- | --- |
| `core` | Animations, poses, easing, matrix math. No Bukkit, no PacketEvents. Java 21. | MIT |
| `paper` | Sends the frames to players. | GPL-3.0-only |
| `example` | The test plugin. | GPL-3.0-only |

PacketEvents is GPL-3.0, so the module that uses it is GPL-3.0 too. `core` stays MIT, so you can write your own sender. This is not legal advice.

## Usage stats

`DisplayMotion.create(plugin)` sends anonymous stats to [bStats](https://bstats.org/plugin/bukkit/displaymotion/34708): how many servers use displaymotion and which plugins. It helps to see what people use. Turn it off with `DisplayMotion.create(plugin, false)`. Server owners can turn off all bStats in `plugins/bStats/config.yml`. The `Metrics` class is the official bStats single file class (MIT), only its package is changed.
