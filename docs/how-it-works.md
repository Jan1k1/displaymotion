# How it works

You do not need this page to use the library. Read it if you want to know why it is fast, or why something has a limit.

## Threads

- **Main thread:** every 10 ticks it copies the online players (position, world, connection). Events for quit, teleport, respawn and world change only queue a note. Nothing else runs there.
- **Motion thread:** one per plugin, every 50 ms. It runs your commands, decides who sees what, steps animations every 2 ticks and builds the packets.
- **Network threads:** each player gets one task and one bundle per tick.
- `MotionDisplay` methods only queue a command. They are safe from any thread.

## Why it is cheap

- An animation is baked once into a matrix per frame. A step reads the table.
- Only parts that moved are computed again. Children follow their parent.
- A display that nobody sees is not computed.
- A packet is encoded once and shared by every viewer. Only the changed transform fields are sent.
- A slow connection skips frames and gets the full state when it catches up.
- Parts that stay above the spawn point get a culling box, so the client skips them when they are off screen.
- Big animations are baked on another thread.

## Fake entities

Every part is a display entity that exists only on the client. The server never ticks it, saves it or collides with it.
The library spawns it when a player comes in range, and removes it when the player leaves the range (with 4 blocks of margin), quits, teleports far, respawns or changes world.

## Limits

- Java Edition only. Bedrock players (Geyser) do not see display entities.
- Clients from 1.19.4 up work through ViaVersion. Older clients do not have display entities.
- Text over 32000 characters of JSON is refused, ViaVersion kicks players on huge text.
- Glide time for `teleport` is 0 to 59 ticks, the client limit.
- The client blends in straight lines. Curves are sent as a frame every 2 ticks.

## Client bugs we work around

- **MC-295859:** a teleport and a transform in the same tick get out of sync. The library never moves an entity to fake a transform.
- **MC-311974 (26.3):** entity movement can stutter. Animations change the transform only, never the position.
- **MC-260874:** a long blend started early shows a "ghost". Frames always use the same 2 tick length.
- **MC-259926:** late joiners do not know the blend in progress. They get the current frame at spawn.
