#!/bin/bash
# =============================================================================
# MobileLinux — First-Run Setup Script
# Runs inside the Android app's files directory to configure Ubuntu environment
# =============================================================================

set -e

ROOTFS_DIR="$1"          # e.g. /data/data/com.mobilelinux.app/files/ubuntu-rootfs
BINARIES_DIR="$2"        # e.g. /data/data/com.mobilelinux.app/files/binaries
SCRIPTS_DIR="$3"         # e.g. /data/data/com.mobilelinux.app/files/scripts
IS_ROOTED="$4"           # "true" or "false"

log() {
    echo "[MobileLinux Setup] $1"
}

log "Starting Ubuntu 24.04 environment setup..."
log "Rootfs: $ROOTFS_DIR"
log "Rooted: $IS_ROOTED"

# ===========================================================================
# Step 1: Verify rootfs exists
# ===========================================================================
if [ ! -d "$ROOTFS_DIR/usr" ]; then
    log "ERROR: Ubuntu rootfs not found at $ROOTFS_DIR"
    exit 1
fi
log "✓ Ubuntu rootfs verified"

# ===========================================================================
# Step 2: Configure /etc/resolv.conf (DNS)
# ===========================================================================
mkdir -p "$ROOTFS_DIR/etc"
cat > "$ROOTFS_DIR/etc/resolv.conf" << 'EOF'
nameserver 8.8.8.8
nameserver 8.8.4.4
nameserver 1.1.1.1
EOF
log "✓ DNS configured"

# ===========================================================================
# Step 3: Configure /etc/hosts
# ===========================================================================
cat > "$ROOTFS_DIR/etc/hosts" << 'EOF'
127.0.0.1   localhost
127.0.1.1   mobilelinux
::1         localhost ip6-localhost ip6-loopback
EOF
log "✓ Hosts file configured"

# ===========================================================================
# Step 4: Configure passwd / group for proot (non-rooted mode)
# ===========================================================================
if [ "$IS_ROOTED" = "false" ]; then
    # In proot mode, we appear as root inside the container
    # Ensure /etc/passwd has root entry
    if ! grep -q "^root:" "$ROOTFS_DIR/etc/passwd" 2>/dev/null; then
        echo "root:x:0:0:root:/root:/bin/bash" >> "$ROOTFS_DIR/etc/passwd"
    fi
    if ! grep -q "^root:" "$ROOTFS_DIR/etc/group" 2>/dev/null; then
        echo "root:x:0:" >> "$ROOTFS_DIR/etc/group"
    fi
fi
log "✓ User/group configured"

# ===========================================================================
# Step 5: Configure sudoers for non-rooted mode (fakeroot approach)
# ===========================================================================
mkdir -p "$ROOTFS_DIR/etc/sudoers.d"
cat > "$ROOTFS_DIR/etc/sudoers.d/mobilelinux" << 'EOF'
# MobileLinux: allow all users to run all commands without password
ALL ALL=(ALL:ALL) NOPASSWD: ALL
root ALL=(ALL:ALL) NOPASSWD: ALL
EOF
chmod 440 "$ROOTFS_DIR/etc/sudoers.d/mobilelinux"
log "✓ Sudoers configured (passwordless)"

# ===========================================================================
# Step 6: Install fake-sudo, python, and pip wrappers
# ===========================================================================
cp "$SCRIPTS_DIR/fake-sudo" "$ROOTFS_DIR/usr/local/bin/sudo"
chmod +x "$ROOTFS_DIR/usr/local/bin/sudo"

cat > "$ROOTFS_DIR/usr/local/bin/python" << 'EOF'
#!/bin/bash
if [ -x /usr/bin/python3 ]; then
    exec /usr/bin/python3 "$@"
elif [ -x /bin/python3 ]; then
    exec /bin/python3 "$@"
else
    echo "[MobileLinux] python3 is not found. Run: sudo apt-get update && sudo apt-get install -y python3"
    exit 127
fi
EOF
chmod +x "$ROOTFS_DIR/usr/local/bin/python"

cat > "$ROOTFS_DIR/usr/local/bin/pip" << 'EOF'
#!/bin/bash
if /usr/bin/python3 -m pip --version >/dev/null 2>&1; then
    exec /usr/bin/python3 -m pip "$@"
elif [ -x /usr/bin/pip3 ]; then
    exec /usr/bin/pip3 "$@"
fi
echo -e "\033[1;36m[MobileLinux]\033[0m pip is not installed yet. Installing python3-pip..."
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -y && sudo apt-get install -y --no-install-recommends python3-pip
if /usr/bin/python3 -m pip --version >/dev/null 2>&1; then
    exec /usr/bin/python3 -m pip "$@"
elif [ -x /usr/bin/pip3 ]; then
    exec /usr/bin/pip3 "$@"
else
    echo -e "\033[1;31m[MobileLinux]\033[0m Failed to install pip."
    exit 1
fi
EOF
chmod +x "$ROOTFS_DIR/usr/local/bin/pip"

