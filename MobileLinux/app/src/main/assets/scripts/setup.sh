#!/bin/bash
# =============================================================================
# MobileLinux — First-Run Setup Script
# Runs inside the Android app's files directory to configure Ubuntu environment
# =============================================================================

# NOTE: Do NOT use 'set -e' — proot/mobile environments have many transient errors.
# Each step handles its own errors gracefully.

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
echo -e "\033[1;36m[MobileLinux]\033[0m Starting installation of \033[1;32m$PIP_PKG\033[0m..."
INSTALLED_ANY=0
if [ -n "$APT_PKG" ]; then
    echo -e "\033[1;34m[MobileLinux]\033[0m Checking APT package $APT_PKG..."
    export DEBIAN_FRONTEND=noninteractive
    if sudo apt-get -o DPkg::Lock::Timeout=10 install -y --no-install-recommends "$APT_PKG" 2>&1; then
        INSTALLED_ANY=1
        echo -e "\033[1;32m[MobileLinux]\033[0m Installed via APT: $APT_PKG"
    else
        echo -e "\033[1;33m[MobileLinux]\033[0m Updating package lists and retrying APT install..."
        sudo apt-get -o DPkg::Lock::Timeout=10 update 2>&1 || true
        if sudo apt-get -o DPkg::Lock::Timeout=10 install -y --no-install-recommends "$APT_PKG" 2>&1; then
            INSTALLED_ANY=1
            echo -e "\033[1;32m[MobileLinux]\033[0m Installed via APT: $APT_PKG"
        fi
    fi
fi
if [ $INSTALLED_ANY -eq 0 ]; then
    echo -e "\033[1;34m[MobileLinux]\033[0m Installing $PIP_PKG via pip3..."
    if ! command -v pip3 >/dev/null 2>&1; then
        echo -e "\033[1;33m[MobileLinux]\033[0m Setting up pip3..."
        sudo apt-get -o DPkg::Lock::Timeout=10 install -y python3-pip 2>&1 || true
    fi
    if command -v pip3 >/dev/null 2>&1; then
        if pip3 install --break-system-packages --prefer-binary --no-cache-dir --default-timeout=30 "$PIP_PKG" 2>&1; then
            INSTALLED_ANY=1
            echo -e "\033[1;32m[MobileLinux]\033[0m Installed via pip3: $PIP_PKG"
        fi
    fi
fi
SEEN_DIRS=" "
for conda_base in /home/ubuntu/miniforge3 /root/miniconda3 /opt/conda; do
    if [ -d "$conda_base" ]; then
        SEEN_DIRS="$SEEN_DIRS$conda_base "
        if "$conda_base/bin/python" -c "import $PIP_PKG" >/dev/null 2>&1; then
            echo -e "\033[1;32m[MobileLinux]\033[0m $PIP_PKG is already available in Conda: $conda_base"
            INSTALLED_ANY=1
            continue
        fi
        if [ -x "$conda_base/bin/pip" ]; then
            echo -e "\033[1;34m[MobileLinux]\033[0m Installing $PIP_PKG into Conda ($conda_base)..."
            if "$conda_base/bin/pip" install --prefer-binary --no-cache-dir --default-timeout=30 "$PIP_PKG" 2>&1; then
                INSTALLED_ANY=1
            fi
        fi
    fi
done
for env_pip in /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip; do
    if [ -x "$env_pip" ]; then
        ENV_DIR="$(dirname "$(dirname "$env_pip")")"
        if [[ "$SEEN_DIRS" != *" $ENV_DIR "* ]]; then
            SEEN_DIRS="$SEEN_DIRS$ENV_DIR "
            if "$ENV_DIR/bin/python" -c "import $PIP_PKG" >/dev/null 2>&1; then
                continue
            fi
            "$env_pip" install --prefer-binary --no-cache-dir --default-timeout=30 "$PIP_PKG" 2>&1 || true
        fi
    fi
done
if [ -n "$CONDA_PREFIX" ] && [ -x "$CONDA_PREFIX/bin/pip" ]; then
    if ! "$CONDA_PREFIX/bin/python" -c "import $PIP_PKG" >/dev/null 2>&1; then
        "$CONDA_PREFIX/bin/pip" install --prefer-binary --no-cache-dir --default-timeout=30 "$PIP_PKG" 2>&1 || true
    fi
