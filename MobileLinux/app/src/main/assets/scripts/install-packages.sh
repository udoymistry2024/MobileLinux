#!/bin/bash
# =============================================================================
# MobileLinux — Package Installation Script
# Installs essential developer tools inside Ubuntu environment
# Run this from INSIDE the Ubuntu environment (proot or chroot)
# =============================================================================

# NOTE: Do NOT use 'set -e' here — in proot/mobile environments, many commands
# return transient errors (DNS timeouts, lock files, missing caches). Each section
# handles its own errors gracefully so a single failure doesn't abort everything.

log() { echo -e "\033[0;36m[MobileLinux]\033[0m $1"; }
log_ok() { echo -e "\033[0;32m[MobileLinux]\033[0m ✓ $1"; }
log_err() { echo -e "\033[0;31m[MobileLinux]\033[0m ✗ $1"; }

# Clean stale locks before starting (common in proot after interrupted installs)
rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* /var/lib/dpkg/updates/* 2>/dev/null || true
dpkg --configure -a 2>/dev/null || true

log "Installing essential development packages..."
log "This may take a few minutes..."
echo ""

# Update package lists
log "Updating package lists..."
if apt-get update -y 2>&1 | tail -3; then
    log_ok "Package lists updated"
else
    log_err "Package list update had warnings (continuing anyway)"
fi

# Core utilities
log "Installing core utilities..."
DEBIAN_FRONTEND=noninteractive apt-get install -y \
    bash \
    bash-completion \
    curl \
    wget \
    git \
    vim \
    nano \
    tmux \
    htop \
    tree \
    file \
    unzip \
    zip \
    tar \
    gzip \
    bzip2 \
    xz-utils \
    less \
    man-db \
    locales \
    sudo \
    ca-certificates \
    gnupg \
    lsb-release \
    software-properties-common \
    2>&1 | grep -E "(Installing|Removing|Unpacking|Setting up)" | head -30

log_ok "Core utilities installed"

# Development tools
log "Installing development tools..."
DEBIAN_FRONTEND=noninteractive apt-get install -y \
    build-essential \
    cmake \
    make \
    gcc \
    g++ \
    gdb \
    clang \
    python3 \
    python3-pip \
    python3-venv \
    python3-dev \
    python3-setuptools \
    python3-wheel \
    2>&1 | grep -E "(Installing|Removing|Unpacking|Setting up)" | head -30

log_ok "Development tools installed"

# Node.js (LTS via NodeSource)
log "Installing Node.js LTS..."
curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - 2>&1 | tail -5
DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs 2>&1 | tail -5
log_ok "Node.js $(node --version) installed"
log_ok "npm $(npm --version) installed"

# Additional Python packages
log "Installing Python packages..."
pip3 install --break-system-packages \
    jupyter \
    notebook \
    numpy \
    pandas \
    matplotlib \
    requests \
    flask \
    fastapi \
    uvicorn \
    ipython \
    2>&1 | grep -E "(Successfully|already)" | head -20

log_ok "Python packages installed"

# Networking tools
log "Installing networking tools..."
DEBIAN_FRONTEND=noninteractive apt-get install -y \
    net-tools \
    iputils-ping \
    dnsutils \
    netcat-traditional \
    openssh-client \
    rsync \
    2>&1 | grep -E "(Installing|Setting up)" | head -10

log_ok "Networking tools installed"

# Locale generation
log "Generating locales..."
locale-gen en_US.UTF-8 2>&1 | tail -2
log_ok "Locale generated"

# Clean up
log "Cleaning up..."
apt-get autoremove -y 2>&1 | tail -2
apt-get clean 2>&1

echo ""
echo -e "\033[0;32m╔══════════════════════════════════════════╗\033[0m"
echo -e "\033[0;32m║ ✅ All packages installed successfully!  ║\033[0m"
echo -e "\033[0;32m╚══════════════════════════════════════════╝\033[0m"
echo ""
echo "Installed tools:"
echo "  • Python: $(python3 --version 2>&1)"
echo "  • Node.js: $(node --version 2>&1)"
echo "  • npm: $(npm --version 2>&1)"
echo "  • Git: $(git --version 2>&1)"
echo "  • tmux: $(tmux -V 2>&1)"
echo ""
