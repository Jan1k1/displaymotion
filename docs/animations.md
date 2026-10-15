# Animations

An `Animation` is a pose over time. Build one from the presets, then combine.

## Presets

| Call | Result |
| --- | --- |
| `Animations.popIn(ticks)` | Scale 0 to 1, fast at first. |
| `Animations.popOut(ticks)` | Scale 1 to 0, fast at the end. |
| `Animations.spin(ticksPerTurn)` | Turn around Y forever. At least 6 ticks per turn. |
| `Animations.turn(pitch, yaw, roll, ticks)` | Turn by these degrees once. |
| `Animations.bob(height, ticks)` | Up and down forever. |
| `Animations.slide(x, y, z, ticks, ease)` | Move by an offset once. |
| `Animations.hold(ticks)` | Stay still. A pause inside `then`. |

## Combine

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

## Your own keyframes

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
- The ease of a keyframe is how the blend *into* it feels.
- `Ease` is `LINEAR`, `IN`, `OUT`, `IN_OUT`.

## Pose

`Pose.IDENTITY.withOffset(x, y, z).withRotation(pitch, yaw, roll).withScale(s)`

- Offset in blocks. Rotation in degrees, applied yaw, then pitch, then roll. Scale per axis, negative mirrors.

## Playing

```java
display.play(show);                         // whole display, replaces what plays now
display.play(Animations.popOut(10), display::remove);   // runs remove on the main thread at the end
display.play("hub", Animations.turn(0f, 0f, 360f, 60).loop());   // one part of a model
display.stop();
```

## Timing

- A frame goes out every 2 ticks. The client blends between frames. Lengths round to whole frames.
- At most 6000 ticks (intro plus one loop).
- A new `play` blends from the current pose to the first frame over 2 ticks.
- An endless animation never calls `onEnd`. Anything after it in `then` never plays.
