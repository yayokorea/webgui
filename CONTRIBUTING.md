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
git clone https://github.com/mc-webgui/webgui.git
cd webgui
./gradlew build          # build all MC versions
./gradlew :1.21.11:runClient  # launch game with the mod
```

Requires Java 21. All three supported Minecraft versions build from the same source.

## The Paper plugin

`paper/` is a plain Gradle subproject beside the Stonecutter mod builds. It compiles the loader-independent code in `common/` into one jar, `paper/build/libs/webgui-paper-<mod_version>.jar`, that runs on Paper 1.21.1 through 26.3. It talks to Fabric clients that run the mod, over the `webgui:*` plugin channels.

It is compiled against the 1.21.1 API and, as a check that is never run or shipped, against the 26.3 API (`compilePaper263Java`, part of `check`). Paper 26 needs Java 25, so **building the plugin needs both JDK 21 and JDK 25**. If Gradle does not find them by itself, point it at them:

```bash
./gradlew :paper:build \
  -Dorg.gradle.java.installations.paths=/path/to/jdk-21,/path/to/jdk-25
# or, as CI does, name the environment variables that hold them:
#   org.gradle.java.installations.fromEnv=JAVA_HOME_21_X64,JAVA_HOME_25_X64
```

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

`PAPER_PORT` sets the server port (default 25565) and `GRADLE_ARGS` passes extra arguments to Gradle, e.g. the JDK paths above. What the protocol test covers, and how to run it against a server you started yourself, is in [`paper/e2e/README.md`](paper/e2e/README.md).

## Adding a new Minecraft version

1. Add one line to `settings.gradle`:
   ```groovy
   version '1.22', '1.22'
   ```
2. Create `versions/1.22/gradle.properties` with MC-specific dependency versions.
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
- Keep it simple — this is a client mod

## License

By contributing you agree your code is licensed under [MIT](LICENSE).
