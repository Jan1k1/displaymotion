# Importing models

You can build a model in code, but a robot with 100 parts is a lot of numbers. It is easier to draw it in an editor and load the file.

displaymotion reads files from two free editors:

| Editor | File | What comes in |
| --- | --- | --- |
| [Blockbench](https://www.blockbench.net) | `.bbmodel` | Cubes, groups, rotations and animations. Every cube becomes the vanilla block that is closest to its color. |
| [BDEngine](https://block-display.com) | `.bdengine` | Blocks, items, player heads, texts, groups and animations. They look like they do in the editor. |

Which one should you use?

- Pick **BDEngine** if you want real vanilla blocks and items, with their real textures.
- Pick **Blockbench** if you want to draw your own shapes and use its animation timeline. The result is made of solid color blocks.

## Quick start

1. Make a model, or download one.
2. Put the file in your plugin folder.
3. Load it once with `ImportedModel.load`.
4. Spawn it with `spawn`.
5. Play an animation from the file with `play`.

The next parts show every step.

## 1. Get a model

### Blockbench

1. Open Blockbench. It runs in the browser and as a desktop app.
2. Make a new project of the type **Free Model**. Any type that has animations works.
3. Build the model from cubes. Put cubes that move together in a **group** and give the group a name, for example `head` or `arm_left`.
4. Paint the cubes. Use flat, clear colors. See [Colors](#colors-blockbench) below.
5. Open the **Animate** tab. Make an animation, for example `walk`, and add keyframes on the groups.
6. Save with **File, Save Project**. The file ends with `.bbmodel`. The textures are saved inside the file.

### BDEngine

1. Open [block-display.com](https://block-display.com) and make a model from blocks, items, heads and texts. Or download one from the gallery.
2. Make animations in the editor and give each one a name, for example `open`.
3. Save the project. The file ends with `.bdengine`.

Models in the BDEngine gallery have a license. Many are BDL BY-NC: free for non-commercial use, and you must credit the author. Read it before you use one on a paid server.

## 2. Put the file in your plugin

The plugin reads the file from its data folder: `plugins/YourPlugin/models/`.

**Option A: ship the file inside your jar.** Put it in `src/main/resources/models/robot.bbmodel`. Then copy it out when the plugin starts:

```java
saveResource("models/robot.bbmodel", false);
```

- `false` means: do not overwrite. The server owner can edit the file and the plugin keeps it.
- `true` means: always write the file from the jar again.

**Option B: copy the file by hand.** Make the folder `plugins/YourPlugin/models/` and drop the file in it. Then the server owner can add their own models without a new jar.

## 3. Load and spawn

`ImportedModel.load` reads the file. It picks the format from the file name. It can throw `IOException`, so catch it.

Load a model once, for example in `onEnable`. Then spawn it as many times as you want. Loading is safe on any thread.

```java
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.packetevents.DisplayMotion;
import org.jan1k.displaymotion.packetevents.ImportedModel;
import org.jan1k.displaymotion.packetevents.MotionDisplay;

import java.io.IOException;
import java.nio.file.Path;

public final class MyPlugin extends JavaPlugin {

    private DisplayMotion motion;
    private ImportedModel robot;

    @Override
    public void onEnable() {
        motion = DisplayMotion.create(this);

        saveResource("models/robot.bbmodel", false);
        Path file = getDataFolder().toPath().resolve("models/robot.bbmodel");
        try {
            robot = ImportedModel.load(file);
        } catch (IOException e) {
            getLogger().severe("Could not read " + file + ": " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Animations: " + robot.animations());
        getLogger().info("Parts: " + robot.parts());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Location at = player.getLocation();
        MotionDisplay display = robot.spawn(motion, at);
        robot.play(display, "walk");
        display.play(Animations.popIn(15));
        return true;
    }
}
```

For the command to work, add it to `plugin.yml`:

```yaml
commands:
  robot:
    description: Spawns the robot
```

Look at the log when the server starts. It prints the animation names and the part names. You need them in the next steps.

What the three calls do:

- `robot.spawn(motion, at)` makes the model at the location. It returns a `MotionDisplay`.
- `robot.play(display, "walk")` starts the animation named `walk` from the file.
- `display.play(Animations.popIn(15))` grows the whole model in. This is optional. It works together with the animation from the file.

You can also spawn bigger or smaller: `robot.spawn(motion, at, 0.5f)` is half size.

To remove the model, call `display.remove()`. To change the animation, call `robot.play(display, "wave")` again.

## How animations work

- The name you give to `play` is the name of the animation in the editor. Capital letters matter. If the name is wrong, you get an error that lists all names.
- `robot.animations()` gives you the list of names.
- A new `play` replaces the old animation. Parts that the new animation does not move go back to their rest pose.
- Blockbench: only **groups** move. A cube that is not inside a group never moves, so put every moving part in a group.
- Blockbench: an animation that is set to **loop** loops here too. Any other animation plays once and stays on its last pose.
- BDEngine: animations always loop.
- Blockbench keyframes are in seconds. They are turned into ticks (20 ticks are one second). BDEngine keyframes are already in ticks.
- One animation can be at most 6000 ticks (5 minutes).

You can still animate one part with your own code. It replaces the animation from the file for that part:

```java
display.play("head", Animations.turn(0f, 360f, 0f, 40).loop());
```

## Part names

Every part of the model has a name. You need it for `display.play("head", ...)`, `display.glow("head", ...)` and other calls on one part. Print `robot.parts()` to see them all.

- **Blockbench:** the name of the group or cube, as you wrote it in the editor. If two parts have the same name, the second one gets `_2`, the third `_3`, and so on.
- **BDEngine:** the name in lowercase, with spaces turned into `_`. Blocks and items are named by their id, for example `minecraft:oak_planks`. A text is named by its text. Repeated names get `_2` too. BDEngine models also have one hidden part called `root`.

```java
display.glow("head", Color.AQUA);
display.brightness("lamp", new Display.Brightness(15, 15));
```

Blockbench removes some cubes while it loads, so the model needs fewer entities. A cube that is hidden inside another cube is dropped. Cubes of the same flat color that touch each other are merged into one. Groups are never removed.

## Colors (Blockbench)

A vanilla block display shows a block. It cannot show a custom texture. So displaymotion turns every cube into one block:

1. It takes the pixels of the texture that cover the faces of the cube. See-through pixels are ignored.
2. It makes one average color from them.
3. It picks the closest block from a list of about 100 full blocks. The list has concrete, wool and terracotta in all 16 colors, stone types, planks, bricks, and blocks like gold, iron, diamond, lapis and snow.

This means:

- One cube is one block. The detail inside a cube is lost. If you want detail, use more and smaller cubes.
- Use clear, flat colors. A cube with a plain red, blue or white texture gives a good block. A muddy color can give a block you did not expect.
- A cube with no texture, or with a texture that is not saved inside the file, becomes a gray block.
- If you do not like the block, change the color a little in Blockbench and load again.

## Size, position and turning

- **Blockbench:** 16 pixels are one block.
- **BDEngine:** the size is the same as in the editor.
- The point (0, 0, 0) of the editor is placed on the location you spawn at. If your model stands on the floor at height 0, its feet touch the location.
- The yaw and pitch of the location turn the whole model. The example plugin turns the model to look at the player with `at.setYaw(player.getLocation().getYaw() + 180f)`.
- The scale in `spawn(motion, at, scale)` changes the size and also the distance parts move in animations.

## What works and what does not

**Blockbench**

- Works: cubes, nested groups, cube rotation on any axis, inflate, animations with rotation, position and scale keyframes. Blending can be linear, smooth or step. Smooth also covers bezier, the bezier handles are ignored.
- Skipped: meshes and every element that is not a cube. Sounds, particles and other keyframe types.
- Molang in keyframes, like `math.sin(q.anim_time * 90)`, is not supported yet. Those values are read as 0.
- Only groups can be animated, not single cubes.
- Textures have to be saved inside the file. Blockbench does this by default.

**BDEngine**

- Works: blocks with block states, items with their display mode, player heads with skins, texts, brightness, groups and animations. Both the new project files and the older text files work.
- Text comes in as plain text. Colors, bold, background and alignment are not read.
- Other item data, like enchantments or custom model data, is not read.
- A block or item that the server does not know (for example from a newer version) becomes stone.

## Limits

- At most 256 parts per model. A BDEngine model uses one of them for `root`.
- An animation can be at most 6000 ticks.
- Every part is one entity that the player's game draws. A model with 200 parts costs more fps than one with 20. Fewer cubes is better.

## Problems and fixes

| What you see | Why | Fix |
| --- | --- | --- |
| `only .bbmodel and .bdengine files work` | The file has another ending. | Save the project again. Use `.bbmodel` or `.bdengine`. |
| `not a bdengine file` | The file is not a BDEngine project. | Save it as a project in BDEngine, not as a command. |
| `no animation named 'x', there are [...]` | The name is wrong. Capital letters matter. | Use a name from the list in the error, or from `animations()`. |
| `a model can have at most 256 parts` | The model has too many parts. | Use fewer and bigger cubes. |
| `saveResource` says it cannot find the file | The file is not in your jar. | Put it in `src/main/resources/models/` and build again. |
| The animation plays but nothing moves | Blockbench: the cubes are not in a group. Or the keyframes use Molang. | Put the cubes in a group. Use plain numbers in keyframes. |
| Colors look wrong or gray | The texture is not inside the file, or the color is muddy. | Save the textures inside the file. Use clear flat colors. |
| The model floats or sinks | The model is not at height 0 in the editor. | Move it in the editor, or spawn it a bit higher or lower. |
| The model looks the wrong way | The yaw of the location. | Turn the location, for example `at.setYaw(at.getYaw() + 180f)`. |
| The model is too big or small | The editor size is not the size you want. | Use the scale: `spawn(motion, at, 0.5f)`. |
| You see nothing | You are more than 48 blocks away, or the player has a client older than 1.19.4. | Go closer and check the client version. |

## Examples

The example plugin ships three models:

- `models/robot.bbmodel` with the animations `idle`, `walk`, `wave` and `dance`
- `models/windmill.bbmodel` with `spin` and `idle`
- `models/chest.bdengine` with `open`

Run `/dm model robot.bbmodel walk` in game. Drop more files into `plugins/displaymotion-example/models/` and run `/dm model <file> [animation]`. Tab complete lists the files and the animations.
