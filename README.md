# D.L.T — DarkLine Terminal

<p align="center">
  <img src="app/src/main/res/mipmap-xhdpi/ic_launcher.png" alt="D.L.T Logo" width="120"/>
</p>

<p align="center">
  <strong>A modern, feature-rich Android terminal emulator</strong><br>
  Powered by Termux terminal-emulator &amp; terminal-view • Linux distros via proot • Integrated AI assistant
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#building">Building</a> •
  <a href="#permissions">Permissions</a> •
  <a href="#license">License</a>
</p>

---

## Features

| Feature | Description |
|---------|-------------|
| **True Terminal Emulator** | Full VT100/xterm emulation using Termux `terminal-emulator` + `terminal-view` |
| **Multi-Session** | Multiple independent terminal sessions with easy switching |
| **Linux Distros (proot)** | Install and run full Linux rootfs (Ubuntu, Debian, Alpine…) without root |
| **AI Assistant** | Built-in chat panel (Groq / OpenAI-compatible) for commands, explanations and debugging |
| **File Manager** | Browse, open and edit files inside the terminal environment |
| **Code Editor** | Syntax-highlighted editor based on Sora Editor + TextMate grammars |
| **Extra Keys** | On-screen ESC, CTRL, ALT, arrows and more |
| **Dark / Light Themes** | Adaptive Material Design theming |
| **Foreground Service** | Keep sessions alive in the background |

---

## Tech Stack

- **Language**: Java 17
- **UI**: Material Design 3 + ViewBinding
- **Terminal Core**: Termux `terminal-emulator` & `terminal-view` (Apache-2.0)
- **Native**: prebuilt `libproot`, `libtermux`, loaders (arm64-v8a, armeabi-v7a, x86, x86_64)
- **Networking**: OkHttp + SSE for streaming AI responses
- **Editor**: [Sora Editor](https://github.com/Rosemoe/sora-editor) (LGPL-2.1) + TextMate
- **Build**: Android Gradle Plugin 8.7+, Gradle 8.13, compileSdk 35

---

## Building

### Requirements

- Android Studio (recent) or command-line tools
- JDK 17
- Android SDK 35

### Steps

```bash
git clone https://github.com/AlonsoAliaga-dev/DARK-LINE-TERMINAL.git
cd DARK-LINE-TERMINAL
./gradlew wrapper
./gradlew :app:assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

> The project was originally exported from CodeAssist. Build files have been cleaned and modernized.

---

## Permissions

D.L.T requests only the permissions required for reliable terminal operation (aligned with Termux practices for the used components):

| Permission | Reason |
|------------|--------|
| `INTERNET` / `ACCESS_NETWORK_STATE` | Download distros, AI chat (Groq API) |
| `READ/WRITE_EXTERNAL_STORAGE` + `MANAGE_EXTERNAL_STORAGE` | Import rootfs, file manager, bind-mounts |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Persistent terminal sessions |
| `POST_NOTIFICATIONS` | Session notifications |
| `WAKE_LOCK` | Prevent device sleep during long-running commands |
| `VIBRATE` | Terminal bell |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | **Required for stable operation**. Without exemption Android can kill the process, causing the terminal (and proot sessions) to crash. Users should grant this when prompted. |

No unnecessary permissions are requested.

---

## Project Structure

```
DARK-LINE-TERMINAL/
├── app/                          # Main application
│   ├── src/main/
│   │   ├── java/.../terminal/    # Activities, AI, distro manager, file manager, service
│   │   ├── jniLibs/              # Prebuilt native libraries
│   │   ├── assets/               # Fonts + TextMate grammars
│   │   └── res/
│   └── build.gradle.kts
├── terminal-emulator/            # Termux terminal core (Apache-2.0)
├── terminal-view/                # Termux rendering view (Apache-2.0)
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## AI Chat

The integrated AI panel uses the **Groq** API (OpenAI-compatible endpoint).

1. Open the AI panel from the terminal.
2. Configure your API key in **Settings** (stored locally).
3. Chat naturally — the system prompt is tuned for terminal/Linux help.

Streaming responses are supported via Server-Sent Events (SSE).

---

## Distro Management

- Select from registered distros or import a custom rootfs (tar/xz).
- proot-based isolation — no root required.
- Bind-mount support for sharing directories with the host.

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
| **terminal-emulator** & **terminal-view** | Apache-2.0 | Explicit exception in Termux. Derived from Jackpal Android Terminal Emulator. Only these modules are used. |
| **Sora Editor** | LGPL-2.1+ | Dynamic linking. See [Rosemoe/sora-editor](https://github.com/Rosemoe/sora-editor). |
| **proot** (native libs) | GPL (upstream) | Prebuilt shared libraries. |
| AndroidX, Material, OkHttp, Gson, etc. | Apache-2.0 | Standard libraries. |

Full attribution is available in the [NOTICE](NOTICE) file.

---

## Acknowledgments

- [Termux](https://github.com/termux/termux-app) — for the excellent `terminal-emulator` and `terminal-view` libraries
- [Jackpal Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) — original source of the terminal core
- [Sora Editor / Rosemoe](https://github.com/Rosemoe/sora-editor) — powerful code editor
- [proot](https://github.com/proot-me/proot) / Termux proot — user-space isolation
- Groq — fast inference for the AI assistant

---

<p align="center">
  Made with ❤️ for the Android terminal community
</p>
