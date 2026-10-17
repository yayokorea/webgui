# Paper plugin protocol test

Headless: bots speak the Minecraft protocol (via `minecraft-protocol`) and pretend to be a
WebGUI Fabric client — on the wire that is one `minecraft:register` listing the `webgui:*`
channels. It checks the handshake order, the config snapshot, asset requests, console commands,
page events (fire, malformed, flood), `WebGUIClientReadyEvent`, entity bindings, the death screen,
`requireClientMod`, and an outdated / vanilla client.

- **Entity bindings:** mobs are summoned next to a bot with known UUIDs and the bot sends the
  packets of a real click (`use_entity` INTERACT_AT, then INTERACT). It asserts one page per click,
  the resolved URL and entity context, that an off-hand click, a bare INTERACT and an unbound entity
  open nothing, and that `cancelInteraction` stops a villager's trade window and an armor stand's
  equipment swap (each with a control that is not cancelled).
- **Death screen:** sets `deathScreenUrl`, then kills a bot with `/kill`, with `damage ... by <zombie>`
  and `damage ... by <player>` and checks the info JSON of each.

`minecraft-protocol` supports Minecraft up to 26.1, so this cannot drive Paper 26.2 or newer.
It runs against 1.21.1 and 1.21.11.

CI runs it as the `paper e2e (1.21.11)` job of `.github/workflows/build.yml`, and the FIFO
below is what `.github/scripts/paper-smoke-test.sh 1.21.11 900 --e2e` sets up for you: it boots the
server, checks the plugin enabled without errors, runs this test, then stops the server and checks
the plugin disabled cleanly. `npm ci` in this folder comes first. That job is not part of the
required "Build all versions" check, because the test is timing-based and has not run on a hosted
runner for long.

By hand:

```sh
# 1. A server with the plugin, debug logging on, and a FIFO as its console.
#    (paper/run/<version>/ needs eula.txt and a server.properties with online-mode=false.)
mkfifo /tmp/paper-in
exec 3<>/tmp/paper-in
../../gradlew :paper:runServer -PrunPaperDebugLog=1 <&3        # -PrunPaperVersion=1.21.1 for another version

# 2. The test, from another shell.
npm install
node e2e.mjs --run-dir ../run/1.21.11 --port <server-port> --version 1.21.11 --console /tmp/paper-in
```

It edits `plugins/WebGUI/server.json` (`requireClientMod` and `deathScreenUrl`, restored at the end)
and runs `/webgui reload`; it also turns mob spawning off and kills every non-player entity near
a bot. Use a throwaway server directory.

The new bots get a fresh name on every run: a player who died, or was left standing somewhere odd,
keeps that in the world's playerdata, and a bot that rejoins under the same name inherits it.