fi
VERIFIED=0
if python3 -c "import $PIP_PKG" >/dev/null 2>&1; then
    VERIFIED=1
elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c "import $PIP_PKG" >/dev/null 2>&1; then
    VERIFIED=1
elif [ -x /root/miniconda3/bin/python ] && /root/miniconda3/bin/python -c "import $PIP_PKG" >/dev/null 2>&1; then
    VERIFIED=1
elif [ -n "$CONDA_PREFIX" ] && [ -x "$CONDA_PREFIX/bin/python" ] && "$CONDA_PREFIX/bin/python" -c "import $PIP_PKG" >/dev/null 2>&1; then
    VERIFIED=1
fi
if [ $VERIFIED -eq 1 ]; then
    echo -e "\033[1;32m[MobileLinux]\033[0m ✓ $PIP_PKG installation complete and verified!"
    exit 0
else
    echo -e "\033[1;31m[MobileLinux]\033[0m ✗ $PIP_PKG installation could not be verified."
    exit 1
fi
EOF
chmod +x "$ROOTFS_DIR/usr/local/bin/pkg-install-python"

cat > "$ROOTFS_DIR/usr/local/bin/pkg-uninstall-python" << 'EOF'
#!/bin/bash
PIP_PKG="$1"
APT_PKG="$2"
MODULE_NAME="$3"
if [ -z "$PIP_PKG" ]; then
    echo "Usage: pkg-uninstall-python <pip_package_name> [apt_package_name] [module_name]"
    exit 1
fi
# Auto-resolve Python import module name if not explicitly provided
if [ -z "$MODULE_NAME" ]; then
    case "$PIP_PKG" in
        opencv-python|opencv-contrib-python) MODULE_NAME="cv2" ;;
        scikit-learn) MODULE_NAME="sklearn" ;;
        Pillow) MODULE_NAME="PIL" ;;
        beautifulsoup4) MODULE_NAME="bs4" ;;
        PyYAML) MODULE_NAME="yaml" ;;
        sherlock-project) MODULE_NAME="sherlock" ;;
        *) MODULE_NAME="${PIP_PKG//-/_}" ;;
    esac
fi
echo -e "\033[1;36m[MobileLinux]\033[0m Purging \033[1;31m$PIP_PKG\033[0m across all environments..."
sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true
export DEBIAN_FRONTEND=noninteractive
TARGETS=""
[ -n "$APT_PKG" ] && TARGETS="$TARGETS $APT_PKG"
TARGETS="$TARGETS python3-$PIP_PKG python-$PIP_PKG"
echo -e "\033[1;34m[MobileLinux]\033[0m Removing APT packages ($TARGETS)..."
sudo apt-get -o DPkg::Lock::Timeout=10 purge -y $TARGETS 2>&1 || true
sudo apt-get clean 2>/dev/null || true
echo -e "\033[1;34m[MobileLinux]\033[0m Removing from system pip3..."
pip3 uninstall -y --break-system-packages "$PIP_PKG" 2>&1 || true
python3 -m pip uninstall -y --break-system-packages "$PIP_PKG" 2>&1 || true
echo -e "\033[1;34m[MobileLinux]\033[0m Cleaning Python site-packages..."
rm -rf /home/ubuntu/.local/lib/python*/site-packages/${PIP_PKG}* /home/ubuntu/.local/lib/python*/site-packages/${MODULE_NAME}* 2>/dev/null || true
rm -rf /root/.local/lib/python*/site-packages/${PIP_PKG}* /root/.local/lib/python*/site-packages/${MODULE_NAME}* 2>/dev/null || true
rm -rf /usr/local/lib/python*/dist-packages/${PIP_PKG}* /usr/local/lib/python*/dist-packages/${MODULE_NAME}* 2>/dev/null || true
rm -rf /usr/local/lib/python*/site-packages/${PIP_PKG}* /usr/local/lib/python*/site-packages/${MODULE_NAME}* 2>/dev/null || true
rm -rf /usr/lib/python3/dist-packages/${PIP_PKG}* /usr/lib/python3/dist-packages/${MODULE_NAME}* 2>/dev/null || true
rm -rf /usr/lib/python*/dist-packages/${PIP_PKG}* /usr/lib/python*/dist-packages/${MODULE_NAME}* 2>/dev/null || true
rm -rf /usr/lib/python*/site-packages/${PIP_PKG}* /usr/lib/python*/site-packages/${MODULE_NAME}* 2>/dev/null || true
echo -e "\033[1;34m[MobileLinux]\033[0m Cleaning Conda environments..."
SEEN_DIRS=" "
for conda_base in /home/ubuntu/miniforge3 /root/miniconda3 /opt/conda; do
    if [ -d "$conda_base" ]; then
        SEEN_DIRS="$SEEN_DIRS$conda_base "
        if [ -x "$conda_base/bin/pip" ]; then
            "$conda_base/bin/pip" uninstall -y "$PIP_PKG" 2>/dev/null || true
        fi
        rm -rf "$conda_base"/lib/python*/site-packages/${PIP_PKG}* "$conda_base"/lib/python*/site-packages/${MODULE_NAME}* 2>/dev/null || true
        if [ -x "$conda_base/bin/conda" ]; then
            if "$conda_base/bin/conda" list 2>/dev/null | grep -E -q "^${PIP_PKG}[[:space:]]"; then
                "$conda_base/bin/conda" remove -y -q "$PIP_PKG" 2>/dev/null || true
            fi
        fi
    fi
