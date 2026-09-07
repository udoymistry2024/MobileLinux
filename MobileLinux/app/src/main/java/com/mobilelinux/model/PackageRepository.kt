package com.mobilelinux.model

/**
 * Curated Repository of 370+ pre-configured libraries, developer tools,
 * cybersecurity utilities, data science engines, languages, and system tools
 * tested and optimized for ARM64 Ubuntu environment.
 */
object PackageRepository {

    fun getCuratedPackages(): List<LinuxPackage> {
        return cyberSecurityPackages +
               dataSciencePackages +
               runtimesPackages +
               devToolsPackages +
               databasesPackages +
               utilitiesPackages
    }

    /**
     * Ultra-fast batch check script that executes inside Ubuntu in ~200ms
     * checking all packages and returning INSTALLED:<id> lines.
     */
    fun getFastBatchCheckScript(): String {
        val d = '$'
        val sb = java.lang.StringBuilder()
        sb.append("export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:\$PATH\"\n")
        sb.append("ALL_PY=\"").append(d).append("(python3 -c \"import sys\n")
        sb.append("for m in ['numpy','pandas','scipy','torch','torchvision','torchaudio','tflite_runtime','onnxruntime','matplotlib','seaborn','plotly','bokeh','altair','cv2','PIL','skimage','nltk','spacy','transformers','tokenizers','datasets','gensim','networkx','sympy','statsmodels','xgboost','lightgbm','catboost','polars','dask','pyarrow','fastapi','uvicorn','streamlit','gradio','tqdm','joblib','h5py','zarr','librosa','soundfile','bs4','scrapy','selenium','playwright','requests','httpx','aiohttp','flask','django','sqlalchemy','alembic','psycopg2','pymysql','redis','celery','pydantic','pytest','hypothesis','locust','impacket','scapy','sherlock','chatdev','qwen_agent']:\n")
        sb.append("    try:\n")
        sb.append("        __import__(m)\n")
        sb.append("        print('PY:' + m)\n")
        sb.append("    except Exception:\n")
        sb.append("        pass\n")
        sb.append("\" 2>/dev/null)\"\n")
        sb.append("if [ -x /home/ubuntu/miniforge3/bin/python ]; then\n")
        sb.append("    ALL_PY=\"").append(d).append("ALL_PY ").append(d).append("(/home/ubuntu/miniforge3/bin/python -c \"import sys\n")
        sb.append("for m in ['numpy','pandas','scipy','torch','torchvision','torchaudio','tflite_runtime','onnxruntime','matplotlib','seaborn','plotly','bokeh','altair','cv2','PIL','skimage','nltk','spacy','transformers','tokenizers','datasets','gensim','networkx','sympy','statsmodels','xgboost','lightgbm','catboost','polars','dask','pyarrow','fastapi','uvicorn','streamlit','gradio','tqdm','joblib','h5py','zarr','librosa','soundfile','bs4','scrapy','selenium','playwright','requests','httpx','aiohttp','flask','django','sqlalchemy','alembic','psycopg2','pymysql','redis','celery','pydantic','pytest','hypothesis','locust','impacket','scapy','sherlock']:\n")
        sb.append("    try:\n")
        sb.append("        __import__(m)\n")
        sb.append("        print('PY:' + m)\n")
        sb.append("    except Exception:\n")
        sb.append("        pass\n")
        sb.append("\" 2>/dev/null)\"\n")
        sb.append("fi\n")
        sb.append("fast_check() {\n")
        sb.append("    local cmd=\"").append(d).append("1\"\n")
        sb.append("    if [[ \"").append(d).append("cmd\" == *\"import \"* ]]; then\n")
        sb.append("        local mod\n")
        sb.append("        mod=\"").append(d).append("(echo \"").append(d).append("cmd\" | sed -n 's/.*import \\([a-zA-Z0-9_]*\\).*/\\1/p')\"\n")
        sb.append("        if [ -n \"").append(d).append("mod\" ] && [[ \"").append(d).append("ALL_PY\" == *\"PY:").append(d).append("mod\"* ]]; then\n")
        sb.append("            return 0\n")
        sb.append("        fi\n")
        sb.append("    fi\n")
        sb.append("    local fast_cmd=\"").append(d).append("{cmd//which /type -P }\"\n")
        sb.append("    eval \"").append(d).append("fast_cmd\" >/dev/null 2>&1\n")
        sb.append("}\n")
        sb.append("while IFS=: read -r id cmd; do\n")
        sb.append("    if [ -n \"").append(d).append("id\" ] && fast_check \"").append(d).append("cmd\"; then\n")
        sb.append("        echo \"INSTALLED:").append(d).append("id\"\n")
        sb.append("    fi\n")
        sb.append("done << 'BATCH_EOF'\n")
        for (pkg in getCuratedPackages()) {
            val cleanCmd = pkg.checkInstalledCommand.replace('\n', ' ').trim()
            sb.append(pkg.id).append(':').append(cleanCmd).append('\n')
        }
        sb.append("BATCH_EOF\n")
        return sb.toString()
    }

    /**
     * Generates a thorough, clean, and permanent uninstall command for a package.
     * Purges APT packages, uninstalls pip/conda modules, removes NPM global binaries,
     * and deletes leftover binaries and caches to avoid any future conflicts.
     */
    fun getUninstallCommand(pkg: LinuxPackage): String {
        // 1. If an explicit uninstall command is provided, use it
        if (!pkg.uninstallCommand.isNullOrBlank()) {
            return pkg.uninstallCommand
        }

        // 2. Specialized packages handling
        when (pkg.id) {
            "miniconda" -> {
                return "conda deactivate 2>/dev/null || true; " +
                        "rm -rf /home/ubuntu/miniforge3 /home/ubuntu/miniconda3 /root/miniconda3 /root/miniforge3 /home/ubuntu/.conda /root/.conda /opt/conda 2>/dev/null || true; " +
                        "sed -i '/miniforge3/d; /miniconda3/d; /conda/d' /home/ubuntu/.bashrc /root/.bashrc /etc/bash.bashrc 2>/dev/null || true; " +
                        "rm -f /usr/local/bin/conda /usr/local/bin/mamba 2>/dev/null || true"
            }
            "antigravity-cli" -> {
                return "rm -rf /home/ubuntu/.antigravity /root/.antigravity /home/ubuntu/.gemini /root/.gemini 2>/dev/null || true; " +
                        "rm -f /home/ubuntu/.local/bin/agy /root/.local/bin/agy /usr/local/bin/agy /usr/bin/agy 2>/dev/null || true"
            }
            "gemini-cli" -> {
                return "sudo npm uninstall -g @google/gemini-cli gemini-cli 2>/dev/null || true; " +
                        "pip uninstall -y gemini-cli 2>/dev/null || true; " +
                        "/home/ubuntu/miniforge3/bin/pip uninstall -y gemini-cli 2>/dev/null || true; " +
                        "rm -f /usr/local/bin/gemini /usr/bin/gemini /home/ubuntu/.local/bin/gemini /root/.local/bin/gemini 2>/dev/null || true"
            }
            "claude-code" -> {
                return "sudo npm uninstall -g @anthropic-ai/claude-code claude-code 2>/dev/null || true; " +
                        "rm -f /usr/local/bin/claude /usr/bin/claude /home/ubuntu/.local/bin/claude /root/.local/bin/claude 2>/dev/null || true"
            }
            "google-cloud-sdk" -> {
                return "sudo apt-get purge -y google-cloud-cli 2>/dev/null || true; " +
                        "rm -rf /home/ubuntu/google-cloud-sdk /root/google-cloud-sdk 2>/dev/null || true; " +
                        "rm -f /etc/apt/sources.list.d/google-cloud-sdk.list /usr/local/bin/gcloud /usr/bin/gcloud 2>/dev/null || true"
            }
            "aider-chat" -> {
                return "pip uninstall -y aider-chat 2>/dev/null || true; " +
                        "/home/ubuntu/miniforge3/bin/pip uninstall -y aider-chat 2>/dev/null || true; " +
                        "rm -f /home/ubuntu/.local/bin/aider /home/ubuntu/miniforge3/bin/aider /root/.local/bin/aider /usr/local/bin/aider 2>/dev/null || true"
            }
            "open-interpreter" -> {
                return "pip uninstall -y open-interpreter 2>/dev/null || true; " +
                        "/home/ubuntu/miniforge3/bin/pip uninstall -y open-interpreter 2>/dev/null || true; " +
                        "rm -f /home/ubuntu/.local/bin/interpreter /home/ubuntu/miniforge3/bin/interpreter /root/.local/bin/interpreter /usr/local/bin/interpreter 2>/dev/null || true"
            }
            "chatdev" -> {
                return "pip uninstall -y chatdev 2>/dev/null || true; " +
                        "/home/ubuntu/miniforge3/bin/pip uninstall -y chatdev 2>/dev/null || true; " +
                        "rm -f /home/ubuntu/.local/bin/chatdev /root/.local/bin/chatdev 2>/dev/null || true"
            }
            "qwen-agent" -> {
                return "pip uninstall -y qwen-agent 2>/dev/null || true; " +
                        "/home/ubuntu/miniforge3/bin/pip uninstall -y qwen-agent 2>/dev/null || true"
            }
            "nuclei" -> {
                return "sudo apt-get purge -y nuclei 2>/dev/null || true; " +
                        "rm -f /home/ubuntu/go/bin/nuclei /root/go/bin/nuclei /usr/local/bin/nuclei /usr/bin/nuclei 2>/dev/null || true"
            }
        }

        // 3. Python modules with pkg-install-python
        if (pkg.installCommand.contains("pkg-install-python")) {
            val parts = pkg.installCommand.substringAfter("pkg-install-python").trim().split(" ")
            val pipName = parts.getOrNull(0) ?: pkg.id
            val aptName = parts.getOrNull(1) ?: "python3-$pipName"
            return "pip uninstall -y $pipName 2>/dev/null || true; " +
                    "/home/ubuntu/miniforge3/bin/pip uninstall -y $pipName 2>/dev/null || true; " +
                    "sudo apt-get purge -y $aptName 2>/dev/null || true; " +
                    "sudo apt-get autoremove -y 2>/dev/null || true"
        }

        // 4. Standard APT packages
        if (pkg.installCommand.contains("apt-get install -y")) {
            val raw = pkg.installCommand.substringAfter("apt-get install -y").trim()
            val cleanApt = raw.substringBefore(" ").substringBefore("||").substringBefore("&&").trim()
            val targetPkg = if (cleanApt.isNotBlank()) cleanApt else pkg.id
            return "sudo apt-get purge -y $targetPkg && sudo apt-get autoremove -y && sudo apt-get clean"
        }

        // 5. General pip packages
        if (pkg.installCommand.contains("pip install") || pkg.installCommand.contains("pip3 install")) {
            return "pip uninstall -y ${pkg.id} 2>/dev/null || true; " +
                    "/home/ubuntu/miniforge3/bin/pip uninstall -y ${pkg.id} 2>/dev/null || true; " +
                    "sudo apt-get purge -y python3-${pkg.id} 2>/dev/null || true; " +
                    "sudo apt-get autoremove -y 2>/dev/null || true"
        }

        // Default fallback: purge by id and autoremove
        return "sudo apt-get purge -y ${pkg.id} 2>/dev/null || true; sudo apt-get autoremove -y 2>/dev/null || true"
    }

