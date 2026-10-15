# Importing models

Make a model in an editor, load it with one line.

| Editor | File | What comes in |
| --- | --- | --- |
| [Blockbench](https://www.blockbench.net) | `.bbmodel` (File, Save Project) | Cubes, groups, rotations, animations. Every cube becomes the vanilla block closest to its texture color. |
| [BDEngine](https://block-display.com) | `.bdengine` (save the project) | Blocks, items, player heads with skins, texts, groups, animations. Exactly like in the editor. |

## Code

```java
ImportedModel chest = ImportedModel.load(getDataFolder().toPath().resolve("models/chest.bdengine"));

MotionDisplay display = chest.spawn(motion, location);      // or chest.spawn(motion, location, 0.5f) for half size
chest.play(display, "open");                                // switch with play again
chest.animations();                                         // the names in the file
```

- `ImportedModel.load` picks the format by the file name. Load once, for example in `onEnable`, then spawn as often as you want.
- Loading is safe on any thread. Spawning and playing work like any other display.
- The parts keep the names from the editor (lowercase, `_2` added when a name repeats), so `display.play("lid", ...)` and `display.glow("lid", ...)` work too.

## Blockbench notes

- 16 Blockbench pixels are one block.
- Textures have to be inside the file. Blockbench saves them inside by default.
- Cubes only. Meshes are skipped.
- Animations: rotation, position and scale keyframes, with linear, smooth (catmullrom, bezier) and step blending. Animations set to loop in Blockbench loop here too.
- Molang in keyframes (like `math.sin(q.anim_time*90)`) is not supported yet and reads as 0.

## BDEngine notes

- Both the new project files (binary) and older text files work.
- Animations loop.
- Gallery models have a license. Many are BDL BY-NC: free for non-commercial use, credit the author.

## Limits

- At most 256 parts per model.
- An animation can be at most 6000 ticks.

## Examples

The example plugin ships `models/robot.bbmodel` (walk, wave) and `models/chest.bdengine` (open).
Drop more files into `plugins/displaymotion-example/models/` and run `/dm model <file> [animation]`. Tab complete lists the files and animations.
