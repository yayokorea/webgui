# WebGUI

[한국어](README.ko.md)

## Changes in this fork

- **Paper support** — Adds a [Paper server plugin](#installation-paper-server) for Paper 1.21.1–26.3. Players still need the WebGUI Fabric client mod; NeoForge clients are untested on Paper.
- **Minecraft GUI scaling** — Web GUIs and HUDs follow Minecraft’s GUI Scale and automatic scale changes when resizing the window.

This fork is based on [mc-webgui/webgui](https://github.com/mc-webgui/webgui). Download its client mod JARs and Paper plugin from the [Build workflow artifacts](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain) (GitHub account required). Upstream [Modrinth downloads](https://modrinth.com/project/webgui) do not include this fork’s changes.

---

**Embed any web page as a full-screen GUI or transparent HUD overlay inside Minecraft.**

WebGUI is a Fabric & NeoForge mod (with a companion [Paper plugin](#installation-paper-server) for servers) that embeds a real Chromium browser (through its built-in java-cef integration) directly in the game client. Server operators can display any React, Vue, or plain HTML app to their players — as a HUD overlay that auto-opens on join, or as a custom main menu accessible with a keybind.

[![Build](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml)
[![Modrinth](https://img.shields.io/modrinth/dt/webgui?logo=modrinth&label=Modrinth&color=1bd96a)](https://modrinth.com/project/webgui)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Features

- **Full-screen GUI** — open any URL as a screen that closes with Esc
- **HUD overlay** — transparent overlay on top of the game; toggle interactive mode with `` ` ``
- **Auto HUD on join** — automatically shows a HUD overlay when a player connects
- **Custom main menu** — players press `F6` to open your web page
- **Key combinations** — open a different page for each key combination you define (`Shift+F`, `Ctrl+G`, ...), with no limit on how many; players can rebind each one
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

1. Download this fork's client mod JAR from the [Build workflow artifacts](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain) (GitHub account required), picking the right Minecraft version and loader. For upstream builds, use [Modrinth](https://modrinth.com/project/webgui); those do not include this fork's Paper changes.
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

### Key combinations

`keyMenus` binds key combinations to pages. The list is yours to make as long as you like; it is sent to each player on join and on `/webgui reload`:

```json
"keyMenus": [
  { "id": "shop",   "keys": ["shift", "F"], "url": "https://example.com/shop" },
  { "id": "quests", "keys": ["ctrl", "G"],  "url": "https://example.com/quests" },
  { "id": "map",    "keys": ["alt", "1"],   "url": "webgui://map.html" }
]
```

- `id` — letters, digits, `_`, `.` and `-` (up to 64). It is what a player's own rebinding is filed under, so keep it stable.
- `keys` — the keys to hold together, in any order. A list of names, or one string: `"shift+F"`. Names are not case-sensitive: letters `a`–`z`, digits `0`–`9`, `f1`–`f24`, `shift`, `ctrl`, `alt` (either side; `lshift`, `rshift`, ... for one side), `space`, `tab`, `enter`, `escape`, `backspace`, `delete`, `insert`, `home`, `end`, `pageup`, `pagedown`, `up`, `down`, `left`, `right`, `grave`, `minus`, `equal`, `comma`, `period`, `slash`, `backslash`, `semicolon`, `apostrophe`, `leftbracket`, `rightbracket`, `capslock` and the keypad (`kp0`–`kp9`, `kpadd`, `kpmultiply`, `kpenter`). Up to 6 keys.
- `url` — the page to open, like `mainMenuUrl` (no player token is appended).

How a combination behaves:

- It fires **once**, when the last key of it goes down. Holding the keys does not repeat it, and keys held for other reasons (walking) do not stop it. If several combinations match one press — `F` and `Shift+F` — the one with the most keys wins.
- It is ignored while chat or any other screen is open.
- The press that completes a combination is **consumed**, so `Shift+F` does not also swap hands. The keys pressed before it are not: `Shift` still sneaks while held. Pick combinations around that, and expect conflicts with other mods that read the keyboard the same way.
- An entry with an unknown key, a repeated or invalid `id`, or no `url` is skipped with a line in the log; the rest still work.
- `F6` and `mainMenuUrl` keep working next to this. Do not give a combination the same keys as another mod's key binding unless you mean to take them.
- Players can change the keys of each entry (or unbind it) under **Mod settings → WebGUI → Key menus...** while connected to the server; the choice is kept in their `config/webgui/client.json` and survives the server changing its defaults.

Server and client must run the same WebGUI protocol: this feature raised it to **2**, so a client or server older than this change drops the other's pages and says so in chat on join. Update both together.

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

1. Download `build-artifacts-paper` from a successful [Build run](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain) and extract `webgui-paper-<version>.jar`, or build it yourself (`./gradlew :paper:build`, the jar is in `paper/build/libs/`). Downloading Actions artifacts requires a GitHub account.
2. Put it in the server's `plugins/` folder and start the server.
3. **Players need the WebGUI mod on the Fabric client** (see [Installation](#installation) above). The server does not send the mod to them.
4. Settings are in `plugins/WebGUI/server.json`, created on first start. It has the same format as the mod's `config/webgui/server.json` (see [Quick setup](#quick-setup-server)); `/webgui reload` re-reads it. Pages the server hosts go in `plugins/WebGUI/web/`.

Paper automatic release checks are disabled until Paper releases are published. Check the [Build workflow](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain) for updated artifacts; `updateNotifyInChat` has no effect on Paper.

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

Requires Java 17+ and internet access on the first build. Gradle automatically selects and downloads the required JDKs (see [CONTRIBUTING.md](CONTRIBUTING.md)).

```bash
git clone https://github.com/yayokorea/webgui-paper.git
cd webgui-paper
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