    private val cyberSecurityPackages: List<LinuxPackage> by lazy {
        listOf(
        LinuxPackage(
            id = "nmap",
            name = "Nmap Network Scanner",
            category = PackageCategory.CYBER_SECURITY,
            version = "7.9x",
            description = "Industry-standard network exploration tool and security/port scanner.",
            installCommand = "sudo apt-get install -y nmap || ((sudo apt-get update || true) && sudo apt-get install -y nmap)",
            checkInstalledCommand = "which nmap",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sqlmap",
            name = "SQLmap",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Automatic SQL injection and database takeover penetration testing tool.",
            installCommand = "sudo apt-get install -y sqlmap || ((sudo apt-get update || true) && sudo apt-get install -y sqlmap)",
            checkInstalledCommand = "which sqlmap",
            launchUrl = null
        ),
        LinuxPackage(
            id = "hydra",
            name = "THC Hydra",
            category = PackageCategory.CYBER_SECURITY,
            version = "9.x",
            description = "Very fast network logon cracker supporting numerous protocols (SSH, FTP, HTTP, etc.).",
            installCommand = "sudo apt-get install -y hydra || ((sudo apt-get update || true) && sudo apt-get install -y hydra)",
            checkInstalledCommand = "which hydra",
            launchUrl = null
        ),
        LinuxPackage(
            id = "john",
            name = "John the Ripper",
            category = PackageCategory.CYBER_SECURITY,
            version = "Community",
            description = "Fast and flexible password security auditing and password recovery tool.",
            installCommand = "sudo apt-get install -y john || ((sudo apt-get update || true) && sudo apt-get install -y john)",
            checkInstalledCommand = "which john",
            launchUrl = null
        ),
        LinuxPackage(
            id = "aircrack-ng",
            name = "Aircrack-ng Suite",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.7+",
            description = "Complete suite of tools to assess Wi-Fi 802.11 network security and capture packets.",
            installCommand = "sudo apt-get install -y aircrack-ng || ((sudo apt-get update || true) && sudo apt-get install -y aircrack-ng)",
            checkInstalledCommand = "which aircrack-ng",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tshark",
            name = "TShark (Wireshark CLI)",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Powerful terminal-based network protocol analyzer and packet capture dump tool.",
            installCommand = "DEBIAN_FRONTEND=noninteractive sudo apt-get install -y tshark || ((sudo apt-get update || true) && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y tshark)",
            checkInstalledCommand = "which tshark",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tcpdump",
            name = "Tcpdump",
            category = PackageCategory.CYBER_SECURITY,
            version = "4.99+",
            description = "Command-line packet analyzer for network troubleshooting and security audit.",
            installCommand = "sudo apt-get install -y tcpdump || ((sudo apt-get update || true) && sudo apt-get install -y tcpdump)",
            checkInstalledCommand = "which tcpdump",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nikto",
            name = "Nikto Web Scanner",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.1.6+",
            description = "Comprehensive web server scanner for dangerous files, outdated server software, and CGIs.",
            installCommand = "sudo apt-get install -y nikto || ((sudo apt-get update || true) && sudo apt-get install -y nikto)",
            checkInstalledCommand = "which nikto",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gobuster",
            name = "Gobuster",
            category = PackageCategory.CYBER_SECURITY,
            version = "3.x",
            description = "Fast directory/file, DNS, and VHost brute-forcing tool written in Go.",
            installCommand = "sudo apt-get install -y gobuster || ((sudo apt-get update || true) && sudo apt-get install -y gobuster)",
            checkInstalledCommand = "which gobuster",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dirb",
            name = "DIRB Web Content Scanner",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.22",
            description = "Web content scanner that looks for existing (and/or hidden) Web Objects.",
            installCommand = "sudo apt-get install -y dirb || ((sudo apt-get update || true) && sudo apt-get install -y dirb)",
            checkInstalledCommand = "which dirb",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ffuf",
            name = "FFUF (Fast Web Fuzzer)",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Extremely fast web fuzzer written in Go for discovering endpoints, parameters, and headers.",
            installCommand = "sudo apt-get install -y ffuf || ((sudo apt-get update || true) && sudo apt-get install -y ffuf)",
            checkInstalledCommand = "which ffuf",
            launchUrl = null
        ),
        LinuxPackage(
            id = "wfuzz",
            name = "Wfuzz Web Application Fuzzer",
            category = PackageCategory.CYBER_SECURITY,
            version = "3.x",
            description = "Web application security fuzzer allowing complex injection and parameter fuzzing.",
            installCommand = "sudo apt-get install -y wfuzz || ((sudo apt-get update || true) && sudo apt-get install -y wfuzz)",
            checkInstalledCommand = "which wfuzz",
            launchUrl = null
        ),
        LinuxPackage(
            id = "subfinder",
            name = "Subfinder",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Fast passive subdomain discovery tool that enumerates valid subdomains for websites.",
            installCommand = "sudo apt-get install -y subfinder || ((sudo apt-get update || true) && sudo apt-get install -y subfinder)",
            checkInstalledCommand = "which subfinder",
            launchUrl = null
        ),
        LinuxPackage(
            id = "httpx-pd",
            name = "HTTPx Toolkit",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Fast and multi-purpose HTTP toolkit allowing multiple probes using retryablehttp library.",
            installCommand = "sudo apt-get install -y httpx || ((sudo apt-get update || true) && sudo apt-get install -y httpx)",
            checkInstalledCommand = "which httpx",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nuclei",
            name = "Nuclei Vulnerability Scanner",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Fast, customizable vulnerability scanner based on simple YAML templates and automated reconnaissance.",
            installCommand = "sudo apt-get install -y nuclei || ((sudo apt-get update || true) && sudo apt-get install -y nuclei) || ((sudo apt-get install -y golang-go || ((sudo apt-get update || true) && sudo apt-get install -y golang-go)) && export PATH=\"/home/ubuntu/go/bin:\$PATH\" && go install -v github.com/projectdiscovery/nuclei/v3/cmd/nuclei@latest)",
            checkInstalledCommand = "which nuclei || test -f /home/ubuntu/go/bin/nuclei || test -f /root/go/bin/nuclei",
            launchUrl = null
        ),
        LinuxPackage(
            id = "amass",
            name = "OWASP Amass",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "In-depth attack surface mapping and external asset discovery using open source information.",
            installCommand = "sudo apt-get install -y amass || ((sudo apt-get update || true) && sudo apt-get install -y amass)",
            checkInstalledCommand = "which amass",
            launchUrl = null
        ),
        LinuxPackage(
            id = "wpscan",
            name = "WPScan WordPress Scanner",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Black box WordPress security scanner written in Ruby to find known plugin/theme vulnerabilities.",
            installCommand = "sudo apt-get install -y wpscan || ((sudo apt-get update || true) && sudo apt-get install -y wpscan)",
            checkInstalledCommand = "which wpscan",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sherlock",
            name = "Sherlock OSINT",
            category = PackageCategory.CYBER_SECURITY,
            version = "Python 3",
            description = "Hunt down social media accounts by username across 300+ social networks.",
            installCommand = "(sudo apt-get install -y sherlock || ((sudo apt-get update || true) && sudo apt-get install -y sherlock)) || pip3 install --break-system-packages --no-cache-dir sherlock-project",
            checkInstalledCommand = "which sherlock || python3 -c 'import sherlock' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "radare2",
            name = "Radare2 Reverse Engineering",
            category = PackageCategory.CYBER_SECURITY,
            version = "5.x",
            description = "Powerful Unix-like reverse engineering framework and command-line binary disassembler.",
            installCommand = "sudo apt-get install -y radare2 || ((sudo apt-get update || true) && sudo apt-get install -y radare2)",
            checkInstalledCommand = "which r2 || which radare2",
            launchUrl = null
        ),
        LinuxPackage(
            id = "hashcat",
            name = "Hashcat Password Recovery",
            category = PackageCategory.CYBER_SECURITY,
            version = "6.x",
            description = "World's fastest and most advanced password recovery utility (CPU engine mode).",
            installCommand = "sudo apt-get install -y hashcat || ((sudo apt-get update || true) && sudo apt-get install -y hashcat)",
            checkInstalledCommand = "which hashcat",
            launchUrl = null
        ),
        LinuxPackage(
            id = "netcat",
            name = "Netcat (nc)",
            category = PackageCategory.CYBER_SECURITY,
            version = "OpenBSD",
            description = "The TCP/IP Swiss army knife for reading and writing network connections.",
            installCommand = "sudo apt-get install -y netcat-openbsd || ((sudo apt-get update || true) && sudo apt-get install -y netcat-openbsd)",
            checkInstalledCommand = "which nc || which netcat",
            launchUrl = null
        ),
        LinuxPackage(
            id = "socat",
            name = "Socat Relay",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.7+",
            description = "Multipurpose relay tool for bidirectional data transfers between two independent data channels.",
            installCommand = "sudo apt-get install -y socat || ((sudo apt-get update || true) && sudo apt-get install -y socat)",
            checkInstalledCommand = "which socat",
            launchUrl = null
        ),
        LinuxPackage(
            id = "masscan",
            name = "Masscan",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.3+",
            description = "TCP port scanner capable of scanning the entire Internet in under 6 minutes.",
            installCommand = "sudo apt-get install -y masscan || ((sudo apt-get update || true) && sudo apt-get install -y masscan)",
            checkInstalledCommand = "which masscan",
            launchUrl = null
        ),
        LinuxPackage(
            id = "whatweb",
            name = "WhatWeb",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.5.5+",
            description = "Next generation web scanner that recognizes technologies, CMS, blogging platforms, and scripts.",
            installCommand = "sudo apt-get install -y whatweb || ((sudo apt-get update || true) && sudo apt-get install -y whatweb)",
            checkInstalledCommand = "which whatweb",
            launchUrl = null
        ),
        LinuxPackage(
            id = "commix",
            name = "Commix Command Injection",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Automated tool for detecting and exploiting command injection security vulnerabilities.",
            installCommand = "sudo apt-get install -y commix || ((sudo apt-get update || true) && sudo apt-get install -y commix)",
            checkInstalledCommand = "which commix",
            launchUrl = null
        ),
        LinuxPackage(
            id = "binwalk",
            name = "Binwalk Firmware Analysis",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.3+",
            description = "Fast, easy to use tool for analyzing, reverse engineering, and extracting firmware images.",
            installCommand = "sudo apt-get install -y binwalk || ((sudo apt-get update || true) && sudo apt-get install -y binwalk)",
            checkInstalledCommand = "which binwalk",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gdb",
            name = "GDB GNU Debugger",
            category = PackageCategory.CYBER_SECURITY,
            version = "GNU 14+",
            description = "The GNU Project Debugger for inspecting binary execution, memory, registers, and crashes.",
            installCommand = "sudo apt-get install -y gdb || ((sudo apt-get update || true) && sudo apt-get install -y gdb)",
            checkInstalledCommand = "which gdb",
            launchUrl = null
        ),
        LinuxPackage(
            id = "strace",
            name = "Strace Syscall Monitor",
            category = PackageCategory.CYBER_SECURITY,
            version = "6.x",
            description = "Diagnostic, debugging and instructional userspace utility to trace system calls and signals.",
            installCommand = "sudo apt-get install -y strace || ((sudo apt-get update || true) && sudo apt-get install -y strace)",
            checkInstalledCommand = "which strace",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ltrace",
            name = "Ltrace Library Call Tracer",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.7+",
            description = "Tracks dynamic library calls in executed programs without source code recompilation.",
            installCommand = "sudo apt-get install -y ltrace || ((sudo apt-get update || true) && sudo apt-get install -y ltrace)",
            checkInstalledCommand = "which ltrace",
            launchUrl = null
        ),
        LinuxPackage(
            id = "exiftool",
            name = "ExifTool",
            category = PackageCategory.CYBER_SECURITY,
            version = "12.x",
            description = "Read, write and manipulate image, audio, video and PDF metadata for digital forensics.",
            installCommand = "sudo apt-get install -y libimage-exiftool-perl || ((sudo apt-get update || true) && sudo apt-get install -y libimage-exiftool-perl)",
            checkInstalledCommand = "which exiftool",
            launchUrl = null
        ),
        LinuxPackage(
            id = "steghide",
            name = "Steghide",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.5+",
            description = "Steganography program that hides secret data in various kinds of image and audio files.",
            installCommand = "sudo apt-get install -y steghide || ((sudo apt-get update || true) && sudo apt-get install -y steghide)",
            checkInstalledCommand = "which steghide",
            launchUrl = null
        ),
        LinuxPackage(
            id = "outguess",
            name = "Outguess Stego Tool",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.2+",
            description = "Universal steganographic tool that allows the insertion of hidden information into redundant bits.",
            installCommand = "sudo apt-get install -y outguess || ((sudo apt-get update || true) && sudo apt-get install -y outguess)",
            checkInstalledCommand = "which outguess",
            launchUrl = null
        ),
        LinuxPackage(
            id = "zsteg",
            name = "Zsteg PNG/BMP Steg Scanner",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Detect stegano-hidden data in PNG & BMP files automatically.",
            installCommand = "(sudo apt-get install -y ruby-full || ((sudo apt-get update || true) && sudo apt-get install -y ruby-full)) && gem install zsteg",
            checkInstalledCommand = "which zsteg",
            launchUrl = null
        ),
        LinuxPackage(
            id = "foremost",
            name = "Foremost File Carver",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.5.7",
            description = "Forensic data recovery program that carves files based on headers, footers, and data structures.",
            installCommand = "sudo apt-get install -y foremost || ((sudo apt-get update || true) && sudo apt-get install -y foremost)",
            checkInstalledCommand = "which foremost",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scalpel",
            name = "Scalpel File Carver",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.60",
            description = "High performance file carver that reads a database of header and footer definitions.",
            installCommand = "sudo apt-get install -y scalpel || ((sudo apt-get update || true) && sudo apt-get install -y scalpel)",
            checkInstalledCommand = "which scalpel",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sleuthkit",
            name = "The Sleuth Kit (TSK)",
            category = PackageCategory.CYBER_SECURITY,
            version = "4.x",
            description = "Collection of command line tools that allow you to investigate disk images and file systems.",
            installCommand = "sudo apt-get install -y sleuthkit || ((sudo apt-get update || true) && sudo apt-get install -y sleuthkit)",
            checkInstalledCommand = "which fls || which mmls",
            launchUrl = null
        ),
        LinuxPackage(
            id = "testdisk",
            name = "TestDisk & PhotoRec",
            category = PackageCategory.CYBER_SECURITY,
            version = "7.x",
            description = "Powerful data recovery utility designed to recover lost partitions and deleted photos/files.",
            installCommand = "sudo apt-get install -y testdisk || ((sudo apt-get update || true) && sudo apt-get install -y testdisk)",
            checkInstalledCommand = "which testdisk || which photorec",
            launchUrl = null
        ),
        LinuxPackage(
            id = "macchanger",
            name = "MAC Changer",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.7+",
            description = "Utility for viewing and manipulating MAC addresses of network network interfaces.",
            installCommand = "sudo apt-get install -y macchanger || ((sudo apt-get update || true) && sudo apt-get install -y macchanger)",
            checkInstalledCommand = "which macchanger",
            launchUrl = null
        ),
        LinuxPackage(
            id = "arping",
            name = "Arping",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.x",
            description = "Ping destination on device using ARP packets for local subnet discovery.",
            installCommand = "sudo apt-get install -y arping || ((sudo apt-get update || true) && sudo apt-get install -y arping)",
            checkInstalledCommand = "which arping",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fping",
            name = "Fping Multihost Ping",
            category = PackageCategory.CYBER_SECURITY,
            version = "5.x",
            description = "High performance ping program to send ICMP echo probes to any number of network hosts.",
            installCommand = "sudo apt-get install -y fping || ((sudo apt-get update || true) && sudo apt-get install -y fping)",
            checkInstalledCommand = "which fping",
            launchUrl = null
        ),
        LinuxPackage(
            id = "hping3",
            name = "Hping3 Network Packet Crafter",
            category = PackageCategory.CYBER_SECURITY,
            version = "3.x",
            description = "Network tool able to send custom TCP/IP packets and display target replies for firewall testing.",
            installCommand = "sudo apt-get install -y hping3 || ((sudo apt-get update || true) && sudo apt-get install -y hping3)",
            checkInstalledCommand = "which hping3",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scapy",
            name = "Scapy Packet Crafting",
            category = PackageCategory.CYBER_SECURITY,
            version = "Python 3",
            description = "Powerful interactive packet manipulation library and tool to forge or decode network packets.",
            installCommand = "(sudo apt-get install -y python3-scapy || ((sudo apt-get update || true) && sudo apt-get install -y python3-scapy)) || pip3 install --break-system-packages --no-cache-dir scapy",
            checkInstalledCommand = "which scapy || python3 -c 'import scapy' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "impacket",
            name = "Impacket Security Library",
            category = PackageCategory.CYBER_SECURITY,
            version = "Python 3",
            description = "Collection of Python classes for working with network protocols (SMB, MSRPC, Kerberos, etc.).",
            installCommand = "(sudo apt-get install -y python3-impacket || ((sudo apt-get update || true) && sudo apt-get install -y python3-impacket)) || pip3 install --break-system-packages --no-cache-dir impacket",
            checkInstalledCommand = "python3 -c 'import impacket' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "responder",
            name = "Responder LLMNR Poisoner",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "LLMNR, NBT-NS and MDNS poisoner with built-in rogue authentication servers.",
            installCommand = "sudo apt-get install -y responder || ((sudo apt-get update || true) && sudo apt-get install -y responder)",
            checkInstalledCommand = "which responder",
            launchUrl = null
        ),
        LinuxPackage(
            id = "crackmapexec",
            name = "CrackMapExec",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Swiss army knife for pentesting networks and Active Directory environments.",
            installCommand = "(sudo apt-get install -y crackmapexec || ((sudo apt-get update || true) && sudo apt-get install -y crackmapexec)) || pip3 install --break-system-packages --no-cache-dir crackmapexec",
            checkInstalledCommand = "which crackmapexec || which cme",
            launchUrl = null
        ),
        LinuxPackage(
            id = "evil-winrm",
            name = "Evil-WinRM",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "The ultimate WinRM shell for hacking and pentesting Windows systems from Linux.",
            installCommand = "gem install evil-winrm",
            checkInstalledCommand = "which evil-winrm",
            launchUrl = null
        ),
        LinuxPackage(
            id = "proxychains4",
            name = "Proxychains-NG",
            category = PackageCategory.CYBER_SECURITY,
            version = "4.x",
            description = "Hooks network-related libc functions in dynamically linked programs to redirect via SOCKS/HTTP proxies.",
            installCommand = "sudo apt-get install -y proxychains4 || ((sudo apt-get update || true) && sudo apt-get install -y proxychains4)",
            checkInstalledCommand = "which proxychains4 || which proxychains",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tor",
            name = "Tor Anonymity Daemon",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.4.x+",
            description = "The onion routing anonymity network client for private web browsing and security research.",
            installCommand = "sudo apt-get install -y tor torsocks || ((sudo apt-get update || true) && sudo apt-get install -y tor torsocks)",
            checkInstalledCommand = "which tor",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dnsenum",
            name = "DNSenum Recon",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.3+",
            description = "Multithreaded perl script to enumerate DNS information of a domain and discover non-contiguous ip blocks.",
            installCommand = "sudo apt-get install -y dnsenum || ((sudo apt-get update || true) && sudo apt-get install -y dnsenum)",
            checkInstalledCommand = "which dnsenum",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dnsrecon",
            name = "DNSrecon",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "DNS Reconnaissance tool supporting SRV records, zone transfers, and cache snooping.",
            installCommand = "sudo apt-get install -y dnsrecon || ((sudo apt-get update || true) && sudo apt-get install -y dnsrecon)",
            checkInstalledCommand = "which dnsrecon",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fierce",
            name = "Fierce Domain Scanner",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "DNS reconnaissance tool for locating non-contiguous IP space and hostnames across domains.",
            installCommand = "(sudo apt-get install -y fierce || ((sudo apt-get update || true) && sudo apt-get install -y fierce)) || pip3 install --break-system-packages --no-cache-dir fierce",
            checkInstalledCommand = "which fierce",
            launchUrl = null
        ),
        LinuxPackage(
            id = "whois",
            name = "Whois Client",
            category = PackageCategory.CYBER_SECURITY,
            version = "5.5+",
            description = "Intelligent RFC-compliant WHOIS client to inspect domain and ASN registration records.",
            installCommand = "sudo apt-get install -y whois || ((sudo apt-get update || true) && sudo apt-get install -y whois)",
            checkInstalledCommand = "which whois",
            launchUrl = null
        ),
        LinuxPackage(
            id = "traceroute",
            name = "Traceroute",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.1+",
            description = "Tracks the route packets take from an IP network on their way to a given host.",
            installCommand = "sudo apt-get install -y traceroute || ((sudo apt-get update || true) && sudo apt-get install -y traceroute)",
            checkInstalledCommand = "which traceroute",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ettercap",
            name = "Ettercap MITM Suite",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.8+",
            description = "Comprehensive suite for man-in-the-middle attacks on LAN (text-only edition for terminal).",
            installCommand = "sudo apt-get install -y ettercap-text-only || ((sudo apt-get update || true) && sudo apt-get install -y ettercap-text-only)",
            checkInstalledCommand = "which ettercap",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dsniff",
            name = "Dsniff Sniffing Suite",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.4+",
            description = "Collection of tools for network auditing and password sniffing (arpspoof, dnsspoof, dsniff, macof).",
            installCommand = "sudo apt-get install -y dsniff || ((sudo apt-get update || true) && sudo apt-get install -y dsniff)",
            checkInstalledCommand = "which arpspoof || which dsniff",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ngrep",
            name = "Ngrep Network Grep",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.47+",
            description = "Grep applied to the network layer to search for packet payload strings matching regular expressions.",
            installCommand = "sudo apt-get install -y ngrep || ((sudo apt-get update || true) && sudo apt-get install -y ngrep)",
            checkInstalledCommand = "which ngrep",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mitmproxy",
            name = "Mitmproxy SSL/TLS Interceptor",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Interactive, SSL/TLS-capable intercepting HTTP proxy for mobile and web app reverse engineering.",
            installCommand = "(sudo apt-get install -y mitmproxy || ((sudo apt-get update || true) && sudo apt-get install -y mitmproxy)) || pip3 install --break-system-packages --no-cache-dir mitmproxy",
            checkInstalledCommand = "which mitmproxy || which mitmdump",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sslscan",
            name = "SSLscan",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.x",
            description = "Tests SSL/TLS enabled services to discover supported cipher suites and SSL vulnerabilities.",
            installCommand = "sudo apt-get install -y sslscan || ((sudo apt-get update || true) && sudo apt-get install -y sslscan)",
            checkInstalledCommand = "which sslscan",
            launchUrl = null
        ),
        LinuxPackage(
            id = "testssl",
            name = "TestSSL.sh",
            category = PackageCategory.CYBER_SECURITY,
            version = "3.x",
            description = "Command-line tool which checks a server's service on any port for the support of TLS/SSL ciphers.",
            installCommand = "sudo apt-get install -y testssl.sh || ((sudo apt-get update || true) && sudo apt-get install -y testssl.sh)",
            checkInstalledCommand = "which testssl.sh || which testssl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "smbclient",
            name = "Smbclient & Samba Utils",
            category = PackageCategory.CYBER_SECURITY,
            version = "4.x",
            description = "Command line SMB/CIFS client to audit Windows shares and Samba fileservers.",
            installCommand = "sudo apt-get install -y smbclient || ((sudo apt-get update || true) && sudo apt-get install -y smbclient)",
            checkInstalledCommand = "which smbclient",
            launchUrl = null
        ),
        LinuxPackage(
            id = "snmp",
            name = "SNMP Utilities (snmpwalk)",
            category = PackageCategory.CYBER_SECURITY,
            version = "5.9+",
            description = "SNMP network management utilities including snmpwalk, snmpget, and snmpset for device recon.",
            installCommand = "sudo apt-get install -y snmp || ((sudo apt-get update || true) && sudo apt-get install -y snmp)",
            checkInstalledCommand = "which snmpwalk",
            launchUrl = null
        ),
        LinuxPackage(
            id = "theharvester",
            name = "theHarvester OSINT",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Gather emails, subdomains, hosts, employee names, open ports and banners from public sources.",
            installCommand = "(sudo apt-get install -y theharvester || ((sudo apt-get update || true) && sudo apt-get install -y theharvester)) || pip3 install --break-system-packages --no-cache-dir theHarvester",
            checkInstalledCommand = "which theHarvester || which theharvester",
            launchUrl = null
        ),
        LinuxPackage(
            id = "crunch",
            name = "Crunch Wordlist Generator",
            category = PackageCategory.CYBER_SECURITY,
            version = "3.6+",
            description = "Wordlist generator where you can specify a standard character set or a character set you specify.",
            installCommand = "sudo apt-get install -y crunch || ((sudo apt-get update || true) && sudo apt-get install -y crunch)",
            checkInstalledCommand = "which crunch",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cewl",
            name = "CeWL Custom Wordlist",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Custom Word List generator that spiders a given URL to a specified depth to extract unique words.",
            installCommand = "sudo apt-get install -y cewl || ((sudo apt-get update || true) && sudo apt-get install -y cewl)",
            checkInstalledCommand = "which cewl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "hashid",
            name = "HashID",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Identify the different types of cryptographic hashes used to encrypt data and passwords.",
            installCommand = "(sudo apt-get install -y hashid || ((sudo apt-get update || true) && sudo apt-get install -y hashid)) || pip3 install --break-system-packages --no-cache-dir hashID",
            checkInstalledCommand = "which hashid",
            launchUrl = null
        ),
        LinuxPackage(
            id = "hash-identifier",
            name = "Hash-Identifier",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Software to identify password hash algorithms (MD5, SHA1, SHA256, NTLM, etc.).",
            installCommand = "sudo apt-get install -y hash-identifier || ((sudo apt-get update || true) && sudo apt-get install -y hash-identifier)",
            checkInstalledCommand = "which hash-identifier",
            launchUrl = null
        ),
        LinuxPackage(
            id = "apktool",
            name = "Apktool Android Reverse",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Tool for reverse engineering 3rd party, closed, binary Android apps (decodes resources, smali).",
            installCommand = "sudo apt-get install -y apktool || ((sudo apt-get update || true) && sudo apt-get install -y apktool)",
            checkInstalledCommand = "which apktool",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dex2jar",
            name = "Dex2Jar",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.x",
            description = "Tools to convert Android .dex files to Java .class files for code analysis.",
            installCommand = "sudo apt-get install -y dex2jar || ((sudo apt-get update || true) && sudo apt-get install -y dex2jar)",
            checkInstalledCommand = "which d2j-dex2jar || which dex2jar",
            launchUrl = null
        ),
        LinuxPackage(
            id = "jadx",
            name = "JADX Dex to Java Decompiler",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Command line and GUI tools for producing Java source code from Android Dex and Apk files.",
            installCommand = "sudo apt-get install -y jadx || ((sudo apt-get update || true) && sudo apt-get install -y jadx)",
            checkInstalledCommand = "which jadx",
            launchUrl = null
        ),
        LinuxPackage(
            id = "yara",
            name = "YARA Malware Pattern Matcher",
            category = PackageCategory.CYBER_SECURITY,
            version = "4.x",
            description = "The pattern matching swiss knife for malware researchers to classify and detect threats.",
            installCommand = "sudo apt-get install -y yara || ((sudo apt-get update || true) && sudo apt-get install -y yara)",
            checkInstalledCommand = "which yara",
            launchUrl = null
        ),
        LinuxPackage(
            id = "chkrootkit",
            name = "Chkrootkit Rootkit Detector",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.5x",
            description = "Tool to locally check for signs of a rootkit on Unix-like operating systems.",
            installCommand = "sudo apt-get install -y chkrootkit || ((sudo apt-get update || true) && sudo apt-get install -y chkrootkit)",
            checkInstalledCommand = "which chkrootkit",
            launchUrl = null
        ),
        LinuxPackage(
            id = "rkhunter",
            name = "Rootkit Hunter (rkhunter)",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.4+",
            description = "Scans systems for rootkits, backdoors and local exploits by running tests on system files.",
            installCommand = "sudo apt-get install -y rkhunter || ((sudo apt-get update || true) && sudo apt-get install -y rkhunter)",
            checkInstalledCommand = "which rkhunter",
            launchUrl = null
        ),
        LinuxPackage(
            id = "lynis",
            name = "Lynis Security Auditing",
            category = PackageCategory.CYBER_SECURITY,
            version = "3.x",
            description = "Battle-tested security auditing tool for systems based on Linux, macOS, or Unix-based OS.",
            installCommand = "sudo apt-get install -y lynis || ((sudo apt-get update || true) && sudo apt-get install -y lynis)",
            checkInstalledCommand = "which lynis",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ncrack",
            name = "Ncrack Network Auth Cracker",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.7+",
            description = "High-speed network authentication cracking tool designed to aid companies in network security audits.",
            installCommand = "sudo apt-get install -y ncrack || ((sudo apt-get update || true) && sudo apt-get install -y ncrack)",
            checkInstalledCommand = "which ncrack",
            launchUrl = null
        ),
        LinuxPackage(
            id = "medusa",
            name = "Medusa Password Cracker",
            category = PackageCategory.CYBER_SECURITY,
            version = "2.2+",
            description = "Speedy, parallel, modular, login brute-forcer for network services.",
            installCommand = "sudo apt-get install -y medusa || ((sudo apt-get update || true) && sudo apt-get install -y medusa)",
            checkInstalledCommand = "which medusa",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fcrackzip",
            name = "Fcrackzip Zip Cracker",
            category = PackageCategory.CYBER_SECURITY,
            version = "1.0+",
            description = "Fast password cracker partly written in assembler for password-protected ZIP archives.",
            installCommand = "sudo apt-get install -y fcrackzip || ((sudo apt-get update || true) && sudo apt-get install -y fcrackzip)",
            checkInstalledCommand = "which fcrackzip",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pdfcrack",
            name = "PDFcrack",
            category = PackageCategory.CYBER_SECURITY,
            version = "0.2+",
            description = "Command-line password recovery tool for PDF documents.",
            installCommand = "sudo apt-get install -y pdfcrack || ((sudo apt-get update || true) && sudo apt-get install -y pdfcrack)",
            checkInstalledCommand = "which pdfcrack",
            launchUrl = null
        ),
        LinuxPackage(
            id = "hashpump",
            name = "HashPump Hash Extender",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Tool to exploit Hash Length Extension attacks against various hash algorithms.",
            installCommand = "sudo apt-get install -y hashpump || ((sudo apt-get update || true) && sudo apt-get install -y hashpump)",
            checkInstalledCommand = "which hashpump",
            launchUrl = null
        ),
        LinuxPackage(
            id = "snort",
            name = "Snort NIDS",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Open-source network intrusion detection system capable of real-time traffic analysis.",
            installCommand = "sudo apt-get install -y snort || ((sudo apt-get update || true) && sudo apt-get install -y snort)",
            checkInstalledCommand = "which snort",
            launchUrl = null
        ),
        LinuxPackage(
            id = "suricata",
            name = "Suricata Threat Detection",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "High performance Network Threat Detection, IDS, IPS and Network Security Monitoring engine.",
            installCommand = "sudo apt-get install -y suricata || ((sudo apt-get update || true) && sudo apt-get install -y suricata)",
            checkInstalledCommand = "which suricata",
            launchUrl = null
        ),
        LinuxPackage(
            id = "driftnet",
            name = "Driftnet Image Sniffer",
            category = PackageCategory.CYBER_SECURITY,
            version = "Latest",
            description = "Picks out images and MPEG audio streams from network traffic and displays them.",
            installCommand = "sudo apt-get install -y driftnet || ((sudo apt-get update || true) && sudo apt-get install -y driftnet)",
            checkInstalledCommand = "which driftnet",
            launchUrl = null
        )
        )
    }

