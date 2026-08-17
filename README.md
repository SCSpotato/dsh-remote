# DSH Remote

> [中文](./README.md) | [English](./README.en.md)

> 一千万以内，最好的遥控器。

来小d给我整个活，哇~哇~哇~哇~哇。

希望你用的开心^_^

---

## 目录

- [整体架构](#整体架构)
- [功能特性](#功能特性)
- [仓库结构](#仓库结构)
- [快速开始（TL;DR）](#快速开始tldr)
- [第一步：安装 DSH Harness](#第一步安装-dsh-harness)
- [第二步：安装插件](#第二步安装插件)
- [第三步：启动 dsh web](#第三步启动-dsh-web)
- [第四步：Tailscale 组网](#第四步tailscale-组网)
- [第五步：Caddy HTTPS 反向代理](#第五步caddy-https-反向代理)
- [第六步：手机安装并连接](#第六步手机安装并连接)
- [如何自己构建 App](#如何自己构建-app)
- [实现原理](#实现原理)
- [常见问题（FAQ）](#常见问题faq)
- [安全须知](#安全须知)

---

## 整体架构

```
  Android 手机 (DshRemote App)
        │  HTTPS + WebSocket（/api/events.mux）
        ▼
  Caddy 反代  (https://<你的机器>.ts.net:8443 → localhost:3080)
        │
        ▼
  DSH 宿主  (dsh web, 127.0.0.1:3080)
        │  ┌─ @deepseek-ai/dsh-base
        │  ├─ @deepseek-ai/dsh-web-app
        │  ├─ @liustack/modlens         （图像/OCR 引擎）
        │  ├─ dsh-better-sidebar        （侧边栏增强）
        │  └─ dsh-remote-control        （本仓库：给手机提供的 /remote/* 接口）
        ▼
  LLM（DeepSeek 等）
```

- 手机 App 只做「展示 + 交互」，所有会话、工具、文件都在电脑上的 DSH 宿主里。
- `dsh-remote-control` 是**本仓库自带的服务端插件**，负责暴露 `/remote/*` 路由（列目录、上传下载文件、删除重命名等）给手机 App。

---

## 功能特性

### 会话管理

- **会话列表**：展示所有会话 + 实时运行状态（运行中的会话显示蓝色呼吸圆点）。
- **新建 / 归档 / 重命名 / 搜索**：随手新建会话，归档不再需要的，按标题搜索定位。
- **子代理会话树**：父会话下的子代理一目了然，点进去单独查看子代理的对话。
- **「刚完成」置顶**：最近 5 分钟内跑完的会话置顶 + 未读红点，方便回来接着看。

### 对话体验

- **流式输出**：助手正文与思考过程（reasoning）实时逐字显示。
- **Markdown 渲染**：标题、粗体、行内代码（灰底）、代码块、链接、多级列表、引用、勾选清单。
- **Todo 清单**：模型的任务列表实时展示进度。
- **工具调用卡**：终端命令、文件编辑（diff）、搜索、网页抓取等工具，都折叠成可展开的卡片。
- **产物列表**：每个回合结束，自动列出本回合新建/修改的文件路径。
- **分支（Fork）与复制**：对任意一条消息一键 fork 出子会话，或复制其文本。

### 输入区

- **模型选择**：切换 provider / model，并可设推理强度。
- **权限预设**：read-only / workspace-write / danger-full-access 一键切换。
- **计划模式**：`/plan` 进入计划模式，让模型先出方案、审过再动手。
- **命令菜单**：内置 `/plan`、`/goal`、`/compact`、`/permission` 等命令（走真实宿生命令通道，不是当文字发）。
- **图片附件**：拍照/相册发图给模型（需 modlens 插件）。

### 决策交互（内联卡片）

计划审批、工具批准、AI 提问这三类决策，都以**卡片形式内联在对话流底部**，不会弹窗盖住聊天内容：

- **计划审批**：`确认执行` / `继续规划`（带左侧色条 + 图标，长计划可滚动）。
- **工具批准**：`允许一次` / `拒绝`。
- **AI 提问**：单选 / 多选 + 自定义回答。

### 轨迹面板

三泳道（输入 / 模型 / 工具）时间线，展示每个回合的：轮次、步骤、耗时、工具调用、token 统计（含缓存命中）。

### 文件管理

通过 `dsh-remote-control` 服务端插件，在手机上浏览电脑工作区目录，上传 / 下载 / 删除 / 重命名 / 复制文件。

### 通知与后台

- 前台服务常驻，App 退到后台也能收到：任务完成、任务出错、AI 提问、需要批准等系统通知。
- 点通知直接跳转到对应会话。

### 主题与其它

- 深色 / 浅色 / 跟随系统三档主题。
- DeepSeek 余额查询（可选，填 API Key 后显示）。
- 服务器地址可在设置里随时修改，改完自动重连。

---

## 仓库结构

```
dsh-remote/
├── DshRemote/            Android 客户端源码（Kotlin + Jetpack Compose）
│   └── app/src/main/java/dev/dsh/remote/
│       ├── data/         数据模型、DSH API、设置存储
│       ├── net/          RPC 客户端、WebSocket 客户端
│       ├── ui/           各界面（主页/会话/轨迹/文件/设置…）
│       ├── service/      前台服务（后台收通知）
│       └── MainActivity.kt
├── remote-control/       dsh-remote-control 服务端插件
│   ├── lib/host.js       注册 HTTP + SSE 路由（/remote/*）
│   ├── cordis.patch.yml  把插件挂进 profile 的 patch
│   └── package.json      bundle 声明
├── web-profile/          DSH web profile 部署模板（package.json）
│   （APK 通过 GitHub Releases 分发，不随源码进 git）
├── convert-icons.js      SVG → Android VectorDrawable 图标转换
├── render-launcher-whale.js   启动图标渲染脚本
├── launcher-whale-wifi.svg    启动图标素材
├── keystore.properties.example 签名配置模板
└── README.md
```

---

## 快速开始（TL;DR）

电脑端（Windows 示例）：

```bash
# 1) 装 DSH harness
mkdir dsh-app && cd dsh-app
npm install @deepseek-ai/dsh

# 2) 装插件（进入 web profile）
npx dsh plugin --profile web add dsh-better-sidebar @liustack/modlens
npx dsh plugin --profile web add "file:C:/path/to/dsh-remote/remote-control"

# 3) 启动
npx dsh web
```

手机端：从 Releases 页面下载最新 `DshRemote-1.3.1.apk` → 设置里填服务器地址 → 连接。

> 想要外网访问 + HTTPS，再补 [第四步 Tailscale](#第四步tailscale-组网) 和 [第五步 Caddy](#第五步caddy-https-反向代理)。

---

## 第一步：安装 DSH Harness

### 前置条件

- **Node.js** ≥ 20（[nodejs.org](https://nodejs.org/) 下载 LTS 版即可）
- 能访问 npm 仓库（国内可配镜像：`npm config set registry https://registry.npmmirror.com`）

### 安装

DSH 是一个 npm 包，装到本地一个目录里即可：

```bash
mkdir -p ~/dsh-app && cd ~/dsh-app
npm init -y
npm install @deepseek-ai/dsh
```

装完后，`dsh` 命令在 `node_modules/.bin/` 下：

```bash
# 直接用 npx 调用（推荐，不用配 PATH）
npx dsh --version
# 输出：0.1.0-rc.6
```

> 也可以 `npm install -g @deepseek-ai/dsh` 全局安装，然后直接 `dsh --version`。

DSH 的数据（会话、设置、profile）默认放在 `~/.dsh`（Windows 是 `C:\Users\<你>\.dsh`）。

---

## 第二步：安装插件

DSH 的 profile 是一个「插件 bundle 栈」。本项目的 web profile 由下面 5 个 bundle 组成：

| bundle | 作用 |
|---|---|
| `@deepseek-ai/dsh-base` | DSH 核心 |
| `@deepseek-ai/dsh-web-app` | 网页 UI（也承载 `/api` RPC 与 WebSocket） |
| `@liustack/modlens` | 图像识别/OCR（手机端看图要用） |
| `dsh-better-sidebar` | 侧边栏增强 |
| `dsh-remote-control` | **本仓库**的插件：给手机提供 `/remote/*` 文件接口 |

### 方式 A：官方命令（推荐）

```bash
npx dsh plugin --profile web add dsh-better-sidebar @liustack/modlens
# 本仓库的 remote-control 用本地路径（把路径换成你 clone 下来的位置）
npx dsh plugin --profile web add "file:C:/path/to/dsh-remote/remote-control"
```

`dsh plugin` 会把剩余参数转发给 profile 目录里的 pnpm，等价于在该目录执行 `pnpm add`。

### 方式 B：手动写 package.json

把本仓库 `web-profile/package.json` 复制到 `~/.dsh/profiles/web/`，把 `dsh-remote-control` 的 `file:` 路径改成你本机的绝对路径，然后：

```bash
cd ~/.dsh/profiles/web
npm install
```

装完可以用 `npx dsh --dump-config` 检查 bundle 栈是否正确。

---

## 第三步：启动 dsh web

```bash
cd ~/dsh-app
npx dsh web
```

默认监听 `127.0.0.1:3080`。浏览器打开 `http://127.0.0.1:3080` 应该能看到 DSH 的网页界面。

常用参数：

```bash
# 换端口
npx dsh web --port 8080
# 允许外部（Tailscale）域名通过浏览器信任墙
npx dsh web --trusted-host "desktop-xxxx.tailxxxx.ts.net:8443"
```

> 手机 App 是通过 HTTPS + WebSocket 连接的，所以还需要下面两步把 3080 暴露出去。

---

## 第四步：Tailscale 组网

Tailscale 让你电脑和手机进同一个私有网络，不需要公网 IP、不用在路由器开端口。

1. 电脑装 Tailscale：[https://tailscale.com/download](https://tailscale.com/download)
2. 手机也装 Tailscale（App Store / Google Play）
3. 两边登录**同一个账号**，在 admin 面板确认设备都在线
4. 记下你电脑的 MagicDNS 名字，形如：`desktop-xxxx.tailxxxx.ts.net`

> Tailscale 的安装和登录步骤官方文档已经写得很清楚，这里只引用，不重复展开。只要电脑和手机能互相 ping 通就算组网成功。

---

## 第五步：Caddy HTTPS 反向代理

App 用 HTTPS 连你的 DSH，需要一个 HTTPS 端点。最简单的是用 Caddy 做反代 + 自签证书（App 端已做 TrustAll，接受自签证书）。

1. 下载 Caddy：[https://caddyserver.com/download](https://caddyserver.com/download)
2. 在 caddy 目录新建 `Caddyfile`：

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

把 `desktop-xxxx.tailxxxx.ts.net` 换成你电脑的 Tailscale 名字。

3. 启动：

```bash
caddy run --config Caddyfile
```

4. 手机浏览器打开 `https://desktop-xxxx.tailxxxx.ts.net:8443`，能打开 DSH 网页即成功。

> 注意：`dsh web` 启动时要带 `--trusted-host "desktop-xxxx.tailxxxx.ts.net:8443"`，否则浏览器的信任墙会拦掉非本机的 Host 头。

---

## 第六步：手机安装并连接

1. 安装 APK：从本仓库的 **Releases** 页面下载最新 `DshRemote-*.apk`（Android 8.0+），或按 [如何自己构建 App](#如何自己构建-app) 自己打包。
2. 打开 App → 右上角设置 → 服务器地址填：

```
https://desktop-xxxx.tailxxxx.ts.net:8443
```

3. 返回主页，会自动连接。连上后即可：
   - 查看会话列表、进入会话聊天
   - 看轨迹面板（轮次/步骤/耗时/工具调用/token）
   - 处理计划审批、工具批准、AI 提问（卡片内联在对话里）
   - 浏览/上传/下载电脑工作区文件
   - 后台收「任务完成 / 需要批准」通知

---

## 如何自己构建 App

如果你要改代码自己打 APK：

### 环境

| 依赖 | 版本 |
|---|---|
| JDK | 21 |
| Gradle | 8.11.1 |
| Android SDK | compileSdk 35 |

用 Android Studio 打开 `DshRemote/` 即可（会提示装 SDK）。命令行构建：

```bash
cd DshRemote
# 生成签名密钥（只需一次）
keytool -genkey -v -keystore keystore/my-release.keystore \
  -alias myalias -keyalg RSA -keysize 2048 -validity 10000
# 配置签名
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填上你的 keystore 路径/别名/密码
# 构建
gradle assembleRelease
```

产物在 `DshRemote/app/build/outputs/apk/release/`。

> 说明：`keystore.properties` 已被 `.gitignore` 忽略，签名的私钥和密码**不会**进仓库。示例里只是占位符。

---

## 实现原理

### 通信协议

- **RPC**：`POST /api/<method>`，信封 `{"type":"client-request","rpcId":"<uuid>","method":"...","payload":{...}}`，响应 `{"type":"server-response","rpcId":...,"result":{"ok":true,"value":...}}`。
- **实时事件**：WebSocket `/api/events.mux`，帧类型包括 `session/event`（会话事件流）、`session/queue`、`session/jobs`、`session/projection`、`question/requested`（AI 提问/计划审批）、`approval/requested`（工具批准）等。
- **命令**：`POST /api/commands/execute`，payload `{"args":{"agentId":"<sessionId>","line":"/plan"}}`，用于执行 `/plan`、`/goal`、`/compact`、`/permission` 等宿生命令（不是当普通消息发）。

### 关键模块（App 端）

| 模块 | 职责 |
|---|---|
| `net/RpcClient.kt` | HTTP RPC 信封编解码 |
| `net/WsClient.kt` | WebSocket mux 事件流 |
| `data/DshApi.kt` | 封装所有 DSH 方法（session/workspace/agentPreset/goal/commands…） |
| `data/Models.kt` | 数据模型 + `foldChat`（把事件流折叠成聊天项） |
| `ui/AppViewModel.kt` | 连接、会话切换、事件处理、决策应答 |
| `ui/MainScreen.kt` | 主界面/首页/会话视图 |
| `ui/ChatScreen.kt` | 聊天流 + 输入区 + 内联决策卡 |
| `ui/TrajectoryScreen.kt` | 三泳道轨迹面板 |
| `ui/Markdown.kt` | Markdown 渲染 |
| `ui/theme/` | 深浅色主题（跟随系统） |
| `service/DshForegroundService.kt` | 前台服务，后台收通知 |

### 服务端插件（remote-control）

`lib/host.js` 在 web profile 的 `webServer` 上注册路由：

- `GET /remote/list` — 列目录
- `GET /remote/file` — 下载文件
- `POST /remote/upload` — 上传文件（base64）
- `POST /remote/delete` / `POST /remote/rename` / `POST /remote/copy` — 文件操作

---

## 常见问题（FAQ）

**Q：连不上 / 转圈？**
先确认：① `dsh web` 在跑；② 手机和电脑 Tailscale 能互通；③ Caddy 在跑；④ 地址填的是 `https://...:8443`（带 https）。

**Q：计划模式 `/plan` 不生效？**
`/plan` 是宿生命令，必须走命令通道（App 的「命令」按钮、或输入框里以 `/` 开头发送），不能当普通文字消息发。详见 App 内「命令」菜单。

**Q：能看到图片 / OCR 吗？**
需要 `@liustack/modlens` 插件已装，App 端会自动走它。

**Q：能改服务器地址吗？**
能，设置页里随时改，改完自动重连。

---

## 安全须知

### 给使用者的话

- **完全本地部署，非常安全**：所有会话、文件、密钥都只存在**你自己电脑**的 DSH 宿主里。App 只是通过 Tailscale 私网连回你的电脑，**数据不出你的设备**，不上传任何第三方云、也不采集任何信息。
- **下载/安装时弹出警告是正常的**：因为 APK 目前用的是个人自签证书（还没花钱买正式代码签名证书），Chrome/安卓会提示「未知来源 / 此应用可能不安全」。点「仍要安装」「更多信息 → 仍要安装」继续即可，**不是病毒**，只是没花钱买证书而已。
- App 连的是你自己的 `https://<你的机器>.ts.net:8443`，自签证书所以会提示「证书不受信任」，这也在预期内。

### 给开发 / 部署者的话

- **不要提交** `keystore.properties`、`*.keystore`、`*.jks`（签名私钥）。
- **不要提交** `~/.dsh` 下的 `.credentials.yaml`、`remote-control.token`、`settings.yaml`、`sessions/`（含你的密钥和会话）。
- 用 PAT 上传后，**立刻到 GitHub 里删除该 token**。
- 自签证书 + TrustAll 是为了个人内网方便；如果部署到公网，建议换成正式证书并去掉 TrustAll。

---

## 许可证

本项目采用 **GNU General Public License v3.0 (GPL-3.0)**（全文见 `LICENSE`）。

要点：

- 你可以自由使用、修改、分发本项目。
- **引用或分发（含修改版）时，必须保留本项目的版权声明与许可证文本，并附上完整源代码**。
- 你的衍生作品也必须以 GPL-3.0 开源，不得闭源。
- 本软件不提供任何担保，详见许可证原文。
