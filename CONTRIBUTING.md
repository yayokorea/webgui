# Contributing to WebGUI

Thank you for wanting to improve WebGUI!

## What we accept

| Type | Accepted |
|------|---------|
| Bug fixes | ✅ Always welcome |
| Translations | ✅ Always welcome |
| New features | ⚠️ Open an issue first |
| Refactors | ❌ Usually not |

For new features, please open a Feature Request issue and wait for a response before writing code.

## Development setup

```bash
git clone https://github.com/yayokorea/webgui-paper.git
cd webgui-paper
./gradlew build                         # build all mod targets and the Paper plugin
./gradlew :1.21.11-fabric:runClient      # launch the Fabric client
```

The first build needs Java 17+ and internet access. Gradle selects Temurin 25 to run the build and automatically downloads missing JDK 21/25 toolchains.

Optional: install and activate [mise](https://mise.jdx.dev/getting-started.html), then run `mise trust` and `mise install` to also select the project's Java in your shell. In an IDE, use the Gradle Wrapper and the project's Daemon JVM criteria.

All mod targets in `settings.gradle` share the same source. To build only Fabric 26.3 and Paper, run `./gradlew :26.3-fabric:build :paper:build`.

## The Paper plugin

`paper/` is a plain Gradle subproject beside the Stonecutter mod builds. It compiles the loader-independent code in `common/` into one jar, `paper/build/libs/webgui-paper-<mod_version>.jar`, that runs on Paper 1.21.1 through 26.3. It talks to Fabric clients that run the mod, over the `webgui:*` plugin channels.

It compiles against the 1.21.1 API with JDK 21 and checks compatibility with the 26.3 API using JDK 25 (`compilePaper263Java`, part of `check`). Gradle provisions both JDKs automatically when needed.

Run a server with the plugin (the world and `plugins/` live in `paper/run/<version>/`, which is git-ignored; accept the EULA in `eula.txt` there on the first run):

```bash
./gradlew :paper:runServer                              # Paper 1.21.11
./gradlew :paper:runServer -PrunPaperVersion=1.21.1     # another version
./gradlew :paper:runServer263                           # Paper 26.3, on Java 25
```

Two checks run in CI, and both can be run locally:

```bash
# Boots a real Paper server and asserts that the plugin enables and disables without errors.
# Writes eula.txt and server.properties into paper/run/<version>/ — see the script's header.
.github/scripts/paper-smoke-test.sh 1.21.11 600          # also: 1.21.1, 26.3

# The same, then the headless protocol test: bots pretend to be a WebGUI Fabric client.
# Paper 1.21.1 and 1.21.11 only. Needs Node (CI uses 22).
(cd paper/e2e && npm ci)
.github/scripts/paper-smoke-test.sh 1.21.11 900 --e2e
```

`PAPER_PORT` sets the server port (default 25565) and `GRADLE_ARGS` passes extra arguments to Gradle, e.g. `--stacktrace`. What the protocol test covers, and how to run it against a server you started yourself, is in [`paper/e2e/README.md`](paper/e2e/README.md).

## Adding a new Minecraft version

1. Add one line to `settings.gradle`:
   ```groovy
   version '1.22-fabric', '1.22-fabric'
   ```
2. Create `versions/1.22-fabric/gradle.properties` with MC-specific dependency versions.
3. Add version-conditional code with Stonecutter syntax if the MC API changed:
   ```java
   //? if mc >= 1.22 {
   newMethod();
   //? } else {
   oldMethod();
   //? }
   ```
4. Run `./gradlew build` to verify all versions compile.

## Pull request checklist

- [ ] `./gradlew build` passes (all MC versions)
- [ ] For changes to the Paper plugin or `common/`: `./gradlew :paper:build` passes too
- [ ] Tested in-game on at least one MC version
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] No unrelated formatting changes

## Code style

- Standard Java conventions
- No unused imports
- Keep it simple
- Write documentation, code comments, and commit messages in English; preserve translations, locales, and multilingual test data.

## License

By contributing you agree your code is licensed under [MIT](LICENSE).