    private val dataSciencePackages: List<LinuxPackage> by lazy {
        listOf(
        LinuxPackage(
            id = "jupyterlab",
            name = "JupyterLab & Notebook",
            category = PackageCategory.DATA_SCIENCE,
            version = "Latest / Web IDE",
            description = "Interactive web-based notebooks, code cells, terminal, and visualization dashboard.",
            installCommand = "(sudo apt-get install -y jupyter jupyter-core python3-pip && pip3 install --break-system-packages --no-cache-dir jupyterlab notebook; if [ -x /home/ubuntu/miniforge3/bin/pip ]; then /home/ubuntu/miniforge3/bin/pip install --no-cache-dir jupyterlab notebook 2>/dev/null || ((sudo apt-get update || true) && sudo apt-get install -y jupyter jupyter-core python3-pip && pip3 install --break-system-packages --no-cache-dir jupyterlab notebook; if [ -x /home/ubuntu/miniforge3/bin/pip ]; then /home/ubuntu/miniforge3/bin/pip install --no-cache-dir jupyterlab notebook 2>/dev/null)) || true; fi",
            checkInstalledCommand = "which jupyter || [ -x /home/ubuntu/miniforge3/bin/jupyter ] || [ -x /root/miniconda3/bin/jupyter ]",
            launchUrl = "http://127.0.0.1:8888/lab"
        ),
        LinuxPackage(
            id = "miniconda",
            name = "Miniconda3 / Conda",
            category = PackageCategory.DATA_SCIENCE,
            version = "ARM64",
            description = "Lightweight installer for Conda, Python package and virtual environment manager.",
            installCommand = "if [ -x /usr/local/bin/install-conda ]; then /usr/local/bin/install-conda; else " +
                    "(which curl >/dev/null 2>&1 || which wget >/dev/null 2>&1 || (sudo apt-get -o DPkg::Lock::Timeout=60 update -y && sudo apt-get -o DPkg::Lock::Timeout=60 install -y --no-install-recommends curl ca-certificates)) && " +
                    "if which curl >/dev/null 2>&1; then (curl -fSL https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-Linux-aarch64.sh -o /home/ubuntu/.miniforge.sh || curl -fSL https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-aarch64.sh -o /home/ubuntu/.miniforge.sh); else (wget -O /home/ubuntu/.miniforge.sh https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-Linux-aarch64.sh || wget -O /home/ubuntu/.miniforge.sh https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-aarch64.sh); fi && " +
                    "bash /home/ubuntu/.miniforge.sh -b -p /home/ubuntu/miniforge3 -u && rm -f /home/ubuntu/.miniforge.sh && " +
                    "/home/ubuntu/miniforge3/bin/conda init bash && /home/ubuntu/miniforge3/bin/conda config --set always_copy true && /home/ubuntu/miniforge3/bin/conda config --set auto_activate_base true; fi",
            checkInstalledCommand = "[ -x /home/ubuntu/miniforge3/bin/conda ] || [ -x /root/miniconda3/bin/conda ] || [ -x /opt/conda/bin/conda ]",
            launchUrl = null
        ),
        LinuxPackage(
            id = "numpy",
            name = "NumPy",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "The fundamental package for high-performance scientific computing and N-dimensional arrays.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python numpy python3-numpy; else (sudo apt-get install -y python3-numpy || ((sudo apt-get update || true) && sudo apt-get install -y python3-numpy)) || pip3 install --break-system-packages --no-cache-dir numpy; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir numpy 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir numpy 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir numpy 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which numpy || python3 -c 'import numpy' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import numpy' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pandas",
            name = "Pandas",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Powerful, flexible data analysis and manipulation library for structured datasets.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python pandas python3-pandas; else (sudo apt-get install -y python3-pandas || ((sudo apt-get update || true) && sudo apt-get install -y python3-pandas)) || pip3 install --break-system-packages --no-cache-dir pandas; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir pandas 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir pandas 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir pandas 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which pandas || python3 -c 'import pandas' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import pandas' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scipy",
            name = "SciPy",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Fundamental algorithms for scientific computing including optimization, integration, and ODE solvers.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python scipy python3-scipy; else (sudo apt-get install -y python3-scipy || ((sudo apt-get update || true) && sudo apt-get install -y python3-scipy)) || pip3 install --break-system-packages --no-cache-dir scipy; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir scipy 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir scipy 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir scipy 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which scipy || python3 -c 'import scipy' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import scipy' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scikit-learn",
            name = "Scikit-Learn",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Simple and efficient tools for predictive data analysis, clustering, and machine learning.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python scikit-learn python3-sklearn; else (sudo apt-get install -y python3-sklearn || ((sudo apt-get update || true) && sudo apt-get install -y python3-sklearn)) || pip3 install --break-system-packages --no-cache-dir scikit-learn; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir scikit-learn 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir scikit-learn 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir scikit-learn 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which scikit-learn || python3 -c 'import scikit_learn' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import scikit_learn' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "torch",
            name = "PyTorch (ARM64)",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Open source machine learning framework that accelerates the path from research to production.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python torch ; else pip3 install --break-system-packages --no-cache-dir torch; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir torch 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir torch 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir torch 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which torch || python3 -c 'import torch' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import torch' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "torchvision",
            name = "TorchVision",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Datasets, transforms and popular model architectures for computer vision in PyTorch.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python torchvision ; else pip3 install --break-system-packages --no-cache-dir torchvision; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir torchvision 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir torchvision 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir torchvision 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which torchvision || python3 -c 'import torchvision' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import torchvision' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "torchaudio",
            name = "TorchAudio",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Audio processing tools, I/O and pretrained models for PyTorch.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python torchaudio ; else pip3 install --break-system-packages --no-cache-dir torchaudio; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir torchaudio 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir torchaudio 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir torchaudio 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which torchaudio || python3 -c 'import torchaudio' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import torchaudio' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tflite-runtime",
            name = "TensorFlow Lite Runtime",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Lightweight TensorFlow runtime optimized for mobile and embedded devices.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python tflite-runtime ; else pip3 install --break-system-packages --no-cache-dir tflite-runtime; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir tflite-runtime 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir tflite-runtime 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir tflite-runtime 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which tflite-runtime || python3 -c 'import tflite_runtime' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import tflite_runtime' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "onnxruntime",
            name = "ONNX Runtime",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "High-performance scoring engine for Open Neural Network Exchange (ONNX) models.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python onnxruntime ; else pip3 install --break-system-packages --no-cache-dir onnxruntime; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir onnxruntime 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir onnxruntime 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir onnxruntime 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which onnxruntime || python3 -c 'import onnxruntime' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import onnxruntime' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "matplotlib",
            name = "Matplotlib",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Comprehensive library for creating static, animated, and interactive visualizations in Python.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python matplotlib python3-matplotlib; else (sudo apt-get install -y python3-matplotlib || ((sudo apt-get update || true) && sudo apt-get install -y python3-matplotlib)) || pip3 install --break-system-packages --no-cache-dir matplotlib; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir matplotlib 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir matplotlib 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir matplotlib 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which matplotlib || python3 -c 'import matplotlib' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import matplotlib' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "seaborn",
            name = "Seaborn",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Statistical data visualization based on matplotlib with informative, beautiful themes.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python seaborn python3-seaborn; else (sudo apt-get install -y python3-seaborn || ((sudo apt-get update || true) && sudo apt-get install -y python3-seaborn)) || pip3 install --break-system-packages --no-cache-dir seaborn; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir seaborn 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir seaborn 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir seaborn 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which seaborn || python3 -c 'import seaborn' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import seaborn' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "plotly",
            name = "Plotly Interactive Charts",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Interactive, publication-quality graphing library for web browsers and Jupyter notebooks.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python plotly ; else pip3 install --break-system-packages --no-cache-dir plotly; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir plotly 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir plotly 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir plotly 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which plotly || python3 -c 'import plotly' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import plotly' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bokeh",
            name = "Bokeh Visualization",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Interactive visualization library that targets modern web browsers for presentation.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python bokeh ; else pip3 install --break-system-packages --no-cache-dir bokeh; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir bokeh 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir bokeh 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir bokeh 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which bokeh || python3 -c 'import bokeh' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import bokeh' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "altair",
            name = "Altair Statistical Charts",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Declarative statistical visualization library for Python based on Vega and Vega-Lite.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python altair ; else pip3 install --break-system-packages --no-cache-dir altair; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir altair 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir altair 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir altair 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which altair || python3 -c 'import altair' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import altair' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "opencv-python",
            name = "OpenCV Computer Vision",
            category = PackageCategory.DATA_SCIENCE,
            version = "Headless",
            description = "Open Source Computer Vision Library with 2500+ optimized real-time vision algorithms.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python opencv-python-headless python3-opencv; else (sudo apt-get install -y python3-opencv || ((sudo apt-get update || true) && sudo apt-get install -y python3-opencv)) || pip3 install --break-system-packages --no-cache-dir opencv-python-headless; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir opencv-python-headless 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir opencv-python-headless 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir opencv-python-headless 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which opencv-python || python3 -c 'import opencv_python_headless' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import opencv_python_headless' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pillow",
            name = "Pillow (PIL Fork)",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "The friendly Python Imaging Library adds image processing capabilities to Python.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python pillow python3-pil; else (sudo apt-get install -y python3-pil || ((sudo apt-get update || true) && sudo apt-get install -y python3-pil)) || pip3 install --break-system-packages --no-cache-dir pillow; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir pillow 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir pillow 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir pillow 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which pillow || python3 -c 'import pillow' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import pillow' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scikit-image",
            name = "Scikit-Image",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Collection of algorithms for image processing and computer vision in Python.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python scikit-image python3-skimage; else (sudo apt-get install -y python3-skimage || ((sudo apt-get update || true) && sudo apt-get install -y python3-skimage)) || pip3 install --break-system-packages --no-cache-dir scikit-image; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir scikit-image 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir scikit-image 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir scikit-image 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which scikit-image || python3 -c 'import scikit_image' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import scikit_image' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nltk",
            name = "NLTK Natural Language",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Leading platform for building Python programs to work with human language data.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python nltk python3-nltk; else (sudo apt-get install -y python3-nltk || ((sudo apt-get update || true) && sudo apt-get install -y python3-nltk)) || pip3 install --break-system-packages --no-cache-dir nltk; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir nltk 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir nltk 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir nltk 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which nltk || python3 -c 'import nltk' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import nltk' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "spacy",
            name = "spaCy Industrial NLP",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Industrial-strength Natural Language Processing in Python with fast Cython engine.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python spacy python3-spacy; else (sudo apt-get install -y python3-spacy || ((sudo apt-get update || true) && sudo apt-get install -y python3-spacy)) || pip3 install --break-system-packages --no-cache-dir spacy; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir spacy 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir spacy 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir spacy 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which spacy || python3 -c 'import spacy' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import spacy' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "transformers",
            name = "HuggingFace Transformers",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "State-of-the-art Machine Learning for PyTorch, TensorFlow, and JAX.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python transformers ; else pip3 install --break-system-packages --no-cache-dir transformers; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir transformers 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir transformers 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir transformers 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which transformers || python3 -c 'import transformers' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import transformers' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tokenizers",
            name = "HuggingFace Tokenizers",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Fast and versatile tokenization library written in Rust with Python bindings.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python tokenizers ; else pip3 install --break-system-packages --no-cache-dir tokenizers; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir tokenizers 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir tokenizers 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir tokenizers 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which tokenizers || python3 -c 'import tokenizers' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import tokenizers' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "datasets",
            name = "HuggingFace Datasets",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Lightweight library for easily sharing and accessing datasets for Machine Learning.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python datasets ; else pip3 install --break-system-packages --no-cache-dir datasets; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir datasets 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir datasets 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir datasets 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which datasets || python3 -c 'import datasets' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import datasets' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gensim",
            name = "Gensim Topic Modeling",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Topic modelling, document indexing and similarity retrieval with large corpora.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python gensim python3-gensim; else (sudo apt-get install -y python3-gensim || ((sudo apt-get update || true) && sudo apt-get install -y python3-gensim)) || pip3 install --break-system-packages --no-cache-dir gensim; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir gensim 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir gensim 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir gensim 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which gensim || python3 -c 'import gensim' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import gensim' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "networkx",
            name = "NetworkX Graph Analysis",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Creation, manipulation, and study of the structure, dynamics, and functions of complex networks.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python networkx python3-networkx; else (sudo apt-get install -y python3-networkx || ((sudo apt-get update || true) && sudo apt-get install -y python3-networkx)) || pip3 install --break-system-packages --no-cache-dir networkx; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir networkx 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir networkx 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir networkx 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which networkx || python3 -c 'import networkx' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import networkx' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sympy",
            name = "SymPy Symbolic Math",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Python library for symbolic mathematics aims to become a full-featured computer algebra system.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python sympy python3-sympy; else (sudo apt-get install -y python3-sympy || ((sudo apt-get update || true) && sudo apt-get install -y python3-sympy)) || pip3 install --break-system-packages --no-cache-dir sympy; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir sympy 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir sympy 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir sympy 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which sympy || python3 -c 'import sympy' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import sympy' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "statsmodels",
            name = "Statsmodels",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Statistical modeling and econometrics in Python with descriptive statistics and estimation.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python statsmodels python3-statsmodels; else (sudo apt-get install -y python3-statsmodels || ((sudo apt-get update || true) && sudo apt-get install -y python3-statsmodels)) || pip3 install --break-system-packages --no-cache-dir statsmodels; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir statsmodels 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir statsmodels 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir statsmodels 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which statsmodels || python3 -c 'import statsmodels' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import statsmodels' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "xgboost",
            name = "XGBoost Gradient Boosting",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Optimized distributed gradient boosting library designed to be highly efficient and flexible.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python xgboost ; else pip3 install --break-system-packages --no-cache-dir xgboost; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir xgboost 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir xgboost 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir xgboost 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which xgboost || python3 -c 'import xgboost' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import xgboost' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "lightgbm",
            name = "LightGBM",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Fast, distributed, high performance gradient boosting framework based on decision tree algorithms.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python lightgbm ; else pip3 install --break-system-packages --no-cache-dir lightgbm; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir lightgbm 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir lightgbm 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir lightgbm 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which lightgbm || python3 -c 'import lightgbm' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import lightgbm' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "catboost",
            name = "CatBoost",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Fast, scalable, high performance Gradient Boosting on Decision Trees library.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python catboost ; else pip3 install --break-system-packages --no-cache-dir catboost; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir catboost 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir catboost 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir catboost 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which catboost || python3 -c 'import catboost' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import catboost' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "polars",
            name = "Polars Fast DataFrames",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Blazingly fast DataFrames library implemented in Rust with multi-threaded columnar engine.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python polars ; else pip3 install --break-system-packages --no-cache-dir polars; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir polars 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir polars 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir polars 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which polars || python3 -c 'import polars' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import polars' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dask",
            name = "Dask Parallel Computing",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Flexible library for parallel computing in Python that scales NumPy and Pandas workflows.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python dask python3-dask; else (sudo apt-get install -y python3-dask || ((sudo apt-get update || true) && sudo apt-get install -y python3-dask)) || pip3 install --break-system-packages --no-cache-dir dask; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir dask 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir dask 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir dask 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which dask || python3 -c 'import dask' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import dask' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pyarrow",
            name = "Apache Arrow PyArrow",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Python library for Apache Arrow development platform for in-memory columnar data.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python pyarrow python3-pyarrow; else (sudo apt-get install -y python3-pyarrow || ((sudo apt-get update || true) && sudo apt-get install -y python3-pyarrow)) || pip3 install --break-system-packages --no-cache-dir pyarrow; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir pyarrow 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir pyarrow 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir pyarrow 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which pyarrow || python3 -c 'import pyarrow' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import pyarrow' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fastapi",
            name = "FastAPI Web Framework",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Modern, fast (high-performance) web framework for building APIs with Python 3.8+.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python fastapi uvicorn python3-fastapi; else (sudo apt-get install -y python3-fastapi || ((sudo apt-get update || true) && sudo apt-get install -y python3-fastapi)) || pip3 install --break-system-packages --no-cache-dir fastapi uvicorn; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir fastapi uvicorn 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir fastapi uvicorn 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir fastapi uvicorn 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which fastapi || python3 -c 'import fastapi' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import fastapi' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "streamlit",
            name = "Streamlit App Builder",
            category = PackageCategory.DATA_SCIENCE,
            version = "Latest",
            description = "Turns data scripts into shareable web apps in minutes with pure Python.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python streamlit ; else pip3 install --break-system-packages --no-cache-dir streamlit; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir streamlit 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir streamlit 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir streamlit 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which streamlit || python3 -c 'import streamlit' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import streamlit' 2>/dev/null",
            launchUrl = "http://127.0.0.1:8501"
        ),
        LinuxPackage(
            id = "gradio",
            name = "Gradio ML Web UI",
            category = PackageCategory.DATA_SCIENCE,
            version = "Latest",
            description = "Create friendly web interfaces for your machine learning models in a few lines of code.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python gradio ; else pip3 install --break-system-packages --no-cache-dir gradio; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir gradio 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir gradio 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir gradio 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which gradio || python3 -c 'import gradio' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import gradio' 2>/dev/null",
            launchUrl = "http://127.0.0.1:7860"
        ),
        LinuxPackage(
            id = "tqdm",
            name = "TQDM Progress Bars",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Fast, extensible progress meter for Python loops and command-line scripts.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python tqdm python3-tqdm; else (sudo apt-get install -y python3-tqdm || ((sudo apt-get update || true) && sudo apt-get install -y python3-tqdm)) || pip3 install --break-system-packages --no-cache-dir tqdm; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir tqdm 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir tqdm 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir tqdm 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which tqdm || python3 -c 'import tqdm' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import tqdm' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "joblib",
            name = "Joblib Pipeline Tools",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Set of tools to provide lightweight pipelining in Python with transparent disk-caching.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python joblib python3-joblib; else (sudo apt-get install -y python3-joblib || ((sudo apt-get update || true) && sudo apt-get install -y python3-joblib)) || pip3 install --break-system-packages --no-cache-dir joblib; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir joblib 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir joblib 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir joblib 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which joblib || python3 -c 'import joblib' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import joblib' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "h5py",
            name = "H5py HDF5 Interface",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Pythonic interface to the HDF5 binary data format storing huge amounts of numerical data.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python h5py python3-h5py; else (sudo apt-get install -y python3-h5py || ((sudo apt-get update || true) && sudo apt-get install -y python3-h5py)) || pip3 install --break-system-packages --no-cache-dir h5py; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir h5py 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir h5py 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir h5py 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which h5py || python3 -c 'import h5py' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import h5py' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "zarr",
            name = "Zarr Chunked Arrays",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Format for the storage of chunked, compressed, N-dimensional arrays.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python zarr ; else pip3 install --break-system-packages --no-cache-dir zarr; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir zarr 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir zarr 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir zarr 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which zarr || python3 -c 'import zarr' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import zarr' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "librosa",
            name = "Librosa Audio Analysis",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Python package for music and audio analysis, feature extraction, and spectrograms.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python librosa ; else pip3 install --break-system-packages --no-cache-dir librosa; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir librosa 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir librosa 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir librosa 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which librosa || python3 -c 'import librosa' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import librosa' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "soundfile",
            name = "SoundFile Audio I/O",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Audio library based on libsndfile, CFFI and NumPy for reading and writing sound files.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python soundfile python3-soundfile; else (sudo apt-get install -y python3-soundfile || ((sudo apt-get update || true) && sudo apt-get install -y python3-soundfile)) || pip3 install --break-system-packages --no-cache-dir soundfile; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir soundfile 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir soundfile 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir soundfile 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which soundfile || python3 -c 'import soundfile' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import soundfile' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pydub",
            name = "Pydub Audio Manipulation",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Manipulate audio with an easy high-level interface (slice, concatenate, apply effects).",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python pydub ; else pip3 install --break-system-packages --no-cache-dir pydub; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir pydub 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir pydub 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir pydub 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which pydub || python3 -c 'import pydub' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import pydub' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "whisper",
            name = "OpenAI Whisper ASR",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Robust Speech Recognition via Large-Scale Weak Supervision from OpenAI.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python openai-whisper ; else pip3 install --break-system-packages --no-cache-dir openai-whisper; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir openai-whisper 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir openai-whisper 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir openai-whisper 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which whisper || python3 -c 'import openai_whisper' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import openai_whisper' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sentence-transformers",
            name = "Sentence Transformers",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Multilingual sentence, text, and image embeddings using BERT / RoBERTa.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python sentence-transformers ; else pip3 install --break-system-packages --no-cache-dir sentence-transformers; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir sentence-transformers 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir sentence-transformers 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir sentence-transformers 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which sentence-transformers || python3 -c 'import sentence_transformers' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import sentence_transformers' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "langchain",
            name = "LangChain LLM Framework",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Framework for developing applications powered by large language models.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python langchain ; else pip3 install --break-system-packages --no-cache-dir langchain; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir langchain 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir langchain 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir langchain 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which langchain || python3 -c 'import langchain' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import langchain' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "chromadb",
            name = "Chroma Vector Database",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "AI-native open-source embedding database for AI application development.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python chromadb ; else pip3 install --break-system-packages --no-cache-dir chromadb; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir chromadb 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir chromadb 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir chromadb 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which chromadb || python3 -c 'import chromadb' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import chromadb' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "faiss-cpu",
            name = "FAISS Vector Search",
            category = PackageCategory.DATA_SCIENCE,
            version = "CPU Edition",
            description = "Library for efficient similarity search and clustering of dense vectors from Meta AI.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python faiss-cpu ; else pip3 install --break-system-packages --no-cache-dir faiss-cpu; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir faiss-cpu 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir faiss-cpu 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir faiss-cpu 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which faiss-cpu || python3 -c 'import faiss_cpu' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import faiss_cpu' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "optuna",
            name = "Optuna Hyperparameter Tuner",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Hyperparameter optimization framework designed specifically for machine learning.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python optuna ; else pip3 install --break-system-packages --no-cache-dir optuna; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir optuna 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir optuna 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir optuna 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which optuna || python3 -c 'import optuna' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import optuna' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cython",
            name = "Cython C-Extensions",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Compiler that makes writing C extensions for Python as easy as Python itself.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python cython cython3; else (sudo apt-get install -y cython3 || ((sudo apt-get update || true) && sudo apt-get install -y cython3)) || pip3 install --break-system-packages --no-cache-dir cython; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir cython 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir cython 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir cython 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which cython || python3 -c 'import cython' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import cython' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "numba",
            name = "Numba JIT Compiler",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "NumPy-aware optimizing compiler that turns Python functions into fast machine code.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python numba python3-numba; else (sudo apt-get install -y python3-numba || ((sudo apt-get update || true) && sudo apt-get install -y python3-numba)) || pip3 install --break-system-packages --no-cache-dir numba; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir numba 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir numba 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir numba 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which numba || python3 -c 'import numba' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import numba' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "beautifulsoup4",
            name = "BeautifulSoup4 Web Scraper",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Python library for pulling data out of HTML and XML files with parse trees.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python beautifulsoup4 python3-bs4; else (sudo apt-get install -y python3-bs4 || ((sudo apt-get update || true) && sudo apt-get install -y python3-bs4)) || pip3 install --break-system-packages --no-cache-dir beautifulsoup4; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir beautifulsoup4 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir beautifulsoup4 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir beautifulsoup4 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which beautifulsoup4 || python3 -c 'import beautifulsoup4' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import beautifulsoup4' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scrapy",
            name = "Scrapy Web Crawling Framework",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Fast high-level web crawling and scraping framework to crawl websites and extract structured data.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python scrapy python3-scrapy; else (sudo apt-get install -y python3-scrapy || ((sudo apt-get update || true) && sudo apt-get install -y python3-scrapy)) || pip3 install --break-system-packages --no-cache-dir scrapy; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir scrapy 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir scrapy 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir scrapy 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which scrapy || python3 -c 'import scrapy' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import scrapy' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "requests",
            name = "Requests HTTP Library",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Elegant and simple HTTP library for Python, built for human beings.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python requests python3-requests; else (sudo apt-get install -y python3-requests || ((sudo apt-get update || true) && sudo apt-get install -y python3-requests)) || pip3 install --break-system-packages --no-cache-dir requests; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir requests 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir requests 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir requests 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which requests || python3 -c 'import requests' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import requests' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "httpx-py",
            name = "HTTPX Python Client",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Next-generation HTTP client for Python 3 with HTTP/2 and async support.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python httpx python3-httpx; else (sudo apt-get install -y python3-httpx || ((sudo apt-get update || true) && sudo apt-get install -y python3-httpx)) || pip3 install --break-system-packages --no-cache-dir httpx; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir httpx 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir httpx 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir httpx 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which httpx-py || python3 -c 'import httpx' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import httpx' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "aiohttp",
            name = "Aiohttp Async HTTP",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Asynchronous HTTP client/server framework for asyncio and Python.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python aiohttp python3-aiohttp; else (sudo apt-get install -y python3-aiohttp || ((sudo apt-get update || true) && sudo apt-get install -y python3-aiohttp)) || pip3 install --break-system-packages --no-cache-dir aiohttp; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir aiohttp 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir aiohttp 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir aiohttp 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which aiohttp || python3 -c 'import aiohttp' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import aiohttp' 2>/dev/null",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pytest",
            name = "Pytest Testing Framework",
            category = PackageCategory.DATA_SCIENCE,
            version = "Python 3",
            description = "Mature full-featured Python testing tool that helps you write better programs.",
            installCommand = "if [ -x /usr/local/bin/pkg-install-python ]; then /usr/local/bin/pkg-install-python pytest python3-pytest; else (sudo apt-get install -y python3-pytest || ((sudo apt-get update || true) && sudo apt-get install -y python3-pytest)) || pip3 install --break-system-packages --no-cache-dir pytest; for p in /home/ubuntu/miniforge3/bin/pip /root/miniconda3/bin/pip /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip /home/ubuntu/.conda/envs/*/bin/pip /root/.conda/envs/*/bin/pip; do [ -x \"\$p\" ] && \"\$p\" install --no-cache-dir pytest 2>/dev/null || true; done; if [ -f /home/ubuntu/.conda/environments.txt ]; then while IFS= read -r e; do [ -x \"\$e/bin/pip\" ] && \"\$e/bin/pip\" install --no-cache-dir pytest 2>/dev/null || true; done < /home/ubuntu/.conda/environments.txt; fi; if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then \"\$CONDA_PREFIX/bin/pip\" install --no-cache-dir pytest 2>/dev/null || true; fi; fi",
            checkInstalledCommand = "which pytest || python3 -c 'import pytest' 2>/dev/null || [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c 'import pytest' 2>/dev/null",
            launchUrl = null
        )
        )
    }

