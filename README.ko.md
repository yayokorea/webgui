# WebGUI

[English](README.md)

> **이 포크의 Paper 지원** — 이 포크는 [mc-webgui/webgui](https://github.com/mc-webgui/webgui)를 포크한 것으로, Paper 1.21.1–26.3용 [Paper 서버 플러그인](#installation-paper-server)을 추가합니다. 플레이어는 여전히 WebGUI Fabric 클라이언트 모드가 필요하며, NeoForge 클라이언트는 Paper에서 테스트되지 않았습니다.
>
> 이 포크의 클라이언트 모드 JAR와 Paper 플러그인은 [Build 워크플로 아티팩트](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain)에서 내려받을 수 있습니다(GitHub 계정 필요). 업스트림의 [Modrinth 다운로드](https://modrinth.com/project/webgui)에는 이 포크의 Paper 변경 사항이 포함되어 있지 않습니다.

**어떤 웹 페이지든 Minecraft 안에서 전체 화면 GUI 또는 투명 HUD 오버레이로 임베드하세요.**

WebGUI는 Fabric 및 NeoForge 모드(서버용 [Paper 플러그인](#installation-paper-server) 포함)로, 내장 java-cef 연동을 통해 실제 Chromium 브라우저를 게임 클라이언트에 직접 임베드합니다. 서버 운영자는 React, Vue 또는 일반 HTML 앱을 플레이어에게 보여줄 수 있습니다. 접속 시 자동으로 열리는 HUD 오버레이로도, 단축키로 여는 커스텀 메인 메뉴로도 사용할 수 있습니다.

[![Build](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml)
[![Modrinth](https://img.shields.io/modrinth/dt/webgui?logo=modrinth&label=Modrinth&color=1bd96a)](https://modrinth.com/project/webgui)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

<a id="features"></a>
## 기능

- **전체 화면 GUI** — 어떤 URL이든 Esc로 닫는 화면으로 열기
- **HUD 오버레이** — 게임 위에 표시되는 투명 오버레이이며, `` ` ``로 상호작용 모드 전환
- **접속 시 자동 HUD** — 플레이어가 접속하면 HUD 오버레이를 자동으로 표시
- **커스텀 메인 메뉴** — 플레이어가 `F6`을 눌러 여러분의 웹 페이지를 열기
- **실시간 클라이언트 데이터** — 모드가 `window.webgui.client`(위치, 시선 방향, 체력, 허기, 경험치 레벨, 게임 모드, 차원, 서버 정보)를 20 TPS로 페이지에 전달
- **서명된 토큰** — 모드가 여는 모든 URL에 HMAC으로 서명된 토큰이 포함되어 백엔드가 플레이어의 신원을 검증할 수 있음
- **모드 API** — 다른 모드(Fabric 또는 NeoForge)가 `WebviewApi`를 통해 프로그램적으로 WebGUI 오버레이를 열 수 있음

---

<a id="compatibility"></a>
## 호환성

| Minecraft | 로더 | 상태 |
|-----------|--------|--------|
| 26.3 | Fabric & NeoForge | 🧪 베타 |
| 26.2 | Fabric & NeoForge | ✅ 활성 |
| 26.1.2 | NeoForge | ✅ 활성 |
| 1.21.5 – 1.21.11 | Fabric | ✅ 활성 |
| 1.21.5 – 1.21.11 | NeoForge | ✅ 활성 |
| 1.21 – 1.21.1 | Fabric | ✅ 지원 |
| 1.21 – 1.21.1 | NeoForge | ✅ 지원 |
| 1.20.1 | Fabric | ✅ 지원 |
| 1.21.1 – 26.3 | Paper (서버 플러그인) | ✅ 지원 — Fabric 모드를 쓰는 플레이어 대상 |

Minecraft 26.2와 26.3 빌드는 두 로더를 모두 지원하며, 26.1.2는 NeoForge 전용입니다.

Paper 플러그인은 **서버** 플러그인이므로 자체적으로 아무것도 렌더링하지 않습니다. 플레이어의 게임에는 여전히 모드가 필요하며, Paper 서버에서는 **Fabric** 모드를 뜻합니다(NeoForge 클라이언트는 테스트되지 않았습니다).

Chromium(약 150 MB)은 첫 실행 시 자동으로 다운로드됩니다. 별도의 브라우저 모드는 필요하지 않습니다.

---

<a id="installation"></a>
## 설치

1. 이 포크의 클라이언트 모드 JAR를 [Build 워크플로 아티팩트](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain)에서 내려받으세요(GitHub 계정 필요). 알맞은 Minecraft 버전과 로더를 선택해야 합니다. 업스트림 빌드는 [Modrinth](https://modrinth.com/project/webgui)를 이용하세요. 해당 빌드에는 이 포크의 Paper 변경 사항이 포함되어 있지 않습니다.
2. `.minecraft/mods/`에 넣으세요.
   - **Fabric:** Fabric API도 함께 추가하세요. 브라우저 라이브러리는 WebGUI jar 안에 포함되어 있습니다.
   - **NeoForge:** 별도의 브라우저 모드는 필요하지 않습니다.
3. 게임을 시작하세요.

---

<a id="quick-setup-server"></a>
## 빠른 설정 (서버)

`config/webgui/server.json`을 만들거나 편집하세요:

```json
{
  "autoHudOnJoin": true,
  "autoHudUrl": "https://your-hud.example.com",
  "mainMenuUrl": "https://your-menu.example.com"
}
```

서버를 재시작하고 접속해 테스트하세요.

---

<a id="server-commands"></a>
## 서버 명령어

```
/webgui gui <targets> <url>   — open a full-screen GUI for players
/webgui hud <targets> <url>   — open a HUD overlay for players
```

- `/webgui gui <targets> <url>` — 플레이어에게 전체 화면 GUI 열기
- `/webgui hud <targets> <url>` — 플레이어에게 HUD 오버레이 열기

오퍼레이터 레벨 2가 필요합니다. `fabric-permissions-api`(LuckPerms 등)를 지원합니다.

---

<a id="installation-paper-server"></a>
## 설치 (Paper 서버)

Paper 플러그인을 사용하면 Paper(또는 Purpur) 서버에서도 Fabric 또는 NeoForge 서버가 모드로 하는 일을 할 수 있습니다. 플레이어에게 페이지 열기, 접속 시 HUD 표시, 서버 자체 페이지 호스팅, 엔티티에 페이지 연결, 사망 화면 교체가 가능합니다. jar 하나로 Paper 1.21.1부터 26.3까지 모두 지원합니다.

1. 성공한 [Build 실행](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain)에서 `build-artifacts-paper`를 내려받아 `webgui-paper-<version>.jar`를 추출하거나, 직접 빌드하세요(`./gradlew :paper:build`, jar는 `paper/build/libs/`에 생성됩니다). Actions 아티팩트를 내려받으려면 GitHub 계정이 필요합니다.
2. 서버의 `plugins/` 폴더에 넣고 서버를 시작하세요.
3. **플레이어는 Fabric 클라이언트에 WebGUI 모드가 필요합니다**(위의 [설치](#installation) 참고). 서버가 모드를 플레이어에게 보내주지는 않습니다.
4. 설정은 첫 시작 시 생성되는 `plugins/WebGUI/server.json`에 있습니다. 모드의 `config/webgui/server.json`과 형식이 같으며([빠른 설정](#quick-setup-server) 참고), `/webgui reload`로 다시 읽습니다. 서버가 호스팅하는 페이지는 `plugins/WebGUI/web/`에 넣으세요.

Paper 릴리스가 게시되기 전까지 Paper 자동 릴리스 확인은 비활성화되어 있습니다. 업데이트된 아티팩트는 [Build 워크플로](https://github.com/yayokorea/webgui-paper/actions/workflows/build.yml?query=branch%3Amain)에서 확인하세요. `updateNotifyInChat`은 Paper에서 아무 효과가 없습니다.

<a id="commands-and-permissions"></a>
### 명령어와 권한

```
/webgui gui <targets> <url>                              — open a full-screen GUI for players
/webgui hud <targets> <url>                              — open a HUD overlay for players
/webgui bind entity <entity> <url> [cancel_interaction]  — open a page when players right-click the entity
/webgui unbind entity <entity>                           — remove that binding
/webgui reload                                           — reload server.json and the entity bindings
```

- `/webgui gui <targets> <url>` — 플레이어에게 전체 화면 GUI 열기
- `/webgui hud <targets> <url>` — 플레이어에게 HUD 오버레이 열기
- `/webgui bind entity <entity> <url> [cancel_interaction]` — 플레이어가 해당 엔티티를 우클릭하면 페이지 열기
- `/webgui unbind entity <entity>` — 해당 연결 제거
- `/webgui reload` — server.json과 엔티티 연결을 다시 로드

| 권한 | 허용 내용 | 기본값 |
|------------|--------|---------|
| `webgui.command` | 아래의 모든 하위 명령어 | op |
| `webgui.command.gui` | `/webgui gui` | op |
| `webgui.command.hud` | `/webgui hud` | op |
| `webgui.command.bind` | `/webgui bind` | op |
| `webgui.command.unbind` | `/webgui unbind` | op |
| `webgui.command.reload` | `/webgui reload` | op |

이 권한 노드가 유일한 검사입니다. `/webgui gui`를 실행할 수 있는 사람은 바닐라의 선택자 권한이 있든 없든 `@a`도 대상으로 지정할 수 있습니다.

<a id="limitations"></a>
### 제한 사항

- **Fabric 클라이언트 전용.** NeoForge 클라이언트는 Paper에서 테스트되지 않았고 지원되지 않습니다.
- **플레이어는 접속 후 약 5초 뒤에 인식됩니다.** 클라이언트는 월드에 들어간 뒤에야 WebGUI 채널을 알리며, 플러그인은 이를 최대 5초 기다립니다. 그때까지는 바닐라 클라이언트와 느린 클라이언트를 구별할 수 없으므로, "WebGUI가 없습니다" 안내와 `requireClientMod` 킥은 이 대기 이후에 이루어집니다.
- **페이지는 `PlayerJoinEvent`가 아니라 `WebGUIClientReadyEvent` 이후에 보내야 합니다.** 클라이언트가 아직 알리지 않은 채널로 보낸 내용은 Bukkit이 조용히 버립니다.
- **너무 큰 페이지 이벤트는 플레이어의 연결을 끊습니다.** Paper는 바닐라와 마찬가지로 클라이언트→서버 플러그인 메시지를 32767바이트로 제한합니다. 모드는 페이지의 `postToGame`이 최대 32768자까지 전달하도록 허용하므로, 매우 큰 이벤트에서만 이 문제가 발생합니다. 또한 Paper는 클라이언트가 최대 128개의 채널만 등록하도록 허용합니다.
- **플레이어가 접속 중일 때 플러그인을 로드하면**(plugman, `/reload`) 재접속하기 전까지 이벤트를 보내거나 에셋을 요청하지 못할 수 있습니다. 대신 서버를 재시작하세요.

<a id="for-plugin-developers"></a>
### 플러그인 개발자용

`plugin.yml`에 `depend: [WebGUI]`(또는 `softdepend`)를 추가하고 플러그인 jar에 대해 컴파일하세요. 아직 Maven 아티팩트는 없습니다. API는 `land.webgui.paper.api.WebGUIPaperApi`이며 `openGui`, `openHud`, `sendMainMenuUrl`, `emitToPage`, `bindEntity` / `unbindEntity`, `isReady`, `clientKind`를 제공합니다. 메인 스레드에서 호출하세요.

나머지는 두 가지 이벤트가 담당합니다:

- **`WebGUIClientReadyEvent`** — 플레이어의 클라이언트가 최신 WebGUI이고 서버 설정을 전달받았을 때 접속당 한 번 발생합니다. **이 시점이 그 플레이어에게 보낸 페이지가 처음으로 도착하는 순간입니다.** 그보다 먼저(예: `PlayerJoinEvent`에서) 보낸 것은 흔적 없이 버려집니다. 자동 접속 HUD 이후에 발생하므로, 여기서 연 페이지가 그 HUD를 대체합니다.
- **`WebGUIPageEvent`** — 페이지가 `window.webgui.postToGame({ channel, ... })`를 호출하면 발생합니다. 페이로드는 플레이어의 컴퓨터에서 온 JSON이므로 방어적으로 파싱하세요.

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

<a id="react-library"></a>
## React 라이브러리

타입 안전한 훅으로 SPA를 만드세요:

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

→ [npm: @webgui/react](https://www.npmjs.com/package/@webgui/react) · [문서](https://webgui.space)

---

<a id="building-from-source"></a>
## 소스에서 빌드하기

Gradle은 JDK 25로 실행하고, JDK 21과 JDK 25를 모두 사용할 수 있도록 준비하세요([CONTRIBUTING.md](CONTRIBUTING.md) 참고).

```bash
git clone https://github.com/yayokorea/webgui-paper.git
cd webgui-paper
./gradlew build
```

지원되는 모든 Minecraft 버전의 JAR를 `versions/*/build/libs/`에, Paper 플러그인을 `paper/build/libs/`에 출력합니다.

---

<a id="documentation"></a>
## 문서

전체 문서는 **[webgui.space](https://webgui.space)**에서 확인하세요.

---

<a id="contributing"></a>
## 기여하기

[CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요. 버그 수정과 번역은 언제나 환영합니다.

---

<a id="license"></a>
## 라이선스

[MIT](LICENSE) © KoSHeroff
