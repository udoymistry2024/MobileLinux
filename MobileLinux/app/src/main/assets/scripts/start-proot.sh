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

# CRITICAL: Disable seccomp and ignore missing bindings for Android 12-16 compatibility
export PROOT_NO_SECCOMP=1
export PROOT_IGNORE_MISSING_BINDINGS=1
export PROOT_FORCE_ROOTFS_FALLBACK=1

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
    --bind=/dev
    --bind="${SHM_DIR}:/dev/shm"
    --bind="${SHM_DIR}:/run/shm"

    # proot flags
    --kill-on-exit
    --link2symlink
    --sysvipc

    # Start in /home/ubuntu home dir
    --cwd=/home/ubuntu
)

# Conditional binds — only add if paths exist and are readable (Android 14+ restricts many)
for path in /proc/self/fd:/dev/fd /proc/self/fd/0:/dev/stdin /proc/self/fd/1:/dev/stdout /proc/self/fd/2:/dev/stderr; do
    src="${path%%:*}"
    if [ -e "$src" ]; then
        CMD+=(--bind="$path")
    fi
done

# Device nodes — only bind if accessible
for devpath in /dev/pts /dev/null /dev/zero /dev/random /dev/urandom /dev/full; do
    if [ -e "$devpath" ]; then
        CMD+=(--bind="$devpath")
    fi
done

# /sys — restricted on Android 14+, only bind if readable
if [ -d "/sys" ] && [ -r "/sys/kernel" ] 2>/dev/null; then
    CMD+=(--bind=/sys)
fi

# Android system partitions — only bind if accessible (not available in all ROMs)
for syspart in /system /vendor; do
    if [ -d "$syspart" ] && [ -r "$syspart" ] 2>/dev/null; then
        CMD+=(--bind="$syspart")
    fi
done

# /sdcard access from inside Ubuntu
if [ -d "${SDCARD_DIR}" ]; then
    CMD+=(--bind="${SDCARD_DIR}:/sdcard")
    CMD+=(--bind="${SDCARD_DIR}:/mnt/sdcard")
fi

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
    PATH=/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games
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