done
for env_dir in /home/ubuntu/miniforge3/envs/* /root/miniconda3/envs/* /home/ubuntu/.conda/envs/* /root/.conda/envs/*; do
    if [ -d "$env_dir" ]; then
        if [[ "$SEEN_DIRS" != *" $env_dir "* ]]; then
            SEEN_DIRS="$SEEN_DIRS$env_dir "
            if [ -x "$env_dir/bin/pip" ]; then
                "$env_dir/bin/pip" uninstall -y "$PIP_PKG" 2>/dev/null || true
            fi
            rm -rf "$env_dir"/lib/python*/site-packages/${PIP_PKG}* "$env_dir"/lib/python*/site-packages/${MODULE_NAME}* 2>/dev/null || true
        fi
    fi
done
if [ -n "$CONDA_PREFIX" ] && [ -d "$CONDA_PREFIX" ]; then
    if [ -x "$CONDA_PREFIX/bin/pip" ]; then
        "$CONDA_PREFIX/bin/pip" uninstall -y "$PIP_PKG" 2>/dev/null || true
    fi
    rm -rf "$CONDA_PREFIX"/lib/python*/site-packages/${PIP_PKG}* "$CONDA_PREFIX"/lib/python*/site-packages/${MODULE_NAME}* 2>/dev/null || true
fi
for py in python3 /home/ubuntu/miniforge3/bin/python /root/miniconda3/bin/python; do
    [ -x "$py" ] || continue
    LOC="$("$py" -c "import $MODULE_NAME; import os; print(os.path.dirname(getattr($MODULE_NAME, '__file__', '')))" 2>/dev/null)"
    if [ -n "$LOC" ] && [ -d "$LOC" ]; then
        rm -rf "$LOC" "${LOC}.dist-info" "${LOC}.egg-info" "${LOC}"-*.dist-info 2>/dev/null || true
        PARENT="$(dirname "$LOC")"
        rm -rf "$PARENT/${PIP_PKG}"* "$PARENT/${MODULE_NAME}"* "$PARENT/${PIP_PKG}-"*.dist-info 2>/dev/null || true
    fi
    FILE_LOC="$("$py" -c "import $MODULE_NAME; print(getattr($MODULE_NAME, '__file__', ''))" 2>/dev/null)"
    if [ -n "$FILE_LOC" ] && [ -f "$FILE_LOC" ]; then
        rm -f "$FILE_LOC" 2>/dev/null || true
    fi
done
rm -f "/usr/local/bin/$PIP_PKG" "/usr/local/bin/${PIP_PKG}3" "/usr/bin/$PIP_PKG" 2>/dev/null || true
rm -f "/home/ubuntu/.local/bin/$PIP_PKG" "/root/.local/bin/$PIP_PKG" 2>/dev/null || true
for cb in /home/ubuntu/miniforge3/bin /root/miniconda3/bin /home/ubuntu/miniforge3/envs/*/bin /root/miniconda3/envs/*/bin; do
    rm -f "$cb/$PIP_PKG" "$cb/${PIP_PKG}3" 2>/dev/null || true
