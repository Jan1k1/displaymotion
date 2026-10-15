# Models

A model is many displays that move as one. Parts can be blocks, items or text.

```java
MotionDisplay lamp = motion.model(location)
        .block("post", Material.DARK_OAK_FENCE.createBlockData(), Pose.IDENTITY.withOffset(0f, 1f, 0f).withScale(1f, 2f, 1f))
        .item("light", ItemStack.of(Material.LANTERN), Pose.IDENTITY.withOffset(0f, 1.3f, 0f), "post")
        .text("sign", Component.text("Shop"), Pose.IDENTITY.withOffset(0f, 0.5f, 0.2f).withScale(0.5f), "post")
        .spawn()
        .play(Animations.popIn(20));
```

## Rules

- A parent must be added before its children.
- Offset and rotation of a part pass to its children.
- Scale of a part is its own size. Children do not get it.
- Animations pass everything to the children, scale too.
- Blocks turn and scale around their center. Items around their model center. Text grows up from its pose.
- The model faces the yaw and pitch of the spawn location.
- At most 256 parts.

## Animate one part

```java
windmill.play(Animations.popIn(25));                                  // the whole model
windmill.play("axle", Animations.turn(0f, 0f, -360f, 120).loop());   // the axle and everything on it
```

Tip: when many parts turn around one point, put them under one parent and turn the parent.

## Groups

A group is an invisible pivot. Nothing is spawned for it, it only holds children.

```java
motion.model(location)
        .group("hinge", Pose.IDENTITY.withOffset(0f, 0.5f, -0.4f))
        .block("lid", Material.DARK_OAK_PLANKS.createBlockData(), Pose.IDENTITY.withOffset(0f, 0f, 0.4f).withScale(1f, 0.2f, 0.8f), "hinge")
        .spawn()
        .play("hinge", Animations.turn(-70f, 0f, 0f, 10));   // the lid opens around the hinge
```

## Matrix parts

If you already have a 4x4 matrix (from `/summon`, another tool, or your own math), give it row by row:

```java
float[] matrix = {0.5f, 0, 0, -0.25f,  0, 0.5f, 0, 0,  0, 0, 0.5f, -0.25f,  0, 0, 0, 1};
model.block("cube", Material.STONE.createBlockData(), matrix, null);
```

Matrix parts work exactly like vanilla: blocks turn around their corner and the whole matrix, scale too, passes to the children.
There are matrix versions of `block`, `item`, `text` and `group`. The importers use them.

## Looks per part

```java
lamp.glow("light", Color.ORANGE);
lamp.brightness("sign", new Display.Brightness(15, 15));
lamp.billboard("sign", Display.Billboard.CENTER);
lamp.text("sign", Component.text("Open"));
lamp.background("sign", Color.fromARGB(0, 0, 0, 0));   // no dark box behind the text
```

## Example

`example/src/main/java/org/jan1k/displaymotion/example/ExamplePlugin.java` builds a 53 part windmill. Run it with `/dm windmill`.
To build models in an editor instead, see [Importing](importing.md).