# Clean up any leftover mobilelinux-shell.sh in home dirs
rm -f "$ROOTFS_DIR/home/ubuntu/mobilelinux-shell.sh" "$ROOTFS_DIR/root/mobilelinux-shell.sh" 2>/dev/null || true

log "✓ Command wrappers installed (sudo, python, pip)"

# ===========================================================================
# Step 7: Configure apt sources for Ubuntu 24.04 ARM64
# ===========================================================================
cat > "$ROOTFS_DIR/etc/apt/sources.list" << 'EOF'
deb http://ports.ubuntu.com/ubuntu-ports noble main restricted universe multiverse
deb http://ports.ubuntu.com/ubuntu-ports noble-updates main restricted universe multiverse
deb http://ports.ubuntu.com/ubuntu-ports noble-security main restricted universe multiverse
deb http://ports.ubuntu.com/ubuntu-ports noble-backports main restricted universe multiverse
EOF
log "✓ APT sources configured (Ubuntu 24.04 Noble ARM64)"

# ===========================================================================
# Step 8: Create /root home directory with bashrc
# ===========================================================================
mkdir -p "$ROOTFS_DIR/root"
cat > "$ROOTFS_DIR/root/.bashrc" << 'BASHRC'
# MobileLinux ~/.bashrc

# Don't run for non-interactive shells
[ -z "$PS1" ] && return

# --- Colors ---
RED='\[\033[0;31m\]'
GREEN='\[\033[0;32m\]'
YELLOW='\[\033[1;33m\]'
BLUE='\[\033[0;34m\]'
CYAN='\[\033[0;36m\]'
MAGENTA='\[\033[0;35m\]'
WHITE='\[\033[1;37m\]'
RESET='\[\033[0m\]'

# --- Prompt ---
if [ "$(id -u)" = "0" ]; then
    PS1="${RED}┌─[${CYAN}MobileLinux${RED}]─[${YELLOW}\u@\h${RED}]─[${WHITE}\w${RED}]\n└─${RED}# ${RESET}"
else
    PS1="${GREEN}┌─[${CYAN}MobileLinux${GREEN}]─[${YELLOW}\u@\h${GREEN}]─[${WHITE}\w${GREEN}]\n└─${GREEN}$ ${RESET}"
fi

# --- Aliases ---
alias ls='ls --color=auto'
alias ll='ls -alF --color=auto'
alias la='ls -A --color=auto'
alias l='ls -CF --color=auto'
alias grep='grep --color=auto'
alias ..='cd ..'
alias ...='cd ../..'
alias clear='printf "\033[H\033[2J\033[3J"'
alias cls='printf "\033[H\033[2J\033[3J"'
alias update='apt-get update'
alias upgrade='apt-get upgrade -y'
alias install='apt-get install -y'

# --- Environment ---
export TERM=xterm-256color
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games"
shopt -s checkwinsize
BASHRC
log "✓ .bashrc configured with custom prompt"

# Ensure PRoot compatibility for Conda (force copy mode to prevent .l2s hardlink errors)
cat > "$ROOTFS_DIR/root/.condarc" << 'EOF'
always_copy: true
EOF
mkdir -p "$ROOTFS_DIR/home/ubuntu"
cat > "$ROOTFS_DIR/home/ubuntu/.condarc" << 'EOF'
always_copy: true
EOF
log "✓ .condarc configured (always_copy: true)"

# ===========================================================================
# Step 9: Create /etc/profile.d/mobilelinux.sh
# ===========================================================================
mkdir -p "$ROOTFS_DIR/etc/profile.d"
cat > "$ROOTFS_DIR/etc/profile.d/mobilelinux.sh" << 'EOF'
export TERM=xterm-256color
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export ANDROID_HOST=true
EOF
log "✓ Profile configured"

# ===========================================================================
# Step 10: Setup /proc, /sys, /dev bind points
# ===========================================================================
mkdir -p "$ROOTFS_DIR/proc" "$ROOTFS_DIR/sys" "$ROOTFS_DIR/dev" \
         "$ROOTFS_DIR/dev/pts" "$ROOTFS_DIR/tmp" "$ROOTFS_DIR/run"
log "✓ Mount points created"

# ===========================================================================
# Step 11: Locale setup (silent, avoid locale warnings)
# ===========================================================================
cat > "$ROOTFS_DIR/etc/locale.gen" << 'EOF'
C.UTF-8 UTF-8
EOF
cat > "$ROOTFS_DIR/etc/locale.conf" << 'EOF'
LANG=C.UTF-8
LC_ALL=C.UTF-8
EOF
mkdir -p "$ROOTFS_DIR/etc/default"
cat > "$ROOTFS_DIR/etc/default/locale" << 'EOF'
LANG=C.UTF-8
LC_ALL=C.UTF-8
EOF
cat > "$ROOTFS_DIR/etc/environment" << 'EOF'
LANG=C.UTF-8
LC_ALL=C.UTF-8
PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games"
EOF
log "✓ Locale configured"

log ""
log "✅ Setup complete! Ubuntu 24.04 ARM64 environment ready."
log "Run 'start-proot.sh' to enter the environment."