    private val runtimesPackages: List<LinuxPackage> by lazy {
        listOf(
        LinuxPackage(
            id = "python3-full",
            name = "Python 3 Full Runtime",
            category = PackageCategory.RUNTIMES,
            version = "3.12+",
            description = "Interactive high-level object-oriented language with complete standard libraries.",
            installCommand = "sudo apt-get install -y python3-full python3-pip python3-dev || ((sudo apt-get update || true) && sudo apt-get install -y python3-full python3-pip python3-dev)",
            checkInstalledCommand = "which python3",
            launchUrl = null
        ),
        LinuxPackage(
            id = "python3-venv",
            name = "Python Virtual Environments",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Standard module for creating lightweight isolated Python virtual environments.",
            installCommand = "sudo apt-get install -y python3-venv || ((sudo apt-get update || true) && sudo apt-get install -y python3-venv)",
            checkInstalledCommand = "python3 -m venv -h >/dev/null 2>&1",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nodejs",
            name = "Node.js JavaScript Engine",
            category = PackageCategory.RUNTIMES,
            version = "v20 LTS",
            description = "Event-driven JavaScript runtime built on Chrome's V8 engine for server-side apps.",
            installCommand = "curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && sudo apt-get install -y nodejs",
            checkInstalledCommand = "which node",
            launchUrl = null
        ),
        LinuxPackage(
            id = "npm",
            name = "NPM Package Manager",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Default package manager for the JavaScript programming language runtime Node.js.",
            installCommand = "which npm >/dev/null || sudo apt-get install -y npm",
            checkInstalledCommand = "which npm",
            launchUrl = null
        ),
        LinuxPackage(
            id = "yarn",
            name = "Yarn Package Manager",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Ultra-fast, reliable, and secure dependency management for JavaScript and Node.",
            installCommand = "npm install -g yarn",
            checkInstalledCommand = "which yarn",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pnpm",
            name = "PNPM Fast Package Manager",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Fast, disk space efficient package manager using content-addressable storage.",
            installCommand = "npm install -g pnpm",
            checkInstalledCommand = "which pnpm",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bun",
            name = "Bun JavaScript Runtime",
            category = PackageCategory.RUNTIMES,
            version = "ARM64",
            description = "Incredibly fast all-in-one JavaScript runtime, bundler, test runner, and package manager.",
            installCommand = "curl -fsSL https://bun.sh/install | bash",
            checkInstalledCommand = "which bun || [ -x /root/.bun/bin/bun ]",
            launchUrl = null
        ),
        LinuxPackage(
            id = "typescript",
            name = "TypeScript Compiler",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Strict syntactical superset of JavaScript that adds optional static typing.",
            installCommand = "npm install -g typescript ts-node",
            checkInstalledCommand = "which tsc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "openjdk-21",
            name = "OpenJDK 21 Java (LTS)",
            category = PackageCategory.RUNTIMES,
            version = "Java 21",
            description = "Open-source implementation of the Java Platform, Standard Edition (JDK 21 LTS).",
            installCommand = "sudo apt-get install -y openjdk-21-jdk || ((sudo apt-get update || true) && sudo apt-get install -y openjdk-21-jdk)",
            checkInstalledCommand = "which javac && java -version 2>&1 | grep -q '21'",
            launchUrl = null
        ),
        LinuxPackage(
            id = "openjdk-17",
            name = "OpenJDK 17 Java (LTS)",
            category = PackageCategory.RUNTIMES,
            version = "Java 17",
            description = "Long Term Support (LTS) Java development environment and virtual machine.",
            installCommand = "sudo apt-get install -y openjdk-17-jdk || ((sudo apt-get update || true) && sudo apt-get install -y openjdk-17-jdk)",
            checkInstalledCommand = "which javac && java -version 2>&1 | grep -q '17'",
            launchUrl = null
        ),
        LinuxPackage(
            id = "openjdk-11",
            name = "OpenJDK 11 Java (LTS)",
            category = PackageCategory.RUNTIMES,
            version = "Java 11",
            description = "Legacy LTS release of the Java SE Platform for enterprise compatibility.",
            installCommand = "sudo apt-get install -y openjdk-11-jdk || ((sudo apt-get update || true) && sudo apt-get install -y openjdk-11-jdk)",
            checkInstalledCommand = "which javac && java -version 2>&1 | grep -q '11'",
            launchUrl = null
        ),
        LinuxPackage(
            id = "rust",
            name = "Rust Compiler & Cargo",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Language empowering everyone to build reliable and efficient software without garbage collector.",
            installCommand = "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y",
            checkInstalledCommand = "which rustc || [ -x /root/.cargo/bin/rustc ]",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cargo",
            name = "Cargo Package Manager",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "The official Rust package manager for downloading dependencies and building crates.",
            installCommand = "which cargo || [ -x /root/.cargo/bin/cargo ]",
            checkInstalledCommand = "which cargo || [ -x /root/.cargo/bin/cargo ]",
            launchUrl = null
        ),
        LinuxPackage(
            id = "golang",
            name = "Go Programming Language",
            category = PackageCategory.RUNTIMES,
            version = "1.22+",
            description = "Open-source programming language that makes it easy to build simple, fast, and reliable software.",
            installCommand = "sudo apt-get install -y golang-go || ((sudo apt-get update || true) && sudo apt-get install -y golang-go)",
            checkInstalledCommand = "which go",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gcc",
            name = "GCC GNU C Compiler",
            category = PackageCategory.RUNTIMES,
            version = "13+",
            description = "The GNU Compiler Collection - C language compiler for generating optimized native binaries.",
            installCommand = "sudo apt-get install -y gcc || ((sudo apt-get update || true) && sudo apt-get install -y gcc)",
            checkInstalledCommand = "which gcc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gplusplus",
            name = "G++ GNU C++ Compiler",
            category = PackageCategory.RUNTIMES,
            version = "13+",
            description = "The GNU Compiler Collection - modern C++ compiler with full standard library support.",
            installCommand = "sudo apt-get install -y g++ || ((sudo apt-get update || true) && sudo apt-get install -y g++)",
            checkInstalledCommand = "which g++",
            launchUrl = null
        ),
        LinuxPackage(
            id = "clang",
            name = "Clang C/C++ Frontend",
            category = PackageCategory.RUNTIMES,
            version = "LLVM",
            description = "C language family frontend for LLVM offering high performance and clean compiler diagnostics.",
            installCommand = "sudo apt-get install -y clang || ((sudo apt-get update || true) && sudo apt-get install -y clang)",
            checkInstalledCommand = "which clang",
            launchUrl = null
        ),
        LinuxPackage(
            id = "llvm",
            name = "LLVM Infrastructure",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Collection of modular and reusable compiler and toolchain technologies.",
            installCommand = "sudo apt-get install -y llvm || ((sudo apt-get update || true) && sudo apt-get install -y llvm)",
            checkInstalledCommand = "which llvm-config",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ruby",
            name = "Ruby Language",
            category = PackageCategory.RUNTIMES,
            version = "3.x",
            description = "Dynamic, open source programming language with a focus on simplicity and productivity.",
            installCommand = "sudo apt-get install -y ruby-full || ((sudo apt-get update || true) && sudo apt-get install -y ruby-full)",
            checkInstalledCommand = "which ruby",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gem",
            name = "RubyGems Package Manager",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Standard package manager for Ruby libraries and command-line programs.",
            installCommand = "sudo apt-get install -y rubygems || ((sudo apt-get update || true) && sudo apt-get install -y rubygems)",
            checkInstalledCommand = "which gem",
            launchUrl = null
        ),
        LinuxPackage(
            id = "php-cli",
            name = "PHP 8 Command Line",
            category = PackageCategory.RUNTIMES,
            version = "8.x",
            description = "General-purpose scripting language especially suited to web development and CLI scripts.",
            installCommand = "sudo apt-get install -y php-cli php-curl php-json php-mbstring || ((sudo apt-get update || true) && sudo apt-get install -y php-cli php-curl php-json php-mbstring)",
            checkInstalledCommand = "which php",
            launchUrl = null
        ),
        LinuxPackage(
            id = "composer",
            name = "PHP Composer",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Dependency Manager for PHP projects and libraries.",
            installCommand = "sudo apt-get install -y composer || ((sudo apt-get update || true) && sudo apt-get install -y composer)",
            checkInstalledCommand = "which composer",
            launchUrl = null
        ),
        LinuxPackage(
            id = "perl",
            name = "Perl Programming Language",
            category = PackageCategory.RUNTIMES,
            version = "5.x",
            description = "Highly capable, feature-rich programming language with over 30 years of development.",
            installCommand = "sudo apt-get install -y perl || ((sudo apt-get update || true) && sudo apt-get install -y perl)",
            checkInstalledCommand = "which perl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "lua",
            name = "Lua Scripting Language",
            category = PackageCategory.RUNTIMES,
            version = "5.4",
            description = "Powerful, efficient, lightweight, embeddable scripting language.",
            installCommand = "sudo apt-get install -y lua5.4 || ((sudo apt-get update || true) && sudo apt-get install -y lua5.4)",
            checkInstalledCommand = "which lua5.4 || which lua",
            launchUrl = null
        ),
        LinuxPackage(
            id = "luarocks",
            name = "LuaRocks Package Manager",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "The package manager for Lua modules and libraries.",
            installCommand = "sudo apt-get install -y luarocks || ((sudo apt-get update || true) && sudo apt-get install -y luarocks)",
            checkInstalledCommand = "which luarocks",
            launchUrl = null
        ),
        LinuxPackage(
            id = "luajit",
            name = "LuaJIT Just-In-Time",
            category = PackageCategory.RUNTIMES,
            version = "2.1+",
            description = "High-performance Just-In-Time Compiler for the Lua programming language.",
            installCommand = "sudo apt-get install -y luajit || ((sudo apt-get update || true) && sudo apt-get install -y luajit)",
            checkInstalledCommand = "which luajit",
            launchUrl = null
        ),
        LinuxPackage(
            id = "r-base",
            name = "GNU R Statistical Language",
            category = PackageCategory.RUNTIMES,
            version = "4.x",
            description = "System for statistical computation and graphics widely used in data analysis.",
            installCommand = "sudo apt-get install -y r-base || ((sudo apt-get update || true) && sudo apt-get install -y r-base)",
            checkInstalledCommand = "which R",
            launchUrl = null
        ),
        LinuxPackage(
            id = "julia",
            name = "Julia Scientific Language",
            category = PackageCategory.RUNTIMES,
            version = "1.x",
            description = "High-level, high-performance dynamic programming language for technical computing.",
            installCommand = "sudo apt-get install -y julia || ((sudo apt-get update || true) && sudo apt-get install -y julia)",
            checkInstalledCommand = "which julia",
            launchUrl = null
        ),
        LinuxPackage(
            id = "erlang",
            name = "Erlang OTP",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Programming language used to build massively scalable soft real-time systems.",
            installCommand = "sudo apt-get install -y erlang || ((sudo apt-get update || true) && sudo apt-get install -y erlang)",
            checkInstalledCommand = "which erl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "elixir",
            name = "Elixir Language",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Dynamic, functional language designed for building scalable and maintainable applications.",
            installCommand = "sudo apt-get install -y elixir || ((sudo apt-get update || true) && sudo apt-get install -y elixir)",
            checkInstalledCommand = "which elixir",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ghc",
            name = "GHC Haskell Compiler",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "The Glasgow Haskell Compiler - state-of-the-art open source compiler for Haskell.",
            installCommand = "sudo apt-get install -y ghc || ((sudo apt-get update || true) && sudo apt-get install -y ghc)",
            checkInstalledCommand = "which ghc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cabal",
            name = "Cabal Haskell Build Tool",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "System for building and packaging Haskell libraries and programs.",
            installCommand = "sudo apt-get install -y cabal-install || ((sudo apt-get update || true) && sudo apt-get install -y cabal-install)",
            checkInstalledCommand = "which cabal",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scala",
            name = "Scala Language",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Combines object-oriented and functional programming in one concise, high-level language.",
            installCommand = "sudo apt-get install -y scala || ((sudo apt-get update || true) && sudo apt-get install -y scala)",
            checkInstalledCommand = "which scala",
            launchUrl = null
        ),
        LinuxPackage(
            id = "kotlin-comp",
            name = "Kotlin Native Compiler",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Modern multiplatform language by JetBrains that makes developers happier.",
            installCommand = "sudo apt-get install -y kotlin || ((sudo apt-get update || true) && sudo apt-get install -y kotlin)",
            checkInstalledCommand = "which kotlinc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "groovy",
            name = "Apache Groovy",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Powerful, optionally typed and dynamic language for the Java platform.",
            installCommand = "sudo apt-get install -y groovy || ((sudo apt-get update || true) && sudo apt-get install -y groovy)",
            checkInstalledCommand = "which groovy",
            launchUrl = null
        ),
        LinuxPackage(
            id = "clojure",
            name = "Clojure Lisp on JVM",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Dynamic, general-purpose programming language combining functional programming with JVM.",
            installCommand = "sudo apt-get install -y clojure || ((sudo apt-get update || true) && sudo apt-get install -y clojure)",
            checkInstalledCommand = "which clojure",
            launchUrl = null
        ),
        LinuxPackage(
            id = "swi-prolog",
            name = "SWI-Prolog",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Comprehensive Prolog environment for artificial intelligence and symbolic reasoning.",
            installCommand = "sudo apt-get install -y swi-prolog || ((sudo apt-get update || true) && sudo apt-get install -y swi-prolog)",
            checkInstalledCommand = "which swipl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tcl",
            name = "Tcl (Tool Command Language)",
            category = PackageCategory.RUNTIMES,
            version = "8.6",
            description = "Very powerful yet easy to learn dynamic programming language.",
            installCommand = "sudo apt-get install -y tcl || ((sudo apt-get update || true) && sudo apt-get install -y tcl)",
            checkInstalledCommand = "which tclsh",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nasm",
            name = "NASM Netwide Assembler",
            category = PackageCategory.RUNTIMES,
            version = "2.16+",
            description = "An 80x86 and x86-64 assembler designed for portability and modularity.",
            installCommand = "sudo apt-get install -y nasm || ((sudo apt-get update || true) && sudo apt-get install -y nasm)",
            checkInstalledCommand = "which nasm",
            launchUrl = null
        ),
        LinuxPackage(
            id = "yasm",
            name = "Yasm Modular Assembler",
            category = PackageCategory.RUNTIMES,
            version = "1.3+",
            description = "Complete rewrite of the NASM assembler under the 'new' BSD License.",
            installCommand = "sudo apt-get install -y yasm || ((sudo apt-get update || true) && sudo apt-get install -y yasm)",
            checkInstalledCommand = "which yasm",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fasm",
            name = "Flat Assembler (FASM)",
            category = PackageCategory.RUNTIMES,
            version = "Latest",
            description = "Fast and efficient self-assembling 80x86 assembler for DOS, Windows and Linux.",
            installCommand = "sudo apt-get install -y fasm || ((sudo apt-get update || true) && sudo apt-get install -y fasm)",
            checkInstalledCommand = "which fasm",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dart",
            name = "Dart SDK",
            category = PackageCategory.RUNTIMES,
            version = "3.x",
            description = "Client-optimized language for fast apps on any platform by Google.",
            installCommand = "sudo apt-get install -y dart || ((sudo apt-get update || true) && sudo apt-get install -y dart)",
            checkInstalledCommand = "which dart",
            launchUrl = null
        ),
        LinuxPackage(
            id = "zig",
            name = "Zig Programming Language",
            category = PackageCategory.RUNTIMES,
            version = "0.12+",
            description = "General-purpose programming language and toolchain for maintaining robust, optimal software.",
            installCommand = "sudo apt-get install -y zig || ((sudo apt-get update || true) && sudo apt-get install -y zig)",
            checkInstalledCommand = "which zig",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fortran",
            name = "GFortran Compiler",
            category = PackageCategory.RUNTIMES,
            version = "GNU 13+",
            description = "The GNU Fortran 95/2003/2008 compiler for scientific and numerical calculation.",
            installCommand = "sudo apt-get install -y gfortran || ((sudo apt-get update || true) && sudo apt-get install -y gfortran)",
            checkInstalledCommand = "which gfortran",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bison",
            name = "GNU Bison Parser Generator",
            category = PackageCategory.RUNTIMES,
            version = "3.8+",
            description = "General-purpose parser generator that converts an annotated context-free grammar.",
            installCommand = "sudo apt-get install -y bison || ((sudo apt-get update || true) && sudo apt-get install -y bison)",
            checkInstalledCommand = "which bison",
            launchUrl = null
        ),
        LinuxPackage(
            id = "flex",
            name = "Flex Fast Lexical Analyzer",
            category = PackageCategory.RUNTIMES,
            version = "2.6+",
            description = "Tool for generating programs that perform pattern-matching on text.",
            installCommand = "sudo apt-get install -y flex || ((sudo apt-get update || true) && sudo apt-get install -y flex)",
            checkInstalledCommand = "which flex",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gperf",
            name = "GNU Gperf Perfect Hash",
            category = PackageCategory.RUNTIMES,
            version = "3.1+",
            description = "Perfect hash function generator from a key set.",
            installCommand = "sudo apt-get install -y gperf || ((sudo apt-get update || true) && sudo apt-get install -y gperf)",
            checkInstalledCommand = "which gperf",
            launchUrl = null
        )
        )
    }

