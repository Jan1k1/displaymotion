# Getting started

This page builds a small plugin from zero. At the end, a sword spins and floats above you when you type a command.

You need Paper 26.3, Java 25 and the PacketEvents plugin (2.14.0 or newer) on the server. PacketEvents is a separate plugin. The server owner installs it. You do not put it in your jar.

## What is displaymotion?

Minecraft has three display entities: item, block and text displays. They show an item, a block or a text in the world. They can move, turn and change size.

displaymotion makes this easy. You say what should move, and the library sends the right packets to the players. There are no real entities on the server, so it does not slow the server down. Players need no mod and no resource pack.

## 1. Add the library

Make a Gradle project. This is the full `build.gradle.kts`:

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
    compileOnly("io.papermc.paper:paper-api:26.3.build.180-stable")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    implementation("com.github.Jan1k1.displaymotion:displaymotion-packetevents:v0.1.0")
}

tasks.shadowJar {
    relocate("org.jan1k.displaymotion", "your.plugin.libs.displaymotion")
}
```

What the parts do:

- `compileOnly` means: use it to build, but do not put it in the jar. Paper and PacketEvents are on the server already.
- `implementation` means: put it in the jar. This is displaymotion.
- `relocate` moves the displaymotion classes into your own package. Then two plugins that both use displaymotion never clash. Change `your.plugin.libs.displaymotion` to a package that fits your plugin.
- `v0.1.0` is the first release. `main-SNAPSHOT` gives the newest commit instead.

Do not shade PacketEvents. The server installs it.

## 2. plugin.yml

Make the file `src/main/resources/plugin.yml`:

```yaml
name: MyPlugin
version: "1.0"
main: your.plugin.MyPlugin
api-version: "26.3"
depend: [packetevents]
commands:
  sword:
    description: Shows a spinning sword
```

`depend: [packetevents]` is important. It makes the server load PacketEvents first. Without it, `DisplayMotion.create` fails.

## 3. First display

Make the file `src/main/java/your/plugin/MyPlugin.java`:

```java
package your.plugin;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.packetevents.DisplayMotion;
import org.jan1k.displaymotion.packetevents.MotionDisplay;

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

## 4. Build and run

1. Run `./gradlew shadowJar`. The jar is in `build/libs/`.
2. Copy the jar to the `plugins` folder of a Paper 26.3 server. Put the PacketEvents jar there too.
3. Start the server and join.
4. Type `/sword`.

You should see a diamond sword grow out of nothing, about one and a half blocks above your feet. Then it spins and floats up and down. It stays until you remove it or the plugin stops. To remove it, call `sword.remove()`. If you walk more than 48 blocks away, it hides, and it comes back when you return.

If something is wrong:

| What you see | Fix |
| --- | --- |
| `PacketEvents is not running` in the log | Install PacketEvents on the server. Check that `depend: [packetevents]` is in `plugin.yml`. |
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
