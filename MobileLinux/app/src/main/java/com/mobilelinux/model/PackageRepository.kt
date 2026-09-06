package com.mobilelinux.model

object PackageRepository {

    fun getCuratedPackages(): List<LinuxPackage> = listOf(
        // ==========================================
        // 1. AI & Data Science
        // ==========================================
        LinuxPackage(
            id = "jupyterlab",
            name = "JupyterLab & Notebook",
            category = PackageCategory.DATA_SCIENCE,
            version = "Latest / Web IDE",
            description = "Interactive web-based notebooks, code cells, terminal, and visualization dashboard.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && if which conda >/dev/null 2>&1; then conda install -y jupyterlab notebook; elif which pip3 >/dev/null 2>&1; then pip3 install --no-cache-dir jupyterlab notebook; else sudo apt-get update -y && sudo apt-get install -y python3-pip && pip3 install --no-cache-dir jupyterlab notebook; fi",
            checkInstalledCommand = "which jupyter || [ -x /home/ubuntu/miniforge3/bin/jupyter ]",
            launchUrl = "http://127.0.0.1:8888/lab"
        ),
        LinuxPackage(
            id = "miniconda",
            name = "Miniconda3 / Conda",
            category = PackageCategory.DATA_SCIENCE,
            version = "Linux ARM64",
            description = "Lightweight installer for Conda, Python package and virtual environment manager.",
            installCommand = "curl -fsSL https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-aarch64.sh -o /tmp/miniconda.sh && bash /tmp/miniconda.sh -b -p /home/ubuntu/miniforge3 && echo 'export PATH=/home/ubuntu/miniforge3/bin:\$PATH' >> /home/ubuntu/.bashrc && /home/ubuntu/miniforge3/bin/conda init bash && rm -f /tmp/miniconda.sh",
            checkInstalledCommand = "which conda || [ -x /home/ubuntu/miniforge3/bin/conda ]"
        ),
        LinuxPackage(
            id = "numpy",
            name = "NumPy",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "The fundamental package for high-performance scientific computing and N-dimensional arrays.",
            installCommand = "if which conda >/dev/null 2>&1; then conda install -y numpy; else pip3 install --no-cache-dir numpy; fi",
            checkInstalledCommand = "python3 -c 'import numpy' 2>/dev/null"
        ),
        LinuxPackage(
            id = "pandas",
            name = "Pandas",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Powerful, flexible data analysis and manipulation library for structured datasets.",
            installCommand = "if which conda >/dev/null 2>&1; then conda install -y pandas; else pip3 install --no-cache-dir pandas; fi",
            checkInstalledCommand = "python3 -c 'import pandas' 2>/dev/null"
        ),
        LinuxPackage(
            id = "matplotlib-seaborn",
            name = "Matplotlib & Seaborn",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Comprehensive library for creating static, animated, and interactive data visualizations.",
            installCommand = "if which conda >/dev/null 2>&1; then conda install -y matplotlib seaborn; else pip3 install --no-cache-dir matplotlib seaborn; fi",
            checkInstalledCommand = "python3 -c 'import matplotlib; import seaborn' 2>/dev/null"
        ),
        LinuxPackage(
            id = "scikit-learn",
            name = "Scikit-Learn",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Simple and efficient tools for predictive data analysis, clustering, and machine learning.",
            installCommand = "if which conda >/dev/null 2>&1; then conda install -y scikit-learn; else pip3 install --no-cache-dir scikit-learn; fi",
            checkInstalledCommand = "python3 -c 'import sklearn' 2>/dev/null"
        ),
        LinuxPackage(
            id = "pytorch",
            name = "PyTorch (CPU)",
            category = PackageCategory.DATA_SCIENCE,
            version = "PyTorch 2.x",
            description = "Optimized deep learning tensor library and neural networks framework.",
            installCommand = "if which conda >/dev/null 2>&1; then conda install -y pytorch cpuonly -c pytorch; else pip3 install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu; fi",
            checkInstalledCommand = "python3 -c 'import torch' 2>/dev/null"
        ),
        LinuxPackage(
            id = "scipy",
            name = "SciPy",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Algorithms for scientific computing, optimization, linear algebra, and statistics.",
            installCommand = "if which conda >/dev/null 2>&1; then conda install -y scipy; else pip3 install --no-cache-dir scipy; fi",
            checkInstalledCommand = "python3 -c 'import scipy' 2>/dev/null"
        ),
        LinuxPackage(
            id = "opencv",
            name = "OpenCV (Headless)",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Real-time computer vision and image/video processing algorithms library.",
            installCommand = "if which conda >/dev/null 2>&1; then conda install -y opencv; else pip3 install --no-cache-dir opencv-python-headless; fi",
            checkInstalledCommand = "python3 -c 'import cv2' 2>/dev/null"
        ),

        // ==========================================
        // 2. Languages & Runtimes
        // ==========================================
        LinuxPackage(
            id = "python3-pip",
            name = "Python 3 & Pip",
            category = PackageCategory.RUNTIMES,
            version = "3.12 Standard",
            description = "Full Python 3 runtime with Pip package manager, setuptools, and wheel.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y python3 python3-pip python3-venv python3-dev",
            checkInstalledCommand = "which python3 && which pip3"
        ),
        LinuxPackage(
            id = "nodejs-npm",
            name = "Node.js & npm",
            category = PackageCategory.RUNTIMES,
            version = "JavaScript / LTS",
            description = "JavaScript runtime environment with npm package manager for full-stack apps.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y nodejs npm",
            checkInstalledCommand = "which node && which npm"
        ),
        LinuxPackage(
            id = "build-essential",
            name = "C / C++ Compiler (GCC/G++)",
            category = PackageCategory.RUNTIMES,
            version = "GCC 14 / Make",
            description = "Complete build-essential toolchain: gcc, g++, make, libc-dev, and CMake.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y build-essential cmake gdb",
            checkInstalledCommand = "which gcc && which g++ && which make"
        ),
        LinuxPackage(
            id = "openjdk-21",
            name = "Java JDK (OpenJDK 21)",
            category = PackageCategory.RUNTIMES,
            version = "Java 21 LTS",
            description = "Modern Java Development Kit and runtime environment for Android/Java applications.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y openjdk-21-jdk-headless",
            checkInstalledCommand = "which java && which javac"
        ),
        LinuxPackage(
            id = "rust-cargo",
            name = "Rust & Cargo",
            category = PackageCategory.RUNTIMES,
            version = "Rust Toolchain",
            description = "Fast, memory-safe systems programming language with Cargo build system.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y rustc cargo",
            checkInstalledCommand = "which rustc && which cargo"
        ),
        LinuxPackage(
            id = "golang",
            name = "Go (Golang)",
            category = PackageCategory.RUNTIMES,
            version = "Go 1.22+",
            description = "Open source programming language designed for fast, reliable, and simple software.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y golang-go",
            checkInstalledCommand = "which go"
        ),
        LinuxPackage(
            id = "clang-llvm",
            name = "Clang & LLVM",
            category = PackageCategory.RUNTIMES,
            version = "LLVM Compiler",
            description = "C language family frontend for LLVM with modern diagnostics and tooling.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y clang llvm",
            checkInstalledCommand = "which clang"
        ),
        LinuxPackage(
            id = "php",
            name = "PHP CLI",
            category = PackageCategory.RUNTIMES,
            version = "PHP 8.x",
            description = "Popular general-purpose scripting language suited for web backend development.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y php-cli php-common php-mbstring",
            checkInstalledCommand = "which php"
        ),
        LinuxPackage(
            id = "ruby",
            name = "Ruby",
            category = PackageCategory.RUNTIMES,
            version = "Ruby 3.x",
            description = "Dynamic, open source programming language with a focus on simplicity and productivity.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y ruby ruby-dev",
            checkInstalledCommand = "which ruby"
        ),

        // ==========================================
        // 3. Developer & CLI Tools
        // ==========================================
        LinuxPackage(
            id = "git",
            name = "Git Version Control",
            category = PackageCategory.DEV_TOOLS,
            version = "Git 2.43+",
            description = "Fast, scalable, distributed revision control system with rich command sets.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y git",
            checkInstalledCommand = "which git"
        ),
        LinuxPackage(
            id = "github-cli",
            name = "GitHub CLI (gh)",
            category = PackageCategory.DEV_TOOLS,
            version = "Official CLI",
            description = "Work with GitHub issues, pull requests, checks, and repositories directly from terminal.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y gh",
            checkInstalledCommand = "which gh"
        ),
        LinuxPackage(
            id = "neovim",
            name = "Neovim",
            category = PackageCategory.DEV_TOOLS,
            version = "Vim-fork",
            description = "Hyperextensible Vim-based text editor with Lua plugin architecture and LSP support.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y neovim",
            checkInstalledCommand = "which nvim"
        ),
        LinuxPackage(
            id = "tmux",
            name = "Tmux",
            category = PackageCategory.DEV_TOOLS,
            version = "Multiplexer",
            description = "Terminal multiplexer to switch easily between several programs in one terminal and detach.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y tmux",
            checkInstalledCommand = "which tmux"
        ),
        LinuxPackage(
            id = "zsh",
            name = "Zsh Shell",
            category = PackageCategory.DEV_TOOLS,
            version = "Z Shell",
            description = "Advanced command interpreter with programmable command-line completion and themes.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y zsh",
            checkInstalledCommand = "which zsh"
        ),
        LinuxPackage(
            id = "htop",
            name = "Htop Process Viewer",
            category = PackageCategory.DEV_TOOLS,
            version = "Interactive",
            description = "Interactive system-monitor process viewer and process manager for Linux.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y htop",
            checkInstalledCommand = "which htop"
        ),
        LinuxPackage(
            id = "fzf",
            name = "FZF Fuzzy Finder",
            category = PackageCategory.DEV_TOOLS,
            version = "Command-line",
            description = "General-purpose command-line fuzzy finder for files, history, and processes.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y fzf",
            checkInstalledCommand = "which fzf"
        ),
        LinuxPackage(
            id = "tree",
            name = "Tree",
            category = PackageCategory.DEV_TOOLS,
            version = "Visualizer",
            description = "Recursive directory listing program that displays a depth indented listing of files.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y tree",
            checkInstalledCommand = "which tree"
        ),
        LinuxPackage(
            id = "curl-wget",
            name = "cURL & Wget",
            category = PackageCategory.DEV_TOOLS,
            version = "Networking",
            description = "Command line tools and library for transferring data with URLs and HTTP/FTP downloads.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y curl wget",
            checkInstalledCommand = "which curl && which wget"
        ),

        // ==========================================
        // 4. Databases & Web
        // ==========================================
        LinuxPackage(
            id = "sqlite3",
            name = "SQLite 3",
            category = PackageCategory.DATABASES,
            version = "Embedded SQL",
            description = "Self-contained, serverless, zero-configuration, transactional SQL database engine.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y sqlite3 libsqlite3-dev",
            checkInstalledCommand = "which sqlite3"
        ),
        LinuxPackage(
            id = "postgresql-client",
            name = "PostgreSQL Client",
            category = PackageCategory.DATABASES,
            version = "psql CLI",
            description = "Command-line client tools (psql, pg_dump) to connect and manage PostgreSQL databases.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y postgresql-client",
            checkInstalledCommand = "which psql"
        ),
        LinuxPackage(
            id = "nginx",
            name = "Nginx Web Server",
            category = PackageCategory.DATABASES,
            version = "HTTP & Reverse Proxy",
            description = "High-performance HTTP server, reverse proxy, and load balancer.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y nginx",
            checkInstalledCommand = "which nginx"
        ),

        // ==========================================
        // 5. Media & Utilities
        // ==========================================
        LinuxPackage(
            id = "ffmpeg",
            name = "FFmpeg",
            category = PackageCategory.UTILITIES,
            version = "Audio/Video",
            description = "Complete, cross-platform solution to record, convert, and stream audio and video.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y ffmpeg",
            checkInstalledCommand = "which ffmpeg"
        ),
        LinuxPackage(
            id = "imagemagick",
            name = "ImageMagick",
            category = PackageCategory.UTILITIES,
            version = "Image CLI",
            description = "Software suite to create, edit, compose, or convert bitmap images.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y imagemagick",
            checkInstalledCommand = "which convert || which magick"
        ),
        LinuxPackage(
            id = "pandoc",
            name = "Pandoc",
            category = PackageCategory.UTILITIES,
            version = "Document Converter",
            description = "Universal markup converter between Markdown, HTML, LaTeX, PDF, and EPUB.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y pandoc",
            checkInstalledCommand = "which pandoc"
        ),
        LinuxPackage(
            id = "speedtest-cli",
            name = "Speedtest CLI",
            category = PackageCategory.UTILITIES,
            version = "Network Tester",
            description = "Command line interface for testing internet bandwidth using speedtest.net.",
            installCommand = "export DEBIAN_FRONTEND=noninteractive && sudo apt-get update -y && sudo apt-get install -y speedtest-cli",
            checkInstalledCommand = "which speedtest-cli || which speedtest"
        )
    )
}
