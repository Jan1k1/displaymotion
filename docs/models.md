# Models

A model is many displays that move as one. Use a model when one block or item is not enough: a lamp, a windmill, a pet with a body and a head.

Parts can be blocks, items or texts. Every part has a **name**. You use the name later to move or change that part.

If you draw your model in an editor, read [Importing](importing.md) instead. This page is for building models in code.

## Your first model

```java
MotionDisplay lamp = motion.model(location)
        .block("post", Material.DARK_OAK_FENCE.createBlockData(), Pose.IDENTITY.withOffset(0f, 1f, 0f).withScale(1f, 2f, 1f))
        .item("light", ItemStack.of(Material.LANTERN), Pose.IDENTITY.withOffset(0f, 1.3f, 0f), "post")
        .text("sign", Component.text("Shop"), Pose.IDENTITY.withOffset(0f, 0.5f, 0.2f).withScale(0.5f), "post")
        .spawn()
        .play(Animations.popIn(20));
```

How to read it:

- `motion.model(location)` starts a model. The location is the point where it stands.
- `.block("post", ...)` adds a block part named `post`. Its offset is `(0, 1, 0)` and its scale is `(1, 2, 1)`. So it is a post that is one block wide and two blocks tall, and it stands on the location.
- `.item("light", ...)` adds a lantern. The last text `"post"` is the **parent**. The lantern moves with the post.
- `.text("sign", ...)` adds a text part, also under the post.
- `.spawn()` makes the model. It returns a `MotionDisplay`.
- `.play(...)` plays an animation on the whole model.

For a block part, the **offset is where its center goes** and the **scale is its size in blocks**. A block with scale `(1, 2, 1)` is a post two blocks tall. To make it stand on the floor, put its center one block up.

## Rules

- A parent must be added before its children.
- Names must be different. Two parts with the same name give an error.
- Offset and rotation of a part pass to its children.
- Scale of a part is its own size. Children do not get it.
- Animations pass everything to the children, scale too.
- Blocks turn and scale around their center. Items around their model center. Text grows up from its pose.
- The model faces the yaw and pitch of the spawn location.
- At most 256 parts.

## Parents

A parent is a part that other parts follow. When the parent moves or turns, the children go with it. This is how you make arms, doors and wheels.

```java
windmill.play(Animations.popIn(25));                                  // the whole model
windmill.play("axle", Animations.turn(0f, 0f, -360f, 120).loop());   // the axle and everything on it
```

Tip: when many parts turn around one point, put them under one parent and turn the parent. This is easier than turning every part.

## Groups

A group is an invisible pivot. Nothing is shown for it. It only holds children, and you can turn or move it.

Use a group when the point you want to turn around is not a block. A door turns around its hinge. A lid turns around its back edge.

```java
motion.model(location)
        .group("hinge", Pose.IDENTITY.withOffset(0f, 0.5f, -0.4f))
        .block("lid", Material.DARK_OAK_PLANKS.createBlockData(), Pose.IDENTITY.withOffset(0f, 0f, 0.4f).withScale(1f, 0.2f, 0.8f), "hinge")
        .spawn()
        .play("hinge", Animations.turn(-70f, 0f, 0f, 10));   // the lid opens around the hinge
```

The hinge is the pivot. The lid sits 0.4 blocks away from the hinge. When the hinge turns, the lid swings up like a real lid.

## Matrix parts

If you already have a 4x4 matrix (from `/summon`, another tool, or your own math), give it row by row:

```java
float[] matrix = {0.5f, 0, 0, -0.25f,  0, 0.5f, 0, 0,  0, 0, 0.5f, -0.25f,  0, 0, 0, 1};
model.block("cube", Material.STONE.createBlockData(), matrix, null);
```

Matrix parts work exactly like vanilla: blocks turn around their corner and the whole matrix, scale too, passes to the children.
There are matrix versions of `block`, `item`, `text` and `group`. The importers use them.

## Looks per part

These calls change how one part looks. Leave the part name out to change every part.

```java
lamp.glow("light", Color.ORANGE);
lamp.brightness("sign", new Display.Brightness(15, 15));
lamp.billboard("sign", Display.Billboard.CENTER);
lamp.text("sign", Component.text("Open"));
lamp.background("sign", Color.fromARGB(0, 0, 0, 0));   // no dark box behind the text
lamp.shadow("light", 1.5f, 0.8f);                      // round shadow on the ground
lamp.lineWidth("sign", 120);
lamp.textOpacity("sign", 200);
lamp.textShadow("sign", true);
lamp.seeThrough("sign", true);
lamp.alignment("sign", TextDisplay.TextAlignment.LEFT);
lamp.item("icon", new ItemStack(Material.DIAMOND));     // swap the item of an item part
lamp.block("base", Material.GOLD_BLOCK.createBlockData());
```

