# DSH Remote

> [中文](./README.md) | English

> The best remote control under ten million yuan.
>
> Come on, little d, do your thing — buzzzzz~
>
> Hope you enjoy it ^_^

A native Android client that remote-controls [DeepSeek Harness (DSH)](https://github.com/deepseek-ai/DeepSeek-Harness) from your phone. Not a WebView wrapper — it is a from-scratch **Kotlin + Jetpack Compose** app that talks to the DSH host running on your computer over **HTTPS + WebSocket**. Keep the conversation going, watch the trajectory, and approve plans, all from your pocket. With Tailscale, it works from anywhere.

---

## Table of Contents

- [Architecture](#architecture)
- [Features](#features)
- [Repository Layout](#repository-layout)
- [Quick Start](#quick-start)
- [1. Install the DSH Harness](#1-install-the-dsh-harness)
- [2. Install the Plugins](#2-install-the-plugins)
- [3. Start `dsh web`](#3-start-dsh-web)
- [4. Tailscale Networking](#4-tailscale-networking)
- [5. Caddy HTTPS Reverse Proxy](#5-caddy-https-reverse-proxy)
- [6. Install & Connect on the Phone](#6-install--connect-on-the-phone)
- [Building the App Yourself](#building-the-app-yourself)
- [How It Works](#how-it-works)
- [FAQ](#faq)
- [Security Notes](#security-notes)
- [License](#license)

---

## Architecture

```
  Android phone (DshRemote App)
        │  HTTPS + WebSocket (/api/events.mux)
        ▼
  Caddy reverse proxy  (https://<your-machine>.ts.net:8443 → localhost:3080)
        │
        ▼
  DSH host  (dsh web, 127.0.0.1:3080)
        │  ┌─ @deepseek-ai/dsh-base
        │  ├─ @deepseek-ai/dsh-web-app
        │  ├─ @liustack/modlens         (image / OCR engine)
        │  ├─ dsh-better-sidebar        (sidebar enhancement)
        │  └─ dsh-remote-control        (this repo: /remote/* API for the phone)
        ▼
  LLM (DeepSeek, etc.)
```

- The phone only renders and interacts. All sessions, tools, and files live on the DSH host on your computer.
- `dsh-remote-control` is the **server-side plugin shipped in this repo**; it exposes the `/remote/*` routes (list dir, upload/download files, delete, rename, copy) used by the app.

---

## Features

### Session management

- Session list with live running state (a pulsing blue dot marks running sessions).
- New / archive / rename / search sessions.
- Subagent tree: see the children of each parent session and open them separately.
- "Just finished" section pins sessions completed in the last 5 minutes, with an unread dot.

### Chat

- Streaming output for both the assistant text and its reasoning.
- Markdown rendering: headings, bold, inline code (grey box), code blocks, links, multi-level lists, quotes, task lists.
- Live todo list from the model.
- Tool cards for terminal commands, file edits (diff), search, and web fetches.
- Deliverables: at the end of each turn, the files created/modified are listed.
- Fork & copy on any message.

### Composer

- Model / provider selection with reasoning effort.
- Permission preset (read-only / workspace-write / danger-full-access).
- Plan mode (`/plan`).
- Command menu (`/plan`, `/goal`, `/compact`, `/permission`) — sent through the real host command channel, not as plain text.
- Image attachments (needs the modlens plugin).

### Decision cards (inline)

Plan review, tool approval, and AI questions render as **inline cards at the bottom of the conversation** — never as a modal covering the chat:

- Plan review: `Approve` / `Keep planning` (accent bar + icon, scrollable long plans).
- Tool approval: `Allow once` / `Reject`.
- AI question: single / multi select + custom answer.

### Trajectory panel

A three-lane (input / model / tools) timeline showing per-turn steps, duration, tool calls, and token stats (including cache hits).

### Files

Browse the computer's workspace directory, upload / download / delete / rename / copy files via the `dsh-remote-control` plugin.

### Notifications & background

A foreground service keeps the app alive in the background and posts system notifications for: task done, task error, AI question, and approval requests. Tapping a notification deep-links to the right session.

### Theme & other

- Dark / light / follow-system.
- DeepSeek balance check (optional, with an API key).
- Server address is editable in settings; it reconnects automatically.

### Language

- **中文 / English** switchable in Settings.

---

## Repository Layout

```
dsh-remote/
├── DshRemote/            Android client source (Kotlin + Jetpack Compose)
│   └── app/src/main/java/dev/dsh/remote/
│       ├── data/         models, DSH API, settings store
│       ├── net/          RPC client, WebSocket client
│       ├── ui/           screens (home/chat/trajectory/files/settings…)
│       ├── service/      foreground service (background notifications)
│       └── MainActivity.kt
├── remote-control/       dsh-remote-control server plugin
│   ├── lib/host.js       registers HTTP + SSE routes (/remote/*)
│   ├── cordis.patch.yml  mounts the plugin into the profile
│   └── package.json      bundle declaration
├── web-profile/          DSH web profile deployment template (package.json)
│   (APKs are distributed via GitHub Releases, not committed to git)
├── convert-icons.js      SVG → Android VectorDrawable icon converter
├── render-launcher-whale.js   launcher icon renderer
├── launcher-whale-wifi.svg    launcher icon source
├── keystore.properties.example signing config template
└── README.md / README.en.md
```

---

## Quick Start

On the computer (Windows example):

```bash
# 1) install the DSH harness
mkdir dsh-app && cd dsh-app
npm install @deepseek-ai/dsh

# 2) install plugins (into the web profile)
npx dsh plugin --profile web add dsh-better-sidebar @liustack/modlens
npx dsh plugin --profile web add "file:C:/path/to/dsh-remote/remote-control"

# 3) start
npx dsh web
```

On the phone: download the latest `DshRemote-1.3.1.apk` from the Releases page → set the server address in Settings → connect.

> For remote access over HTTPS, also do [4. Tailscale](#4-tailscale-networking) and [5. Caddy](#5-caddy-https-reverse-proxy).

---

## 1. Install the DSH Harness

### Prerequisites

- **Node.js** ≥ 20 ([nodejs.org](https://nodejs.org/)).
- npm registry access (in China you may set a mirror: `npm config set registry https://registry.npmmirror.com`).

### Install

```bash
mkdir -p ~/dsh-app && cd ~/dsh-app
npm init -y
npm install @deepseek-ai/dsh
```

The `dsh` binary is under `node_modules/.bin/`:

```bash
npx dsh --version
# 0.1.0-rc.6
```

DSH data (sessions, settings, profiles) lives in `~/.dsh` (`C:\Users\<you>\.dsh` on Windows).

---

## 2. Install the Plugins

The web profile is a stack of plugin bundles. This project uses:

| bundle | role |
|---|---|
| `@deepseek-ai/dsh-base` | DSH core |
| `@deepseek-ai/dsh-web-app` | web UI (also hosts `/api` RPC + WebSocket) |
| `@liustack/modlens` | image / OCR |
| `dsh-better-sidebar` | sidebar enhancement |
| `dsh-remote-control` | **this repo**: the `/remote/*` file API for the phone |

### Option A: official command (recommended)

```bash
npx dsh plugin --profile web add dsh-better-sidebar @liustack/modlens
# link this repo's plugin by its absolute path
npx dsh plugin --profile web add "file:C:/path/to/dsh-remote/remote-control"
```

### Option B: manual package.json

Copy `web-profile/package.json` to `~/.dsh/profiles/web/`, set the `dsh-remote-control` `file:` path to your local absolute path, then:

```bash
cd ~/.dsh/profiles/web
npm install
```

Check the composed bundle stack with `npx dsh --dump-config`.

---

## 3. Start `dsh web`

```bash
cd ~/dsh-app
npx dsh web
```

Serves on `127.0.0.1:3080` by default; open `http://127.0.0.1:3080`.

```bash
npx dsh web --port 8080                                   # other port
npx dsh web --trusted-host "desktop-xxxx.tailxxxx.ts.net:8443"  # allow the Tailscale hostname
```

---

## 4. Tailscale Networking

1. Install Tailscale on the computer: [https://tailscale.com/download](https://tailscale.com/download).
2. Install it on the phone too.
3. Sign in with the same account on both.
4. Note your computer's MagicDNS name: `desktop-xxxx.tailxxxx.ts.net`.

> Tailscale's official docs already cover installation and login — it's referenced here, not repeated. Once the two devices can ping each other, networking is done.

---

## 5. Caddy HTTPS Reverse Proxy

The app talks HTTPS, so add a Caddy reverse proxy with a self-signed cert (the app trusts it):

```caddyfile
{
  servers {
    protocols h1 h2
  }
}

https://desktop-xxxx.tailxxxx.ts.net:8443 {
  tls internal
  reverse_proxy localhost:3080
}
```

Start it with `caddy run --config Caddyfile`, then verify the phone browser can open `https://desktop-xxxx.tailxxxx.ts.net:8443`.

---

## 6. Install & Connect on the Phone

1. Install the APK from the Releases page (Android 8.0+), or build it yourself.
2. Open the app → Settings → set the server address to:

```
https://desktop-xxxx.tailxxxx.ts.net:8443
```

3. Go back — it connects automatically. Then you can browse sessions, chat, watch the trajectory, approve plans/approvals/questions inline, browse/upload/download files, and receive background notifications.

---

## Building the App Yourself

| dependency | version |
|---|---|
| JDK | 21 |
| Gradle | 8.11.1 |
| Android SDK | compileSdk 35 |

Open `DshRemote/` in Android Studio, or build from the command line:

```bash
cd DshRemote
keytool -genkey -v -keystore keystore/my-release.keystore \
  -alias myalias -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties
# edit keystore.properties with your keystore path / alias / passwords
gradle assembleRelease
```

Output: `DshRemote/app/build/outputs/apk/release/`.

> `keystore.properties` is git-ignored — your signing key and passwords never enter the repo.

---

## How It Works

### Protocol

- **RPC**: `POST /api/<method>`, envelope `{"type":"client-request","rpcId":"<uuid>","method":"...","payload":{...}}`, response `{"type":"server-response","rpcId":...,"result":{"ok":true,"value":...}}`.
- **Realtime events**: WebSocket `/api/events.mux` — `session/event`, `session/queue`, `session/jobs`, `session/projection`, `question/requested` (AI question / plan review), `approval/requested` (tool approval), and more.
- **Commands**: `POST /api/commands/execute` with `{"args":{"agentId":"<sessionId>","line":"/plan"}}` to run host commands such as `/plan`, `/goal`, `/compact`, `/permission`.

### App modules

| module | role |
|---|---|
| `net/RpcClient.kt` | HTTP RPC envelope |
| `net/WsClient.kt` | WebSocket mux event stream |
| `data/DshApi.kt` | wraps every DSH method (session/workspace/agentPreset/goal/commands…) |
| `data/Models.kt` | models + `foldChat` (event stream → chat items) |
| `ui/AppViewModel.kt` | connection, session switching, event handling, decision replies |
| `ui/MainScreen.kt` | home / session views |
| `ui/ChatScreen.kt` | chat flow + composer + inline decision cards |
| `ui/TrajectoryScreen.kt` | three-lane trajectory panel |
| `ui/Markdown.kt` | Markdown rendering |
| `ui/Strings.kt` | in-app i18n (中文 / English) |
| `ui/theme/` | dark/light theme |
| `service/DshForegroundService.kt` | foreground service + background notifications |

### Server plugin (remote-control)

`lib/host.js` registers routes on the web profile's `webServer`:

- `GET /remote/list` — list a directory
- `GET /remote/file` — download a file
- `POST /remote/upload` — upload a file (base64)
- `POST /remote/delete` / `POST /remote/rename` / `POST /remote/copy` — file operations

---

## FAQ

**Q: It keeps spinning / won't connect?**
Check: ① `dsh web` is running; ② phone and computer can reach each other over Tailscale; ③ Caddy is running; ④ the address is `https://...:8443` (with `https`).

**Q: `/plan` doesn't enter plan mode?**
`/plan` is a host command — it must go through the command channel (the app's "Commands" menu, or a message starting with `/`), not as plain text.

**Q: Can I see images / OCR?**
Only with the `@liustack/modlens` plugin installed.

**Q: Can I change the server address?**
Yes, in Settings; it reconnects automatically.

---

## Security Notes

### For users

- **Fully local, very safe**: all sessions, files, and keys live only on **your own computer's** DSH host. The app connects back to your computer over a Tailscale private network — **your data never leaves your devices**, no third-party cloud, no telemetry.
- **Download/install warnings are normal**: the APK is signed with a personal self-signed certificate (no paid code-signing cert yet), so Chrome/Android may warn "unknown source / may be unsafe". Tap "Install anyway" / "More info → Install anyway" — it's **not a virus**, it's just an unsigned-by-a-CA app.
- The app connects to your own `https://<your-machine>.ts.net:8443`; the "untrusted certificate" prompt is expected for the same reason.

### For developers / deployers

- **Never commit** `keystore.properties`, `*.keystore`, `*.jks` (signing private key).
- **Never commit** `.credentials.yaml`, `remote-control.token`, `settings.yaml`, or `sessions/` under `~/.dsh`.
- Delete the PAT immediately after using it.
- Self-signed cert + TrustAll is for personal LAN convenience; for public deployment, use a real certificate and remove TrustAll.

---

## License

**GNU General Public License v3.0 (GPL-3.0)** — full text in `LICENSE`.

- Free to use, modify, and distribute.
- **Redistribution (including modifications) must keep the copyright notice and license text and include the complete source code**.
- Derivatives must also be open-sourced under GPL-3.0.
- No warranty, as described in the license.
