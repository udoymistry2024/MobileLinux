# MobileLinux — proot Binaries

এই ফোল্ডারে statically compiled proot binaries রাখতে হবে।

## Required Files

| File | Architecture | Device |
|------|-------------|--------|
| `proot-aarch64` | ARM64 | Modern Android phones |
| `proot-x86_64` | x86_64 | Intel/AMD Android devices |
| `proot-armv7` | ARMv7 | Older 32-bit Android phones |

## How to Get proot Binaries

### Option 1: Download from termux-packages (Recommended)
```bash
# ARM64
curl -LO "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.x/proot_static_aarch64"
mv proot_static_aarch64 proot-aarch64
chmod +x proot-aarch64

# x86_64
curl -LO "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.x/proot_static_x86_64"
mv proot_static_x86_64 proot-x86_64
chmod +x proot-x86_64
```

### Option 2: Build from source
```bash
git clone https://github.com/proot-me/proot.git
cd proot
# Follow build instructions for Android target
```

### Option 3: From proot-distro releases
Download from: https://github.com/termux/proot-distro/

## Note
The binaries must be:
- Statically linked (no dynamic dependencies)
- Compatible with Android's seccomp filters
- Version ≥ 5.4.0 for best compatibility

## Ubuntu rootfs

Download Ubuntu 24.04 minimal ARM64 rootfs:
```bash
# Official Ubuntu cloud images (minimal)
curl -LO "https://cloud-images.ubuntu.com/minimal/releases/noble/release/ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz"
mv ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz ubuntu-24.04-arm64-minimal.tar.xz

# Place in: app/src/main/assets/ubuntu-rootfs/
```

Or use Termux's proot-distro Ubuntu image:
```bash
# URL from: https://github.com/termux/proot-distro/
# Look for ubuntu-24.04-aarch64-pd-*.tar.xz
```
