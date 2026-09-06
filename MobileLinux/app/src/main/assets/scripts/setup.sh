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

# Configure pip to allow package installation on Ubuntu 24.04 (PEP 668 override)
mkdir -p "$ROOTFS_DIR/etc"
cat > "$ROOTFS_DIR/etc/pip.conf" << 'EOF'
[global]
break-system-packages = true
EOF

cat > "$ROOTFS_DIR/usr/local/bin/pkg-install-python" << 'EOF'
#!/bin/bash
PIP_PKG="$1"
APT_PKG="$2"
if [ -z "$PIP_PKG" ]; then
    echo "Usage: pkg-install-python <pip_package_name> [apt_package_name]"
    exit 1
fi
echo -e "\033[1;36m[MobileLinux]\033[0m Installing \033[1;32m$PIP_PKG\033[0m across Python environments..."
if [ -n "$APT_PKG" ]; then
    export DEBIAN_FRONTEND=noninteractive
    sudo apt-get update -y >/dev/null 2>&1 || true
    sudo apt-get install -y "$APT_PKG" 2>/dev/null || pip3 install --break-system-packages --no-cache-dir "$PIP_PKG" || true
else
    pip3 install --break-system-packages --no-cache-dir "$PIP_PKG" || true
fi
for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do
    if [ -x "$p" ]; then
        "$p" install --no-cache-dir "$PIP_PKG" 2>/dev/null || true
    fi
done
if [ -f /home/ubuntu/.conda/environments.txt ]; then
    while IFS= read -r env_path; do
        if [ -x "$env_path/bin/pip" ] && [ -d "$env_path" ]; then
            "$env_path/bin/pip" install --no-cache-dir "$PIP_PKG" 2>/dev/null || true
        fi
    done < /home/ubuntu/.conda/environments.txt
fi
if [ -n "$CONDA_PREFIX" ] && [ -x "$CONDA_PREFIX/bin/pip" ]; then
    "$CONDA_PREFIX/bin/pip" install --no-cache-dir "$PIP_PKG" 2>/dev/null || true
fi
echo -e "\033[1;32m[MobileLinux]\033[0m ✓ $PIP_PKG installed across Python environments!"
EOF
chmod +x "$ROOTFS_DIR/usr/local/bin/pkg-install-python"

cat > "$ROOTFS_DIR/usr/local/bin/conda-sync-packages" << 'EOF'
#!/bin/bash
TARGET_ENV="$1"
if [ -z "$TARGET_ENV" ]; then
    if [ -n "$CONDA_DEFAULT_ENV" ] && [ "$CONDA_DEFAULT_ENV" != "base" ]; then
        TARGET_ENV="$CONDA_DEFAULT_ENV"
    else
        echo "Usage: conda-sync-packages <conda_environment_name>"
        exit 1
    fi
fi
ENV_PIP=""
if [ -x "/home/ubuntu/miniforge3/envs/$TARGET_ENV/bin/pip" ]; then
    ENV_PIP="/home/ubuntu/miniforge3/envs/$TARGET_ENV/bin/pip"
elif [ -n "$CONDA_PREFIX" ] && [ -x "$CONDA_PREFIX/bin/pip" ]; then
    ENV_PIP="$CONDA_PREFIX/bin/pip"
fi
if [ -z "$ENV_PIP" ] || [ ! -x "$ENV_PIP" ]; then
    echo "Could not find pip for environment '$TARGET_ENV'."
    exit 1
fi
echo -e "\033[1;36m[MobileLinux]\033[0m Syncing essential packages to \033[1;33m$TARGET_ENV\033[0m..."
for pkg in numpy pandas scipy matplotlib ipykernel; do
    "$ENV_PIP" install --no-cache-dir "$pkg" 2>/dev/null || true
done
echo -e "\033[1;32m[MobileLinux]\033[0m ✓ Sync complete for environment '$TARGET_ENV'!"
EOF
chmod +x "$ROOTFS_DIR/usr/local/bin/conda-sync-packages"

