# Animations

An `Animation` describes how a display changes over time: where it moves, how it turns and how big it is. You build one, then play it on a display.

Time in Minecraft is counted in **ticks**. 20 ticks are one second. So `popIn(20)` takes one second, and `popIn(10)` takes half a second.

An animation moves a display away from where you placed it. `Pose.IDENTITY` means "stay where you are".

## Presets

Presets are ready animations for the most common things. Start here.

| Call | Result | Use it for |
| --- | --- | --- |
| `Animations.popIn(ticks)` | Scale 0 to 1, fast at first. | Showing something. |
| `Animations.popOut(ticks)` | Scale 1 to 0, fast at the end. | Hiding or removing something. |
| `Animations.spin(ticksPerTurn)` | Turn around Y forever. At least 6 ticks per turn. | Items that float and turn. |
| `Animations.turn(pitch, yaw, roll, ticks)` | Turn by these degrees once. | Opening a lid, turning a wheel. |
| `Animations.bob(height, ticks)` | Up and down forever. | Floating. |
| `Animations.slide(x, y, z, ticks, ease)` | Move by an offset once. | Moving a part. |
| `Animations.hold(ticks)` | Stay still. | A pause inside `then`. |

## Combine

You make long animations by joining short ones.

| Call | Result |
| --- | --- |
| `a.then(b)` | `a`, then `b`. `b` starts where `a` ended. |
| `a.with(b)` | Both at once. Offsets and angles add, scales multiply. |
| `a.loop()` | Repeat forever. |

```java
Animation idle = Animations.spin(80).with(Animations.bob(0.3f, 80));
Animation show = Animations.popIn(20).then(idle);
Animation hop = Animations.slide(0f, 1f, 0f, 6, Ease.OUT)
        .then(Animations.slide(0f, -1f, 0f, 6, Ease.IN))
        .loop();
```

Read the code above like this:

- `idle` turns and floats at the same time, for ever.
- `show` grows the display in, then goes on with `idle`.
- `hop` jumps up one block, falls back down, and does this again and again.

`spin` and `bob` already loop. `slide`, `turn`, `popIn` and `popOut` play once. Add `.loop()` if you want them to repeat.

## Easing

Easing is how a movement feels. Without it, everything moves at one speed and looks like a robot.

| Ease | Feel | Example |
| --- | --- | --- |
| `LINEAR` | Same speed all the time. | A wheel that turns. |
| `OUT` | Fast at the start, slow at the end. | Something that pops in. |
| `IN` | Slow at the start, fast at the end. | Something that falls. |
| `IN_OUT` | Slow, fast, slow. | Floating up and down. |

## Your own keyframes

A keyframe says: at this tick, the display must have this pose. The library fills the space between keyframes for you.

```java
Animation wobble = Animation.keyframes()
        .at(0, Pose.IDENTITY)
        .at(5, Pose.IDENTITY.withRotation(0f, 0f, 15f), Ease.OUT)
        .at(10, Pose.IDENTITY.withRotation(0f, 0f, -15f), Ease.IN_OUT)
        .at(15, Pose.IDENTITY, Ease.IN)
        .build()
        .loop();
```

- Ticks must go up.
- The ease of a keyframe is how the blend *into* it feels. So the ease at tick 5 above is how it feels to go from tick 0 to tick 5.
- Before the first keyframe the display holds the first pose. After the last keyframe it holds the last pose.

## Pose

A pose is three things: an offset, a rotation and a scale.

```java
Pose.IDENTITY.withOffset(x, y, z).withRotation(pitch, yaw, roll).withScale(s)
```

- **Offset** moves it, in blocks.
- **Rotation** turns it, in degrees. The order is yaw, then pitch, then roll.
- **Scale** changes the size. `withScale(2f)` is twice as big. `withScale(1f, 2f, 1f)` is twice as tall. A negative scale mirrors it.

A pose never changes. Every `with...` call returns a new pose.

## Playing

```java
display.play(show);                         // whole display, replaces what plays now
display.play(Animations.popOut(10), display::remove);   // runs remove on the main thread at the end
display.play("hub", Animations.turn(0f, 0f, 360f, 60).loop());   // one part of a model
display.stop();
```

- `play(animation)` plays on the whole display. If something plays already, it is replaced.
- `play(animation, onEnd)` also runs code when the animation ends. This is how you remove a display after it shrinks.
- `play("part", animation)` plays on one part of a [model](models.md). The children of that part follow it.
- `stop()` freezes the display where it is.
- `isPlaying()` tells you if an animation plays on the whole display.

## Timing

- A frame goes out every 2 ticks. The player's game blends between frames. Lengths are rounded to whole frames.
- An animation can be at most 6000 ticks (intro plus one loop).
- A new `play` blends from the current pose to the first frame over 2 ticks. So there is no jump.
- An endless animation never calls `onEnd`. Anything after it in `then` never plays.
- `spin` needs at least 6 ticks per turn. With less, the player's game cannot tell which way it turns.
- Keep animations in `static final` fields. Each animation object is baked once, so one object can be used for many displays.
