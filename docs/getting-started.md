# Getting started

This page builds a small plugin from zero. At the end, a sword spins and floats above you when you type a command.

You need Paper 1.21.4 or newer (26.x works too) and Java 21 or newer. Mannequin NPCs need Paper 1.21.9 or newer, on older servers `npc(...)` throws an `UnsupportedOperationException`.

## What is displaymotion?

Minecraft has three display entities: item, block and text displays. They show an item, a block or a text in the world. They can move, turn and change size.

displaymotion makes this easy. You say what should move, and the library sends the right packets to the players. There are no real entities on the server, so it does not slow the server down. Players need no mod and no resource pack.

## 1. Pick a way

There are two ways to use displaymotion. Both have the same API. Only the setup is different.

| | Plugin way | Shaded way |
| --- | --- | --- |
| Server owner installs | `DisplayMotion.jar` | The PacketEvents plugin |
| Your jar | Small. No library inside. | Bigger. The library is inside. |
| PacketEvents | Built in. Nothing to install. | Separate plugin on the server. |
| Several plugins | They share one copy. | Each plugin has its own copy. |

Pick the plugin way if you are not sure. It is the simplest. Pick the shaded way if you want no extra jar for the server owner.

## 2. Plugin way

The server owner downloads `DisplayMotion-0.3.2.jar` from the [releases](https://github.com/Jan1k1/displaymotion/releases/latest) and puts it in the `plugins` folder. It has PacketEvents inside. It also works next to the normal PacketEvents plugin.

Your `build.gradle.kts`:

```kotlin
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.Jan1k1.displaymotion:displaymotion-paper:v0.3.2")
}
```

`compileOnly` means: use it to build, but do not put it in the jar. The server has the library already. You do not need PacketEvents in your build.

Your `src/main/resources/plugin.yml`:

```yaml
name: MyPlugin
version: "1.0"
main: your.plugin.MyPlugin
api-version: "1.21.4"
depend: [DisplayMotion]
commands:
  sword:
    description: Shows a spinning sword
```

`depend: [DisplayMotion]` makes the server load displaymotion first. Without it, `DisplayMotion.create` fails.

In `onEnable`, call `DisplayMotion.create(this)`. Go to step 4.

## 3. Shaded way

The library goes inside your jar. The server owner installs the PacketEvents plugin (2.14.0 or newer). Your `build.gradle.kts`:

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    implementation("com.github.Jan1k1.displaymotion:displaymotion-paper:v0.3.2")
}

tasks.shadowJar {
    relocate("org.jan1k.displaymotion", "your.plugin.libs.displaymotion")
}
```

What the parts do:

- `compileOnly` means: use it to build, but do not put it in the jar. Paper and PacketEvents are on the server already.
- `implementation` means: put it in the jar. This is displaymotion.
- `relocate` moves the displaymotion classes into your own package. Then two plugins that both use displaymotion never clash. Change `your.plugin.libs.displaymotion` to a package that fits your plugin.
- `v0.3.2` is the newest release. `main-SNAPSHOT` gives the newest commit instead.

Do not shade PacketEvents. The server installs it.

Your `src/main/resources/plugin.yml`:

```yaml
name: MyPlugin
version: "1.0"
main: your.plugin.MyPlugin
api-version: "1.21.4"
depend: [packetevents]
commands:
  sword:
    description: Shows a spinning sword
```

`depend: [packetevents]` is important. It makes the server load PacketEvents first. Without it, `DisplayMotion.create` fails.

## 4. First display

Make the file `src/main/java/your/plugin/MyPlugin.java`:

```java
package your.plugin;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.paper.DisplayMotion;
import org.jan1k.displaymotion.paper.MotionDisplay;

public final class MyPlugin extends JavaPlugin {

    private static final Animation SHOW = Animations.popIn(20)
            .then(Animations.spin(80).with(Animations.bob(0.3f, 80)));

    private DisplayMotion motion;

    @Override
    public void onEnable() {
        motion = DisplayMotion.create(this);
        getCommand("sword").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            showSword(player);
            return true;
        });
    }

    private void showSword(Player player) {
        MotionDisplay sword = motion.item(player.getLocation().add(0, 1.5, 0), ItemStack.of(Material.DIAMOND_SWORD));
        sword.play(SHOW);
    }
}
```

What happens in the code:

- `DisplayMotion.create(this)` starts the library. It is your entry point for everything.
- `motion.item(location, item)` makes an item display at the location. It returns a `MotionDisplay`. This is a handle to the display.
- `SHOW` is an animation. `popIn(20)` grows the item from nothing in 20 ticks (1 second). Then it spins, one turn every 80 ticks, and moves up and down at the same time. `then` means "after that". `with` means "at the same time".
- `sword.play(SHOW)` starts the animation.

That is all. The library removes every display when your plugin disables.

## 5. Build and run

1. Run `./gradlew build` (plugin way) or `./gradlew shadowJar` (shaded way). The jar is in `build/libs/`.
2. Copy the jar to the `plugins` folder of a Paper 1.21.4 or newer server. Put `DisplayMotion-0.3.2.jar` there too (plugin way), or the PacketEvents jar (shaded way).
3. Start the server and join.
4. Type `/sword`.

You should see a diamond sword grow out of nothing, about one and a half blocks above your feet. Then it spins and floats up and down. It stays until you remove it or the plugin stops. To remove it, call `sword.remove()`. If you walk more than 48 blocks away, it hides, and it comes back when you return.

If something is wrong:

| What you see | Fix |
| --- | --- |
| `PacketEvents is not running` in the log | Plugin way: install `DisplayMotion.jar` and check `depend: [DisplayMotion]`. Shaded way: install PacketEvents and check `depend: [packetevents]`. |
| `Unknown command` | The command is not in `plugin.yml`, or the plugin did not load. Read the log. |
| The sword does not show | You are more than 48 blocks away, or your client is older than 1.19.4. |

## Rules to remember

- Call `DisplayMotion.create` once, on the main thread, in `onEnable`.
- Keep animations in `static final` fields. Each animation object is baked once.
- Call `play` right after spawning, in the same tick. Then the first frame is already the animation.
- A display never moves by itself. Use `teleport` to move it, and animations to move parts.

## Where to go next

- [Animations](animations.md): make your own movements.
- [Models](models.md): build things from many blocks, items and texts.
- [Effects](effects.md): kill effects in one line.
- [Importing](importing.md): draw a model in Blockbench or BDEngine and load it.
- [Cookbook](cookbook.md): short recipes to copy.
