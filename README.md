# RemoteTouchpad

Turn your Android phone into a wireless trackpad, keyboard, and media remote for your Windows PC — over your local WiFi, no cloud, no account, no internet required.

![platform](https://img.shields.io/badge/platform-Windows%20%7C%20Android-blue)
![license](https://img.shields.io/badge/license-MIT-green)

## Why

Most "remote mouse" apps either require an account, phone home to a cloud server, or come bundled with ads and trackers for a feature this simple. RemoteTouchpad is two small, readable, fully open-source programs that talk to each other directly over your WiFi network — nothing leaves your LAN.

## Features

- **Full-screen trackpad** — drag to move the cursor, tap to left-click, hold to right-click
- **Two-finger gestures** — drag to scroll, tap to right-click (matches standard Windows Precision Touchpad behavior)
- **Zoom in / out** buttons (sends Ctrl+Scroll)
- **On-demand keyboard** — type and it streams to the PC live, including native backspace
- **Media controls** — play/pause, next/previous track, volume up/down
- **Reverse-scroll toggle** — persisted, one tap
- **Zero-config discovery** — the phone finds your PC automatically on the LAN; no typing IP addresses
- **Auto-reconnect** — survives WiFi blips and PC sleep without manual re-pairing
- **Runs from the system tray** — the Windows side is a lightweight background app, not a console window

## How it works

```
┌─────────────────────┐        WebSocket (ws://)       ┌──────────────────────┐
│   Android app        │ ───────────────────────────▶  │   Windows tray app    │
│   (Kotlin)           │   JSON messages: move/click/   │   (Python)            │
│                       │   scroll/zoom/key              │                       │
│   UDP broadcast  ◀──────────────────────────────────  │   UDP discovery       │
│   "where are you?"    │                                 │   responder           │
└─────────────────────┘                                 └──────────────────────┘
```

- **Discovery**: the phone broadcasts a UDP packet on the LAN; any PC running RemoteTouchpad answers with its name and IP. No manual IP entry needed.
- **Control channel**: a single WebSocket connection carries small JSON messages (`{"type":"move","dx":3,"dy":-1}`, `{"type":"scroll","clicks":-2}`, etc.).
- **Input simulation**: the Windows side uses `pynput` for keyboard input and the native Win32 `mouse_event` API for cursor movement (a single relative-move syscall — smoother and lower-latency than higher-level libraries that read-then-set cursor position).

See [`windows-server/server.py`](windows-server/server.py) for the full message protocol if you want to build your own client.

## Getting started

### 1. Windows (the PC you want to control)

Requires [Python 3.10+](https://www.python.org/downloads/) installed with "Add to PATH" checked.

```powershell
cd windows-server
setup.bat
```

This installs dependencies, builds `RemoteTouchpad.exe` with PyInstaller, and launches it. After the first build, you can just double-click `dist\RemoteTouchpad.exe` directly — no need to re-run `setup.bat`.

Optional: run `install_autostart.bat` once to have it launch automatically on login.

### 2. Android (the remote)

Open the `android/` folder in Android Studio (Hedgehog or newer), let Gradle sync, and run on your device. Minimum SDK 24 (Android 7.0).

To build an installable APK yourself: **Build → Generate Signed App Bundle / APK → APK**, create a keystore when prompted, and build the `release` variant.

### 3. Connect

1. Launch `RemoteTouchpad.exe` on the PC (it sits quietly in the system tray).
2. Open the app on your phone — same WiFi network as the PC.
3. Tap **Find laptop**. It should appear within ~2 seconds; tap it to connect.

## Gesture reference

| Gesture | Action |
|---|---|
| One finger, tap | Left click |
| One finger, drag | Move cursor |
| One finger, hold still | Right click |
| Two fingers, drag | Scroll |
| Two fingers, tap | Right click |
| Zoom buttons | Zoom in / out (Ctrl+Scroll) |
| Keyboard icon | Toggle the text input bar |
| Media icon | Toggle playback/volume controls |
| Reverse-scroll icon | Flip scroll direction (persists) |

## Privacy & security

- All communication stays on your local network — nothing is sent to the internet.
- There is currently **no authentication** on the WebSocket connection: any device on your WiFi can discover and connect to a running RemoteTouchpad server. This is fine on a trusted home network; avoid running it on public/shared WiFi. PRs adding optional pairing/auth are welcome.

## Project structure

```
android/           Kotlin Android app (Android Studio project)
windows-server/    Python Windows server + tray app
  server.py          WebSocket server + input simulation + discovery responder
  tray_server.py     System tray wrapper (the PyInstaller entry point)
  test_client.py     Manual test script — sends sample move/click/key events
  setup.bat          One-click install + build + run
  build.bat          Build RemoteTouchpad.exe only
  install_autostart.bat   Add to Windows startup
```

## Contributing

Issues and PRs welcome — this is a small, hackable codebase on purpose. Ideas that would be great contributions: optional connection PIN/auth, three/four-finger gesture support, Linux/macOS server ports, horizontal scroll.

## License

MIT — see [LICENSE](LICENSE).
