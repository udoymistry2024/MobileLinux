# MobileLinux

**Ubuntu 24.04 ARM64 on Android — Premium tmux-like Terminal App**

একটি অ্যান্ড্রয়েড অ্যাপ্লিকেশন যা আপনার মোবাইল ফোনকে সরাসরি একটি Ubuntu Linux সার্ভারে পরিণত করে।

---

## Screenshots

```
┌──────────────────────────────────────────────┐
│ [≡] MobileLinux — Main     [+New] [⚙]        │
├──────────────────────────────────────────────┤
│┌──────────┐  ╔══════════════════════════════╗│
││Sessions  │  ║                              ║│
││          │  ║  ┌─[MobileLinux]─[root@ml]   ║│
││▐ Main    │  ║  └─# uname -a               ║│
││  Session2│  ║  Linux 5.15.0-android aarch64║│
││  debug   │  ║                              ║│
││          │  ║  ┌─[MobileLinux]─[root@ml]   ║│
││+ New     │  ║  └─# python3 --version      ║│
│└──────────┘  ║  Python 3.12.4              ║│
│              ╚══════════════════════════════╝│
├──────────────────────────────────────────────┤
│[ESC][Tab][Ctrl][↑][↓][←][→][C-c][|][~][/].. │
└──────────────────────────────────────────────┘
```

---

## Features

- 🐧 **Real Ubuntu 24.04 LTS ARM64** — authentic Linux environment
- 🔓 **Root + Rootless** — proot (non-rooted) and chroot (rooted) support
- 📟 **tmux-like UI** — left drawer sidebar, multiple sessions
- 🔑 **sudo works** — proot `--root-id` + fake-sudo wrapper
- ⌨️ **40+ extra keys** — Ctrl, Alt, Tab, arrows, symbols
- 🔋 **Background persistence** — WakeLock + Foreground Service
- 📦 **Pre-installed tools** — git, python3, nodejs, tmux, vim
- 🎨 **Premium dark theme** — GitHub-inspired color palette

---

## Project Structure

```
MobileLinux/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── binaries/          ← proot static binaries (add manually)
│       │   │   └── README.md
│       │   ├── scripts/           ← Setup + launch scripts
│       │   │   ├── setup.sh
│       │   │   ├── start-proot.sh
│       │   │   ├── start-chroot.sh
│       │   │   ├── fake-sudo
│       │   │   └── install-packages.sh
│       │   └── ubuntu-rootfs/     ← Ubuntu rootfs tarball (add manually)
│       │       └── (ubuntu-24.04-arm64-minimal.tar.xz)
│       ├── java/com/mobilelinux/
│       │   ├── runtime/
│       │   │   ├── UbuntuRuntime.kt     ← Core runtime orchestrator
│       │   │   ├── AssetExtractor.kt    ← Rootfs/binary extraction
│       │   │   └── RootDetector.kt      ← Root detection + ABI
│       │   ├── terminal/
│       │   │   ├── TerminalSession.kt   ← Session data model
│       │   │   ├── TerminalManager.kt   ← Session lifecycle
│       │   │   └── TerminalView.kt      ← VT100 emulator + canvas renderer
│       │   ├── service/
│       │   │   ├── LinuxService.kt      ← Foreground service + WakeLock
│       │   │   └── BootReceiver.kt      ← Auto-start on boot
│       │   ├── ui/
│       │   │   ├── MainActivity.kt      ← DrawerLayout host
│       │   │   ├── MainViewModel.kt     ← Session state ViewModel
│       │   │   ├── SetupActivity.kt     ← First-run wizard
│       │   │   ├── SettingsActivity.kt
│       │   │   ├── TerminalFragment.kt  ← Terminal host fragment
│       │   │   ├── SessionSidebarFragment.kt
│       │   │   ├── ExtraKeysView.kt     ← Custom key bar
│       │   │   └── RenameSessionDialog.kt
│       │   └── util/
│       │       └── TerminalColors.kt    ← ANSI 16 + 256 color palette
│       └── res/
│           ├── layout/                  ← All XML layouts
│           ├── values/                  ← colors, strings, themes, arrays
│           ├── drawable/                ← Vector icons
│           └── xml/                     ← Preferences, file paths
└── build.gradle.kts
```

---

## Setup Instructions

### 1. Get proot binary

```bash
# Download from termux-packages
wget https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.4.0-1_aarch64.deb
ar x proot_*.deb
tar xf data.tar.xz
cp ./data/data/com.termux/files/usr/bin/proot app/src/main/assets/binaries/proot-aarch64
chmod +x app/src/main/assets/binaries/proot-aarch64
```

### 2. Get Ubuntu 24.04 rootfs

```bash
# Option A: Ubuntu official minimal cloud image
wget https://cloud-images.ubuntu.com/minimal/releases/noble/release/ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz
cp ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz \
   app/src/main/assets/ubuntu-rootfs/ubuntu-24.04-arm64-minimal.tar.xz
```

### 3. Add JetBrains Mono font

```bash
wget "https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip"
unzip JetBrainsMono-2.304.zip
cp fonts/ttf/JetBrainsMono-Regular.ttf app/src/main/assets/fonts/
cp fonts/ttf/JetBrainsMono-Bold.ttf app/src/main/assets/fonts/
```

### 4. Add Inter font (for UI)

```bash
# Download from Google Fonts
# Place Inter-Regular.ttf, Inter-Medium.ttf, Inter-Bold.ttf in:
# app/src/main/res/font/
```

### 5. Build

```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## Technical Details

### Root Mode Detection
- `RootDetector.isRooted()` — checks su binary existence
- `RootDetector.canExecuteAsRoot()` — tests actual execution
- Auto-selects proot (non-rooted) or chroot (rooted)

### proot Configuration
```bash
proot --rootfs=/path/to/ubuntu \
      --root-id \           # Appear as UID 0 inside container
      --link2symlink \      # Handle symlinks correctly
      --sysvipc \           # IPC support
      --bind=/proc \        # Mount /proc from Android
      --bind=/sys \
      --bind=/dev \
      --kill-on-exit \      # Clean up on exit
      /bin/bash --login
```

### Session Architecture
```
Android App → TerminalFragment
                → output reader coroutine (Dispatchers.IO)
                    ← SessionProcess.inputStream (stdout)
                TerminalView ← processOutput(bytes)
                              → ANSI parser → screen buffer
                              → Canvas render
                ExtraKeysView → sendInput(bytes)
                    → SessionProcess.outputStream (stdin)
```

### Background Survival
```
LinuxService (Foreground)
  + PARTIAL_WAKE_LOCK
  + START_STICKY
  + stopWithTask=false
  + onTaskRemoved() = no-op (don't stop!)
```

---

## License

Apache License 2.0

Terminal emulator code inspired by [Termux](https://github.com/termux/termux-app) (Apache 2.0).
