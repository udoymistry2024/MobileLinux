#!/bin/bash
# =============================================================================
# MobileLinux — chroot Launch Script (Rooted mode)
# Starts Ubuntu 24.04 inside true chroot (requires root)
# =============================================================================

ROOTFS_DIR="$1"
EXEC_CMD="${2:-/bin/bash}"

if [ "$(id -u)" != "0" ]; then
    echo "ERROR: chroot mode requires root. Please root your device or use proot mode."
    exit 1
fi

# Mount necessary filesystems
mount --bind /proc "$ROOTFS_DIR/proc" 2>/dev/null || true
mount --bind /sys "$ROOTFS_DIR/sys" 2>/dev/null || true
mount --bind /dev "$ROOTFS_DIR/dev" 2>/dev/null || true
mount --bind /dev/pts "$ROOTFS_DIR/dev/pts" 2>/dev/null || true

# Shared memory tmpfs mount for multiprocessing & POSIX semaphores
mkdir -p "$ROOTFS_DIR/dev/shm" "$ROOTFS_DIR/run/shm" "$ROOTFS_DIR/tmp" 2>/dev/null || true
chmod 1777 "$ROOTFS_DIR/dev/shm" "$ROOTFS_DIR/run/shm" "$ROOTFS_DIR/tmp" 2>/dev/null || true
mount -t tmpfs -o rw,nosuid,nodev,mode=1777 tmpfs "$ROOTFS_DIR/dev/shm" 2>/dev/null || true

# DNS inside chroot
cp /etc/resolv.conf "$ROOTFS_DIR/etc/resolv.conf" 2>/dev/null || true

# Enter chroot
chroot "$ROOTFS_DIR" \
    /usr/bin/env \
    -i \
    HOME=/root \
    TERM=xterm-256color \
    COLORTERM=truecolor \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TMPDIR=/tmp \
    PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
    SHELL=/bin/bash \
    USER=root \
    LOGNAME=root \
    ANDROID_HOST=true \
    MOBILELINUX_MODE=chroot \
    /bin/bash --login

# Cleanup mounts on exit
umount "$ROOTFS_DIR/dev/shm" 2>/dev/null || true
umount "$ROOTFS_DIR/dev/pts" 2>/dev/null || true
umount "$ROOTFS_DIR/dev" 2>/dev/null || true
umount "$ROOTFS_DIR/sys" 2>/dev/null || true
umount "$ROOTFS_DIR/proc" 2>/dev/null || true