    private val devToolsPackages: List<LinuxPackage> by lazy {
        listOf(
        LinuxPackage(
            id = "antigravity-cli",
            name = "Google Anti-Gravity CLI (agy)",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Next-generation autonomous agentic AI pair programmer and CLI workspace manager by Google DeepMind.",
            installCommand = "curl -fsSL https://antigravity.google/cli/install.sh | bash && export PATH=\"/home/ubuntu/.local/bin:\$PATH\"",
            checkInstalledCommand = "test -x /home/ubuntu/.local/bin/agy || which agy",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gemini-cli",
            name = "Google Gemini CLI",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Official command-line AI assistant powered by Google Gemini models for terminal code generation and chat.",
            installCommand = "export PATH=\"/home/ubuntu/miniforge3/bin:\$HOME/.local/bin:/usr/local/bin:\$PATH\"; which gemini >/dev/null 2>&1 || ((which node >/dev/null 2>&1 && which npm >/dev/null 2>&1) || (sudo apt-get install -y nodejs npm || ((sudo apt-get update || true) && sudo apt-get install -y nodejs npm)); (sudo npm install -g --unsafe-perm=true --engine-strict=false @google/gemini-cli || sudo npm install -g @google/gemini-cli || pip install --break-system-packages gemini-cli || pip3 install --break-system-packages gemini-cli)); which gemini >/dev/null 2>&1 || [ -f \"\$(npm config get prefix 2>/dev/null)/bin/gemini\" ] && sudo ln -sf \"\$(npm config get prefix)/bin/gemini\" /usr/local/bin/gemini || true",
            checkInstalledCommand = "which gemini || test -f /usr/local/bin/gemini || test -f /usr/bin/gemini || test -f /home/ubuntu/.local/bin/gemini || test -f /home/ubuntu/miniforge3/bin/gemini || test -f /root/.local/bin/gemini",
            launchUrl = null
        ),
        LinuxPackage(
            id = "google-cloud-sdk",
            name = "Google Cloud Code / SDK (gcloud)",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Official CLI for Google Cloud services, Cloud Code integrations, and compute clusters.",
            installCommand = "which gcloud >/dev/null 2>&1 || ((sudo apt-get install -y apt-transport-https ca-certificates gnupg curl || ((sudo apt-get update || true) && sudo apt-get install -y apt-transport-https ca-certificates gnupg curl)) && sudo mkdir -p /usr/share/keyrings && (curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor --yes -o /usr/share/keyrings/cloud.google.gpg) && echo \"deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main\" | sudo tee /etc/apt/sources.list.d/google-cloud-sdk.list && (sudo apt-get update || true) && sudo apt-get install -y google-cloud-cli) || (curl -fsSL https://sdk.cloud.google.com | bash --disable-prompts --install-dir=/home/ubuntu && sudo ln -sf /home/ubuntu/google-cloud-sdk/bin/gcloud /usr/local/bin/gcloud)",
            checkInstalledCommand = "which gcloud || test -f /usr/local/bin/gcloud || test -f /usr/bin/gcloud || test -f /home/ubuntu/google-cloud-sdk/bin/gcloud",
            launchUrl = null
        ),
        LinuxPackage(
            id = "claude-code",
            name = "Claude Code CLI (Anthropic)",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Agentic terminal coding tool by Anthropic that deeply understands your codebase and performs complex refactoring.",
            installCommand = "export PATH=\"/home/ubuntu/miniforge3/bin:\$HOME/.local/bin:/usr/local/bin:\$PATH\"; which claude >/dev/null 2>&1 || ((which node >/dev/null 2>&1 && which npm >/dev/null 2>&1) || (sudo apt-get install -y nodejs npm || ((sudo apt-get update || true) && sudo apt-get install -y nodejs npm)); (sudo npm install -g --unsafe-perm=true --engine-strict=false @anthropic-ai/claude-code || sudo npm install -g @anthropic-ai/claude-code)); which claude >/dev/null 2>&1 || [ -f \"\$(npm config get prefix 2>/dev/null)/bin/claude\" ] && sudo ln -sf \"\$(npm config get prefix)/bin/claude\" /usr/local/bin/claude || true",
            checkInstalledCommand = "which claude || test -f /usr/local/bin/claude || test -f /usr/bin/claude || test -f /home/ubuntu/.local/bin/claude || test -f /home/ubuntu/miniforge3/bin/claude || test -f /root/.local/bin/claude",
            launchUrl = null
        ),
        LinuxPackage(
            id = "aider-chat",
            name = "Aider AI Pair Programmer",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Top-rated AI coding agent that pair-programs with you in terminal, edits files, and creates clean git commits.",
            installCommand = "export PATH=\"/home/ubuntu/miniforge3/bin:\$HOME/.local/bin:\$PATH\" && (pip install --break-system-packages aider-chat || /home/ubuntu/miniforge3/bin/pip install aider-chat || pip3 install --break-system-packages aider-chat)",
            checkInstalledCommand = "which aider || test -f /home/ubuntu/.local/bin/aider || test -f /home/ubuntu/miniforge3/bin/aider || test -f /root/.local/bin/aider",
            launchUrl = null
        ),
        LinuxPackage(
            id = "open-interpreter",
            name = "OpenCode / Open Interpreter",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Open-source natural language code execution agent that runs Python, Bash, and JavaScript locally.",
            installCommand = "export PATH=\"/home/ubuntu/miniforge3/bin:\$HOME/.local/bin:\$PATH\" && (pip install --break-system-packages open-interpreter || /home/ubuntu/miniforge3/bin/pip install open-interpreter || pip3 install --break-system-packages open-interpreter)",
            checkInstalledCommand = "which interpreter || test -f /home/ubuntu/.local/bin/interpreter || test -f /home/ubuntu/miniforge3/bin/interpreter || test -f /root/.local/bin/interpreter",
            launchUrl = null
        ),
        LinuxPackage(
            id = "chatdev",
            name = "ChatDev Multi-Agent CLI",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Communicative agentic software development framework by OpenBMB & Tsinghua for multi-agent code generation.",
            installCommand = "export PATH=\"/home/ubuntu/miniforge3/bin:\$HOME/.local/bin:\$PATH\" && (pip install --break-system-packages chatdev || /home/ubuntu/miniforge3/bin/pip install chatdev || pip3 install --break-system-packages chatdev)",
            checkInstalledCommand = "which chatdev || test -f /home/ubuntu/.local/bin/chatdev || test -f /home/ubuntu/miniforge3/bin/chatdev || python3 -c 'import chatdev'",
            launchUrl = null
        ),
        LinuxPackage(
            id = "qwen-agent",
            name = "Qwen-Agent Framework",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Alibaba Qwen LLM agent framework for tool calling, complex reasoning, and code automation in CLI.",
            installCommand = "export PATH=\"/home/ubuntu/miniforge3/bin:\$HOME/.local/bin:\$PATH\" && (pip install --break-system-packages qwen-agent || /home/ubuntu/miniforge3/bin/pip install qwen-agent || pip3 install --break-system-packages qwen-agent)",
            checkInstalledCommand = "python3 -c 'import qwen_agent'",
            launchUrl = null
        ),
        LinuxPackage(
            id = "git",
            name = "Git Version Control",
            category = PackageCategory.DEV_TOOLS,
            version = "2.4x",
            description = "Fast, scalable, distributed revision control system with rich branching and staging.",
            installCommand = "sudo apt-get install -y git || ((sudo apt-get update || true) && sudo apt-get install -y git)",
            checkInstalledCommand = "which git",
            launchUrl = null
        ),
        LinuxPackage(
            id = "git-lfs",
            name = "Git Large File Storage (LFS)",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Replaces large files such as audio, video and datasets with text pointers inside Git.",
            installCommand = "sudo apt-get install -y git-lfs || ((sudo apt-get update || true) && sudo apt-get install -y git-lfs)",
            checkInstalledCommand = "which git-lfs",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gh",
            name = "GitHub CLI (gh)",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Take GitHub to the command line with issues, pull requests, and repository management.",
            installCommand = "sudo apt-get install -y gh || ((sudo apt-get update || true) && sudo apt-get install -y gh)",
            checkInstalledCommand = "which gh",
            launchUrl = null
        ),
        LinuxPackage(
            id = "build-essential",
            name = "Build Essential",
            category = PackageCategory.DEV_TOOLS,
            version = "Ubuntu Metapackage",
            description = "Meta-package including GCC, G++, Make, and libc dev headers required for compilation.",
            installCommand = "sudo apt-get install -y build-essential || ((sudo apt-get update || true) && sudo apt-get install -y build-essential)",
            checkInstalledCommand = "which make && which gcc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cmake",
            name = "CMake Build System",
            category = PackageCategory.DEV_TOOLS,
            version = "3.28+",
            description = "Cross-platform open-source meta-build system to generate native Makefiles and Ninja files.",
            installCommand = "sudo apt-get install -y cmake || ((sudo apt-get update || true) && sudo apt-get install -y cmake)",
            checkInstalledCommand = "which cmake",
            launchUrl = null
        ),
        LinuxPackage(
            id = "make",
            name = "GNU Make",
            category = PackageCategory.DEV_TOOLS,
            version = "4.3+",
            description = "Directs compilation and generates executables from source code via Makefiles.",
            installCommand = "sudo apt-get install -y make || ((sudo apt-get update || true) && sudo apt-get install -y make)",
            checkInstalledCommand = "which make",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ninja-build",
            name = "Ninja Build Accelerator",
            category = PackageCategory.DEV_TOOLS,
            version = "1.11+",
            description = "Small build system with a focus on speed, often used with CMake and Meson.",
            installCommand = "sudo apt-get install -y ninja-build || ((sudo apt-get update || true) && sudo apt-get install -y ninja-build)",
            checkInstalledCommand = "which ninja",
            launchUrl = null
        ),
        LinuxPackage(
            id = "meson",
            name = "Meson Build System",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Open source build system meant to be both extremely fast and user friendly.",
            installCommand = "sudo apt-get install -y meson || ((sudo apt-get update || true) && sudo apt-get install -y meson)",
            checkInstalledCommand = "which meson",
            launchUrl = null
        ),
        LinuxPackage(
            id = "autoconf",
            name = "GNU Autoconf",
            category = PackageCategory.DEV_TOOLS,
            version = "2.7x",
            description = "Extensible package of M4 macros that produce shell scripts to configure source packages.",
            installCommand = "sudo apt-get install -y autoconf || ((sudo apt-get update || true) && sudo apt-get install -y autoconf)",
            checkInstalledCommand = "which autoconf",
            launchUrl = null
        ),
        LinuxPackage(
            id = "automake",
            name = "GNU Automake",
            category = PackageCategory.DEV_TOOLS,
            version = "1.16+",
            description = "Tool for creating GNU Standards-compliant Makefiles from template files.",
            installCommand = "sudo apt-get install -y automake || ((sudo apt-get update || true) && sudo apt-get install -y automake)",
            checkInstalledCommand = "which automake",
            launchUrl = null
        ),
        LinuxPackage(
            id = "libtool",
            name = "GNU Libtool",
            category = PackageCategory.DEV_TOOLS,
            version = "2.4+",
            description = "Generic library support script that hides the complexity of using shared libraries.",
            installCommand = "sudo apt-get install -y libtool || ((sudo apt-get update || true) && sudo apt-get install -y libtool)",
            checkInstalledCommand = "which libtool",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pkg-config",
            name = "Pkg-Config",
            category = PackageCategory.DEV_TOOLS,
            version = "0.29+",
            description = "Helps configure compiler and linker flags for development libraries.",
            installCommand = "sudo apt-get install -y pkg-config || ((sudo apt-get update || true) && sudo apt-get install -y pkg-config)",
            checkInstalledCommand = "which pkg-config",
            launchUrl = null
        ),
        LinuxPackage(
            id = "valgrind",
            name = "Valgrind Memory Debugger",
            category = PackageCategory.DEV_TOOLS,
            version = "3.22+",
            description = "Instrumentation framework for building dynamic analysis tools to detect memory leaks.",
            installCommand = "sudo apt-get install -y valgrind || ((sudo apt-get update || true) && sudo apt-get install -y valgrind)",
            checkInstalledCommand = "which valgrind",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cppcheck",
            name = "Cppcheck Static Analyzer",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Static analysis tool for C/C++ code that detects bugs and undefined behavior.",
            installCommand = "sudo apt-get install -y cppcheck || ((sudo apt-get update || true) && sudo apt-get install -y cppcheck)",
            checkInstalledCommand = "which cppcheck",
            launchUrl = null
        ),
        LinuxPackage(
            id = "clang-format",
            name = "Clang Format",
            category = PackageCategory.DEV_TOOLS,
            version = "LLVM",
            description = "Tool to format C/C++/Java/JavaScript/JSON/Objective-C/Protobuf/C# code.",
            installCommand = "sudo apt-get install -y clang-format || ((sudo apt-get update || true) && sudo apt-get install -y clang-format)",
            checkInstalledCommand = "which clang-format",
            launchUrl = null
        ),
        LinuxPackage(
            id = "clang-tidy",
            name = "Clang Tidy Linter",
            category = PackageCategory.DEV_TOOLS,
            version = "LLVM",
            description = "Clang-based C++ linter tool for detecting style violations and bugs.",
            installCommand = "sudo apt-get install -y clang-tidy || ((sudo apt-get update || true) && sudo apt-get install -y clang-tidy)",
            checkInstalledCommand = "which clang-tidy",
            launchUrl = null
        ),
        LinuxPackage(
            id = "vim",
            name = "Vim Text Editor",
            category = PackageCategory.DEV_TOOLS,
            version = "9.x",
            description = "Vi IMproved - heavily configurable text editor built to make creating and changing text efficient.",
            installCommand = "sudo apt-get install -y vim || ((sudo apt-get update || true) && sudo apt-get install -y vim)",
            checkInstalledCommand = "which vim",
            launchUrl = null
        ),
        LinuxPackage(
            id = "neovim",
            name = "Neovim (nvim)",
            category = PackageCategory.DEV_TOOLS,
            version = "0.9+",
            description = "Vim-fork focused on extensibility and usability with Lua configuration support.",
            installCommand = "sudo apt-get install -y neovim || ((sudo apt-get update || true) && sudo apt-get install -y neovim)",
            checkInstalledCommand = "which nvim",
            launchUrl = null
        ),
        LinuxPackage(
            id = "emacs",
            name = "Emacs (Terminal)",
            category = PackageCategory.DEV_TOOLS,
            version = "29+",
            description = "The extensible, customizable, self-documenting real-time display editor (CLI mode).",
            installCommand = "sudo apt-get install -y emacs-nox || ((sudo apt-get update || true) && sudo apt-get install -y emacs-nox)",
            checkInstalledCommand = "which emacs",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nano",
            name = "Nano Text Editor",
            category = PackageCategory.DEV_TOOLS,
            version = "7.x",
            description = "Small, friendly text editor inspired by Pico with syntax highlighting.",
            installCommand = "sudo apt-get install -y nano || ((sudo apt-get update || true) && sudo apt-get install -y nano)",
            checkInstalledCommand = "which nano",
            launchUrl = null
        ),
        LinuxPackage(
            id = "micro",
            name = "Micro Terminal Editor",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Modern and intuitive terminal-based text editor with easy mouse support and keybindings.",
            installCommand = "sudo apt-get install -y micro || ((sudo apt-get update || true) && sudo apt-get install -y micro)",
            checkInstalledCommand = "which micro",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tmux",
            name = "Tmux Multiplexer",
            category = PackageCategory.DEV_TOOLS,
            version = "3.4+",
            description = "Terminal multiplexer to switch easily between several programs in one terminal.",
            installCommand = "sudo apt-get install -y tmux || ((sudo apt-get update || true) && sudo apt-get install -y tmux)",
            checkInstalledCommand = "which tmux",
            launchUrl = null
        ),
        LinuxPackage(
            id = "screen",
            name = "GNU Screen",
            category = PackageCategory.DEV_TOOLS,
            version = "4.9+",
            description = "Full-screen window manager that multiplexes a physical terminal between several processes.",
            installCommand = "sudo apt-get install -y screen || ((sudo apt-get update || true) && sudo apt-get install -y screen)",
            checkInstalledCommand = "which screen",
            launchUrl = null
        ),
        LinuxPackage(
            id = "zsh",
            name = "Zsh & Oh-My-Zsh",
            category = PackageCategory.DEV_TOOLS,
            version = "5.9+",
            description = "Advanced command interpreter (shell) with rich tab completion and theme support.",
            installCommand = "sudo apt-get install -y zsh || ((sudo apt-get update || true) && sudo apt-get install -y zsh)",
            checkInstalledCommand = "which zsh",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fish",
            name = "Fish Friendly Interactive Shell",
            category = PackageCategory.DEV_TOOLS,
            version = "3.7+",
            description = "Smart and user-friendly command line shell with syntax highlighting and autosuggestions.",
            installCommand = "sudo apt-get install -y fish || ((sudo apt-get update || true) && sudo apt-get install -y fish)",
            checkInstalledCommand = "which fish",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bash-completion",
            name = "Bash Completion",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Programmable completion functions for bash to autocomplete commands and options.",
            installCommand = "sudo apt-get install -y bash-completion || ((sudo apt-get update || true) && sudo apt-get install -y bash-completion)",
            checkInstalledCommand = "[ -f /usr/share/bash-completion/bash_completion ]",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fzf",
            name = "FZF Command-line Fuzzy Finder",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "General-purpose command-line fuzzy finder for files, history, and processes.",
            installCommand = "sudo apt-get install -y fzf || ((sudo apt-get update || true) && sudo apt-get install -y fzf)",
            checkInstalledCommand = "which fzf",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ripgrep",
            name = "Ripgrep (rg)",
            category = PackageCategory.DEV_TOOLS,
            version = "14+",
            description = "Fast line-oriented search tool that recursively searches current directory for regex pattern.",
            installCommand = "sudo apt-get install -y ripgrep || ((sudo apt-get update || true) && sudo apt-get install -y ripgrep)",
            checkInstalledCommand = "which rg",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fd-find",
            name = "FD Fast Find",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Simple, fast and user-friendly alternative to find command written in Rust.",
            installCommand = "sudo apt-get install -y fd-find || ((sudo apt-get update || true) && sudo apt-get install -y fd-find)",
            checkInstalledCommand = "which fdfind || which fd",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bat",
            name = "Bat (Cat with Wings)",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Cat clone with syntax highlighting and Git integration for code inspection.",
            installCommand = "sudo apt-get install -y bat || ((sudo apt-get update || true) && sudo apt-get install -y bat)",
            checkInstalledCommand = "which batcat || which bat",
            launchUrl = null
        ),
        LinuxPackage(
            id = "eza",
            name = "Eza Modern LS",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Modern, maintained replacement for ls with tree view, git status, and icons.",
            installCommand = "sudo apt-get install -y eza || ((sudo apt-get update || true) && sudo apt-get install -y eza)",
            checkInstalledCommand = "which eza",
            launchUrl = null
        ),
        LinuxPackage(
            id = "delta",
            name = "Delta Diff Viewer",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Syntax-highlighting pager for git, diff, and grep output.",
            installCommand = "sudo apt-get install -y git-delta || ((sudo apt-get update || true) && sudo apt-get install -y git-delta)",
            checkInstalledCommand = "which delta",
            launchUrl = null
        ),
        LinuxPackage(
            id = "lazygit",
            name = "Lazygit Terminal UI",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Simple terminal UI for git commands that makes branch management and commits effortless.",
            installCommand = "sudo apt-get install -y lazygit || ((sudo apt-get update || true) && sudo apt-get install -y lazygit)",
            checkInstalledCommand = "which lazygit",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tig",
            name = "Tig Text-Mode Git",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Text-mode interface for Git that functions as a git repository browser.",
            installCommand = "sudo apt-get install -y tig || ((sudo apt-get update || true) && sudo apt-get install -y tig)",
            checkInstalledCommand = "which tig",
            launchUrl = null
        ),
        LinuxPackage(
            id = "jq",
            name = "JQ JSON Processor",
            category = PackageCategory.DEV_TOOLS,
            version = "1.7+",
            description = "Lightweight and flexible command-line JSON processor for slicing, filtering, and mapping.",
            installCommand = "sudo apt-get install -y jq || ((sudo apt-get update || true) && sudo apt-get install -y jq)",
            checkInstalledCommand = "which jq",
            launchUrl = null
        ),
        LinuxPackage(
            id = "yq",
            name = "YQ YAML/JSON/XML Processor",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Portable command-line YAML, JSON, XML, CSV and properties processor.",
            installCommand = "sudo apt-get install -y yq || ((sudo apt-get update || true) && sudo apt-get install -y yq)",
            checkInstalledCommand = "which yq",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fx",
            name = "Fx Terminal JSON Viewer",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Terminal JSON viewer & interactive processing tool.",
            installCommand = "npm install -g fx",
            checkInstalledCommand = "which fx",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gron",
            name = "Gron JSON Grepper",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Transforms JSON into discrete assignments to make it easily greppable.",
            installCommand = "sudo apt-get install -y gron || ((sudo apt-get update || true) && sudo apt-get install -y gron)",
            checkInstalledCommand = "which gron",
            launchUrl = null
        ),
        LinuxPackage(
            id = "httpie",
            name = "HTTPie API Client",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "User-friendly CLI HTTP client with JSON support, syntax highlighting, and persistent sessions.",
            installCommand = "sudo apt-get install -y httpie || ((sudo apt-get update || true) && sudo apt-get install -y httpie)",
            checkInstalledCommand = "which http || which httpie",
            launchUrl = null
        ),
        LinuxPackage(
            id = "curlie",
            name = "Curlie Frontend",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "The power of curl with the ease of use of httpie.",
            installCommand = "sudo apt-get install -y curlie || ((sudo apt-get update || true) && sudo apt-get install -y curlie)",
            checkInstalledCommand = "which curlie",
            launchUrl = null
        ),
        LinuxPackage(
            id = "axel",
            name = "Axel Accelerator",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Light-weight download accelerator that opens multiple connections to retrieve a file.",
            installCommand = "sudo apt-get install -y axel || ((sudo apt-get update || true) && sudo apt-get install -y axel)",
            checkInstalledCommand = "which axel",
            launchUrl = null
        ),
        LinuxPackage(
            id = "aria2",
            name = "Aria2 Multi-Source Downloader",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Ultra fast multi-protocol & multi-source, cross platform download utility (HTTP, FTP, BitTorrent).",
            installCommand = "sudo apt-get install -y aria2 || ((sudo apt-get update || true) && sudo apt-get install -y aria2)",
            checkInstalledCommand = "which aria2c",
            launchUrl = null
        ),
        LinuxPackage(
            id = "direnv",
            name = "Direnv Environment Switcher",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Unclutter your .profile and load environment variables depending on current directory.",
            installCommand = "sudo apt-get install -y direnv || ((sudo apt-get update || true) && sudo apt-get install -y direnv)",
            checkInstalledCommand = "which direnv",
            launchUrl = null
        ),
        LinuxPackage(
            id = "shellcheck",
            name = "ShellCheck Linter",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Gives warnings and suggestions for bash/sh shell scripts to avoid common pitfalls.",
            installCommand = "sudo apt-get install -y shellcheck || ((sudo apt-get update || true) && sudo apt-get install -y shellcheck)",
            checkInstalledCommand = "which shellcheck",
            launchUrl = null
        ),
        LinuxPackage(
            id = "shfmt",
            name = "Shfmt Shell Formatter",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Format shell programs according to standard style guidelines.",
            installCommand = "sudo apt-get install -y shfmt || ((sudo apt-get update || true) && sudo apt-get install -y shfmt)",
            checkInstalledCommand = "which shfmt",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ansible",
            name = "Ansible Automation",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Radically simple IT automation system for configuration management and cloud provisioning.",
            installCommand = "sudo apt-get install -y ansible || ((sudo apt-get update || true) && sudo apt-get install -y ansible)",
            checkInstalledCommand = "which ansible",
            launchUrl = null
        ),
        LinuxPackage(
            id = "terraform",
            name = "Terraform CLI",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Infrastructure as code tool to build, change, and version cloud infrastructure safely.",
            installCommand = "sudo apt-get install -y terraform || ((sudo apt-get update || true) && sudo apt-get install -y terraform)",
            checkInstalledCommand = "which terraform",
            launchUrl = null
        ),
        LinuxPackage(
            id = "packer",
            name = "Packer Machine Imaging",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Tool for creating identical machine images for multiple platforms from a single source.",
            installCommand = "sudo apt-get install -y packer || ((sudo apt-get update || true) && sudo apt-get install -y packer)",
            checkInstalledCommand = "which packer",
            launchUrl = null
        ),
        LinuxPackage(
            id = "kubectl",
            name = "Kubectl Kubernetes CLI",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Command line tool for controlling Kubernetes clusters and inspecting pods/services.",
            installCommand = "sudo apt-get install -y kubectl || ((sudo apt-get update || true) && sudo apt-get install -y kubectl)",
            checkInstalledCommand = "which kubectl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "helm",
            name = "Helm Kubernetes Package Manager",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "The package manager for Kubernetes to manage complex apps and charts.",
            installCommand = "sudo apt-get install -y helm || ((sudo apt-get update || true) && sudo apt-get install -y helm)",
            checkInstalledCommand = "which helm",
            launchUrl = null
        ),
        LinuxPackage(
            id = "k9s",
            name = "K9s Terminal Kubernetes UI",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Kubernetes CLI To Manage Your Clusters In Style with curses-based real-time dashboard.",
            installCommand = "sudo apt-get install -y k9s || ((sudo apt-get update || true) && sudo apt-get install -y k9s)",
            checkInstalledCommand = "which k9s",
            launchUrl = null
        ),
        LinuxPackage(
            id = "docker-cli",
            name = "Docker CLI Tools",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Command-line interface to interact with remote and local Docker daemon engines.",
            installCommand = "sudo apt-get install -y docker.io || ((sudo apt-get update || true) && sudo apt-get install -y docker.io)",
            checkInstalledCommand = "which docker",
            launchUrl = null
        ),
        LinuxPackage(
            id = "docker-compose",
            name = "Docker Compose",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Define and run multi-container Docker applications using YAML configuration files.",
            installCommand = "sudo apt-get install -y docker-compose || ((sudo apt-get update || true) && sudo apt-get install -y docker-compose)",
            checkInstalledCommand = "which docker-compose",
            launchUrl = null
        ),
        LinuxPackage(
            id = "podman",
            name = "Podman Container Tool",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Daemonless tool for finding, running, building, sharing and deploying OCI Containers.",
            installCommand = "sudo apt-get install -y podman || ((sudo apt-get update || true) && sudo apt-get install -y podman)",
            checkInstalledCommand = "which podman",
            launchUrl = null
        ),
        LinuxPackage(
            id = "skopeo",
            name = "Skopeo Container Inspector",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Work with remote images registries - retrieving information, images, signing content.",
            installCommand = "sudo apt-get install -y skopeo || ((sudo apt-get update || true) && sudo apt-get install -y skopeo)",
            checkInstalledCommand = "which skopeo",
            launchUrl = null
        ),
        LinuxPackage(
            id = "rclone",
            name = "Rclone Cloud Sync",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Rsync for cloud storage - sync files and directories to and from 40+ cloud storage providers.",
            installCommand = "sudo apt-get install -y rclone || ((sudo apt-get update || true) && sudo apt-get install -y rclone)",
            checkInstalledCommand = "which rclone",
            launchUrl = null
        ),
        LinuxPackage(
            id = "s3cmd",
            name = "S3cmd Amazon S3 Client",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Command line tool for managing Amazon S3 and other compatible cloud storage services.",
            installCommand = "sudo apt-get install -y s3cmd || ((sudo apt-get update || true) && sudo apt-get install -y s3cmd)",
            checkInstalledCommand = "which s3cmd",
            launchUrl = null
        ),
        LinuxPackage(
            id = "awscli",
            name = "AWS Command Line Interface",
            category = PackageCategory.DEV_TOOLS,
            version = "v2",
            description = "Unified tool to manage your Amazon Web Services from the terminal.",
            installCommand = "sudo apt-get install -y awscli || ((sudo apt-get update || true) && sudo apt-get install -y awscli)",
            checkInstalledCommand = "which aws",
            launchUrl = null
        ),
        LinuxPackage(
            id = "checkinstall",
            name = "CheckInstall Package Creator",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Track installations and create native Debian (.deb) packages automatically.",
            installCommand = "sudo apt-get install -y checkinstall || ((sudo apt-get update || true) && sudo apt-get install -y checkinstall)",
            checkInstalledCommand = "which checkinstall",
            launchUrl = null
        ),
        LinuxPackage(
            id = "colordiff",
            name = "Colordiff",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Tool to colorize diff output for improved human readability.",
            installCommand = "sudo apt-get install -y colordiff || ((sudo apt-get update || true) && sudo apt-get install -y colordiff)",
            checkInstalledCommand = "which colordiff",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cloc",
            name = "Cloc Count Lines of Code",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Count lines of source code in many programming languages with comments and blanks breakdown.",
            installCommand = "sudo apt-get install -y cloc || ((sudo apt-get update || true) && sudo apt-get install -y cloc)",
            checkInstalledCommand = "which cloc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "scc",
            name = "Scc Fast Code Counter",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Sloc, Cloc and Code: very fast accurate code counter with complexity estimates.",
            installCommand = "sudo apt-get install -y scc || ((sudo apt-get update || true) && sudo apt-get install -y scc)",
            checkInstalledCommand = "which scc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "hyperfine",
            name = "Hyperfine CLI Benchmark",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Command-line benchmarking tool in Rust with statistical analysis and multi-run benchmarks.",
            installCommand = "sudo apt-get install -y hyperfine || ((sudo apt-get update || true) && sudo apt-get install -y hyperfine)",
            checkInstalledCommand = "which hyperfine",
            launchUrl = null
        ),
        LinuxPackage(
            id = "procs",
            name = "Procs Modern PS",
            category = PackageCategory.DEV_TOOLS,
            version = "Latest",
            description = "Modern replacement for ps written in Rust with color output and human-readable units.",
            installCommand = "sudo apt-get install -y procs || ((sudo apt-get update || true) && sudo apt-get install -y procs)",
            checkInstalledCommand = "which procs",
            launchUrl = null
        )
        )
    }

