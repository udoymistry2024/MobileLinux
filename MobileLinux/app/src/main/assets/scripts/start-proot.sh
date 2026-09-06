#!/bin/bash
# =============================================================================
# MobileLinux — proot Launch Script
# Android 14/15/16 Compatible
#
# Storage layout (modern Android):
#   proot binary:  /data/app/.../lib/arm64/libproot.so  (nativeLibraryDir)
#   Ubuntu rootfs: /sdcard/Android/data/pkg/files/ubuntu-rootfs/
#   Scripts:       /data/data/pkg/files/scripts/ (internal)
# =============================================================================

PROOT_BIN="$1"           # Full path to libproot.so (nativeLibraryDir)
ROOTFS_DIR="$2"          # Ubuntu rootfs directory (external files dir)
TERM_COLS="${3:-80}"     # Terminal width
TERM_ROWS="${4:-24}"     # Terminal height
EXEC_CMD="${5}"          # Command to run (empty = login bash)
SDCARD_DIR="${6:-/sdcard}"  # Path to sdcard for bind mounting

if [ -z "$PROOT_BIN" ] || [ -z "$ROOTFS_DIR" ]; then
    echo "Usage: start-proot.sh <proot-bin> <rootfs-dir> [cols] [rows] [cmd] [sdcard]"
    exit 1
fi

if [ ! -x "$PROOT_BIN" ]; then
    echo "ERROR: proot not executable: $PROOT_BIN"
    echo "Check nativeLibraryDir path"
    exit 1
fi

if [ ! -d "$ROOTFS_DIR/usr" ]; then
    echo "ERROR: Ubuntu rootfs not found: $ROOTFS_DIR"
    echo "Run setup first"
    exit 1
fi

export TERM=xterm-256color
export COLORTERM=truecolor

# Ensure proot finds its loader and shared libraries
LIB_DIR="$(dirname "$PROOT_BIN")"
if [ -f "$LIB_DIR/libproot-loader.so" ]; then
    export PROOT_LOADER="$LIB_DIR/libproot-loader.so"
fi
if [ -f "$LIB_DIR/libproot-loader32.so" ]; then
    export PROOT_LOADER_32="$LIB_DIR/libproot-loader32.so"
fi
export LD_LIBRARY_PATH="$LIB_DIR:${LD_LIBRARY_PATH:-}"

# Ensure dedicated host shared memory directory exists
SHM_DIR="$(dirname "$ROOTFS_DIR")/shm"
mkdir -p "$SHM_DIR" 2>/dev/null || true
chmod 1777 "$SHM_DIR" 2>/dev/null || true
mkdir -p "$ROOTFS_DIR/dev/shm" "$ROOTFS_DIR/run/shm" "$ROOTFS_DIR/tmp" 2>/dev/null || true
chmod 1777 "$ROOTFS_DIR/dev/shm" "$ROOTFS_DIR/run/shm" "$ROOTFS_DIR/tmp" 2>/dev/null || true

# Build proot command
CMD=(
    "$PROOT_BIN"
    --rootfs="$ROOTFS_DIR"

    # Run as root inside the container (UID mapping)
    --root-id

    # System mounts — essential for Ubuntu to work
    --bind=/proc
    --bind=/proc/self/fd:/dev/fd
    --bind=/proc/self/fd/0:/dev/stdin
    --bind=/proc/self/fd/1:/dev/stdout
    --bind=/proc/self/fd/2:/dev/stderr
    --bind=/sys
    --bind=/dev
    --bind="${SHM_DIR}:/dev/shm"
    --bind="${SHM_DIR}:/run/shm"
    --bind=/dev/pts

    # Android-specific mounts
    --bind=/system
    --bind=/vendor

    # /sdcard access from inside Ubuntu
    --bind="${SDCARD_DIR}:/sdcard"
    --bind="${SDCARD_DIR}:/mnt/sdcard"

    # Misc
    --bind=/dev/null
    --bind=/dev/zero
    --bind=/dev/random
    --bind=/dev/urandom
    --bind=/dev/full

    # proot flags
    --kill-on-exit
    --link2symlink
    --sysvipc

    # Start in /home/ubuntu home dir
    --cwd=/home/ubuntu
)

# Environment to pass into Ubuntu
ENV_VARS=(
    HOME=/home/ubuntu
    TERM=xterm-256color
    COLORTERM=truecolor
    COLUMNS="$TERM_COLS"
    LINES="$TERM_ROWS"
    LANG=C.UTF-8
    LC_ALL=C.UTF-8
    TMPDIR=/tmp
    TZ=Asia/Dhaka
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games
    SHELL=/bin/bash
    USER=ubuntu
    LOGNAME=ubuntu
    ANDROID_HOST=true
    MOBILELINUX_MODE=proot
    MOBILELINUX_VERSION=1.0.0
)

# Netlink / getifaddrs fix for ZeroMQ & Python ipykernel on Android
if [ -f "$ROOTFS_DIR/usr/local/lib/libfixgetifaddrs.so" ]; then
    ENV_VARS+=(LD_PRELOAD=/usr/local/lib/libfixgetifaddrs.so)
fi

# Execute
if [ -n "$EXEC_CMD" ]; then
    exec "${CMD[@]}" /usr/bin/env -i "${ENV_VARS[@]}" /bin/bash -c "$EXEC_CMD"
else
    exec "${CMD[@]}" /usr/bin/env -i "${ENV_VARS[@]}" /bin/bash --login
fi
