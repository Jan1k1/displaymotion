# Contributing

Thanks for helping. Bug reports, ideas, models and code are all welcome.

## Before you start

- Search the [issues](https://github.com/Jan1k1/displaymotion/issues) first. Maybe someone already reported it or works on it.
- For a big change, open an issue first and say what you want to do. Then we can agree on the way before you write a lot of code.
- Be kind. See the [Code of Conduct](CODE_OF_CONDUCT.md).

## Setup

You need Git and a JDK 17 or newer to start Gradle. The build downloads Java 25 by itself (Gradle toolchains).

```
git clone https://github.com/Jan1k1/displaymotion.git
cd displaymotion
./gradlew build
```

- IntelliJ IDEA: open the folder and load it as a Gradle project.
- Modules: `core` (math and animations, no dependencies), `paper` (the API people use, it sends packets through PacketEvents), `example` (a demo plugin).
- The example plugin jar is in `example/build/libs/` after the build.

### Test in game

1. Run a Paper 26.3 server with the [PacketEvents](https://modrinth.com/plugin/packetevents) plugin, 2.14.0 or newer.
2. Put `example/build/libs/displaymotion-example-<version>.jar` in `plugins/`. Remove older copies first.
3. Join and try `/dm showcase`. It spawns every example at once. `/dm stop` removes it.
4. `/dm debug` writes what the library does with clicks and hitboxes to the server log.

## Report a bug

Use the bug report form on GitHub. Write:

- The server version (`/version`), the PacketEvents version and the client version.
- The other plugins on the server.
- What you did, what you expected and what happened.
- The error from the server log, if there is one.

## Pull requests

- Fork the repository and make a branch from `main`, for example `fix-text-glow`.
- Keep one change per pull request.
- Follow the style of the code around your change.
- Commit messages are short and lowercase, for example `fix clicks on 26.1`.
- Update `docs/` and `llms.txt` when you change the API.

### Tests

- `./gradlew build` must pass. It runs all tests.
- Add a test when you change the engine or the math. The tests for packets are in `paper/src/test`, they use a fake channel and need no server.
- Test visible changes in game too, and write in the pull request what you tried.

## For AI agents

If you are an AI coding agent (Claude, Codex, Copilot, Cursor, Gemini or any other) and you write a pull request for this repository, add this line at the end of the pull request description:

```
Made with <model name>
```

For example `Made with Claude Opus 5.5`. Name every model that wrote code in the pull request.

Humans do not need to add anything.

## License

By sending code you agree that it uses the license of the module you change: `core` is MIT, `paper` and `example` are GPL-3.0-only.