    private val databasesPackages: List<LinuxPackage> by lazy {
        listOf(
        LinuxPackage(
            id = "sqlite3",
            name = "SQLite3 Embedded Database",
            category = PackageCategory.DATABASES,
            version = "3.45+",
            description = "Serverless, zero-configuration, transactional SQL database engine CLI.",
            installCommand = "sudo apt-get install -y sqlite3 libsqlite3-dev || ((sudo apt-get update || true) && sudo apt-get install -y sqlite3 libsqlite3-dev)",
            checkInstalledCommand = "which sqlite3",
            launchUrl = null
        ),
        LinuxPackage(
            id = "postgresql-client",
            name = "PostgreSQL Client (psql)",
            category = PackageCategory.DATABASES,
            version = "16+",
            description = "Front-end terminal program for querying PostgreSQL database servers.",
            installCommand = "sudo apt-get install -y postgresql-client || ((sudo apt-get update || true) && sudo apt-get install -y postgresql-client)",
            checkInstalledCommand = "which psql",
            launchUrl = null
        ),
        LinuxPackage(
            id = "postgresql",
            name = "PostgreSQL Server",
            category = PackageCategory.DATABASES,
            version = "16+",
            description = "Powerful, open source object-relational database system.",
            installCommand = "sudo apt-get install -y postgresql || ((sudo apt-get update || true) && sudo apt-get install -y postgresql)",
            checkInstalledCommand = "which postgres || which psql",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mariadb-client",
            name = "MariaDB / MySQL Client",
            category = PackageCategory.DATABASES,
            version = "10.x+",
            description = "Command-line client for MariaDB and MySQL relational database management systems.",
            installCommand = "sudo apt-get install -y mariadb-client || ((sudo apt-get update || true) && sudo apt-get install -y mariadb-client)",
            checkInstalledCommand = "which mysql || which mariadb",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mariadb-server",
            name = "MariaDB Server",
            category = PackageCategory.DATABASES,
            version = "10.x+",
            description = "Community developed fork of MySQL relational database management system.",
            installCommand = "sudo apt-get install -y mariadb-server || ((sudo apt-get update || true) && sudo apt-get install -y mariadb-server)",
            checkInstalledCommand = "which mysqld || which mariadbd",
            launchUrl = null
        ),
        LinuxPackage(
            id = "redis-tools",
            name = "Redis Tools & CLI",
            category = PackageCategory.DATABASES,
            version = "7.x+",
            description = "Client command line tool (redis-cli) and benchmark utilities for Redis.",
            installCommand = "sudo apt-get install -y redis-tools || ((sudo apt-get update || true) && sudo apt-get install -y redis-tools)",
            checkInstalledCommand = "which redis-cli",
            launchUrl = null
        ),
        LinuxPackage(
            id = "redis-server",
            name = "Redis In-Memory Server",
            category = PackageCategory.DATABASES,
            version = "7.x+",
            description = "In-memory data structure store used as a database, cache, message broker, and queue.",
            installCommand = "sudo apt-get install -y redis-server || ((sudo apt-get update || true) && sudo apt-get install -y redis-server)",
            checkInstalledCommand = "which redis-server",
            launchUrl = null
        ),
        LinuxPackage(
            id = "memcached",
            name = "Memcached",
            category = PackageCategory.DATABASES,
            version = "1.6+",
            description = "High-performance, distributed memory object caching system for speeding up dynamic web applications.",
            installCommand = "sudo apt-get install -y memcached || ((sudo apt-get update || true) && sudo apt-get install -y memcached)",
            checkInstalledCommand = "which memcached",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nginx",
            name = "Nginx HTTP & Reverse Proxy",
            category = PackageCategory.DATABASES,
            version = "1.24+",
            description = "High-performance HTTP server, reverse proxy, and IMAP/POP3 proxy server.",
            installCommand = "sudo apt-get install -y nginx || ((sudo apt-get update || true) && sudo apt-get install -y nginx)",
            checkInstalledCommand = "which nginx",
            launchUrl = "http://127.0.0.1:80"
        ),
        LinuxPackage(
            id = "apache2",
            name = "Apache2 HTTP Server",
            category = PackageCategory.DATABASES,
            version = "2.4+",
            description = "The most popular web server on the Internet with support for modular extensions and CGI.",
            installCommand = "sudo apt-get install -y apache2 || ((sudo apt-get update || true) && sudo apt-get install -y apache2)",
            checkInstalledCommand = "which apache2",
            launchUrl = "http://127.0.0.1:80"
        ),
        LinuxPackage(
            id = "caddy",
            name = "Caddy Web Server",
            category = PackageCategory.DATABASES,
            version = "2.x",
            description = "Enterprise-ready, open source web server with automatic HTTPS written in Go.",
            installCommand = "sudo apt-get install -y caddy || ((sudo apt-get update || true) && sudo apt-get install -y caddy)",
            checkInstalledCommand = "which caddy",
            launchUrl = "http://127.0.0.1:2019"
        ),
        LinuxPackage(
            id = "lighttpd",
            name = "Lighttpd Fast Server",
            category = PackageCategory.DATABASES,
            version = "1.4+",
            description = "Web server optimized for speed-critical environments with very low memory footprint.",
            installCommand = "sudo apt-get install -y lighttpd || ((sudo apt-get update || true) && sudo apt-get install -y lighttpd)",
            checkInstalledCommand = "which lighttpd",
            launchUrl = "http://127.0.0.1:80"
        ),
        LinuxPackage(
            id = "haproxy",
            name = "HAProxy Load Balancer",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Reliable, high performance TCP/HTTP load balancer and proxying server.",
            installCommand = "sudo apt-get install -y haproxy || ((sudo apt-get update || true) && sudo apt-get install -y haproxy)",
            checkInstalledCommand = "which haproxy",
            launchUrl = null
        ),
        LinuxPackage(
            id = "squid",
            name = "Squid Proxy Cache",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Full-featured Web proxy cache server application which provides proxy and cache services.",
            installCommand = "sudo apt-get install -y squid || ((sudo apt-get update || true) && sudo apt-get install -y squid)",
            checkInstalledCommand = "which squid",
            launchUrl = null
        ),
        LinuxPackage(
            id = "privoxy",
            name = "Privoxy Privacy Proxy",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Non-caching web proxy with advanced filtering capabilities for enhancing privacy.",
            installCommand = "sudo apt-get install -y privoxy || ((sudo apt-get update || true) && sudo apt-get install -y privoxy)",
            checkInstalledCommand = "which privoxy",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dnsmasq",
            name = "Dnsmasq DNS/DHCP",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Lightweight, easy to configure DNS forwarder and DHCP server designed for small networks.",
            installCommand = "sudo apt-get install -y dnsmasq || ((sudo apt-get update || true) && sudo apt-get install -y dnsmasq)",
            checkInstalledCommand = "which dnsmasq",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bind9-dnsutils",
            name = "DNS Query Utilities (dig, nslookup)",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Clients for querying DNS name servers: dig, host, and nslookup.",
            installCommand = "sudo apt-get install -y bind9-dnsutils || ((sudo apt-get update || true) && sudo apt-get install -y bind9-dnsutils)",
            checkInstalledCommand = "which dig && which nslookup",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mosquitto",
            name = "Mosquitto MQTT Broker",
            category = PackageCategory.DATABASES,
            version = "2.0+",
            description = "Open source message broker that implements the MQTT protocol versions 5.0, 3.1.1 and 3.1.",
            installCommand = "sudo apt-get install -y mosquitto mosquitto-clients || ((sudo apt-get update || true) && sudo apt-get install -y mosquitto mosquitto-clients)",
            checkInstalledCommand = "which mosquitto && which mosquitto_pub",
            launchUrl = null
        ),
        LinuxPackage(
            id = "rabbitmq",
            name = "RabbitMQ Server",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Robust and highly scalable AMQP message broker written in Erlang.",
            installCommand = "sudo apt-get install -y rabbitmq-server || ((sudo apt-get update || true) && sudo apt-get install -y rabbitmq-server)",
            checkInstalledCommand = "which rabbitmqctl",
            launchUrl = "http://127.0.0.1:15672"
        ),
        LinuxPackage(
            id = "zeromq",
            name = "ZeroMQ Messaging Library",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "High-performance asynchronous messaging library aimed at use in distributed applications.",
            installCommand = "sudo apt-get install -y libzmq3-dev || ((sudo apt-get update || true) && sudo apt-get install -y libzmq3-dev)",
            checkInstalledCommand = "[ -f /usr/include/zmq.h ]",
            launchUrl = null
        ),
        LinuxPackage(
            id = "etcd-client",
            name = "Etcd Client",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Distributed, reliable key-value store for the most critical data of a distributed system.",
            installCommand = "sudo apt-get install -y etcd-client || ((sudo apt-get update || true) && sudo apt-get install -y etcd-client)",
            checkInstalledCommand = "which etcdctl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "consul",
            name = "Consul Service Mesh",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Distributed, highly available, and data center-aware tool for service discovery and config.",
            installCommand = "sudo apt-get install -y consul || ((sudo apt-get update || true) && sudo apt-get install -y consul)",
            checkInstalledCommand = "which consul",
            launchUrl = "http://127.0.0.1:8500"
        ),
        LinuxPackage(
            id = "vault",
            name = "HashiCorp Vault",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Tool for secrets management, encryption as a service, and privileged access management.",
            installCommand = "sudo apt-get install -y vault || ((sudo apt-get update || true) && sudo apt-get install -y vault)",
            checkInstalledCommand = "which vault",
            launchUrl = "http://127.0.0.1:8200"
        ),
        LinuxPackage(
            id = "influxdb-client",
            name = "InfluxDB CLI",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Command-line interface for InfluxDB time series database.",
            installCommand = "sudo apt-get install -y influxdb-client || ((sudo apt-get update || true) && sudo apt-get install -y influxdb-client)",
            checkInstalledCommand = "which influx",
            launchUrl = null
        ),
        LinuxPackage(
            id = "prometheus",
            name = "Prometheus Monitoring",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Systems monitoring and alerting toolkit with dimensional data model and PromQL.",
            installCommand = "sudo apt-get install -y prometheus || ((sudo apt-get update || true) && sudo apt-get install -y prometheus)",
            checkInstalledCommand = "which prometheus",
            launchUrl = "http://127.0.0.1:9090"
        ),
        LinuxPackage(
            id = "grafana",
            name = "Grafana Analytics Dashboard",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Operational dashboards for your data here, there, or anywhere.",
            installCommand = "sudo apt-get install -y grafana || ((sudo apt-get update || true) && sudo apt-get install -y grafana)",
            checkInstalledCommand = "which grafana-server",
            launchUrl = "http://127.0.0.1:3000"
        ),
        LinuxPackage(
            id = "certbot",
            name = "Certbot SSL Automator",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "EFF's tool to obtain certs from Let's Encrypt and auto-enable HTTPS on web servers.",
            installCommand = "sudo apt-get install -y certbot || ((sudo apt-get update || true) && sudo apt-get install -y certbot)",
            checkInstalledCommand = "which certbot",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mkcert",
            name = "Mkcert Local CA",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Zero-config tool to make locally trusted development certificates with any names you'd like.",
            installCommand = "sudo apt-get install -y mkcert || ((sudo apt-get update || true) && sudo apt-get install -y mkcert)",
            checkInstalledCommand = "which mkcert",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pgcli",
            name = "PgCLI PostgreSQL Terminal",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Postgres CLI with autocompletion and syntax highlighting for productive SQL writing.",
            installCommand = "(sudo apt-get install -y pgcli || ((sudo apt-get update || true) && sudo apt-get install -y pgcli)) || pip3 install --break-system-packages --no-cache-dir pgcli",
            checkInstalledCommand = "which pgcli",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mycli",
            name = "MyCLI MySQL Terminal",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "MySQL and MariaDB CLI with autocompletion and syntax highlighting.",
            installCommand = "(sudo apt-get install -y mycli || ((sudo apt-get update || true) && sudo apt-get install -y mycli)) || pip3 install --break-system-packages --no-cache-dir mycli",
            checkInstalledCommand = "which mycli",
            launchUrl = null
        ),
        LinuxPackage(
            id = "litecli",
            name = "LiteCLI SQLite Terminal",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "CLI for SQLite Databases with Auto-completion and Syntax Highlighting.",
            installCommand = "pip3 install --break-system-packages --no-cache-dir litecli",
            checkInstalledCommand = "which litecli",
            launchUrl = null
        ),
        LinuxPackage(
            id = "iredis",
            name = "IRedis Interactive Client",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "A Terminal Client for Redis with AutoCompletion and Syntax Highlighting.",
            installCommand = "pip3 install --break-system-packages --no-cache-dir iredis",
            checkInstalledCommand = "which iredis",
            launchUrl = null
        ),
        LinuxPackage(
            id = "usql",
            name = "USQL Universal Database CLI",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Universal command-line interface for SQL databases: PostgreSQL, MySQL, SQLite, Oracle, etc.",
            installCommand = "sudo apt-get install -y usql || ((sudo apt-get update || true) && sudo apt-get install -y usql)",
            checkInstalledCommand = "which usql",
            launchUrl = null
        ),
        LinuxPackage(
            id = "k6",
            name = "k6 Load Testing Tool",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Modern load testing tool, using Go and JavaScript for developer happiness.",
            installCommand = "sudo apt-get install -y k6 || ((sudo apt-get update || true) && sudo apt-get install -y k6)",
            checkInstalledCommand = "which k6",
            launchUrl = null
        ),
        LinuxPackage(
            id = "wrk",
            name = "Wrk HTTP Benchmark",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Modern HTTP benchmarking tool capable of generating significant load when run on a single multi-core CPU.",
            installCommand = "sudo apt-get install -y wrk || ((sudo apt-get update || true) && sudo apt-get install -y wrk)",
            checkInstalledCommand = "which wrk",
            launchUrl = null
        ),
        LinuxPackage(
            id = "apache2-utils",
            name = "ApacheBench (ab)",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Apache HTTP server benchmarking tool for measuring web server requests-per-second.",
            installCommand = "sudo apt-get install -y apache2-utils || ((sudo apt-get update || true) && sudo apt-get install -y apache2-utils)",
            checkInstalledCommand = "which ab",
            launchUrl = null
        ),
        LinuxPackage(
            id = "vegeta",
            name = "Vegeta HTTP Load Tester",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Versatile HTTP load testing tool built out of a need to drill HTTP services with constant request rates.",
            installCommand = "sudo apt-get install -y vegeta || ((sudo apt-get update || true) && sudo apt-get install -y vegeta)",
            checkInstalledCommand = "which vegeta",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cockroachdb",
            name = "CockroachDB SQL CLI",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Distributed SQL database designed for cloud resilience and scale.",
            installCommand = "which cockroach || echo 'installed via download'",
            checkInstalledCommand = "which cockroach",
            launchUrl = null
        ),
        LinuxPackage(
            id = "surrealdb",
            name = "SurrealDB Multi-Model DB",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Ultimate multi-model database for tomorrow's applications with SQL-like query language.",
            installCommand = "curl -sSf https://install.surrealdb.com | sh",
            checkInstalledCommand = "which surreal",
            launchUrl = null
        ),
        LinuxPackage(
            id = "meilisearch",
            name = "Meilisearch Search Engine",
            category = PackageCategory.DATABASES,
            version = "Latest",
            description = "Lightning-fast, ultra-relevant and typo-tolerant search engine API.",
            installCommand = "curl -sSf https://install.meilisearch.com | sh",
            checkInstalledCommand = "which meilisearch",
            launchUrl = "http://127.0.0.1:7700"
        )
        )
    }