done
find /home/ubuntu/.cache /root/.cache /tmp -name "*${PIP_PKG}*" -o -name "*${MODULE_NAME}*" -exec rm -rf {} + 2>/dev/null || true
echo -e "\033[1;34m[MobileLinux]\033[0m Verifying removal..."
STILL_INSTALLED=0
if python3 -c "import $MODULE_NAME" >/dev/null 2>&1; then
    STILL_INSTALLED=1
    echo -e "\033[1;33m[MobileLinux]\033[0m Still importable via system python3"
elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c "import $MODULE_NAME" >/dev/null 2>&1; then
    STILL_INSTALLED=1
    echo -e "\033[1;33m[MobileLinux]\033[0m Still importable via Miniforge3 python"
elif [ -x /root/miniconda3/bin/python ] && /root/miniconda3/bin/python -c "import $MODULE_NAME" >/dev/null 2>&1; then
    STILL_INSTALLED=1
    echo -e "\033[1;33m[MobileLinux]\033[0m Still importable via Miniconda3 python"
fi
if [ $STILL_INSTALLED -eq 0 ]; then
    echo -e "\033[1;32m[MobileLinux]\033[0m ✓ $PIP_PKG permanently uninstalled and purged from all environments!\n"
    exit 0
else
    echo -e "\033[1;31m[MobileLinux]\033[0m ✗ $PIP_PKG could not be fully removed — still importable. Package may be system-protected.\n"
    exit 1
fi
EOF
chmod +x "$ROOTFS_DIR/usr/local/bin/pkg-uninstall-python"

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
ENV_PYTHON=""
if [ -x "/home/ubuntu/miniforge3/envs/$TARGET_ENV/bin/pip" ]; then
    ENV_PIP="/home/ubuntu/miniforge3/envs/$TARGET_ENV/bin/pip"
    ENV_PYTHON="/home/ubuntu/miniforge3/envs/$TARGET_ENV/bin/python"
elif [ -n "$CONDA_PREFIX" ] && [ -x "$CONDA_PREFIX/bin/pip" ]; then
    ENV_PIP="$CONDA_PREFIX/bin/pip"
    ENV_PYTHON="$CONDA_PREFIX/bin/python"
fi
if [ -z "$ENV_PIP" ] || [ ! -x "$ENV_PIP" ]; then
    echo "Could not find pip for environment '$TARGET_ENV'."
    exit 1
fi
echo -e "\033[1;36m[MobileLinux]\033[0m Syncing essential packages to \033[1;33m$TARGET_ENV\033[0m..."
for pkg in numpy pandas scipy matplotlib ipykernel; do
    if [ -x "$ENV_PYTHON" ] && "$ENV_PYTHON" -c "import $pkg" >/dev/null 2>&1; then
        echo -e "\033[1;32m[MobileLinux]\033[0m $pkg already exists in $TARGET_ENV"
        continue
    fi
    echo -e "Syncing $pkg..."
    "$ENV_PIP" install --prefer-binary --no-cache-dir --default-timeout=30 "$pkg" 2>/dev/null || true
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

# --- Colors & Terminal Settings ---
if command -v dircolors >/dev/null 2>&1; then
    eval "$(dircolors -b 2>/dev/null)"
fi
if [ -n "$LS_COLORS" ]; then
    export LS_COLORS="$(echo "$LS_COLORS" | sed 's/ow=[0-9;]*/ow=01;34/g; s/tw=[0-9;]*/tw=01;34/g; s/st=[0-9;]*/st=01;34/g'):ow=01;34:tw=01;34:st=01;34:"
else
    export LS_COLORS="rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:ca=00:tw=01;34:ow=01;34:st=01;34:ex=01;32:"
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
export PATH="/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games"
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
PATH="/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games"
EOF
log "✓ Locale configured"

# ===========================================================================
# Step 12: Conda Environment Setup (if present)
# ===========================================================================
if [ -x "$ROOTFS_DIR/home/ubuntu/miniforge3/bin/conda" ]; then
    "$ROOTFS_DIR/home/ubuntu/miniforge3/bin/conda" init bash 2>/dev/null || true
    "$ROOTFS_DIR/home/ubuntu/miniforge3/bin/conda" config --set always_copy true 2>/dev/null || true
    "$ROOTFS_DIR/home/ubuntu/miniforge3/bin/conda" config --set auto_activate_base true 2>/dev/null || true
fi

log ""
log "✅ Setup complete! Ubuntu 24.04 ARM64 environment ready."
log "Run 'start-proot.sh' to enter the environment."
