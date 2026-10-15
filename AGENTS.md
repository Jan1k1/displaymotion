# AGENTS.md

Notes for AI coding agents that work in this repository.

- Build and test: `./gradlew build`. All tests must pass.
- Modules: `core` (math and animations, no dependencies), `packetevents` (the API people use), `example` (a demo plugin).
- Java 25 toolchain, `core` compiles with release 21. Paper API 26.3, PacketEvents 2.14.0 as compileOnly.
- Follow the style of the code around your change. No new dependencies without a reason.
- When you change the public API, update `docs/` and `llms.txt` too.
- When you open a pull request, write the AI model you are at the bottom, for example `Made with Claude Opus 5.5`. See CONTRIBUTING.md.