    private val utilitiesPackages: List<LinuxPackage> by lazy {
        listOf(
        LinuxPackage(
            id = "curl",
            name = "cURL Transfer Utility",
            category = PackageCategory.UTILITIES,
            version = "8.5+",
            description = "Command-line tool for transferring data with URLs using HTTP, HTTPS, FTP, FTPS, and more.",
            installCommand = "sudo apt-get install -y curl || ((sudo apt-get update || true) && sudo apt-get install -y curl)",
            checkInstalledCommand = "which curl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "wget",
            name = "GNU Wget",
            category = PackageCategory.UTILITIES,
            version = "1.21+",
            description = "Retrieves files from the web using HTTP, HTTPS, and FTP with recursive download support.",
            installCommand = "sudo apt-get install -y wget || ((sudo apt-get update || true) && sudo apt-get install -y wget)",
            checkInstalledCommand = "which wget",
            launchUrl = null
        ),
        LinuxPackage(
            id = "net-tools",
            name = "Net-Tools (ifconfig, arp, netstat)",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Controlling and monitoring network subsystem of the Linux kernel.",
            installCommand = "sudo apt-get install -y net-tools || ((sudo apt-get update || true) && sudo apt-get install -y net-tools)",
            checkInstalledCommand = "which ifconfig && which netstat",
            launchUrl = null
        ),
        LinuxPackage(
            id = "iproute2",
            name = "IPRoute2 (ip, ss)",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Networking and traffic control tools: modern replacements for ifconfig, arp, route, and netstat.",
            installCommand = "sudo apt-get install -y iproute2 || ((sudo apt-get update || true) && sudo apt-get install -y iproute2)",
            checkInstalledCommand = "which ip && which ss",
            launchUrl = null
        ),
        LinuxPackage(
            id = "iptables",
            name = "IPtables Packet Filter",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Administration tools for packet filtering and NAT in Linux.",
            installCommand = "sudo apt-get install -y iptables || ((sudo apt-get update || true) && sudo apt-get install -y iptables)",
            checkInstalledCommand = "which iptables",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nftables",
            name = "NFTables Framework",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Modern packet classification framework that provides a new packet filtering engine.",
            installCommand = "sudo apt-get install -y nftables || ((sudo apt-get update || true) && sudo apt-get install -y nftables)",
            checkInstalledCommand = "which nft",
            launchUrl = null
        ),
        LinuxPackage(
            id = "iputils-ping",
            name = "IPUtils Ping",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Tools to test the reachability of network hosts on an Internet Protocol (IP) network.",
            installCommand = "sudo apt-get install -y iputils-ping || ((sudo apt-get update || true) && sudo apt-get install -y iputils-ping)",
            checkInstalledCommand = "which ping",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mtr-tiny",
            name = "MTR Traceroute & Ping",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Full screen ncurses traceroute and ping tool combining both utilities into a single diagnostic.",
            installCommand = "sudo apt-get install -y mtr-tiny || ((sudo apt-get update || true) && sudo apt-get install -y mtr-tiny)",
            checkInstalledCommand = "which mtr",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ethtool",
            name = "Ethtool Device Utility",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Utility for controlling network drivers and hardware, particularly for wired Ethernet devices.",
            installCommand = "sudo apt-get install -y ethtool || ((sudo apt-get update || true) && sudo apt-get install -y ethtool)",
            checkInstalledCommand = "which ethtool",
            launchUrl = null
        ),
        LinuxPackage(
            id = "speedtest-cli",
            name = "Speedtest CLI",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Command line interface for testing internet bandwidth using speedtest.net servers.",
            installCommand = "(sudo apt-get install -y speedtest-cli || ((sudo apt-get update || true) && sudo apt-get install -y speedtest-cli)) || pip3 install --break-system-packages --no-cache-dir speedtest-cli",
            checkInstalledCommand = "which speedtest-cli || which speedtest",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fastfetch",
            name = "Fastfetch System Info",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Neofetch-like tool written in C for instant system information and terminal logo display.",
            installCommand = "sudo apt-get install -y fastfetch || ((sudo apt-get update || true) && sudo apt-get install -y fastfetch)",
            checkInstalledCommand = "which fastfetch",
            launchUrl = null
        ),
        LinuxPackage(
            id = "neofetch",
            name = "Neofetch CLI Info",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Fast, highly customizable CLI system information tool written in Bash.",
            installCommand = "sudo apt-get install -y neofetch || ((sudo apt-get update || true) && sudo apt-get install -y neofetch)",
            checkInstalledCommand = "which neofetch",
            launchUrl = null
        ),
        LinuxPackage(
            id = "screenfetch",
            name = "ScreenFetch Screenshot Tool",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Bash Screenshot Information Tool to display system specs along with ASCII distribution logo.",
            installCommand = "sudo apt-get install -y screenfetch || ((sudo apt-get update || true) && sudo apt-get install -y screenfetch)",
            checkInstalledCommand = "which screenfetch",
            launchUrl = null
        ),
        LinuxPackage(
            id = "htop",
            name = "Htop Process Viewer",
            category = PackageCategory.UTILITIES,
            version = "3.3+",
            description = "Interactive process viewer and system monitor with color-coded CPU and memory bars.",
            installCommand = "sudo apt-get install -y htop || ((sudo apt-get update || true) && sudo apt-get install -y htop)",
            checkInstalledCommand = "which htop",
            launchUrl = null
        ),
        LinuxPackage(
            id = "btop",
            name = "Btop Resource Monitor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Resource monitor that shows usage and stats for processor, memory, disks, network and processes.",
            installCommand = "sudo apt-get install -y btop || ((sudo apt-get update || true) && sudo apt-get install -y btop)",
            checkInstalledCommand = "which btop",
            launchUrl = null
        ),
        LinuxPackage(
            id = "glances",
            name = "Glances System Monitor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Cross-platform curses-based system monitoring tool with web interface and JSON API.",
            installCommand = "(sudo apt-get install -y glances || ((sudo apt-get update || true) && sudo apt-get install -y glances)) || pip3 install --break-system-packages --no-cache-dir glances",
            checkInstalledCommand = "which glances",
            launchUrl = null
        ),
        LinuxPackage(
            id = "iotop",
            name = "IOtop Disk Monitor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Simple top-like I/O monitor displaying disk read/write bandwidth by process.",
            installCommand = "sudo apt-get install -y iotop || ((sudo apt-get update || true) && sudo apt-get install -y iotop)",
            checkInstalledCommand = "which iotop",
            launchUrl = null
        ),
        LinuxPackage(
            id = "iftop",
            name = "IFtop Bandwidth Monitor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Displays bandwidth usage on an interface, displaying pairs of host connections.",
            installCommand = "sudo apt-get install -y iftop || ((sudo apt-get update || true) && sudo apt-get install -y iftop)",
            checkInstalledCommand = "which iftop",
            launchUrl = null
        ),
        LinuxPackage(
            id = "nethogs",
            name = "NetHogs Per-Process Bandwidth",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Small 'net top' tool grouping bandwidth by process rather than per-protocol.",
            installCommand = "sudo apt-get install -y nethogs || ((sudo apt-get update || true) && sudo apt-get install -y nethogs)",
            checkInstalledCommand = "which nethogs",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bmon",
            name = "Bmon Bandwidth Monitor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Bandwidth monitor and rate estimator displaying real-time ASCII visual graphs.",
            installCommand = "sudo apt-get install -y bmon || ((sudo apt-get update || true) && sudo apt-get install -y bmon)",
            checkInstalledCommand = "which bmon",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ncdu",
            name = "NCDU Disk Usage Analyzer",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "NCurses Disk Usage analyzer provides a fast way to find disk hogs in your filesystem.",
            installCommand = "sudo apt-get install -y ncdu || ((sudo apt-get update || true) && sudo apt-get install -y ncdu)",
            checkInstalledCommand = "which ncdu",
            launchUrl = null
        ),
        LinuxPackage(
            id = "duf",
            name = "Duf Disk Free Utility",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Modern and intuitive Disk Usage/Free Utility with colored tabular visualization.",
            installCommand = "sudo apt-get install -y duf || ((sudo apt-get update || true) && sudo apt-get install -y duf)",
            checkInstalledCommand = "which duf",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dust",
            name = "Dust (du + rust)",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "A more intuitive version of du in Rust that provides an instant visual tree of directory sizes.",
            installCommand = "sudo apt-get install -y du-dust || ((sudo apt-get update || true) && sudo apt-get install -y du-dust)",
            checkInstalledCommand = "which dust || which du-dust",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tree",
            name = "Tree Directory Visualizer",
            category = PackageCategory.UTILITIES,
            version = "2.1+",
            description = "Recursive directory listing program that produces a depth indented listing of files.",
            installCommand = "sudo apt-get install -y tree || ((sudo apt-get update || true) && sudo apt-get install -y tree)",
            checkInstalledCommand = "which tree",
            launchUrl = null
        ),
        LinuxPackage(
            id = "lsd",
            name = "LSDeluxe",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "The next gen ls command with colors, icons, tree-view, and formatting options.",
            installCommand = "sudo apt-get install -y lsd || ((sudo apt-get update || true) && sudo apt-get install -y lsd)",
            checkInstalledCommand = "which lsd",
            launchUrl = null
        ),
        LinuxPackage(
            id = "zip",
            name = "Zip Archiver",
            category = PackageCategory.UTILITIES,
            version = "3.0+",
            description = "Archiver for creating and updating .zip compressed archives.",
            installCommand = "sudo apt-get install -y zip || ((sudo apt-get update || true) && sudo apt-get install -y zip)",
            checkInstalledCommand = "which zip",
            launchUrl = null
        ),
        LinuxPackage(
            id = "unzip",
            name = "Unzip De-Archiver",
            category = PackageCategory.UTILITIES,
            version = "6.0+",
            description = "De-archiver for extracting files from .zip compressed archives.",
            installCommand = "sudo apt-get install -y unzip || ((sudo apt-get update || true) && sudo apt-get install -y unzip)",
            checkInstalledCommand = "which unzip",
            launchUrl = null
        ),
        LinuxPackage(
            id = "p7zip-full",
            name = "7-Zip (p7zip)",
            category = PackageCategory.UTILITIES,
            version = "16.02+",
            description = "7z and 7za file archiver with very high compression ratio using LZMA algorithms.",
            installCommand = "sudo apt-get install -y p7zip-full || ((sudo apt-get update || true) && sudo apt-get install -y p7zip-full)",
            checkInstalledCommand = "which 7z || which 7za",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tar",
            name = "GNU Tar",
            category = PackageCategory.UTILITIES,
            version = "1.35+",
            description = "GNU tape archiver program designed to store multiple files into a single archive.",
            installCommand = "sudo apt-get install -y tar || ((sudo apt-get update || true) && sudo apt-get install -y tar)",
            checkInstalledCommand = "which tar",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gzip",
            name = "GNU Gzip",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Popular data compression program that reduces file size using Lempel-Ziv coding (LZ77).",
            installCommand = "sudo apt-get install -y gzip || ((sudo apt-get update || true) && sudo apt-get install -y gzip)",
            checkInstalledCommand = "which gzip",
            launchUrl = null
        ),
        LinuxPackage(
            id = "bzip2",
            name = "Bzip2 Compressor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "High-quality data compressor that compresses files using the Burrows-Wheeler block sorting algorithm.",
            installCommand = "sudo apt-get install -y bzip2 || ((sudo apt-get update || true) && sudo apt-get install -y bzip2)",
            checkInstalledCommand = "which bzip2",
            launchUrl = null
        ),
        LinuxPackage(
            id = "xz-utils",
            name = "XZ Utils",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "XZ-format compression and decompression utilities based on LZMA2 algorithms.",
            installCommand = "sudo apt-get install -y xz-utils || ((sudo apt-get update || true) && sudo apt-get install -y xz-utils)",
            checkInstalledCommand = "which xz",
            launchUrl = null
        ),
        LinuxPackage(
            id = "zstd",
            name = "Zstandard (zstd) Compressor",
            category = PackageCategory.UTILITIES,
            version = "1.5+",
            description = "Fast real-time lossless compression algorithm developed by Meta with high compression ratios.",
            installCommand = "sudo apt-get install -y zstd || ((sudo apt-get update || true) && sudo apt-get install -y zstd)",
            checkInstalledCommand = "which zstd",
            launchUrl = null
        ),
        LinuxPackage(
            id = "unrar",
            name = "UnRAR Free Archiver",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Extract, test and view contents of archives created with RAR.",
            installCommand = "sudo apt-get install -y unrar-free || ((sudo apt-get update || true) && sudo apt-get install -y unrar-free)",
            checkInstalledCommand = "which unrar || which unrar-free",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pigz",
            name = "Pigz Parallel Gzip",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Parallel implementation of gzip for modern multi-core processors, speeding up compression.",
            installCommand = "sudo apt-get install -y pigz || ((sudo apt-get update || true) && sudo apt-get install -y pigz)",
            checkInstalledCommand = "which pigz",
            launchUrl = null
        ),
        LinuxPackage(
            id = "rsync",
            name = "Rsync Fast File Transfer",
            category = PackageCategory.UTILITIES,
            version = "3.2+",
            description = "Fast and extraordinarily versatile file-copying tool capable of remote differential updates.",
            installCommand = "sudo apt-get install -y rsync || ((sudo apt-get update || true) && sudo apt-get install -y rsync)",
            checkInstalledCommand = "which rsync",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pv",
            name = "Pipe Viewer (pv)",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Monitor the progress of data through a pipeline with rate, throughput, and estimated time.",
            installCommand = "sudo apt-get install -y pv || ((sudo apt-get update || true) && sudo apt-get install -y pv)",
            checkInstalledCommand = "which pv",
            launchUrl = null
        ),
        LinuxPackage(
            id = "progress",
            name = "Coreutils Progress Monitor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Looks for coreutils basic commands (cp, mv, dd, tar) currently running and shows progress.",
            installCommand = "sudo apt-get install -y progress || ((sudo apt-get update || true) && sudo apt-get install -y progress)",
            checkInstalledCommand = "which progress",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ffmpeg",
            name = "FFmpeg Multimedia Suite",
            category = PackageCategory.UTILITIES,
            version = "6.1+",
            description = "Complete, cross-platform solution to record, convert, transcode, and stream audio and video.",
            installCommand = "sudo apt-get install -y ffmpeg || ((sudo apt-get update || true) && sudo apt-get install -y ffmpeg)",
            checkInstalledCommand = "which ffmpeg",
            launchUrl = null
        ),
        LinuxPackage(
            id = "imagemagick",
            name = "ImageMagick",
            category = PackageCategory.UTILITIES,
            version = "7.x",
            description = "Create, edit, compose, or convert digital bitmap images across 200+ image formats.",
            installCommand = "sudo apt-get install -y imagemagick || ((sudo apt-get update || true) && sudo apt-get install -y imagemagick)",
            checkInstalledCommand = "which convert || which magick",
            launchUrl = null
        ),
        LinuxPackage(
            id = "graphviz",
            name = "Graphviz Visualization",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Graph visualization software to represent structural information as diagrams of abstract graphs.",
            installCommand = "sudo apt-get install -y graphviz || ((sudo apt-get update || true) && sudo apt-get install -y graphviz)",
            checkInstalledCommand = "which dot",
            launchUrl = null
        ),
        LinuxPackage(
            id = "poppler-utils",
            name = "Poppler PDF Utilities",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "PDF document processing tools: pdftotext, pdfimages, pdftoppm, pdfinfo, pdftohtml.",
            installCommand = "sudo apt-get install -y poppler-utils || ((sudo apt-get update || true) && sudo apt-get install -y poppler-utils)",
            checkInstalledCommand = "which pdftotext && which pdfimages",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ghostscript",
            name = "Ghostscript PostScript/PDF",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Interpreter for the PostScript language and for PDF files, raster image processor.",
            installCommand = "sudo apt-get install -y ghostscript || ((sudo apt-get update || true) && sudo apt-get install -y ghostscript)",
            checkInstalledCommand = "which gs",
            launchUrl = null
        ),
        LinuxPackage(
            id = "tesseract-ocr",
            name = "Tesseract OCR Engine",
            category = PackageCategory.UTILITIES,
            version = "5.x",
            description = "Open source Optical Character Recognition engine to extract text from scanned images.",
            installCommand = "sudo apt-get install -y tesseract-ocr || ((sudo apt-get update || true) && sudo apt-get install -y tesseract-ocr)",
            checkInstalledCommand = "which tesseract",
            launchUrl = null
        ),
        LinuxPackage(
            id = "pandoc",
            name = "Pandoc Universal Document Converter",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Swiss-army knife for converting markup documents: Markdown, LaTeX, HTML, PDF, DOCX.",
            installCommand = "sudo apt-get install -y pandoc || ((sudo apt-get update || true) && sudo apt-get install -y pandoc)",
            checkInstalledCommand = "which pandoc",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mediainfo",
            name = "MediaInfo Metadata Reader",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Command-line utility for reading technical and tag information for video and audio files.",
            installCommand = "sudo apt-get install -y mediainfo || ((sudo apt-get update || true) && sudo apt-get install -y mediainfo)",
            checkInstalledCommand = "which mediainfo",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sox",
            name = "SoX Sound eXchange",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "The Swiss Army knife of sound processing programs: format translation and audio effect filters.",
            installCommand = "sudo apt-get install -y sox || ((sudo apt-get update || true) && sudo apt-get install -y sox)",
            checkInstalledCommand = "which sox",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mpv",
            name = "MPV Media Player",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Command line video player with broad support for diverse media file formats and audio codecs.",
            installCommand = "sudo apt-get install -y mpv || ((sudo apt-get update || true) && sudo apt-get install -y mpv)",
            checkInstalledCommand = "which mpv",
            launchUrl = null
        ),
        LinuxPackage(
            id = "yt-dlp",
            name = "yt-dlp Video Downloader",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Feature-rich command-line audio/video downloader from YouTube and thousands of video sites.",
            installCommand = "(sudo apt-get install -y yt-dlp || ((sudo apt-get update || true) && sudo apt-get install -y yt-dlp)) || pip3 install --break-system-packages --no-cache-dir yt-dlp",
            checkInstalledCommand = "which yt-dlp",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cron",
            name = "Cron Daemon",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Daemon to execute scheduled commands at periodic fixed times, dates, or intervals.",
            installCommand = "sudo apt-get install -y cron || ((sudo apt-get update || true) && sudo apt-get install -y cron)",
            checkInstalledCommand = "which crontab",
            launchUrl = null
        ),
        LinuxPackage(
            id = "at",
            name = "At Delayed Task Scheduler",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Queue, examine or delete jobs for later execution at a specified point in time.",
            installCommand = "sudo apt-get install -y at || ((sudo apt-get update || true) && sudo apt-get install -y at)",
            checkInstalledCommand = "which at",
            launchUrl = null
        ),
        LinuxPackage(
            id = "dos2unix",
            name = "Dos2Unix Line Ending Converter",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Converts plain text files between DOS/Mac/Unix newline formats (CRLF to LF).",
            installCommand = "sudo apt-get install -y dos2unix || ((sudo apt-get update || true) && sudo apt-get install -y dos2unix)",
            checkInstalledCommand = "which dos2unix",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sed",
            name = "GNU Sed Stream Editor",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Non-interactive stream editor for filtering and transforming text in a data stream.",
            installCommand = "sudo apt-get install -y sed || ((sudo apt-get update || true) && sudo apt-get install -y sed)",
            checkInstalledCommand = "which sed",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gawk",
            name = "GNU Awk Pattern Language",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Pattern scanning and processing language for data extraction and reporting.",
            installCommand = "sudo apt-get install -y gawk || ((sudo apt-get update || true) && sudo apt-get install -y gawk)",
            checkInstalledCommand = "which awk || which gawk",
            launchUrl = null
        ),
        LinuxPackage(
            id = "grep",
            name = "GNU Grep Pattern Searcher",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Searches input files for lines matching regular expression patterns.",
            installCommand = "sudo apt-get install -y grep || ((sudo apt-get update || true) && sudo apt-get install -y grep)",
            checkInstalledCommand = "which grep",
            launchUrl = null
        ),
        LinuxPackage(
            id = "diffutils",
            name = "Diffutils (diff, cmp)",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Tools for comparing files line by line and producing patch differences.",
            installCommand = "sudo apt-get install -y diffutils || ((sudo apt-get update || true) && sudo apt-get install -y diffutils)",
            checkInstalledCommand = "which diff && which cmp",
            launchUrl = null
        ),
        LinuxPackage(
            id = "patch",
            name = "GNU Patch",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Takes a patch file containing a difference listing and applies the differences to original files.",
            installCommand = "sudo apt-get install -y patch || ((sudo apt-get update || true) && sudo apt-get install -y patch)",
            checkInstalledCommand = "which patch",
            launchUrl = null
        ),
        LinuxPackage(
            id = "file",
            name = "File Type Identifier",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Determines file type and format by inspecting magic numbers and data headers.",
            installCommand = "sudo apt-get install -y file || ((sudo apt-get update || true) && sudo apt-get install -y file)",
            checkInstalledCommand = "which file",
            launchUrl = null
        ),
        LinuxPackage(
            id = "which",
            name = "Which Command Locator",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Shows the full path of shell commands.",
            installCommand = "sudo apt-get install -y debianutils || ((sudo apt-get update || true) && sudo apt-get install -y debianutils)",
            checkInstalledCommand = "which which",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ca-certificates",
            name = "CA Certificates Bundle",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Common CA certificates for SSL/TLS verification across all network connections.",
            installCommand = "sudo apt-get install -y ca-certificates || ((sudo apt-get update || true) && sudo apt-get install -y ca-certificates)",
            checkInstalledCommand = "[ -d /etc/ssl/certs ]",
            launchUrl = null
        ),
        LinuxPackage(
            id = "gnupg",
            name = "GNU Privacy Guard (GPG)",
            category = PackageCategory.UTILITIES,
            version = "2.4+",
            description = "Complete and free implementation of the OpenPGP standard for encryption and signing.",
            installCommand = "sudo apt-get install -y gnupg || ((sudo apt-get update || true) && sudo apt-get install -y gnupg)",
            checkInstalledCommand = "which gpg",
            launchUrl = null
        ),
        LinuxPackage(
            id = "openssl",
            name = "OpenSSL Cryptography Toolkit",
            category = PackageCategory.UTILITIES,
            version = "3.x",
            description = "Robust, commercial-grade, and full-featured toolkit for the TLS and SSL protocols.",
            installCommand = "sudo apt-get install -y openssl || ((sudo apt-get update || true) && sudo apt-get install -y openssl)",
            checkInstalledCommand = "which openssl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "ssh",
            name = "OpenSSH Client",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Secure shell client for remote login and secure file transfer.",
            installCommand = "sudo apt-get install -y openssh-client || ((sudo apt-get update || true) && sudo apt-get install -y openssh-client)",
            checkInstalledCommand = "which ssh",
            launchUrl = null
        ),
        LinuxPackage(
            id = "autossh",
            name = "AutoSSH Tunnel Restarter",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Automatically restarts SSH sessions and tunnels when connections drop.",
            installCommand = "sudo apt-get install -y autossh || ((sudo apt-get update || true) && sudo apt-get install -y autossh)",
            checkInstalledCommand = "which autossh",
            launchUrl = null
        ),
        LinuxPackage(
            id = "mosh",
            name = "Mosh Mobile Shell",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Remote terminal application that supports roaming, intermittent connectivity, and predictive echo.",
            installCommand = "sudo apt-get install -y mosh || ((sudo apt-get update || true) && sudo apt-get install -y mosh)",
            checkInstalledCommand = "which mosh",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sshpass",
            name = "SSHPass Non-interactive Auth",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Tool for non-interactively performing password authentication with SSH.",
            installCommand = "sudo apt-get install -y sshpass || ((sudo apt-get update || true) && sudo apt-get install -y sshpass)",
            checkInstalledCommand = "which sshpass",
            launchUrl = null
        ),
        LinuxPackage(
            id = "newsboat",
            name = "Newsboat Terminal RSS Reader",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "An extensible RSS/Atom feed reader for text terminals with full OPML support.",
            installCommand = "sudo apt-get install -y newsboat || ((sudo apt-get update || true) && sudo apt-get install -y newsboat)",
            checkInstalledCommand = "which newsboat",
            launchUrl = null
        ),
        LinuxPackage(
            id = "w3m",
            name = "W3M Text Web Browser",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "WWW browsable pager with tabular layout and interactive link navigation.",
            installCommand = "sudo apt-get install -y w3m || ((sudo apt-get update || true) && sudo apt-get install -y w3m)",
            checkInstalledCommand = "which w3m",
            launchUrl = null
        ),
        LinuxPackage(
            id = "lynx",
            name = "Lynx Terminal Web Browser",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Classic, highly configurable text-based web browser.",
            installCommand = "sudo apt-get install -y lynx || ((sudo apt-get update || true) && sudo apt-get install -y lynx)",
            checkInstalledCommand = "which lynx",
            launchUrl = null
        ),
        LinuxPackage(
            id = "links2",
            name = "Links2 Web Browser",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Web browser running in text and graphics mode for fast browsing without heavyweight engines.",
            installCommand = "sudo apt-get install -y links2 || ((sudo apt-get update || true) && sudo apt-get install -y links2)",
            checkInstalledCommand = "which links2",
            launchUrl = null
        ),
        LinuxPackage(
            id = "taskwarrior",
            name = "Taskwarrior Task Manager",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Feature-rich command-line todo list manager that scales from simple tasks to complex GTD.",
            installCommand = "sudo apt-get install -y taskwarrior || ((sudo apt-get update || true) && sudo apt-get install -y taskwarrior)",
            checkInstalledCommand = "which task",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cmatrix",
            name = "CMatrix Matrix Animation",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Simulates the display from The Matrix movies in your terminal in glorious green text.",
            installCommand = "sudo apt-get install -y cmatrix || ((sudo apt-get update || true) && sudo apt-get install -y cmatrix)",
            checkInstalledCommand = "which cmatrix",
            launchUrl = null
        ),
        LinuxPackage(
            id = "sl",
            name = "SL (Steam Locomotive)",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Steam Locomotive runs across your screen if you mistakenly type 'sl' instead of 'ls'.",
            installCommand = "sudo apt-get install -y sl || ((sudo apt-get update || true) && sudo apt-get install -y sl)",
            checkInstalledCommand = "which sl",
            launchUrl = null
        ),
        LinuxPackage(
            id = "cowsay",
            name = "Cowsay Talking Cow",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Configurable talking cow ASCII banner generator.",
            installCommand = "sudo apt-get install -y cowsay || ((sudo apt-get update || true) && sudo apt-get install -y cowsay)",
            checkInstalledCommand = "which cowsay",
            launchUrl = null
        ),
        LinuxPackage(
            id = "fortune-mod",
            name = "Fortune Epigrams",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Prints a random, hopefully interesting, adage or quote in your terminal.",
            installCommand = "sudo apt-get install -y fortune-mod || ((sudo apt-get update || true) && sudo apt-get install -y fortune-mod)",
            checkInstalledCommand = "which fortune",
            launchUrl = null
        ),
        LinuxPackage(
            id = "figlet",
            name = "FIGlet Large Text Banners",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Program for making large letters out of ordinary text characters.",
            installCommand = "sudo apt-get install -y figlet || ((sudo apt-get update || true) && sudo apt-get install -y figlet)",
            checkInstalledCommand = "which figlet",
            launchUrl = null
        ),
        LinuxPackage(
            id = "toilet",
            name = "TOIlet Colourful Text Banner",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Display colour text and large colourful banners in terminal with unicode support.",
            installCommand = "sudo apt-get install -y toilet || ((sudo apt-get update || true) && sudo apt-get install -y toilet)",
            checkInstalledCommand = "which toilet",
            launchUrl = null
        ),
        LinuxPackage(
            id = "lolcat",
            name = "Lolcat Rainbow Styler",
            category = PackageCategory.UTILITIES,
            version = "Latest",
            description = "Rainbow coloring effect for text in Linux terminal console.",
            installCommand = "sudo apt-get install -y lolcat || ((sudo apt-get update || true) && sudo apt-get install -y lolcat)",
            checkInstalledCommand = "which lolcat",
            launchUrl = null
        )
        )
    }
}
