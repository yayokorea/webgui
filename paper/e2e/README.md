# Paper plugin protocol test

Headless: bots speak the Minecraft protocol (via `minecraft-protocol`) and pretend to be a
WebGUI Fabric client — on the wire that is one `minecraft:register` listing the `webgui:*`
channels. It checks the handshake order, the config snapshot, asset requests, console commands,
page events (fire, malformed, flood), `requireClientMod`, and an outdated / vanilla client.

`minecraft-protocol` supports Minecraft up to 26.1, so this cannot drive Paper 26.2 or newer.
It runs against 1.21.1 and 1.21.11.

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

It edits `plugins/WebGUI/server.json` (`requireClientMod`, restored at the end) and runs
`/webgui reload`, so use a throwaway server directory.