# Clean up any leftover mobilelinux-shell.sh in home dirs
rm -f "$ROOTFS_DIR/home/ubuntu/mobilelinux-shell.sh" "$ROOTFS_DIR/root/mobilelinux-shell.sh" 2>/dev/null || true

log "✓ Command wrappers and pip config installed (sudo, python, pip, pkg-install-python, conda-sync)"

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
# Step 8b: Install ZeroMQ / IPykernel Netlink Fix (libfixgetifaddrs.so)
# ===========================================================================
mkdir -p "$ROOTFS_DIR/usr/local/lib"
if [ -f "$SCRIPTS_DIR/libfixgetifaddrs.so" ]; then
    cp "$SCRIPTS_DIR/libfixgetifaddrs.so" "$ROOTFS_DIR/usr/local/lib/libfixgetifaddrs.so"
    chmod 755 "$ROOTFS_DIR/usr/local/lib/libfixgetifaddrs.so"
    if ! grep -q "/usr/local/lib/libfixgetifaddrs.so" "$ROOTFS_DIR/etc/ld.so.preload" 2>/dev/null; then
        echo "/usr/local/lib/libfixgetifaddrs.so" >> "$ROOTFS_DIR/etc/ld.so.preload"
    fi
    log "✓ ZeroMQ netlink fix installed (/usr/local/lib/libfixgetifaddrs.so)"
fi

# ===========================================================================
# Step 8c: Pre-configure Jupyter Server, Notebook & IPykernel
# ===========================================================================
mkdir -p "$ROOTFS_DIR/etc/jupyter" "$ROOTFS_DIR/etc/ipython" \
         "$ROOTFS_DIR/home/ubuntu/.jupyter/custom" "$ROOTFS_DIR/root/.jupyter"

cat > "$ROOTFS_DIR/etc/jupyter/jupyter_server_config.py" << 'EOF'
# MobileLinux - Built-in Jupyter Configuration
c = get_config()
c.ServerApp.allow_root = True
c.NotebookApp.allow_root = True
c.ServerApp.ip = '127.0.0.1'
c.NotebookApp.ip = '127.0.0.1'
c.ServerApp.port = 8888
c.NotebookApp.port = 8888
c.ServerApp.open_browser = False
c.NotebookApp.open_browser = False
c.ServerApp.token = ''
c.NotebookApp.token = ''
c.ServerApp.password = ''
c.NotebookApp.password = ''
c.ServerApp.disable_check_xsrf = True
c.NotebookApp.disable_check_xsrf = True
c.ServerApp.root_dir = '/home/ubuntu'
c.NotebookApp.root_dir = '/home/ubuntu'
c.IPKernelApp.ip = '127.0.0.1'
EOF

cp "$ROOTFS_DIR/etc/jupyter/jupyter_server_config.py" "$ROOTFS_DIR/etc/jupyter/jupyter_notebook_config.py"
cp "$ROOTFS_DIR/etc/jupyter/jupyter_server_config.py" "$ROOTFS_DIR/home/ubuntu/.jupyter/jupyter_server_config.py"
cp "$ROOTFS_DIR/etc/jupyter/jupyter_server_config.py" "$ROOTFS_DIR/home/ubuntu/.jupyter/jupyter_notebook_config.py"
cp "$ROOTFS_DIR/etc/jupyter/jupyter_server_config.py" "$ROOTFS_DIR/root/.jupyter/jupyter_server_config.py"
cp "$ROOTFS_DIR/etc/jupyter/jupyter_server_config.py" "$ROOTFS_DIR/root/.jupyter/jupyter_notebook_config.py"

cat > "$ROOTFS_DIR/etc/ipython/ipython_kernel_config.py" << 'EOF'
c = get_config()
c.IPKernelApp.ip = '127.0.0.1'
EOF

cat > "$ROOTFS_DIR/home/ubuntu/.jupyter/custom/custom.js" << 'EOF'
define(['base/js/namespace'], function(Jupyter) {
    if (Jupyter) {
        Jupyter._target = '_self';
    }
});
EOF

log "✓ Built-in Jupyter & IPython configurations installed"


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
