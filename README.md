# WebGUI

[한국어](README.ko.md)

> **Paper support in this fork** — This fork of [mc-webgui/webgui](https://github.com/mc-webgui/webgui) adds a [Paper server plugin](#installation-paper-server) for Paper 1.21.1–26.3. Players still need the WebGUI Fabric client mod; NeoForge clients are untested on Paper.
>
> Download this fork's client mod JARs and Paper plugin from the [Build workflow artifacts](https://github.com/yayokorea/webgui/actions/workflows/build.yml?query=branch%3Amain) (GitHub account required). Upstream [Modrinth downloads](https://modrinth.com/project/webgui) do not include this fork's Paper changes.

**Embed any web page as a full-screen GUI or transparent HUD overlay inside Minecraft.**

WebGUI is a Fabric & NeoForge mod (with a companion [Paper plugin](#installation-paper-server) for servers) that embeds a real Chromium browser (through its built-in java-cef integration) directly in the game client. Server operators can display any React, Vue, or plain HTML app to their players — as a HUD overlay that auto-opens on join, or as a custom main menu accessible with a keybind.

[![Build](https://github.com/yayokorea/webgui/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/yayokorea/webgui/actions/workflows/build.yml)
[![Modrinth](https://img.shields.io/modrinth/dt/webgui?logo=modrinth&label=Modrinth&color=1bd96a)](https://modrinth.com/project/webgui)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Features

- **Full-screen GUI** — open any URL as a screen that closes with Esc
- **HUD overlay** — transparent overlay on top of the game; toggle interactive mode with `` ` ``
- **Auto HUD on join** — automatically shows a HUD overlay when a player connects
- **Custom main menu** — players press `F6` to open your web page
- **Live client data** — the mod pushes `window.webgui.client` (position, look direction, health, food, XP level, gamemode, dimension, server info) to the page at 20 TPS
- **Signed tokens** — every URL the mod opens carries an HMAC-signed token so your backend can verify the player's identity
- **Mod API** — other mods (Fabric or NeoForge) can open WebGUI overlays programmatically via `WebviewApi`

---

## Compatibility

| Minecraft | Loader | Status |
|-----------|--------|--------|
| 26.3 | Fabric & NeoForge | 🧪 Beta |
| 26.2 | Fabric & NeoForge | ✅ Active |
| 26.1.2 | NeoForge | ✅ Active |
| 1.21.5 – 1.21.11 | Fabric | ✅ Active |
| 1.21.5 – 1.21.11 | NeoForge | ✅ Active |
| 1.21 – 1.21.1 | Fabric | ✅ Supported |
| 1.21 – 1.21.1 | NeoForge | ✅ Supported |
| 1.20.1 | Fabric | ✅ Supported |
| 1.21.1 – 26.3 | Paper (server plugin) | ✅ Supported — for players on the Fabric mod |

Minecraft 26.2 and 26.3 builds support both loaders; 26.1.2 is NeoForge-only.

The Paper plugin is a **server** plugin: it does not render anything itself. The players' own game still needs the mod, and on a Paper server that means the **Fabric** one (NeoForge clients are untested there).

Chromium (~150 MB) is downloaded automatically on first launch. No separate browser mod is required.

---

## Installation

1. Download this fork's client mod JAR from the [Build workflow artifacts](https://github.com/yayokorea/webgui/actions/workflows/build.yml?query=branch%3Amain) (GitHub account required), picking the right Minecraft version and loader. For upstream builds, use [Modrinth](https://modrinth.com/project/webgui); those do not include this fork's Paper changes.
2. Place it in `.minecraft/mods/`.
   - **Fabric:** also add Fabric API. The browser library ships inside the WebGUI jar.
   - **NeoForge:** no separate browser mod is required.
3. Start the game.

---

## Quick setup (server)

Create or edit `config/webgui/server.json`:

```json
{
  "autoHudOnJoin": true,
  "autoHudUrl": "https://your-hud.example.com",
  "mainMenuUrl": "https://your-menu.example.com"
}
```

Restart the server and join to test.

---

## Server commands

```
/webgui gui <targets> <url>   — open a full-screen GUI for players
/webgui hud <targets> <url>   — open a HUD overlay for players
```

Requires operator level 2. Supports `fabric-permissions-api` (LuckPerms, etc.).

---

## Installation (Paper server)

The Paper plugin lets a Paper (or Purpur) server do what a Fabric or NeoForge server does with the mod: open pages for players, show a HUD on join, host the server's own pages, bind pages to entities and replace the death screen. One jar covers Paper 1.21.1 through 26.3.

1. Download `build-artifacts-paper` from a successful [Build run](https://github.com/yayokorea/webgui/actions/workflows/build.yml?query=branch%3Amain) and extract `webgui-paper-<version>.jar`, or build it yourself (`./gradlew :paper:build`, the jar is in `paper/build/libs/`). Downloading Actions artifacts requires a GitHub account.
2. Put it in the server's `plugins/` folder and start the server.
3. **Players need the WebGUI mod on the Fabric client** (see [Installation](#installation) above). The server does not send the mod to them.
4. Settings are in `plugins/WebGUI/server.json`, created on first start. It has the same format as the mod's `config/webgui/server.json` (see [Quick setup](#quick-setup-server)); `/webgui reload` re-reads it. Pages the server hosts go in `plugins/WebGUI/web/`.

Paper automatic release checks are disabled until Paper releases are published. Check the [Build workflow](https://github.com/yayokorea/webgui/actions/workflows/build.yml?query=branch%3Amain) for updated artifacts; `updateNotifyInChat` has no effect on Paper.

### Commands and permissions

```
/webgui gui <targets> <url>                              — open a full-screen GUI for players
/webgui hud <targets> <url>                              — open a HUD overlay for players
/webgui bind entity <entity> <url> [cancel_interaction]  — open a page when players right-click the entity
/webgui unbind entity <entity>                           — remove that binding
/webgui reload                                           — reload server.json and the entity bindings
```

| Permission | Allows | Default |
|------------|--------|---------|
| `webgui.command` | every subcommand below | op |
| `webgui.command.gui` | `/webgui gui` | op |
| `webgui.command.hud` | `/webgui hud` | op |
| `webgui.command.bind` | `/webgui bind` | op |
| `webgui.command.unbind` | `/webgui unbind` | op |
| `webgui.command.reload` | `/webgui reload` | op |

These permission nodes are the only check: whoever may run `/webgui gui` may also target `@a`, whether or not they have vanilla's selector permission.

### Limitations

- **Fabric clients only.** NeoForge clients are untested, and unsupported, on Paper.
- **A player is recognised about five seconds after joining.** The client announces its WebGUI channels only once it is in the world, and the plugin waits up to 5 s for that. Until then it cannot tell a vanilla client from a slow one, so the "you don't have WebGUI" notice and `requireClientMod` kicks come after that wait.
- **Pages must be sent after `WebGUIClientReadyEvent`**, not in `PlayerJoinEvent`: Bukkit silently drops anything sent on a channel the client has not announced yet.
- **Large page events disconnect the player.** Paper, like vanilla, caps a client-to-server plugin message at 32767 bytes. The mod lets a page's `postToGame` carry up to 32768 characters, so only a very large event hits this. Paper also lets a client register at most 128 channels.
- **Loading the plugin while players are online** (plugman, `/reload`) can leave them unable to send events or request assets until they reconnect. Restart the server instead.

### For plugin developers

Add `depend: [WebGUI]` (or `softdepend`) to your `plugin.yml` and compile against the plugin's jar; there is no Maven artifact yet. The API is `land.webgui.paper.api.WebGUIPaperApi`: `openGui`, `openHud`, `sendMainMenuUrl`, `emitToPage`, `bindEntity` / `unbindEntity`, `isReady` and `clientKind`. Call it from the main thread.

Two events carry the rest:

- **`WebGUIClientReadyEvent`** fires once per join, when the player's client is a current WebGUI and has been sent the server's settings. **This is the first moment a page sent to them arrives** — anything sent earlier, in `PlayerJoinEvent` for example, is dropped without a trace. It fires after the automatic join HUD, so a page opened here replaces that one.
- **`WebGUIPageEvent`** fires when a page calls `window.webgui.postToGame({ channel, ... })`. The payload is JSON from a player's machine: parse it defensively.

```java
public final class MyShop extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onReady(WebGUIClientReadyEvent event) {
        WebGUIPaperApi.openHud(event.getPlayer(), "https://shop.example.com/hud");
    }

    @EventHandler
    public void onPageEvent(WebGUIPageEvent event) {
        if (!event.getChannel().equals("buy")) return;
        Player player = event.getPlayer();
        JsonObject body = JsonParser.parseString(event.getJsonPayload()).getAsJsonObject();   // check what it asks for
        // ...charge the player, then answer the page:
        JsonObject reply = new JsonObject();
        reply.addProperty("item", body.get("item").getAsString());
        WebGUIPaperApi.emitToPage(player, "bought", reply.toString());
    }
}
```

---

## React library

Build your SPA with type-safe hooks:

```bash
npm install @webgui/react
```

```tsx
import { useWebGUIClient, isInMod, isReady } from '@webgui/react';

export function PlayerInfo() {
  const client = useWebGUIClient();
  if (!isInMod())       return <p>Open this inside Minecraft.</p>;
  if (!isReady(client)) return <p>Connecting…</p>;
  return <p>Hello, {client!.username}</p>;
}
```

→ [npm: @webgui/react](https://www.npmjs.com/package/@webgui/react) · [Docs](https://webgui.space)

---

## Building from source

Use JDK 25 to run Gradle and make both JDK 21 and JDK 25 available (see [CONTRIBUTING.md](CONTRIBUTING.md)).

```bash
git clone https://github.com/yayokorea/webgui.git
cd webgui
./gradlew build
```

Outputs JARs for all supported Minecraft versions to `versions/*/build/libs/`, and the Paper plugin to `paper/build/libs/`.

---

## Documentation

Full documentation at **[webgui.space](https://webgui.space)**.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Bug fixes and translations are always welcome.

---

## License

[MIT](LICENSE) © KoSHeroff