- `glow` draws a colored outline. Use `null` to turn it off. Text parts do not glow.
- `brightness` sets a fixed light level (block light, sky light). The part is bright even in a cave.
- `billboard` makes the part turn to the player. `CENTER` turns in every direction.
- `text` changes the text of a text part while it is shown.
- `background` changes the box behind a text. Alpha 0 means no box.
- `shadow(radius, strength)` draws a round shadow under a part. Radius is 0 to 64 (0 is none), strength is 0 or more (1 is the default).
- `lineWidth` (default 200), `textOpacity` (0 to 255), `textShadow`, `seeThrough` and `alignment` style text parts. Without a part name they only change the text parts. With a part name, a part that is not text throws `IllegalArgumentException`.
- `item` and `block` swap what an item or block part shows. With a part name, the wrong kind of part throws `IllegalArgumentException`. Without a name, every part of that kind changes, and a display with none throws.

## Mannequin NPCs

Since 1.21.9 vanilla has a mannequin: a player shaped entity with any skin. displaymotion sends it as packets like the displays, so it is not a real entity on the server and costs nothing there.

```java
MotionDisplay guide = motion.npc(location, Skin.of(player));     // or Skin.name("Notch"), Skin.texture(value, signature)
guide.npc()
        .name(Component.text("Guide", NamedTextColor.GOLD))
        .equip(EquipmentSlot.HAND, ItemStack.of(Material.DIAMOND_SWORD))
        .pose(NpcPose.CROUCHING)
        .size(1.5f)                                                // 0.0625 to 16, 1 is normal
        .lookAtPlayers();                                          // each player sees it look at them, 8 blocks

guide.npc().swing();                                               // one hit with the main hand
```

- In a model, add it with `.npc("name", skin)` and get it with `display.npc("name")`. It stands at the display location and faces where the display looks. Text, items and blocks of the same model move and animate around it as usual.
- `Skin.name("Notch")` is looked up by the players' games, so it works on offline mode servers. `Skin.of(player)` copies the skin a player has now.
- Poses: `STANDING`, `CROUCHING`, `SWIMMING`, `GLIDING`, `SLEEPING`.
- `size(size)` changes the mannequin body size, clamped to 0.0625 to 16 (1 is normal). Display `scale` does not touch the mannequin body.
- `lookAtPlayers(range)` sends each player their own head turn, only when it changes. Farther players see it look ahead.
- `hitbox`, `onClick`, `teleport`, `moveTo`, `follow`, `ride`, `glow`, `lod` and per player visibility work like on any display. Mannequins need a 1.21.9 or newer server and game, `npc(...)` throws an `UnsupportedOperationException` on older servers. Games older than 26.3 do not show the swing.

## Level of detail

Big models cost the players' games fps, also when they are far away and too small to see well. `lod` saves that:

```java
MotionDisplay windmill = motion.model(location)
        .block("tower", ...)
        .block("window", ...)
        .detail("window")             // small extras
        .spawn()
        .lod(24);                     // past 24 blocks
```

- Players farther than the lod get no animation frames. They see the model stand still, and get no packets for it.
- Parts marked with `detail(...)` are not spawned for them at all, so their game has fewer entities to draw.
- When they come closer they get the current pose and the detail parts at once.
- `lod(0)` turns it off, that is the default. Mark parts as detail after you add them.

## Clicks

A display has no hitbox, so players click through it. Give it one with `hitbox`, then listen with `onClick`:

```java
MotionDisplay chest = model.spawn(motion, location)
        .hitbox(1f, 1f);                           // 1 block wide, 1 block high

chest.onClick(click -> {
    if (click.rightClick()) {
        model.play(chest, "open");
    }
});
```

- The box is a vanilla Interaction entity, sent with packets like the display. Players need no mod.
- `hitbox(width, height)` puts the bottom center of the box at the display location. Its name is `main`.
- For more boxes, give each one a name and an offset: `hitbox("lid", 0.8f, 0.3f, 0, 0.6, 0)`. The offset turns with the display.
- `onClick` runs on the thread that owns the player who clicked. On Paper that is the main thread, so you can use the Bukkit API there. `click.player()` is who clicked, `click.hitbox()` is the name of the box, `click.leftClick()` is true for a left click.
- The box stays upright and does not follow animations. It moves with `teleport`.
- Only players who see the display can click it. Clicks from too far away are ignored.

