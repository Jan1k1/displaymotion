# AGENTS.md

Notes for AI coding agents that work in this repository.

- Build and test: `./gradlew build`. All tests must pass.
- Modules: `core` (math and animations, no dependencies), `paper` (the API people use, it sends packets through PacketEvents), `example` (a demo plugin), `plugin` (the standalone DisplayMotion jar with PacketEvents inside).
- Java 25 toolchain, all modules compile with release 21. Built against Paper API 1.21.4, runs on 1.21.4 to 26.3. PacketEvents 2.14.0 as compileOnly.
- Follow the style of the code around your change. No new dependencies without a reason.
- When you change the public API, update `docs/` and `llms.txt` too.
- When you open a pull request, write the AI model you are at the bottom, for example `Made with Claude Opus 5.5`. See CONTRIBUTING.md.
