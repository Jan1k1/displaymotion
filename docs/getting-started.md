# Getting started

You need Paper 26.3, Java 25 and the PacketEvents plugin (2.14.0 or newer) on the server.

## 1. Add the library

`build.gradle.kts`:

```kotlin
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
```

`v0.1.0` is the first release. `main-SNAPSHOT` gives the newest commit instead.

Shade it into your plugin jar and move it to your own package, so two plugins never clash:

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.6.1"
}

tasks.shadowJar {
    relocate("org.jan1k.displaymotion", "your.plugin.libs.displaymotion")
}
```

Do not shade PacketEvents. The server installs it.

## 2. plugin.yml

```yaml
depend: [packetevents]
```

## 3. First display

```java
public final class MyPlugin extends JavaPlugin {

    private static final Animation SHOW = Animations.popIn(20)
            .then(Animations.spin(80).with(Animations.bob(0.3f, 80)));

    private DisplayMotion motion;

    @Override
    public void onEnable() {
        motion = DisplayMotion.create(this);
    }

    public void showSword(Player player) {
        MotionDisplay sword = motion.item(player.getLocation().add(0, 1.5, 0), ItemStack.of(Material.DIAMOND_SWORD));
        sword.play(SHOW);
    }
}
```

That is all. The library removes every display when your plugin disables.

## Rules to remember

- Call `DisplayMotion.create` once, on the main thread, in `onEnable`.
- Keep animations in `static final` fields. Each animation object is baked once.
- Call `play` right after spawning, in the same tick. Then the first frame is already the animation.
- A display never moves by itself. Use `teleport` to move it, and animations to move parts.

Next: [Animations](animations.md), [Models](models.md), [Effects](effects.md), [Importing](importing.md), [Cookbook](cookbook.md).