## Riding

A display can ride a player or a mob, like a hat, a backpack or wings. It sticks to them while they walk.

```java
MotionDisplay crown = motion.model(player.getLocation())
        .block("band", Material.GOLD_BLOCK.createBlockData(), Pose.IDENTITY.withScale(0.5f, 0.12f, 0.5f))
        .spawn()
        .ride(player, Follow.HEAD);             // turns where the player looks
```

- The display becomes a passenger of the entity, only on the players' side. Their game moves it every frame, so it never lags behind and walking costs the server nothing.
- The server only checks the turn of the entity once per tick, and sends one small packet when it changes. That is cheap even with a thousand riders.
- The display location is where a passenger sits: the top of the hitbox, 1.8 blocks above the feet of a player. The drawn head goes up to about 1.875, so lift a hat by about 0.04 or it sits inside the head. `withOffset(0f, -0.74f, -0.235f)` puts a 0.25 deep backpack right on the back.
- `Follow.BODY` turns with the body (backpacks, wings) and leans forward when a player sneaks. `Follow.HEAD` moves like the head (hats): on a player it turns and tilts around the neck and goes down when they sneak. `Follow.NONE` keeps its turn.
- The body of a player is worked out like the game does it (walk direction, the head can turn 50 degrees before the body follows), because the server does not know it.
- The player who wears it sees a turn a little late, about their ping. The server only learns where they look when their game tells it. Other players see it in time with the head.
- `selfView(...)` sets what the player who wears it sees, other players always see it. `SelfView.SHOW` (default for HEAD), `SelfView.HIDE_LOOKING_DOWN` (default for BODY: hidden for the wearer while they look down more than 35 degrees, the only time a backpack shows in first person) and `SelfView.HIDE`. The server cannot tell first person from F5, so a choice counts for both.
- Real passengers of the entity stay on it.
- `dismount()` or `teleport(...)` stops riding. Call `ride` on the main thread.
- When the player dies, the display waits and comes back with them. When a mob dies, the display stays where it was.

## Following

`follow` makes a display float after an entity. Unlike riding it trails a bit behind, which looks alive for pets, drones and floating names:

```java
pet.follow(player);                                        // above and behind, glides over 4 ticks
pet.follow(player, new Vector(0.7, 1.7, -0.6), 5);         // over the right shoulder, a bit slower
pet.unfollow();
```

- The offset turns with the body: x to the right, y up, z forward (negative is behind).
- Every 2 ticks it moves to the new spot and the players' games glide it there, so the server sends little.
- `teleport`, `moveTo` or `unfollow` stops it. When the target dies, a player keeps it after respawn, a mob leaves it where it was.
- Call it on the main thread.
- To glide a display to a fixed spot instead of an entity, use `display.moveTo(location, ticks)` or `display.moveTo(location, ticks, ease)`. It keeps its rotation, and a new `moveTo`, `teleport` or `follow` stops the old one.

## Seats

Players can sit on a model. Mark the places with `seat`, then put the player there with `sit`, for example on a right click:

```java
MotionDisplay bench = model.spawn()
        .hitbox(1.6f, 0.9f)
        .seat("left", -0.4, 0.35, 0)            // x, height, z
        .seat("right", 0.4, 0.35, 0);

bench.onClick(click -> {
    if (click.rightClick() && !bench.sit(click.player(), "left")) {
        bench.sit(click.player(), "right");
    }
});
```

- The hips of a sitting player end up about 0.1 above the seat point. For a seat surface at 0.45 use 0.35.
- A seat is a real invisible entity on the server, because a player can only ride something the server knows. It is made the first time someone sits.
- `sit` returns false when someone sits there already. The player gets up with sneak, like from a horse.
- Seats move with `teleport` and go away with `remove`. They are not saved with the world.

## Find the part names

`display.parts()` gives the names of all parts, in the order you added them.

## Example

`example/src/main/java/org/jan1k/displaymotion/example/ExamplePlugin.java` builds a 53 part windmill. Run it with `/dm windmill`.
`Dragon.java` next to it is the big one: a dragon from two models (about 240 parts and 140 more for the fire) that roars, flies a circle and lands, with `/dm dragon`. `/dm dragon build` first lets it fly together from a swirl of glowing blocks, `/dm dragon three` spawns a fire, an ice and an ender dragon. All its motion is plain `Animation` code, see the `Motion` record at the end of the file.
To build models in an editor instead, see [Importing](importing.md).
