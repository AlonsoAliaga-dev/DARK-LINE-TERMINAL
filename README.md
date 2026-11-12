# D.L.T — DarkLine Terminal

<p align="center">
  <img src="app/src/main/res/mipmap-xhdpi/ic_launcher.png" alt="D.L.T Logo" width="128"/>
</p>

<p align="center">
  <b>Modern Android Terminal Emulator</b><br>
  Termux-powered core • proot Linux distros • Advanced AI assistant
</p>

<p align="center">
  <a href="https://github.com/AlonsoAliaga-dev/DARK-LINE-TERMINAL/actions/workflows/build-apk.yml">
    <img src="https://github.com/AlonsoAliaga-dev/DARK-LINE-TERMINAL/actions/workflows/build-apk.yml/badge.svg" alt="Build Status"/>
  </a>
  <img src="https://img.shields.io/badge/License-MPL--2.0-blue.svg" alt="License: MPL-2.0"/>
  <img src="https://img.shields.io/badge/Android-8.0%2B-green.svg" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Java-17-orange.svg" alt="Java 17"/>
</p>

---

## Features

| Feature | Description |
|---------|-------------|
| **Full Terminal Emulator** | High-quality VT100/xterm emulation based on Termux `terminal-emulator` + `terminal-view` |
| **Multi-Session Support** | Create and switch between multiple independent terminal sessions |
| **Linux Distros (proot)** | Install & run real Linux rootfs (Ubuntu, Debian, Alpine…) **without root** |
| **AI Assistant** | Powerful side-panel chat with streaming responses |
| **Custom API Support** | Use any OpenAI-compatible endpoint (Groq by default + full Custom Base URL / API Key / Model) |
| **Advanced Markdown** | Rich rendering of AI messages with **Markwon** + Table plugin (code blocks, tables, lists…) |
| **Smart Notification Panel** | Persistent notification with quick actions: **New Session**, **Stop All**, **AI Chat** |
| **File Manager** | Browse, open, copy, delete and manage files inside the environment |
| **Code Editor** | Syntax-highlighted editor (Sora Editor + TextMate grammars) |
| **Extra Keys Bar** | On-screen ESC, CTRL, ALT, Tab, arrows and more |
| **Material You Design** | Modern light/dark themes |
| **Foreground Service** | Sessions stay alive in the background |
| **GitHub Actions CI** | Automatic debug APK builds on every push |

---

## AI Assistant Highlights

- **Default provider**: Groq (very fast)
- **Custom Endpoint mode**:  
  Set your own `Base URL` + `API Key` + `Model` (any OpenAI-compatible API)
- Streaming responses (SSE)
- Conversation history
- **Advanced Markdown rendering** on every AI message (tables, fenced code blocks, etc.)
- System prompt optimized for Linux / terminal help

Go to **Settings → AI Assistant** to configure.

---

## Notification Panel

When sessions are running, a persistent notification appears with three useful actions:

| Action | What it does |
|--------|--------------|
| **New Session** | Instantly create a new terminal session |
| **Stop All** | Terminate all running sessions |
| **AI Chat** | Jump directly into the AI panel |

---

## Tech Stack

- **Language**: Java 17
- **UI**: Material Design 3 + ViewBinding
- **Terminal Core**: Termux `terminal-emulator` & `terminal-view` (Apache-2.0)
- **Native**: prebuilt `libproot`, `libtermux` + loaders (arm64-v8a, armeabi-v7a, x86, x86_64)
- **AI / Network**: OkHttp + SSE streaming + OpenAI-compatible API
- **Markdown**: [Markwon](https://github.com/noties/Markwon) + TablePlugin
- **Editor**: [Sora Editor](https://github.com/Rosemoe/sora-editor) (LGPL-2.1) + TextMate
- **Build**: Android Gradle Plugin 8.13, Gradle 8.13, compileSdk 34

---

## Building

### Requirements

- Android Studio (recent version) or command-line tools
- JDK 17
- Android SDK 34

### Quick Start

```bash
git clone https://github.com/AlonsoAliaga-dev/DARK-LINE-TERMINAL.git
cd DARK-LINE-TERMINAL

./gradlew wrapper          # if needed
./gradlew :app:assembleDebug
```

APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` + `ACCESS_NETWORK_STATE` | Download distros & AI chat |
| `READ/WRITE_EXTERNAL_STORAGE` + `MANAGE_EXTERNAL_STORAGE` | Rootfs import, file manager, bind-mounts |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep terminal sessions alive |
| `POST_NOTIFICATIONS` | Rich notification panel |
| `WAKE_LOCK` | Prevent sleep during long-running commands |
| `VIBRATE` | Terminal bell |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | **Strongly recommended** – prevents Android from killing sessions |

---

## Project Structure

```
DARK-LINE-TERMINAL/
├── app/
│   ├── src/main/
│   │   ├── java/.../terminal/
│   │   │   ├── activity/         # Terminal, Settings, File Editor
│   │   │   ├── ai/               # AI Chat Manager + Panel (Custom API + Markdown)
│   │   │   ├── service/          # TerminalSessionService + rich notifications
│   │   │   ├── distro/           # Distro manager & rootfs importer
│   │   │   ├── filemanager/      # File browser
│   │   │   ├── settings/         # Settings + Custom AI endpoint
│   │   │   └── ...
│   │   ├── jniLibs/              # proot + termux native libs
│   │   ├── assets/               # Fonts + TextMate grammars
│   │   └── res/
│   └── build.gradle.kts
├── terminal-emulator/            # Termux core (Apache-2.0)
├── terminal-view/                # Termux view (Apache-2.0)
├── .github/workflows/            # CI – automatic APK builds
├── LICENSE                       # MPL-2.0
├── NOTICE
└── README.md
```

---

## License

This project is licensed under the **Mozilla Public License 2.0 (MPL-2.0)**.

```
This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at https://mozilla.org/MPL/2.0/.
```

See the [LICENSE](LICENSE) file for the full text.

### Third-party components

| Component | License | Notes |
|-----------|---------|-------|
| **terminal-emulator** & **terminal-view** | Apache-2.0 | Explicit exception in Termux. Only these modules are used. |
| **Sora Editor** | LGPL-2.1+ | Dynamic linking |
| **Markwon** | Apache-2.0 | Advanced Markdown rendering |
| **proot** (native) | GPL (upstream) | Prebuilt shared libraries |
| AndroidX / Material / OkHttp / Gson … | Apache-2.0 | Standard libraries |

Full attribution is available in the [NOTICE](NOTICE) file.

---

## Acknowledgments

- [Termux](https://github.com/termux/termux-app) — `terminal-emulator` & `terminal-view`
- [Jackpal Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator)
- [Sora Editor / Rosemoe](https://github.com/Rosemoe/sora-editor)
- [Markwon](https://github.com/noties/Markwon)
- [proot](https://github.com/proot-me/proot) / Termux proot
- Groq and all OpenAI-compatible providers

---

<p align="center">
  Made with ❤️ for the Android terminal community
</p>
