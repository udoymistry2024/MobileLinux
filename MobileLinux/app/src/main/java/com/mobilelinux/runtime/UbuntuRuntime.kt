package com.mobilelinux.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * Core runtime manager for the Ubuntu Linux environment.
 * Handles proot/chroot process lifecycle, session creation, and setup.
 */
class UbuntuRuntime(private val context: Context) {

    private val TAG = "UbuntuRuntime"
    private val assetExtractor = AssetExtractor(context)

    val rootfsDir: File get() = assetExtractor.rootfsDir
    val scriptsDir: File get() = assetExtractor.scriptsDir
    val prootBinary: File get() = assetExtractor.prootBinary

    val isRooted: Boolean by lazy { RootDetector.isRooted() && RootDetector.canExecuteAsRoot() }
    val deviceAbi: String by lazy { RootDetector.getDeviceAbi() }

    /**
     * Returns true if Ubuntu environment is ready to use.
     */
    fun isReady(): Boolean = assetExtractor.isSetupComplete()

    /**
     * Full setup pipeline: extract binaries, extract rootfs, configure.
     * Callbacks report progress (0.0 to 1.0) and status messages.
     */
    suspend fun performSetup(
        onProgress: (Float, String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Step 1: Extract proot binary (10%)
            onProgress(0.0f, "Preparing proot runtime...")
            val prootOk = assetExtractor.extractProot { p, msg ->
                onProgress(p * 0.1f, msg)
            }
            if (!prootOk) {
                onError("Failed to extract proot binary")
                return@withContext false
            }

            // Step 2: Extract scripts (15%)
            onProgress(0.1f, "Extracting scripts...")
            val scriptsOk = assetExtractor.extractScripts { p, msg ->
                onProgress(0.1f + p * 0.05f, msg)
            }
            if (!scriptsOk) {
                onError("Failed to extract scripts")
                return@withContext false
            }

            // Step 3: Extract Ubuntu rootfs (large, 15-85%)
            onProgress(0.15f, "Extracting Ubuntu 24.04 rootfs...")
            val rootfsOk = assetExtractor.extractRootfs { p, msg ->
                onProgress(0.15f + p * 0.70f, msg)
            }
            if (!rootfsOk) {
                onError("Failed to extract Ubuntu rootfs")
                return@withContext false
            }

            // Step 4: Run configuration (85-95%)
            onProgress(0.85f, "Configuring Ubuntu environment...")
            val setupResult = runSetupScript { p, msg ->
                onProgress(0.85f + p * 0.10f, msg)
            }
            if (setupResult.isFailure) {
                val err = setupResult.exceptionOrNull()?.message ?: "Unknown configuration error"
                Log.e(TAG, "Configuration failed: $err", setupResult.exceptionOrNull())
                onError("Configuration failed: $err")
                return@withContext false
            }

            // Step 5: Initialize shared MobileLinux folder in File Manager & mark complete
            try {
                com.mobilelinux.util.StorageHelper.setupSharedStorage(context)
            } catch (ignored: Exception) {}
            assetExtractor.markSetupComplete()
            onProgress(1.0f, "Ubuntu 24.04 ready!")
            Log.i(TAG, "Ubuntu setup complete. Mode: ${if (isRooted) "chroot/root" else "proot/rootless"}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Setup failed unexpectedly: ${e.message}", e)
            onError("Setup error: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    // =========================================================================
    // Safe File I/O Utilities — handles dangling symlinks, permission issues,
    // and edge cases on Android 14/15/16 FUSE/ext4 filesystems.
    // =========================================================================

    /**
     * Safely writes content to a file. Handles:
     * - Dangling symlinks (deletes them before writing)
     * - Missing parent directories (creates them recursively)
     * - Stale files left from previous incomplete setup
     * - Permission issues (silently handled)
     *
     * @throws Exception only if absolutely unable to write after all fallbacks
     */
    private fun safeWriteFile(file: File, content: String) {
        // Step 1: Ensure parent directory exists and is a real directory
        val parent = file.parentFile
        if (parent != null) {
            ensureRealDirectory(parent)
        }

        // Step 2: Remove any pre-existing file/symlink at the target path
        safeDelete(file)

        // Step 3: Write the file
        try {
            file.writeText(content)
        } catch (e: Exception) {
            // Fallback: try with java.nio API
            Log.w(TAG, "writeText failed for ${file.name}, trying NIO: ${e.message}")
            try {
                Files.write(file.toPath(), content.toByteArray(Charsets.UTF_8))
            } catch (e2: Exception) {
                Log.e(TAG, "NIO write also failed for ${file.name}: ${e2.message}")
                // Last resort: delete parent, recreate, try again
                safeDelete(file)
                parent?.mkdirs()
                file.writeText(content)
            }
        }
    }

    /**
     * Safely reads a file's content, handling dangling symlinks and missing files.
     * Returns empty string if the file can't be read for any reason.
     */
    private fun safeReadFile(file: File): String {
        return try {
            // Check if it's a symlink first
            val path = file.toPath()
            if (Files.isSymbolicLink(path)) {
                // It's a symlink — check if target exists
                val target = Files.readSymbolicLink(path)
                val resolvedTarget = if (target.isAbsolute) target else path.parent.resolve(target)
                if (!Files.exists(resolvedTarget)) {
                    // Dangling symlink — delete it and return empty
                    Log.w(TAG, "Dangling symlink: ${file.name} -> $target, removing")
                    safeDelete(file)
                    return ""
                }
            }
            if (file.exists() && file.isFile && file.canRead()) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read ${file.name}: ${e.message}")
            ""
        }
    }

    /**
     * Ensures a path is a real directory (not a symlink pointing to one).
     * If it's a dangling symlink or a file, removes it and creates a directory.
     */
    private fun ensureRealDirectory(dir: File) {
        try {
            val path = dir.toPath()
            if (Files.isSymbolicLink(path)) {
                // It's a symlink — remove it and create a real directory
                Files.deleteIfExists(path)
            }
        } catch (ignored: Exception) {}

        if (!dir.exists()) {
            dir.mkdirs()
        } else if (!dir.isDirectory) {
            // It's a file where we need a directory — remove and create
            try {
                dir.delete()
                dir.mkdirs()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Safely deletes a file or symlink, handling all edge cases.
     */
    private fun safeDelete(file: File) {
        try {
            Files.deleteIfExists(file.toPath())
        } catch (e: Exception) {
            try { file.delete() } catch (ignored: Exception) {}
        }
    }

    /**
     * Ensures a dedicated host-backed shared memory directory exists in internal storage
     * and that rootfs mount points (/dev/shm, /run/shm, /tmp) exist with full permissions.
     * This provides standard POSIX shared memory (sem_open, shm_open) required by
     * Python's multiprocessing module, ProcessPoolExecutor, Miniconda, PyTorch, and databases.
     */
    fun ensureSharedMemoryReady(): File {
        val shmDir = File(context.filesDir, "shm")
        if (!shmDir.exists()) {
            shmDir.mkdirs()
        }
        try {
            shmDir.setReadable(true, false)
            shmDir.setWritable(true, false)
            shmDir.setExecutable(true, false)
        } catch (ignored: Exception) {}

        // Ensure guest rootfs directories exist with sticky/open permissions
        try {
            val devDir = File(rootfsDir, "dev")
            ensureRealDirectory(devDir)
            val devShm = File(devDir, "shm")
            ensureRealDirectory(devShm)
            devShm.setReadable(true, false)
            devShm.setWritable(true, false)
            devShm.setExecutable(true, false)

            val runDir = File(rootfsDir, "run")
            ensureRealDirectory(runDir)
            val runShm = File(runDir, "shm")
            ensureRealDirectory(runShm)
            runShm.setReadable(true, false)
            runShm.setWritable(true, false)
            runShm.setExecutable(true, false)

            val tmpDir = File(rootfsDir, "tmp")
            ensureRealDirectory(tmpDir)
            tmpDir.setReadable(true, false)
            tmpDir.setWritable(true, false)
            tmpDir.setExecutable(true, false)
        } catch (e: Exception) {
            Log.w(TAG, "Notice while ensuring guest shm directories: ${e.message}")
        }

        return shmDir
    }


    // =========================================================================
    // Setup Script — Pure Kotlin implementation
    // =========================================================================

    /**
     * Configures Ubuntu environment directly (DNS, hosts, users, bashrc, apt sources).
     * Pure Kotlin implementation — no dependency on host /bin/bash or external binaries.
     *
     * Each step is individually wrapped in try-catch so a non-critical failure
     * (e.g., can't set locale) doesn't abort the entire setup.
     */
    private suspend fun runSetupScript(onProgress: (Float, String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()

            try {
                onProgress(0.05f, "Creating directories...")
                val etcDir = File(rootfsDir, "etc")
                ensureRealDirectory(etcDir)
                Log.d(TAG, "Setup: etc dir ready at ${etcDir.absolutePath}")

                // 1. DNS (/etc/resolv.conf)
                onProgress(0.10f, "Configuring DNS...")
                try {
                    val resolvConf = File(etcDir, "resolv.conf")
                    safeWriteFile(
                        resolvConf,
                        "nameserver 8.8.8.8\nnameserver 8.8.4.4\nnameserver 1.1.1.1\n"
                    )
                    resolvConf.setReadable(true, false)
                    Log.d(TAG, "Setup: resolv.conf ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: resolv.conf FAILED: ${e.message}", e)
                    errors.add("resolv.conf: ${e.message}")
                }

                // 2. Hosts (/etc/hosts)
                onProgress(0.15f, "Configuring hosts file...")
                try {
                    val hostsFile = File(etcDir, "hosts")
                    safeWriteFile(
                        hostsFile,
                        "127.0.0.1   localhost\n127.0.1.1   mobilelinux\n::1         localhost ip6-localhost ip6-loopback\n"
                    )
                    hostsFile.setReadable(true, false)
                    Log.d(TAG, "Setup: hosts ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: hosts FAILED: ${e.message}", e)
                    errors.add("hosts: ${e.message}")
                }

                // 3. Hostname (/etc/hostname)
                onProgress(0.20f, "Configuring hostname...")
                try {
                    val hostnameFile = File(etcDir, "hostname")
                    safeWriteFile(hostnameFile, "mobilelinux\n")
                    hostnameFile.setReadable(true, false)
                    Log.d(TAG, "Setup: hostname ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: hostname FAILED: ${e.message}", e)
                    errors.add("hostname: ${e.message}")
                }

                // 4. User & Group (/etc/passwd, /etc/group)
                onProgress(0.25f, "Configuring users...")
                try {
                    val passwd = File(etcDir, "passwd")
                    val passwdContent = safeReadFile(passwd)
                    val sb = StringBuilder(passwdContent)
                    if (!passwdContent.endsWith("\n") && passwdContent.isNotEmpty()) sb.append("\n")
                    if (!passwdContent.contains("root:")) {
                        sb.append("root:x:0:0:root:/root:/bin/bash\n")
                    }
                    if (!passwdContent.contains("ubuntu:")) {
                        sb.append("ubuntu:x:1000:1000:Ubuntu User:/home/ubuntu:/bin/bash\n")
                    }
                    safeWriteFile(passwd, sb.toString())
                    passwd.setReadable(true, false)
                    Log.d(TAG, "Setup: passwd ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: passwd FAILED: ${e.message}", e)
                    errors.add("passwd: ${e.message}")
                }

                try {
                    val group = File(etcDir, "group")
                    val groupContent = safeReadFile(group)
                    val sb = StringBuilder(groupContent)
                    if (!groupContent.endsWith("\n") && groupContent.isNotEmpty()) sb.append("\n")
                    if (!groupContent.contains("root:")) sb.append("root:x:0:\n")
                    if (!groupContent.contains("ubuntu:")) sb.append("ubuntu:x:1000:\n")
                    if (!groupContent.contains("sudo:")) sb.append("sudo:x:27:ubuntu\n")
                    if (!groupContent.contains("adm:")) sb.append("adm:x:4:ubuntu\n")
                    safeWriteFile(group, sb.toString())
                    group.setReadable(true, false)
                    Log.d(TAG, "Setup: group ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: group FAILED: ${e.message}", e)
                    errors.add("group: ${e.message}")
                }

                // 5. Sudoers (/etc/sudoers.d/mobilelinux)
                onProgress(0.35f, "Configuring permissions...")
                try {
                    val sudoersDir = File(etcDir, "sudoers.d")
                    ensureRealDirectory(sudoersDir)
                    val sudoersFile = File(sudoersDir, "mobilelinux")
                    safeWriteFile(
                        sudoersFile,
                        "ALL ALL=(ALL:ALL) NOPASSWD: ALL\nroot ALL=(ALL:ALL) NOPASSWD: ALL\nubuntu ALL=(ALL:ALL) NOPASSWD: ALL\n"
                    )
                    sudoersFile.setReadable(true, false)
                    Log.d(TAG, "Setup: sudoers ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: sudoers FAILED: ${e.message}", e)
                    errors.add("sudoers: ${e.message}")
                }

                // 6. Install command wrappers in /usr/local/bin: sudo, su, whoami, who, id
                onProgress(0.45f, "Installing shell wrappers...")
                installCommandWrappers()

                // 7. APT sources (/etc/apt/sources.list)
                onProgress(0.55f, "Configuring APT package sources...")
                try {
                    val aptDir = File(etcDir, "apt")
                    ensureRealDirectory(aptDir)
                    val sourcesList = File(aptDir, "sources.list")
                    safeWriteFile(
                        sourcesList,
                        """
                        deb http://ports.ubuntu.com/ubuntu-ports noble main restricted universe multiverse
                        deb http://ports.ubuntu.com/ubuntu-ports noble-updates main restricted universe multiverse
                        deb http://ports.ubuntu.com/ubuntu-ports noble-security main restricted universe multiverse
                        deb http://ports.ubuntu.com/ubuntu-ports noble-backports main restricted universe multiverse
                        """.trimIndent() + "\n"
                    )
                    sourcesList.setReadable(true, false)
                    Log.d(TAG, "Setup: apt sources ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: apt sources FAILED: ${e.message}", e)
                    errors.add("apt: ${e.message}")
                }

                // 8. /etc/profile.d/mobilelinux.sh
                onProgress(0.65f, "Configuring environment profiles...")
                try {
                    val profileDir = File(etcDir, "profile.d")
                    ensureRealDirectory(profileDir)
                    val profileFile = File(profileDir, "mobilelinux.sh")
                    safeWriteFile(
                        profileFile,
                        """
                        export TERM=xterm-256color
                        export COLORTERM=truecolor
                        export LANG=C.UTF-8
                        export LC_ALL=C.UTF-8
                        export ANDROID_HOST=true
                        """.trimIndent() + "\n"
                    )
                    profileFile.setReadable(true, false)
                    Log.d(TAG, "Setup: profile.d ✓")
                } catch (e: Exception) {
                    Log.e(TAG, "Setup: profile.d FAILED: ${e.message}", e)
                    errors.add("profile: ${e.message}")
                }

                // 9. Locale files
                onProgress(0.70f, "Configuring locale...")
                try {
                    val localeGen = File(etcDir, "locale.gen")
                    safeWriteFile(localeGen, "C.UTF-8 UTF-8\nen_US.UTF-8 UTF-8\n")
                    val localeConf = File(etcDir, "locale.conf")
                    safeWriteFile(localeConf, "LANG=C.UTF-8\nLC_ALL=C.UTF-8\n")
                    Log.d(TAG, "Setup: locale ✓")
                } catch (e: Exception) {
                    Log.w(TAG, "Setup: locale FAILED (non-critical): ${e.message}")
                }

                // 10. /home/ubuntu & /root bash environments
                onProgress(0.80f, "Setting up bash environment...")
                installBashEnvironments()
                installJupyterAndNetlinkFixes()

                // 11. Ensure essential mount points and directories exist
                onProgress(0.90f, "Creating mount points & shared memory...")
                listOf("tmp", "proc", "sys", "dev", "dev/pts", "dev/shm", "run", "run/shm", "root", "home", "sdcard").forEach { dir ->
                    try {
                        ensureRealDirectory(File(rootfsDir, dir))
                    } catch (e: Exception) {
                        Log.w(TAG, "Setup: mkdir $dir failed: ${e.message}")
                    }
                }
                ensureSharedMemoryReady()
                Log.d(TAG, "Setup: mount points & shm ready ✓")

                // 12. Set permissions for /tmp, /dev/shm, /run/shm (writable by all)
                onProgress(0.95f, "Setting permissions...")
                listOf("tmp", "dev/shm", "run/shm").forEach { dirName ->
                    try {
                        val d = File(rootfsDir, dirName)
                        d.setReadable(true, false)
                        d.setWritable(true, false)
                        d.setExecutable(true, false)
                    } catch (ignored: Exception) {}
                }

                onProgress(1.0f, "Configuration complete!")

                // Check for critical failures
                if (errors.isNotEmpty()) {
                    val criticalErrors = errors.filter { err ->
                        // These are critical — setup cannot succeed without them
                        err.startsWith("resolv.conf:") || err.startsWith("bashrc:")
                    }
                    if (criticalErrors.isNotEmpty()) {
                        Log.e(TAG, "Setup completed with critical errors: $criticalErrors")
                        throw RuntimeException("Critical setup failures: ${criticalErrors.joinToString("; ")}")
                    }
                    // Non-critical errors: log but continue
                    Log.w(TAG, "Setup completed with ${errors.size} non-critical warning(s): $errors")
                }

                Log.i(TAG, "Setup configuration completed successfully!")

            } catch (e: Exception) {
                Log.e(TAG, "Setup configuration failed: ${e.message}", e)
                return@withContext Result.failure(e)
            }

            Result.success(Unit)
        }

    /**
     * Ensures /root/.bashrc and /root/.profile are properly written without
     * any blocking checks, so prompt and welcome banner always appear.
     */
    /**
     * Ensures /root/.bashrc, /root/.profile, and /root/mobilelinux-shell.sh
     * are properly written with interactive PTY support and shared folder symlinks.
     */
    private fun ensureBashConfigured() {
        try {
            ensureSharedMemoryReady()
            val etcDir = File(rootfsDir, "etc")
            ensureRealDirectory(etcDir)

            // 1. Ensure /etc/passwd has both root (UID 0) and ubuntu (UID 1000)
            try {
                val passwdFile = File(etcDir, "passwd")
                val existing = if (passwdFile.exists()) passwdFile.readText() else ""
                val sb = StringBuilder(existing)
                if (!existing.endsWith("\n") && existing.isNotEmpty()) {
                    sb.append("\n")
                }
                if (!existing.contains("root:")) {
                    sb.append("root:x:0:0:root:/root:/bin/bash\n")
                }
                if (!existing.contains("ubuntu:")) {
                    sb.append("ubuntu:x:1000:1000:Ubuntu User:/home/ubuntu:/bin/bash\n")
                }
                val updatedPasswd = sb.toString()
                if (updatedPasswd != existing) {
                    safeWriteFile(passwdFile, updatedPasswd)
                    passwdFile.setReadable(true, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Passwd config notice: ${e.message}")
            }

            // 2. Ensure /etc/group has root, ubuntu, sudo, adm, and Android supplementary GIDs
            try {
                val groupFile = File(etcDir, "group")
                val existing = if (groupFile.exists()) groupFile.readText() else ""
                val sb = StringBuilder(existing)
                if (!existing.endsWith("\n") && existing.isNotEmpty()) {
                    sb.append("\n")
                }
                if (!existing.contains("root:")) {
                    sb.append("root:x:0:\n")
                }
                if (!existing.contains("ubuntu:")) {
                    sb.append("ubuntu:x:1000:\n")
                }
                if (!existing.contains("sudo:")) {
                    sb.append("sudo:x:27:ubuntu\n")
                }
                if (!existing.contains("adm:")) {
                    sb.append("adm:x:4:ubuntu\n")
                }

                // Add Android supplementary GIDs to avoid "groups: cannot find name for group ID" warnings
                val androidGids = mutableSetOf(1000, 1015, 1028, 1077, 1078, 1079, 2001, 3003, 9997, 20522, 50522)
                try {
                    val status = File("/proc/self/status").readLines()
                    for (line in status) {
                        if (line.startsWith("Groups:") || line.startsWith("Gid:")) {
                            line.substringAfter(":").trim().split(Regex("\\s+")).forEach {
                                it.toIntOrNull()?.let { gid -> androidGids.add(gid) }
                            }
                        }
                    }
                } catch (ignored: Exception) {}

                for (gid in androidGids) {
                    if (!existing.contains(":$gid:")) {
                        sb.append("aid_$gid:x:$gid:ubuntu,root\n")
                    }
                }

                val updatedGroup = sb.toString()
                if (updatedGroup != existing) {
                    safeWriteFile(groupFile, updatedGroup)
                    groupFile.setReadable(true, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Group config notice: ${e.message}")
            }

            // 3. Sudoers permissions (/etc/sudoers.d/mobilelinux)
            try {
                val sudoersDir = File(etcDir, "sudoers.d")
                ensureRealDirectory(sudoersDir)
                val sudoersFile = File(sudoersDir, "mobilelinux")
                safeWriteFile(
                    sudoersFile,
                    "ALL ALL=(ALL:ALL) NOPASSWD: ALL\nroot ALL=(ALL:ALL) NOPASSWD: ALL\nubuntu ALL=(ALL:ALL) NOPASSWD: ALL\n"
                )
                sudoersFile.setReadable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "Sudoers notice: ${e.message}")
            }

            // 4. Install command wrappers in /usr/local/bin: sudo, su, whoami, who, id
            installCommandWrappers()

            // 5. Setup /home/ubuntu & /root environments (.bashrc, .profile, shell launcher)
            installBashEnvironments()

            // 6. Ensure system-wide C.UTF-8 locale to eliminate setlocale warnings
            try {
                val defaultDir = File(etcDir, "default")
                ensureRealDirectory(defaultDir)
                val defaultLocale = File(defaultDir, "locale")
                safeWriteFile(defaultLocale, "LANG=C.UTF-8\nLC_ALL=C.UTF-8\n")
                defaultLocale.setReadable(true, false)

                val envFile = File(etcDir, "environment")
                safeWriteFile(envFile, "LANG=C.UTF-8\nLC_ALL=C.UTF-8\nPATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games\"\n")
                envFile.setReadable(true, false)

                val localeConf = File(etcDir, "locale.conf")
                safeWriteFile(localeConf, "LANG=C.UTF-8\nLC_ALL=C.UTF-8\n")
                localeConf.setReadable(true, false)

                val profileD = File(etcDir, "profile.d")
                ensureRealDirectory(profileD)
                val localeProfile = File(profileD, "00-locale.sh")
                safeWriteFile(localeProfile, "export LANG=C.UTF-8\nexport LC_ALL=C.UTF-8\n")
                localeProfile.setReadable(true, false)

                val pathsProfile = File(profileD, "01-paths.sh")
                safeWriteFile(pathsProfile, "export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:\$PATH\"\n")
                pathsProfile.setReadable(true, false)

                val colorsProfile = File(profileD, "02-colors.sh")
                val colorsContent = listOf(
                    "# MobileLinux Clean Terminal Colors",
                    "# Prevent ugly bright green/black background on other-writable (ow) and sticky (tw/st) folders",
                    "if command -v dircolors >/dev/null 2>&1; then",
                    "    eval \"\$(dircolors -b 2>/dev/null)\"",
                    "fi",
                    "if [ -n \"\$LS_COLORS\" ]; then",
                    "    export LS_COLORS=\"\$(echo \"\$LS_COLORS\" | sed 's/ow=[0-9;]*/ow=01;34/g; s/tw=[0-9;]*/tw=01;34/g; s/st=[0-9;]*/st=01;34/g'):ow=01;34:tw=01;34:st=01;34:\"",
                    "else",
                    "    export LS_COLORS=\"rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:ca=00:tw=01;34:ow=01;34:st=01;34:ex=01;32:\"",
                    "fi\n"
                ).joinToString("\n")
                safeWriteFile(colorsProfile, colorsContent)
                colorsProfile.setReadable(true, false)

                val bashBashrc = File(etcDir, "bash.bashrc")
                val pathExportLine = "export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:\$PATH\"\n"
                val cmdNotFoundContent = "\n# MobileLinux Smart Command Not Found Handler for Python libraries\n" +
                    "command_not_found_handle() {\n" +
                    "    local cmd=\"\$1\"\n" +
                    "    local arg=\"\$2\"\n" +
                    "    local py=\"\"\n" +
                    "    if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ]; then\n" +
                    "        py=\"\$CONDA_PREFIX/bin/python\"\n" +
                    "    elif [ -x /home/ubuntu/miniforge3/bin/python ]; then\n" +
                    "        py=\"/home/ubuntu/miniforge3/bin/python\"\n" +
                    "    elif [ -x /root/miniconda3/bin/python ]; then\n" +
                    "        py=\"/root/miniconda3/bin/python\"\n" +
                    "    elif command -v python3 >/dev/null 2>&1; then\n" +
                    "        py=\"\$(command -v python3)\"\n" +
                    "    fi\n" +
                    "    if [ -n \"\$py\" ]; then\n" +
                    "        local mod=\"\${cmd//-/_}\"\n" +
                    "        if \"\$py\" -c \"import \$mod\" >/dev/null 2>&1; then\n" +
                    "            local ver\n" +
                    "            ver=\"\$(\"\$py\" -c \"import \$mod as _m; print(getattr(_m, '__version__', 'installed'))\" 2>/dev/null)\"\n" +
                    "            if [ \"\$arg\" = \"--version\" ] || [ \"\$arg\" = \"-v\" ] || [ \"\$arg\" = \"-V\" ]; then\n" +
                    "                echo \"\$cmd \$ver\"\n" +
                    "                return 0\n" +
                    "            fi\n" +
                    "            echo -e \"\\033[1;36m[MobileLinux]\\033[0m '\$cmd' is an installed Python library (v\$ver).\"\n" +
                    "            echo -e \"To use it in Python:\"\n" +
                    "            echo -e \"  \\033[1;33m\$py -c 'import \$mod'\\033[0m\"\n" +
                    "            echo -e \"  OR start Python interactive shell: \\033[1;32m\$py\\033[0m\"\n" +
                    "            return 0\n" +
                    "        fi\n" +
                    "    fi\n" +
                    "    echo \"bash: \$cmd: command not found\" >&2\n" +
                    "    return 127\n" +
                    "}\n"

                if (bashBashrc.exists()) {
                    var content = bashBashrc.readText()
                    var modified = false
                    if (!content.contains("/home/ubuntu/.local/bin")) {
                        content = "$content\n$pathExportLine"
                        modified = true
                    }
                    if (!content.contains("ow=01;34")) {
                        content = "$content\n$colorsContent"
                        modified = true
                    }
                    if (!content.contains("command_not_found_handle")) {
                        content = "$content\n$cmdNotFoundContent"
                        modified = true
                    }
                    if (modified) {
                        safeWriteFile(bashBashrc, content)
                    }
                } else {
                    safeWriteFile(bashBashrc, "$pathExportLine\n$colorsContent\n$cmdNotFoundContent")
                }
                bashBashrc.setReadable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "Locale config notice: ${e.message}")
            }

            // 7. Install ZeroMQ netlink fix and built-in Jupyter / IPython configurations
            installJupyterAndNetlinkFixes()

            // 8. Configure APT globally to eliminate lock collisions, force IPv4, and auto-assume yes
            try {
                val aptConfD = File(etcDir, "apt/apt.conf.d")
                ensureRealDirectory(aptConfD)
                val mlAptConf = File(aptConfD, "99mobilelinux")
                safeWriteFile(mlAptConf, """
                    DPkg::Lock::Timeout "60";
                    Acquire::ForceIPv4 "true";
                    APT::Get::Assume-Yes "true";
                    APT::Get::AllowUnauthenticated "false";
                    Dpkg::Options {
                        "--force-confdef";
                        "--force-confold";
                    };
                """.trimIndent() + "\n")
                mlAptConf.setReadable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "Apt config notice: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: ensureBashConfigured: ${e.message}")
        }
    }

    /**
     * Removes stale APT/dpkg lock files from both the host filesystem (direct File I/O)
     * AND ensures the /var/lib/dpkg/updates directory is cleaned to prevent partial
     * update state from corrupting subsequent installs.
     */
    fun cleanupAptLocks() {
        try {
            val lockFiles = listOf(
                File(rootfsDir, "var/lib/apt/lists/lock"),
                File(rootfsDir, "var/cache/apt/archives/lock"),
                File(rootfsDir, "var/lib/dpkg/lock"),
                File(rootfsDir, "var/lib/dpkg/lock-frontend"),
                File(rootfsDir, "var/cache/debconf/config.dat-lock"),
                File(rootfsDir, "var/cache/debconf/templates.dat-lock")
            )
            lockFiles.forEach { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (ignored: Exception) {}
            }
            // Also clean partial dpkg updates that cause "dpkg interrupted" errors
            try {
                val updatesDir = File(rootfsDir, "var/lib/dpkg/updates")
                if (updatesDir.exists() && updatesDir.isDirectory) {
                    updatesDir.listFiles()?.forEach { f ->
                        try { f.delete() } catch (ignored: Exception) {}
                    }
                }
            } catch (ignored: Exception) {}
        } catch (ignored: Exception) {}
    }

    /**
     * Creates a new terminal session process.
     * Returns the started [Process] connected to a PTY.
     *
     * @param sessionId Unique session identifier
     * @param cols Terminal column count
     * @param rows Terminal row count
     * @param execCommand Command to run (null = bash login shell)
     */
    fun createSessionProcess(
        sessionId: String,
        cols: Int = 80,
        rows: Int = 24,
        execCommand: String? = null
    ): Process {
        Log.d(TAG, "Creating session: $sessionId, mode=${if (isRooted) "chroot" else "proot"}")

        // Ensure .bashrc, .profile, and shell launcher are in place
        ensureBashConfigured()

        // Auto-install essential terminal tools in background if online & interactive session
        if (execCommand == null) {
            installEssentialToolsInBackground()
        }

        val cmd = if (isRooted) {
            buildChrootCommand(execCommand)
        } else {
            buildProotCommand(sessionId, cols, rows, execCommand)
        }

        Log.d(TAG, "Command: ${cmd.joinToString(" ")}")

        val env = buildEnvironment(cols, rows)
        Log.d(TAG, "Environment: $env")

        return ProcessBuilder(cmd)
            .apply {
                environment().putAll(env)
                directory(context.filesDir)
                // CRITICAL: Merge stderr into stdout so all output appears in terminal
                redirectErrorStream(true)
            }
            .start()
    }

    private fun buildProotCommand(sessionId: String, cols: Int, rows: Int, execCmd: String?): List<String> {
        val shmDir = ensureSharedMemoryReady()

        val cmd = mutableListOf(
            prootBinary.absolutePath,
            "--rootfs=${rootfsDir.absolutePath}",
            "--root-id",
            "--bind=/proc",
            "--bind=/dev",
            "--bind=${shmDir.absolutePath}:/dev/shm",
            "--bind=${shmDir.absolutePath}:/run/shm",
            "--sysvipc",
            "--kill-on-exit",
            "--link2symlink",
            "--cwd=/home/ubuntu"
        )

        // Bind standard file descriptors if available
        try {
            if (!File("/dev/fd").exists() && File("/proc/self/fd").exists()) {
                cmd.add("--bind=/proc/self/fd:/dev/fd")
            }
            listOf("0" to "stdin", "1" to "stdout", "2" to "stderr").forEach { (fd, name) ->
                if (!File("/dev/$name").exists() && File("/proc/self/fd/$fd").exists()) {
                    cmd.add("--bind=/proc/self/fd/$fd:/dev/$name")
                }
            }
        } catch (ignored: Exception) {}

        // Bind device nodes if accessible
        listOf("/dev/pts", "/dev/urandom", "/dev/random", "/dev/null", "/dev/zero", "/dev/full").forEach { devPath ->
            try {
                val f = File(devPath)
                if (f.exists()) {
                    cmd.add("--bind=$devPath")
                }
            } catch (ignored: Exception) {}
        }
        try {
            if (!File("/dev/random").exists() && File("/dev/urandom").exists()) {
                cmd.add("--bind=/dev/urandom:/dev/random")
            }
        } catch (ignored: Exception) {}

        // Bind external storage (/sdcard)
        try {
            val sdcard = android.os.Environment.getExternalStorageDirectory()
            if (sdcard != null && sdcard.exists()) {
                cmd.add("--bind=${sdcard.absolutePath}:/sdcard")
            }
        } catch (ignored: Exception) {}

        // Bind public Downloads folder (/sdcard/Download)
        try {
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null && downloads.exists()) {
                cmd.add("--bind=${downloads.absolutePath}:/sdcard/Download")
            }
        } catch (ignored: Exception) {}

        // Bind preferred shared MobileLinux folder
        try {
            val preferredDir = com.mobilelinux.util.StorageHelper.getPreferredSharedDir(context)
            if (preferredDir.exists()) {
                cmd.add("--bind=${preferredDir.absolutePath}:/sdcard/MobileLinux")
            }
        } catch (ignored: Exception) {}

        // Container environment: default to normal user 'ubuntu'
        val containerEnv = mutableListOf(
            "/usr/bin/env",
            "-i",
            "HOME=/home/ubuntu",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "COLUMNS=$cols",
            "LINES=$rows",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "TMPDIR=/tmp",
            "PATH=/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/home/ubuntu/miniconda3/bin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "SHELL=/usr/bin/bash",
            "USER=ubuntu",
            "LOGNAME=ubuntu",
            "ANDROID_HOST=true",
            "MOBILELINUX_MODE=proot",
            "MOBILELINUX_SESSION=$sessionId"
        )
        val netlinkShim = File(rootfsDir, "usr/local/lib/libfixgetifaddrs.so")
        if (netlinkShim.exists()) {
            containerEnv.add("LD_PRELOAD=/usr/local/lib/libfixgetifaddrs.so")
        }
        cmd.addAll(containerEnv)

        if (execCmd != null) {
            cmd.addAll(listOf("/usr/bin/bash", "-c", execCmd))
        } else {
            // Run interactive shell launcher
            cmd.add("/bin/bash")
            cmd.add("/usr/local/bin/mobilelinux-shell.sh")
        }
        return cmd
    }

    private fun buildChrootCommand(execCmd: String?): List<String> {
        ensureSharedMemoryReady()
        val cmd = mutableListOf(
            "su", "-c",
            buildString {
                append("mkdir -p '${rootfsDir.absolutePath}/dev/shm' '${rootfsDir.absolutePath}/run/shm' 2>/dev/null; ")
                append("mount -t tmpfs -o rw,nosuid,nodev,mode=1777 tmpfs '${rootfsDir.absolutePath}/dev/shm' 2>/dev/null || true; ")
                append("chroot '${rootfsDir.absolutePath}' ")
                append("/usr/bin/env -i ")
                append("HOME=/home/ubuntu ")
                append("TERM=xterm-256color ")
                append("LANG=C.UTF-8 ")
                append("PATH=/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/home/ubuntu/miniconda3/bin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ")
                append("USER=ubuntu SHELL=/usr/bin/bash ANDROID_HOST=true MOBILELINUX_MODE=chroot TMPDIR=/tmp ")
                if (execCmd != null) {
                    append("/usr/bin/bash -c '$execCmd'")
                } else {
                    append("/bin/bash /usr/local/bin/mobilelinux-shell.sh")
                }
            }
        )
        return cmd
    }

    private fun buildEnvironment(cols: Int, rows: Int): Map<String, String> {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val tmpDir = context.cacheDir.absolutePath
        val env = mutableMapOf<String, String>()

        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["COLUMNS"] = cols.toString()
        env["LINES"] = rows.toString()
        env["HOME"] = context.filesDir.absolutePath
        env["TMPDIR"] = tmpDir

        // Create temp dir
        File(tmpDir).mkdirs()
        env["PROOT_TMP_DIR"] = tmpDir

        // Only set loader paths if the files actually exist
        val loader = File(nativeLibDir, "libproot-loader.so")
        if (loader.exists()) {
            env["PROOT_LOADER"] = loader.absolutePath
        }
        val loader32 = File(nativeLibDir, "libproot-loader32.so")
        if (loader32.exists()) {
            env["PROOT_LOADER_32"] = loader32.absolutePath
        }

        // proot uses libtalloc.so — must be findable via LD_LIBRARY_PATH
        env["LD_LIBRARY_PATH"] = "$nativeLibDir:${System.getenv("LD_LIBRARY_PATH") ?: ""}"

        // Disable seccomp filter — often causes crashes on Android 12+ kernels
        env["PROOT_NO_SECCOMP"] = "1"

        // CRITICAL for Android 15/16: Ignore missing/denied bindings (e.g. /sys, restricted mounts)
        env["PROOT_IGNORE_MISSING_BINDINGS"] = "1"
        env["PROOT_FORCE_ROOTFS_FALLBACK"] = "1"

        return env
    }

    /**
     * Runs a command inside the Ubuntu environment and returns output.
     * Used for setup tasks and one-off commands.
     * Includes timeout protection to prevent indefinite hangs.
     */
    private val COMMAND_TIMEOUT_SECONDS = 600L  // 10 minutes

    suspend fun runCommand(
        command: String,
        timeoutSeconds: Long = COMMAND_TIMEOUT_SECONDS,
        onOutputLine: ((String) -> Unit)? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val process = createSessionProcess(
                sessionId = "cmd_${System.currentTimeMillis()}",
                execCommand = command
            )
            val fullOutput = java.lang.StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val sb = java.lang.StringBuilder()
            var ch: Int
            while (reader.read().also { ch = it } != -1) {
                val c = ch.toChar()
                fullOutput.append(c)
                if (c == '\n' || c == '\r') {
                    if (sb.isNotEmpty()) {
                        val segment = sb.toString().trim()
                        if (segment.isNotEmpty()) {
                            onOutputLine?.invoke(segment)
                        }
                        sb.setLength(0)
                    }
                } else {
                    sb.append(c)
                }
            }
            if (sb.isNotEmpty()) {
                val segment = sb.toString().trim()
                if (segment.isNotEmpty()) {
                    onOutputLine?.invoke(segment)
                }
            }
            val completed = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "Command timed out after ${timeoutSeconds}s: ${command.take(80)}")
                process.destroyForcibly()
                Pair(-2, fullOutput.toString() + "\n[MobileLinux] Command timed out after ${timeoutSeconds}s")
            } else {
                Pair(process.exitValue(), fullOutput.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command", e)
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    fun installCommandWrappers() {
        try {
            val usrLocalBin = File(rootfsDir, "usr/local/bin")
            ensureRealDirectory(usrLocalBin)

            val sudoFile = File(usrLocalBin, "sudo")
            safeWriteFile(sudoFile, getSudoScript())
            sudoFile.setExecutable(true, false)
            sudoFile.setReadable(true, false)

            val suFile = File(usrLocalBin, "su")
            safeWriteFile(suFile, getSuScript())
            suFile.setExecutable(true, false)
            suFile.setReadable(true, false)

            val whoamiFile = File(usrLocalBin, "whoami")
            safeWriteFile(whoamiFile, getWhoamiScript())
            whoamiFile.setExecutable(true, false)
            whoamiFile.setReadable(true, false)

            val whoFile = File(usrLocalBin, "who")
            safeWriteFile(whoFile, getWhoScript())
            whoFile.setExecutable(true, false)
            whoFile.setReadable(true, false)

            val idFile = File(usrLocalBin, "id")
            safeWriteFile(idFile, getIdScript())
            idFile.setExecutable(true, false)
            idFile.setReadable(true, false)

            // Direct python wrapper: executes python3 directly
            val pythonFile = File(usrLocalBin, "python")
            safeWriteFile(pythonFile, getPythonWrapperScript())
            pythonFile.setExecutable(true, false)
            pythonFile.setReadable(true, false)

            // Fallback in /usr/bin/python for scripts with #!/usr/bin/python
            try {
                val usrBin = File(rootfsDir, "usr/bin")
                val usrBinPython = File(usrBin, "python")
                if (!usrBinPython.exists()) {
                    safeWriteFile(usrBinPython, "#!/bin/sh\nexec /usr/bin/python3 \"\$@\"\n")
                    usrBinPython.setExecutable(true, false)
                    usrBinPython.setReadable(true, false)
                }
            } catch (ignored: Exception) {}

            // Pip wrapper: executes python3 -m pip or installs pip on-demand
            val pipFile = File(usrLocalBin, "pip")
            safeWriteFile(pipFile, getPipWrapperScript())
            pipFile.setExecutable(true, false)
            pipFile.setReadable(true, false)

            val pip3File = File(usrLocalBin, "pip3")
            safeWriteFile(pip3File, getPipWrapperScript())
            pip3File.setExecutable(true, false)
            pip3File.setReadable(true, false)

            // Configure pip default optimizations for mobile PRoot
            try {
                val etcDir = File(rootfsDir, "etc")
                etcDir.mkdirs()
                val pipConf = File(etcDir, "pip.conf")
                safeWriteFile(pipConf, "[global]\nbreak-system-packages = true\nprefer-binary = true\nno-compile = true\n")
                pipConf.setReadable(true, false)

                val aptConfD = File(rootfsDir, "etc/apt/apt.conf.d")
                aptConfD.mkdirs()
                val mlAptConf = File(aptConfD, "99mobilelinux")
                safeWriteFile(
                    mlAptConf,
                    "DPkg::Lock::Timeout \"60\";\n" +
                    "Acquire::ForceIPv4 \"true\";\n" +
                    "APT::Get::Assume-Yes \"true\";\n" +
                    "APT::Get::AllowUnauthenticated \"false\";\n" +
                    "APT::Sandbox::User \"root\";\n" +
                    "Acquire::http::Pipeline-Depth \"0\";\n" +
                    "Acquire::http::No-Cache \"true\";\n" +
                    "Acquire::Languages \"none\";\n" +
                    "Dpkg::Options {\n" +
                    "    \"--force-confdef\";\n" +
                    "    \"--force-confold\";\n" +
                    "};\n"
                )
                mlAptConf.setReadable(true, false)
            } catch (ignored: Exception) {}

            // Conda and Mamba CLI wrappers
            val condaWrapperFile = File(usrLocalBin, "conda")
            safeWriteFile(condaWrapperFile, getCondaWrapperScript())
            condaWrapperFile.setExecutable(true, false)
            condaWrapperFile.setReadable(true, false)

            val mambaWrapperFile = File(usrLocalBin, "mamba")
            safeWriteFile(mambaWrapperFile, getMambaWrapperScript())
            mambaWrapperFile.setExecutable(true, false)
            mambaWrapperFile.setReadable(true, false)

            // install-tools & pkg-install utilities
            val installToolsFile = File(usrLocalBin, "install-tools")
            safeWriteFile(installToolsFile, getInstallToolsScript())
            installToolsFile.setExecutable(true, false)
            installToolsFile.setReadable(true, false)

            val pkgInstallFile = File(usrLocalBin, "pkg-install")
            safeWriteFile(pkgInstallFile, getPkgInstallScript())
            pkgInstallFile.setExecutable(true, false)
            pkgInstallFile.setReadable(true, false)

            // Safe recursive cleaner and smart rm / force-rm / fix-permissions wrappers
            val rmPyFile = File(usrLocalBin, "mobilelinux-rm.py")
            safeWriteFile(rmPyFile, getMobileLinuxRmPyScript())
            rmPyFile.setExecutable(true, false)
            rmPyFile.setReadable(true, false)

            val rmFile = File(usrLocalBin, "rm")
            safeWriteFile(rmFile, getSmartRmScript())
            rmFile.setExecutable(true, false)
            rmFile.setReadable(true, false)

            val forceRmFile = File(usrLocalBin, "force-rm")
            safeWriteFile(forceRmFile, getForceRmScript())
            forceRmFile.setExecutable(true, false)
            forceRmFile.setReadable(true, false)

            val fixPermsFile = File(usrLocalBin, "fix-permissions")
            safeWriteFile(fixPermsFile, getFixPermissionsScript())
            fixPermsFile.setExecutable(true, false)
            fixPermsFile.setReadable(true, false)

            val fixPermsAlias = File(usrLocalBin, "fix-perms")
            safeWriteFile(fixPermsAlias, getFixPermissionsScript())
            fixPermsAlias.setExecutable(true, false)
            fixPermsAlias.setReadable(true, false)

            // Smart on-demand wrappers for essential tools
            listOf(
                "nano" to "nano",
                "vim" to "vim-tiny",
                "git" to "git",
                "htop" to "htop",
                "tree" to "tree",
                "unzip" to "unzip",
                "zip" to "zip"
            ).forEach { (cmdName, pkgName) ->
                val toolFile = File(usrLocalBin, cmdName)
                safeWriteFile(toolFile, getSmartToolWrapperScript(cmdName, pkgName))
                toolFile.setExecutable(true, false)
                toolFile.setReadable(true, false)
            }

            val jupyterStartFile = File(usrLocalBin, "jupyter-start")
            safeWriteFile(jupyterStartFile, getJupyterStartScript())
            jupyterStartFile.setExecutable(true, false)
            jupyterStartFile.setReadable(true, false)

            // Multi-Environment Python Installer, Uninstaller & Conda Synchronizer
            val pkgInstallPythonFile = File(usrLocalBin, "pkg-install-python")
            safeWriteFile(pkgInstallPythonFile, getPkgInstallPythonScript())
            pkgInstallPythonFile.setExecutable(true, false)
            pkgInstallPythonFile.setReadable(true, false)

            val pkgUninstallPythonFile = File(usrLocalBin, "pkg-uninstall-python")
            safeWriteFile(pkgUninstallPythonFile, getPkgUninstallPythonScript())
            pkgUninstallPythonFile.setExecutable(true, false)
            pkgUninstallPythonFile.setReadable(true, false)

            val condaSyncFile = File(usrLocalBin, "conda-sync-packages")
            safeWriteFile(condaSyncFile, getCondaSyncPackagesScript())
            condaSyncFile.setExecutable(true, false)
            condaSyncFile.setReadable(true, false)

            val condaSyncAlias = File(usrLocalBin, "conda-sync")
            safeWriteFile(condaSyncAlias, getCondaSyncPackagesScript())
            condaSyncAlias.setExecutable(true, false)
            condaSyncAlias.setReadable(true, false)

            val condaManagerFile = File(usrLocalBin, "conda-manager")
            try {
                context.assets.open("scripts/conda-manager.sh").use { input ->
                    condaManagerFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                condaManagerFile.setExecutable(true, false)
                condaManagerFile.setReadable(true, false)
            } catch (ignored: Exception) {}

            val installCondaFile = File(usrLocalBin, "install-conda")
            safeWriteFile(installCondaFile, getInstallCondaScript())
            installCondaFile.setExecutable(true, false)
            installCondaFile.setReadable(true, false)

            val condaInstallAlias = File(usrLocalBin, "conda-install")
            safeWriteFile(condaInstallAlias, getInstallCondaScript())
            condaInstallAlias.setExecutable(true, false)
            condaInstallAlias.setReadable(true, false)

            val installJupyterFile = File(usrLocalBin, "install-jupyter")
            safeWriteFile(installJupyterFile, getInstallJupyterScript())
            installJupyterFile.setExecutable(true, false)
            installJupyterFile.setReadable(true, false)

            val jupyterWrapper = File(usrLocalBin, "jupyter")
            if (!jupyterWrapper.exists() || (jupyterWrapper.isFile && jupyterWrapper.readText().contains("MobileLinux Smart Jupyter Dispatcher"))) {
                safeWriteFile(jupyterWrapper, getJupyterDispatcherScript())
                jupyterWrapper.setExecutable(true, false)
                jupyterWrapper.setReadable(true, false)
            }

            val jupyterNotebookWrapper = File(usrLocalBin, "jupyter-notebook")
            if (!jupyterNotebookWrapper.exists() || (jupyterNotebookWrapper.isFile && jupyterNotebookWrapper.readText().contains("MobileLinux Smart Jupyter Notebook Dispatcher"))) {
                safeWriteFile(jupyterNotebookWrapper, getJupyterNotebookDispatcherScript())
                jupyterNotebookWrapper.setExecutable(true, false)
                jupyterNotebookWrapper.setReadable(true, false)
            }

            val jupyterLabWrapper = File(usrLocalBin, "jupyter-lab")
            if (!jupyterLabWrapper.exists() || (jupyterLabWrapper.isFile && jupyterLabWrapper.readText().contains("MobileLinux Smart JupyterLab Dispatcher"))) {
                safeWriteFile(jupyterLabWrapper, getJupyterLabDispatcherScript())
                jupyterLabWrapper.setExecutable(true, false)
                jupyterLabWrapper.setReadable(true, false)
            }

            val fixJupyterMobileFile = File(usrLocalBin, "fix-jupyter-mobile")
            safeWriteFile(fixJupyterMobileFile, getFixJupyterMobileScript())
            fixJupyterMobileFile.setExecutable(true, false)
            fixJupyterMobileFile.setReadable(true, false)

            // Install Smart Python CLI Utilities in /usr/local/bin
            for (cfg in pythonCliConfigs) {
                try {
                    val f = File(usrLocalBin, cfg.cmdName)
                    safeWriteFile(f, getPythonCliWrapperScript(cfg))
                    f.setExecutable(true, false)
                    f.setReadable(true, false)
                } catch (ignored: Exception) {}
            }

            // Mirror real CLI utility scripts into all discovered Conda bin directories
            try {
                val condaBins = mutableListOf<File>()
                val baseCondaBin = File(rootfsDir, "home/ubuntu/miniforge3/bin")
                if (baseCondaBin.exists()) condaBins.add(baseCondaBin)
                val rootCondaBin = File(rootfsDir, "root/miniconda3/bin")
                if (rootCondaBin.exists()) condaBins.add(rootCondaBin)

                val condaEnvsDir = File(rootfsDir, "home/ubuntu/miniforge3/envs")
                if (condaEnvsDir.exists() && condaEnvsDir.isDirectory) {
                    condaEnvsDir.listFiles()?.forEach { envDir ->
                        val envBin = File(envDir, "bin")
                        if (envBin.exists() && envBin.isDirectory) condaBins.add(envBin)
                    }
                }
                val rootEnvsDir = File(rootfsDir, "root/miniconda3/envs")
                if (rootEnvsDir.exists() && rootEnvsDir.isDirectory) {
                    rootEnvsDir.listFiles()?.forEach { envDir ->
                        val envBin = File(envDir, "bin")
                        if (envBin.exists() && envBin.isDirectory) condaBins.add(envBin)
                    }
                }

                val toolsToLink = listOf("pkg-install-python", "pkg-uninstall-python", "conda-sync-packages", "conda-sync", "conda-manager", "install-jupyter", "jupyter-start", "jupyter-notebook", "jupyter-lab") + pythonCliConfigs.map { it.cmdName }
                for (cBin in condaBins) {
                    for (tool in toolsToLink) {
                        val targetLink = File(cBin, tool)
                        safeWriteFile(targetLink, "#!/bin/sh\nexec /usr/local/bin/$tool \"\$@\"\n")
                        targetLink.setExecutable(true, false)
                        targetLink.setReadable(true, false)
                    }
                }
            } catch (ignored: Exception) {}

            Log.d(TAG, "Command wrappers installed ✓")
        } catch (e: Exception) {
            Log.w(TAG, "Wrappers install notice: ${e.message}")
        }
    }

    private fun installBashEnvironments() {
        try {
            val ubuntuHome = File(rootfsDir, "home/ubuntu")
            ensureRealDirectory(ubuntuHome)
            ubuntuHome.setReadable(true, false)
            ubuntuHome.setWritable(false, false)
            ubuntuHome.setWritable(true, true)
            ubuntuHome.setExecutable(true, false)

            val ubuntuLocalBin = File(ubuntuHome, ".local/bin")
            val ubuntuLocalShare = File(ubuntuHome, ".local/share")
            val ubuntuGoBin = File(ubuntuHome, "go/bin")
            val ubuntuCargoBin = File(ubuntuHome, ".cargo/bin")
            ensureRealDirectory(ubuntuLocalBin)
            ensureRealDirectory(ubuntuLocalShare)
            ensureRealDirectory(ubuntuGoBin)
            ensureRealDirectory(ubuntuCargoBin)
            listOf(ubuntuHome, File(ubuntuHome, ".local"), ubuntuLocalBin, ubuntuLocalShare, File(ubuntuHome, "go"), ubuntuGoBin, File(ubuntuHome, ".cargo"), ubuntuCargoBin).forEach {
                it.setReadable(true, false)
                it.setWritable(false, false) // Strip any other-writable (o+w) bits to eliminate ugly green highlights in ls
                it.setWritable(true, true)  // Owner only (0755)
                it.setExecutable(true, false)
            }
            val ubuntuBashrc = File(ubuntuHome, ".bashrc")
            if (!ubuntuBashrc.exists()) {
                safeWriteFile(ubuntuBashrc, getUbuntuBashrc())
            } else {
                var existing = ubuntuBashrc.readText()
                var modified = false
                if (!existing.contains("alias clear=")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("alias clear='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    sb.append("alias cls='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    existing = sb.toString()
                    modified = true
                }
                // Strip legacy python/pip aliases that override Conda/venv environment isolation
                if (existing.contains("alias python='python3'") || existing.contains("alias pip='python3 -m pip'")) {
                    existing = existing.replace("alias python='python3'\n", "")
                        .replace("alias python='python3'", "")
                        .replace("alias pip='python3 -m pip'\n", "")
                        .replace("alias pip='python3 -m pip'", "")
                    modified = true
                }
                // Ensure PATH includes user local bin (~/.local/bin, go/bin, cargo/bin)
                if (!existing.contains("/home/ubuntu/.local/bin")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:\$PATH\"\n")
                    existing = sb.toString()
                    modified = true
                }
                // Ensure clean LS_COLORS (disable hideous green/black highlight on other-writable/sticky folders)
                if (!existing.contains("ow=01;34")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("""
                        # MobileLinux Clean Terminal Colors
                        if command -v dircolors >/dev/null 2>&1; then
                            eval "${'$'}(dircolors -b 2>/dev/null)"
                        fi
                        if [ -n "${'$'}LS_COLORS" ]; then
                            export LS_COLORS="${'$'}(echo "${'$'}LS_COLORS" | sed 's/ow=[0-9;]*/ow=01;34/g; s/tw=[0-9;]*/tw=01;34/g; s/st=[0-9;]*/st=01;34/g'):ow=01;34:tw=01;34:st=01;34:"
                        else
                            export LS_COLORS="rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:ca=00:tw=01;34:ow=01;34:st=01;34:ex=01;32:"
                        fi
                    """.trimIndent()).append("\n")
                    existing = sb.toString()
                    modified = true
                }
                // Ensure PATH includes conda/miniforge
                if (existing.contains("export PATH=") && !existing.contains("/home/ubuntu/miniforge3/bin")) {
                    existing = existing.replace(
                        "export PATH=\"/usr/local/sbin",
                        "export PATH=\"/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin"
                    )
                    modified = true
                }
                if (modified) {
                    safeWriteFile(ubuntuBashrc, existing)
                }
            }
            ubuntuBashrc.setReadable(true, false)
            ubuntuBashrc.setWritable(true, true)

            val ubuntuProfile = File(ubuntuHome, ".profile")
            if (!ubuntuProfile.exists()) {
                safeWriteFile(ubuntuProfile, getProfileContent())
            }
            ubuntuProfile.setReadable(true, false)
            ubuntuProfile.setWritable(true, true)

            val ubuntuBashProfile = File(ubuntuHome, ".bash_profile")
            if (ubuntuBashProfile.exists()) {
                val profileText = ubuntuBashProfile.readText()
                if (!profileText.contains(".bashrc")) {
                    safeWriteFile(ubuntuBashProfile, "$profileText\nif [ -f \"\$HOME/.bashrc\" ]; then\n    . \"\$HOME/.bashrc\"\nfi\n")
                }
            } else {
                safeWriteFile(ubuntuBashProfile, "if [ -f \"\$HOME/.bashrc\" ]; then\n    . \"\$HOME/.bashrc\"\nfi\n")
            }
            ubuntuBashProfile.setReadable(true, false)
            ubuntuBashProfile.setWritable(true, true)

            // Ensure PRoot compatibility: enforce always_copy and auto_activate_base so Conda does not use hardlinks (.l2s)
            val condarcContent = "always_copy: true\nauto_activate_base: true\nnotify_outdated_conda: false\n"
            val ubuntuCondarc = File(ubuntuHome, ".condarc")
            if (!ubuntuCondarc.exists() || !ubuntuCondarc.readText().contains("auto_activate_base")) {
                safeWriteFile(ubuntuCondarc, condarcContent)
            }
            ubuntuCondarc.setReadable(true, false)
            ubuntuCondarc.setWritable(true, true)

            val rootHome = File(rootfsDir, "root")
            ensureRealDirectory(rootHome)
            val rootLocalBin = File(rootHome, ".local/bin")
            val rootLocalShare = File(rootHome, ".local/share")
            val rootGoBin = File(rootHome, "go/bin")
            val rootCargoBin = File(rootHome, ".cargo/bin")
            ensureRealDirectory(rootLocalBin)
            ensureRealDirectory(rootLocalShare)
            ensureRealDirectory(rootGoBin)
            ensureRealDirectory(rootCargoBin)
            rootHome.setReadable(true, false)
            rootHome.setWritable(false, false)
            rootHome.setWritable(true, true)
            rootHome.setExecutable(true, false)

            listOf(rootHome, File(rootHome, ".local"), rootLocalBin, rootLocalShare, File(rootHome, "go"), rootGoBin, File(rootHome, ".cargo"), rootCargoBin).forEach {
                it.setReadable(true, false)
                it.setWritable(false, false) // Strip other-writable bits
                it.setWritable(true, true)  // Owner only (0755)
                it.setExecutable(true, false)
            }

            val rootBashrc = File(rootHome, ".bashrc")
            if (!rootBashrc.exists()) {
                safeWriteFile(rootBashrc, getRootBashrc())
            } else {
                var existing = rootBashrc.readText()
                var modified = false
                if (!existing.contains("alias clear=")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("alias clear='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    sb.append("alias cls='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    existing = sb.toString()
                    modified = true
                }
                if (existing.contains("alias python='python3'") || existing.contains("alias pip='python3 -m pip'")) {
                    existing = existing.replace("alias python='python3'\n", "")
                        .replace("alias python='python3'", "")
                        .replace("alias pip='python3 -m pip'\n", "")
                        .replace("alias pip='python3 -m pip'", "")
                    modified = true
                }
                // Ensure PATH includes user local bin (~/.local/bin, go/bin, cargo/bin)
                if (!existing.contains("/root/.local/bin")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("export PATH=\"/root/.local/bin:/home/ubuntu/.local/bin:/root/go/bin:/home/ubuntu/go/bin:/root/.cargo/bin:/home/ubuntu/.cargo/bin:\$PATH\"\n")
                    existing = sb.toString()
                    modified = true
                }
                // Ensure clean LS_COLORS (disable hideous green/black highlight on other-writable/sticky folders)
                if (!existing.contains("ow=01;34")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("""
                        # MobileLinux Clean Terminal Colors
                        if command -v dircolors >/dev/null 2>&1; then
                            eval "${'$'}(dircolors -b 2>/dev/null)"
                        fi
                        if [ -n "${'$'}LS_COLORS" ]; then
                            export LS_COLORS="${'$'}(echo "${'$'}LS_COLORS" | sed 's/ow=[0-9;]*/ow=01;34/g; s/tw=[0-9;]*/tw=01;34/g; s/st=[0-9;]*/st=01;34/g'):ow=01;34:tw=01;34:st=01;34:"
                        else
                            export LS_COLORS="rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:ca=00:tw=01;34:ow=01;34:st=01;34:ex=01;32:"
                        fi
                    """.trimIndent()).append("\n")
                    existing = sb.toString()
                    modified = true
                }
                // Ensure PATH includes conda/miniforge
                if (existing.contains("export PATH=") && !existing.contains("/home/ubuntu/miniforge3/bin")) {
                    existing = existing.replace(
                        "export PATH=\"/usr/local/sbin",
                        "export PATH=\"/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin"
                    )
                    modified = true
                }
                if (modified) {
                    safeWriteFile(rootBashrc, existing)
                }
            }
            rootBashrc.setReadable(true, false)
            rootBashrc.setWritable(true, true)

            val rootProfile = File(rootHome, ".profile")
            if (!rootProfile.exists()) {
                safeWriteFile(rootProfile, getProfileContent())
            }
            rootProfile.setReadable(true, false)
            rootProfile.setWritable(true, true)

            val rootBashProfile = File(rootHome, ".bash_profile")
            if (rootBashProfile.exists()) {
                val profileText = rootBashProfile.readText()
                if (!profileText.contains(".bashrc")) {
                    safeWriteFile(rootBashProfile, "$profileText\nif [ -f \"\$HOME/.bashrc\" ]; then\n    . \"\$HOME/.bashrc\"\nfi\n")
                }
            } else {
                safeWriteFile(rootBashProfile, "if [ -f \"\$HOME/.bashrc\" ]; then\n    . \"\$HOME/.bashrc\"\nfi\n")
            }
            rootBashProfile.setReadable(true, false)
            rootBashProfile.setWritable(true, true)

            val rootCondarc = File(rootHome, ".condarc")
            if (!rootCondarc.exists() || !rootCondarc.readText().contains("auto_activate_base")) {
                safeWriteFile(rootCondarc, condarcContent)
            }
            rootCondarc.setReadable(true, false)
            rootCondarc.setWritable(true, true)

            // CRITICAL: Clean up obsolete mobilelinux-shell.sh from user home directories
            // In earlier versions, this file was placed in ~/ and caused line-wrapping (e.g. stray 'd') in `ls`
            try {
                File(ubuntuHome, "mobilelinux-shell.sh").delete()
                File(rootHome, "mobilelinux-shell.sh").delete()
            } catch (ignored: Exception) {}

            val usrLocalBin = File(rootfsDir, "usr/local/bin")
            ensureRealDirectory(usrLocalBin)
            val shellLauncher = File(usrLocalBin, "mobilelinux-shell.sh")
            val shellLauncherContent = getShellLauncher()
            safeWriteFile(shellLauncher, shellLauncherContent)
            shellLauncher.setReadable(true, false)
            shellLauncher.setExecutable(true, false)

            val ptyBridge = File(usrLocalBin, "mobilelinux-pty.py")
            val ptyBridgeContent = getPtyBridgeScript()
            safeWriteFile(ptyBridge, ptyBridgeContent)
            ptyBridge.setReadable(true, false)
            ptyBridge.setExecutable(true, false)

            // Auto-detect and configure Conda initialization & auto-activation in .bashrc & .condarc
            configureCondaEnvironment()

            Log.d(TAG, "Bash environments configured ✓")
        } catch (e: Exception) {
            Log.w(TAG, "Bash environment install notice: ${e.message}")
        }
    }

    /**
     * Automatically configures Conda initialization and default auto-activation in .bashrc and .condarc.
     * Guarantees that the Conda 'base' environment (or user's selected default env) is automatically
     * activated when any terminal session starts, without requiring manual activation commands.
     */
    fun configureCondaEnvironment() {
        try {
            val candidatePaths = listOf(
                "/home/ubuntu/miniforge3",
                "/home/ubuntu/miniconda3",
                "/root/miniconda3",
                "/root/miniforge3",
                "/opt/conda"
            )

            var detectedContainerDir: String? = null
            for (candidate in candidatePaths) {
                val condaBin = File(rootfsDir, "${candidate.removePrefix("/")}/bin/conda")
                if (condaBin.exists()) {
                    condaBin.setExecutable(true, false)
                    condaBin.setReadable(true, false)
                    detectedContainerDir = candidate
                    break
                }
            }

            if (detectedContainerDir == null) {
                return
            }

            val condaBinPath = "$detectedContainerDir/bin/conda"
            val ubuntuHome = File(rootfsDir, "home/ubuntu")
            val rootHome = File(rootfsDir, "root")

            // 1. Ensure .condarc has always_copy and auto_activate_base
            val condarcContent = "always_copy: true\nauto_activate_base: true\nnotify_outdated_conda: false\n"
            val ubuntuCondarc = File(ubuntuHome, ".condarc")
            safeWriteFile(ubuntuCondarc, condarcContent)
            ubuntuCondarc.setReadable(true, false)
            ubuntuCondarc.setWritable(true, false)

            val rootCondarc = File(rootHome, ".condarc")
            safeWriteFile(rootCondarc, condarcContent)
            rootCondarc.setReadable(true, false)
            rootCondarc.setWritable(true, false)

            // 2. Configure .bashrc for ubuntu user
            val ubuntuBashrc = File(ubuntuHome, ".bashrc")
            if (ubuntuBashrc.exists()) {
                configureBashrcForConda(ubuntuBashrc, detectedContainerDir, condaBinPath)
            }

            // 3. Configure .bashrc for root user
            val rootCondaDir = if (File(rootfsDir, "root/miniconda3/bin/conda").exists()) {
                "/root/miniconda3"
            } else {
                detectedContainerDir
            }
            val rootBashrc = File(rootHome, ".bashrc")
            if (rootBashrc.exists()) {
                configureBashrcForConda(rootBashrc, rootCondaDir, "$rootCondaDir/bin/conda")
            }

            // 4. Create direct global wrappers for conda and mamba in /usr/local/bin
            val usrLocalBin = File(rootfsDir, "usr/local/bin")
            ensureRealDirectory(usrLocalBin)
            val condaWrapper = File(usrLocalBin, "conda")
            safeWriteFile(condaWrapper, "#!/bin/sh\nexec $condaBinPath \"\$@\"\n")
            condaWrapper.setExecutable(true, false)
            condaWrapper.setReadable(true, false)

            val mambaBin = File(rootfsDir, "${detectedContainerDir.removePrefix("/")}/bin/mamba")
            if (mambaBin.exists()) {
                val mambaWrapper = File(usrLocalBin, "mamba")
                safeWriteFile(mambaWrapper, "#!/bin/sh\nexec $detectedContainerDir/bin/mamba \"\$@\"\n")
                mambaWrapper.setExecutable(true, false)
                mambaWrapper.setReadable(true, false)
            }

            // 5. Mirror wrappers into discovered Conda bin directories
            installCommandWrappers()

            Log.d(TAG, "Conda environment configured & auto-activated for: $detectedContainerDir")
        } catch (e: Exception) {
            Log.w(TAG, "configureCondaEnvironment notice: ${e.message}")
        }
    }

    private fun configureBashrcForConda(bashrcFile: File, condaDir: String, condaBin: String) {
        try {
            var content = bashrcFile.readText()
            var modified = false

            val condaInitMarkerStart = "# >>> conda initialize >>>"
            val condaInitMarkerEnd = "# <<< conda initialize <<<"
            val autoActivateMarker = "# MobileLinux: Auto-activate Conda environment"

            val condaSetupBlock = buildString {
                append("\n$condaInitMarkerStart\n")
                append("# !! Contents within this block are managed by 'conda init' !!\n")
                append("__conda_setup=\"\$('$condaBin' 'shell.bash' 'hook' 2> /dev/null)\"\n")
                append("if [ $? -eq 0 ]; then\n")
                append("    eval \"\$__conda_setup\"\n")
                append("else\n")
                append("    if [ -f \"$condaDir/etc/profile.d/conda.sh\" ]; then\n")
                append("        . \"$condaDir/etc/profile.d/conda.sh\"\n")
                append("    else\n")
                append("        export PATH=\"$condaDir/bin:\$PATH\"\n")
                append("    fi\n")
                append("fi\n")
                append("unset __conda_setup\n")
                append("$condaInitMarkerEnd\n")
            }

            val autoActivateBlock = buildString {
                append("\n$autoActivateMarker\n")
                append("if [ -z \"\$CONDA_DEFAULT_ENV\" ] && type conda >/dev/null 2>&1; then\n")
                append("    if ! grep -q \"conda-manager default-env\" \"\$HOME/.bashrc\" 2>/dev/null; then\n")
                append("        conda activate base 2>/dev/null || true\n")
                append("    fi\n")
                append("fi\n")
            }

            if (!content.contains(condaInitMarkerStart)) {
                content = content.trimEnd() + "\n" + condaSetupBlock + autoActivateBlock
                modified = true
            } else {
                if (!content.contains(autoActivateMarker)) {
                    content = content.trimEnd() + "\n" + autoActivateBlock
                    modified = true
                }
            }

            // Ensure Conda block is placed AFTER PS1 definition so (base) is not overwritten
            if (content.contains(condaInitMarkerStart) && content.contains("PS1=")) {
                val lastPs1Index = content.lastIndexOf("PS1=")
                val condaStartIndex = content.indexOf(condaInitMarkerStart)
                if (lastPs1Index > condaStartIndex) {
                    val startIdx = content.indexOf(condaInitMarkerStart)
                    val endMarkerIdx = content.indexOf(condaInitMarkerEnd)
                    if (startIdx != -1 && endMarkerIdx != -1) {
                        val endIdx = content.indexOf("\n", endMarkerIdx)
                        val actualEnd = if (endIdx != -1) endIdx + 1 else content.length
                        val extractedConda = content.substring(startIdx, actualEnd)
                        content = (content.substring(0, startIdx) + content.substring(actualEnd)).trimEnd() + "\n\n" + extractedConda
                        if (!content.contains(autoActivateMarker)) {
                            content += "\n" + autoActivateBlock
                        }
                        modified = true
                    }
                }
            }

            if (modified) {
                safeWriteFile(bashrcFile, content)
                bashrcFile.setReadable(true, false)
                bashrcFile.setWritable(true, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "configureBashrcForConda notice: ${e.message}")
        }
    }

    /**
     * Installs ZeroMQ netlink fix (libfixgetifaddrs.so) to prevent ipykernel/ZeroMQ crashes
     * on Android 11-16, and pre-configures Jupyter Server, Notebook, and IPython kernel.
     */
    private fun installJupyterAndNetlinkFixes() {
        try {
            val etcDir = File(rootfsDir, "etc")
            ensureRealDirectory(etcDir)

            // 1. ZeroMQ Netlink Fix for Android (libfixgetifaddrs.so)
            val usrLocalLib = File(rootfsDir, "usr/local/lib")
            ensureRealDirectory(usrLocalLib)
            val shimTarget = File(usrLocalLib, "libfixgetifaddrs.so")
            if (!shimTarget.exists() || shimTarget.length() == 0L) {
                val nativeShim = File(context.applicationInfo.nativeLibraryDir, "libfixgetifaddrs.so")
                val scriptShim = File(scriptsDir, "libfixgetifaddrs.so")
                if (nativeShim.exists() && nativeShim.length() > 0) {
                    nativeShim.copyTo(shimTarget, overwrite = true)
                } else if (scriptShim.exists() && scriptShim.length() > 0) {
                    scriptShim.copyTo(shimTarget, overwrite = true)
                } else {
                    try {
                        context.assets.open("scripts/libfixgetifaddrs.so").use { input ->
                            shimTarget.outputStream().use { output -> input.copyTo(output) }
                        }
                    } catch (ignored: Exception) {}
                }
                shimTarget.setReadable(true, false)
                shimTarget.setExecutable(true, false)
            }

            if (shimTarget.exists() && shimTarget.length() > 0) {
                val preloadFile = File(etcDir, "ld.so.preload")
                val shimPath = "/usr/local/lib/libfixgetifaddrs.so"
                val existingPreload = if (preloadFile.exists()) preloadFile.readText() else ""
                if (!existingPreload.contains(shimPath)) {
                    val newPreload = if (existingPreload.isEmpty()) "$shimPath\n" else "$existingPreload\n$shimPath\n"
                    safeWriteFile(preloadFile, newPreload)
                    preloadFile.setReadable(true, false)
                }
                val profileDir = File(etcDir, "profile.d")
                ensureRealDirectory(profileDir)
                safeWriteFile(File(profileDir, "01-netlink-fix.sh"), "export LD_PRELOAD=/usr/local/lib/libfixgetifaddrs.so\n")
            }

            // 2. Pre-configure Jupyter Server & Notebook system-wide & per-user
            val jupyterDir = File(etcDir, "jupyter")
            ensureRealDirectory(jupyterDir)
            val ipythonDir = File(etcDir, "ipython")
            ensureRealDirectory(ipythonDir)

            val jupyterConfigContent = listOf(
                "# MobileLinux Built-in Configuration for Jupyter Server & Notebook",
                "c = get_config()",
                "c.ServerApp.allow_root = True",
                "c.NotebookApp.allow_root = True",
                "c.ServerApp.ip = '127.0.0.1'",
                "c.NotebookApp.ip = '127.0.0.1'",
                "c.ServerApp.port = 8888",
                "c.NotebookApp.port = 8888",
                "c.ServerApp.open_browser = False",
                "c.NotebookApp.open_browser = False",
                "c.ServerApp.token = ''",
                "c.NotebookApp.token = ''",
                "c.ServerApp.password = ''",
                "c.NotebookApp.password = ''",
                "c.IdentityProvider.token = ''",
                "c.IdentityProvider.password = ''",
                "c.ServerApp.disable_check_xsrf = True",
                "c.NotebookApp.disable_check_xsrf = True",
                "c.ServerApp.root_dir = '/home/ubuntu'",
                "c.NotebookApp.root_dir = '/home/ubuntu'",
                "c.IPKernelApp.ip = '127.0.0.1'",
                "c.JupyterNotebookApp.expose_app_in_browser = True",
                "c.JupyterNotebookApp.custom_css = True\n"
            ).joinToString("\n")

            safeWriteFile(File(jupyterDir, "jupyter_server_config.py"), jupyterConfigContent)
            safeWriteFile(File(jupyterDir, "jupyter_notebook_config.py"), jupyterConfigContent)
            safeWriteFile(File(ipythonDir, "ipython_kernel_config.py"), "c = get_config()\nc.IPKernelApp.ip = '127.0.0.1'\n")

            val ubuntuHome = File(rootfsDir, "home/ubuntu")
            val rootHome = File(rootfsDir, "root")
            ensureRealDirectory(ubuntuHome)
            ensureRealDirectory(rootHome)

            val ubuntuJupyterDir = File(ubuntuHome, ".jupyter")
            val rootJupyterDir = File(rootHome, ".jupyter")
            ensureRealDirectory(ubuntuJupyterDir)
            ensureRealDirectory(rootJupyterDir)

            safeWriteFile(File(ubuntuJupyterDir, "jupyter_server_config.py"), jupyterConfigContent)
            safeWriteFile(File(ubuntuJupyterDir, "jupyter_notebook_config.py"), jupyterConfigContent)
            safeWriteFile(File(rootJupyterDir, "jupyter_server_config.py"), jupyterConfigContent)
            safeWriteFile(File(rootJupyterDir, "jupyter_notebook_config.py"), jupyterConfigContent)

            // Custom.js to open notebooks in same tab on mobile
            val customDir = File(ubuntuJupyterDir, "custom")
            val rootCustomDir = File(rootJupyterDir, "custom")
            ensureRealDirectory(customDir)
            ensureRealDirectory(rootCustomDir)
            safeWriteFile(
                File(customDir, "custom.js"),
                "define(['base/js/namespace'], function(Jupyter) { if (Jupyter) { Jupyter._target = '_self'; } });\n"
            )

            // Mobile-optimized touch CSS for menus and toolbar
            val customCss = listOf(
                "/* MobileLinux Touch Optimization for Jupyter Notebook */",
                ".lm-Menu-item { min-height: 44px !important; padding: 10px 18px !important; font-size: 15px !important; touch-action: manipulation !important; }",
                ".lm-MenuBar-item { min-height: 38px !important; padding: 8px 14px !important; font-size: 14px !important; touch-action: manipulation !important; }\n"
            ).joinToString("\n")
            safeWriteFile(File(customDir, "custom.css"), customCss)
            safeWriteFile(File(rootCustomDir, "custom.css"), customCss)

            // Install fix-jupyter-mobile script and patch existing HTML templates
            val usrLocalBin = File(rootfsDir, "usr/local/bin")
            ensureRealDirectory(usrLocalBin)
            val fixJupyterMobileFile = File(usrLocalBin, "fix-jupyter-mobile")
            safeWriteFile(fixJupyterMobileFile, getFixJupyterMobileScript())
            fixJupyterMobileFile.setExecutable(true, false)
            fixJupyterMobileFile.setReadable(true, false)

            patchJupyterTemplatesForMobile(rootfsDir)

            // Conda always_copy mode — use FULL .condarc content (must match installBashEnvironments)
            // BUG FIX: Previously this wrote only "always_copy: true" which overwrote the
            // complete .condarc from installBashEnvironments(), breaking auto_activate_base.
            val fullCondarcContent = "always_copy: true\nauto_activate_base: true\nnotify_outdated_conda: false\n"
            val ubuntuCondarcFile = File(ubuntuHome, ".condarc")
            if (!ubuntuCondarcFile.exists() || !ubuntuCondarcFile.readText().contains("auto_activate_base")) {
                safeWriteFile(ubuntuCondarcFile, fullCondarcContent)
            }
            val rootCondarcFile = File(rootHome, ".condarc")
            if (!rootCondarcFile.exists() || !rootCondarcFile.readText().contains("auto_activate_base")) {
                safeWriteFile(rootCondarcFile, fullCondarcContent)
            }

            // Clean up obsolete Debian jsonschema dist-packages that lack pip RECORD files
            try {
                val debianDistDir = File(rootfsDir, "usr/lib/python3/dist-packages")
                if (debianDistDir.exists() && debianDistDir.isDirectory) {
                    debianDistDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("jsonschema")) {
                            file.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notice: cleanDebianDistPackages: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: installJupyterAndNetlinkFixes: ${e.message}")
        }
    }

    private fun getJupyterStartScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux - Smart Jupyter Launcher",
        "echo -e \"\\033[1;36m┌─[MobileLinux]─[Jupyter Server]\\033[0m\"",
        "echo -e \"\\033[1;36m│\\033[0m Starting Jupyter on \\033[1;33m127.0.0.1:8888\\033[0m (No password needed)\"",
        "echo -e \"\\033[1;36m│\\033[0m \\033[1;32mJupyterLab URL:\\033[0m http://127.0.0.1:8888/lab\"",
        "echo -e \"\\033[1;36m│\\033[0m \\033[1;32mNotebook URL:\\033[0m   http://127.0.0.1:8888/tree\"",
        "echo -e \"\\033[1;36m└──────────────────────────────────────────────\\033[0m\"",
        "",
        "JUPYTER_CMD=\"\"",
        "HAS_NOTEBOOK=0",
        "HAS_LAB=0",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ] && \"\$CONDA_PREFIX/bin/python\" -c \"import notebook\" 2>/dev/null; then",
        "    HAS_NOTEBOOK=1",
        "    JUPYTER_CMD=\"\$CONDA_PREFIX/bin/python -m\"",
        "elif [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ] && \"\$CONDA_PREFIX/bin/python\" -c \"import jupyterlab\" 2>/dev/null; then",
        "    HAS_LAB=1",
        "    JUPYTER_CMD=\"\$CONDA_PREFIX/bin/python -m\"",
        "elif python3 -c \"import notebook\" 2>/dev/null; then",
        "    HAS_NOTEBOOK=1",
        "    JUPYTER_CMD=\"python3 -m\"",
        "elif python3 -c \"import jupyterlab\" 2>/dev/null; then",
        "    HAS_LAB=1",
        "    JUPYTER_CMD=\"python3 -m\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c \"import notebook\" 2>/dev/null; then",
        "    HAS_NOTEBOOK=1",
        "    JUPYTER_CMD=\"/home/ubuntu/miniforge3/bin/python -m\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c \"import jupyterlab\" 2>/dev/null; then",
        "    HAS_LAB=1",
        "    JUPYTER_CMD=\"/home/ubuntu/miniforge3/bin/python -m\"",
        "fi",
        "",
        "if [ \$HAS_LAB -eq 0 ] && [ \$HAS_NOTEBOOK -eq 0 ]; then",
        "    echo -e \"\\033[1;31m[MobileLinux]\\033[0m Neither JupyterLab nor Jupyter Notebook is installed yet.\"",
        "    echo -e \"Run \\033[1;33minstall-jupyter\\033[0m in terminal or install from 'Libraries & Packages' in the app.\"",
        "    exit 1",
        "fi",
        "",
        "if [ -x /usr/local/bin/fix-jupyter-mobile ]; then",
        "    /usr/local/bin/fix-jupyter-mobile >/dev/null 2>&1 || true",
        "fi",
        "",
        "if [ \$HAS_NOTEBOOK -eq 1 ]; then",
        "    exec \$JUPYTER_CMD notebook --allow-root --no-browser --ip=127.0.0.1 \"\$@\"",
        "else",
        "    exec \$JUPYTER_CMD lab --allow-root --no-browser --ip=127.0.0.1 \"\$@\"",
        "fi\n"
    ).joinToString("\n")

    private fun getCondaWrapperScript(): String = listOf(
        "#!/bin/sh",
        "# MobileLinux Conda dispatcher",
        "for d in /home/ubuntu/miniforge3 /home/ubuntu/miniconda3 /root/miniconda3 /root/miniforge3 /opt/conda; do",
        "    if [ -x \"\$d/bin/conda\" ]; then",
        "        exec \"\$d/bin/conda\" \"\$@\"",
        "    fi",
        "done",
        "echo \"conda: command not found (Miniconda / Miniforge not installed yet. You can install it from Libraries & Packages)\" >&2",
        "exit 127\n"
    ).joinToString("\n")

    private fun getMambaWrapperScript(): String = listOf(
        "#!/bin/sh",
        "# MobileLinux Mamba dispatcher",
        "for d in /home/ubuntu/miniforge3 /home/ubuntu/miniconda3 /root/miniconda3 /root/miniforge3 /opt/conda; do",
        "    if [ -x \"\$d/bin/mamba\" ]; then",
        "        exec \"\$d/bin/mamba\" \"\$@\"",
        "    fi",
        "done",
        "if [ -x /usr/local/bin/conda ]; then",
        "    exec /usr/local/bin/conda \"\$@\"",
        "fi",
        "echo \"mamba: command not found\" >&2",
        "exit 127\n"
    ).joinToString("\n")

    private fun getSudoScript(): String = listOf(
        "#!/bin/bash",
        "export LANG=C.UTF-8",
        "export LC_ALL=C.UTF-8",
        "export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/home/ubuntu/miniconda3/bin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:\$PATH\"",
        "if [ \$# -eq 0 ]; then",
        "    echo \"usage: sudo [-h] [-i | -s] [command]\"",
        "    exit 1",
        "fi",
        "while [ \$# -gt 0 ]; do",
        "    case \"\$1\" in",
        "        su)",
        "            shift",
        "            export USER=root",
        "            export LOGNAME=root",
        "            export HOME=/root",
        "            export LANG=C.UTF-8",
        "            export LC_ALL=C.UTF-8",
        "            if [ \"\$1\" = \"-\" ] || [ \"\$1\" = \"-l\" ] || [ \"\$1\" = \"--login\" ]; then",
        "                cd /root",
        "            fi",
        "            exec /bin/bash --login -i",
        "            ;;",
        "        -i|--login|-s|--shell)",
        "            shift",
        "            cd /root",
        "            export USER=root",
        "            export LOGNAME=root",
        "            export HOME=/root",
        "            export LANG=C.UTF-8",
        "            export LC_ALL=C.UTF-8",
        "            if [ \$# -gt 0 ]; then",
        "                exec /bin/bash --login -c \"\$*\"",
        "            else",
        "                exec /bin/bash --login -i",
        "            fi",
        "            ;;",
        "        bash|/bin/bash|/usr/bin/bash|sh|/bin/sh)",
        "            shift",
        "            export USER=root",
        "            export LOGNAME=root",
        "            export HOME=/root",
        "            export LANG=C.UTF-8",
        "            export LC_ALL=C.UTF-8",
        "            if [ \$# -gt 0 ]; then",
        "                exec /bin/bash \"\$@\"",
        "            else",
        "                exec /bin/bash --login -i",
        "            fi",
        "            ;;",
        "        -u|-g)",
        "            shift 2",
        "            ;;",
        "        -o)",
        "            OPT=\"\$2\"",
        "            shift 2",
        "            if [ \"\$1\" = \"apt-get\" ] || [ \"\$1\" = \"apt\" ] || [ \"\$1\" = \"dpkg\" ]; then",
        "                CMD=\"\$1\"",
        "                shift",
        "                export USER=root",
        "                export LOGNAME=root",
        "                export HOME=/root",
        "                export LANG=C.UTF-8",
        "                export LC_ALL=C.UTF-8",
        "                exec \"\$CMD\" -o \"\$OPT\" \"\$@\"",
        "            fi",
        "            ;;",
        "        -E|-H|-n|-S|-k|-K|-v|-l|-b|--)",
        "            shift",
        "            ;;",
        "        *)",
        "            export USER=root",
        "            export LOGNAME=root",
        "            export HOME=/root",
        "            export LANG=C.UTF-8",
        "            export LC_ALL=C.UTF-8",
        "            exec \"\$@\"",
        "            ;;",
        "    esac",
        "done\n"
    ).joinToString("\n")

    private fun getSuScript(): String = listOf(
        "#!/bin/bash",
        "TARGET_USER=\"root\"",
        "LOGIN_SHELL=false",
        "CMD=\"\"",
        "while [ \$# -gt 0 ]; do",
        "    case \"\$1\" in",
        "        -|--login|-l)",
        "            LOGIN_SHELL=true",
        "            shift",
        "            ;;",
        "        -c)",
        "            shift",
        "            CMD=\"\$1\"",
        "            shift",
        "            ;;",
        "        ubuntu)",
        "            TARGET_USER=\"ubuntu\"",
        "            shift",
        "            ;;",
        "        root)",
        "            TARGET_USER=\"root\"",
        "            shift",
        "            ;;",
        "        *)",
        "            TARGET_USER=\"\$1\"",
        "            shift",
        "            ;;",
        "    esac",
        "done",
        "",
        "if [ \"\$TARGET_USER\" = \"root\" ]; then",
        "    export USER=root",
        "    export LOGNAME=root",
        "    export HOME=/root",
        "    export LANG=C.UTF-8",
        "    export LC_ALL=C.UTF-8",
        "    if [ \"\$LOGIN_SHELL\" = true ]; then",
        "        cd /root",
        "    fi",
        "    if [ -n \"\$CMD\" ]; then",
        "        exec /bin/bash -c \"\$CMD\"",
        "    else",
        "        exec /bin/bash --login -i",
        "    fi",
        "elif [ \"\$TARGET_USER\" = \"ubuntu\" ]; then",
        "    export USER=ubuntu",
        "    export LOGNAME=ubuntu",
        "    export HOME=/home/ubuntu",
        "    export LANG=C.UTF-8",
        "    export LC_ALL=C.UTF-8",
        "    if [ \"\$LOGIN_SHELL\" = true ]; then",
        "        cd /home/ubuntu",
        "    fi",
        "    if [ -n \"\$CMD\" ]; then",
        "        exec /bin/bash -c \"\$CMD\"",
        "    else",
        "        exec /bin/bash --login -i",
        "    fi",
        "else",
        "    echo \"su: user \$TARGET_USER does not exist\"",
        "    exit 1",
        "fi\n"
    ).joinToString("\n")

    private fun getWhoamiScript(): String = "#!/bin/bash\necho \"\${USER:-ubuntu}\"\n"

    private fun getWhoScript(): String = listOf(
        "#!/bin/bash",
        "CURRENT_USER=\"\${USER:-ubuntu}\"",
        "if [ \"\$1\" = \"am\" ] && [ \"\$2\" = \"i\" ]; then",
        "    echo \"\$CURRENT_USER  pts/0        \$(date '+%Y-%m-%d %H:%M')\"",
        "    exit 0",
        "fi",
        "if [ \$# -eq 0 ]; then",
        "    echo \"\$CURRENT_USER  pts/0        \$(date '+%Y-%m-%d %H:%M')\"",
        "    exit 0",
        "fi",
        "exec /usr/bin/who \"\$@\" 2>/dev/null || echo \"\$CURRENT_USER  pts/0        \$(date '+%Y-%m-%d %H:%M')\"\n"
    ).joinToString("\n")

    private fun getIdScript(): String = listOf(
        "#!/bin/bash",
        "CURRENT_USER=\"\${USER:-ubuntu}\"",
        "if [ \"\$CURRENT_USER\" = \"root\" ]; then",
        "    if [ \"\$1\" = \"-u\" ] || [ \"\$1\" = \"--user\" ]; then",
        "        echo \"0\"",
        "    elif [ \"\$1\" = \"-un\" ]; then",
        "        echo \"root\"",
        "    elif [ \"\$1\" = \"-g\" ] || [ \"\$1\" = \"--group\" ]; then",
        "        echo \"0\"",
        "    elif [ \"\$1\" = \"-gn\" ]; then",
        "        echo \"root\"",
        "    elif [ \$# -eq 0 ]; then",
        "        echo \"uid=0(root) gid=0(root) groups=0(root)\"",
        "    else",
        "        exec /usr/bin/id \"\$@\"",
        "    fi",
        "else",
        "    if [ \"\$1\" = \"-u\" ] || [ \"\$1\" = \"--user\" ]; then",
        "        echo \"1000\"",
        "    elif [ \"\$1\" = \"-un\" ]; then",
        "        echo \"ubuntu\"",
        "    elif [ \"\$1\" = \"-g\" ] || [ \"\$1\" = \"--group\" ]; then",
        "        echo \"1000\"",
        "    elif [ \"\$1\" = \"-gn\" ]; then",
        "        echo \"ubuntu\"",
        "    elif [ \$# -eq 0 ]; then",
        "        echo \"uid=1000(ubuntu) gid=1000(ubuntu) groups=1000(ubuntu),4(adm),24(cdrom),27(sudo),30(dip),46(plugdev)\"",
        "    else",
        "        exec /usr/bin/id \"\$@\"",
        "    fi",
        "fi\n"
    ).joinToString("\n")

    private fun getUbuntuBashrc(): String = listOf(
        "# MobileLinux ~/.bashrc for ubuntu user",
        "export TERM=xterm-256color",
        "export COLORTERM=truecolor",
        "export LANG=C.UTF-8",
        "export LC_ALL=C.UTF-8",
        "export USER=ubuntu",
        "export LOGNAME=ubuntu",
        "export HOME=/home/ubuntu",
        "export TMPDIR=/tmp",
        "export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games\"",
        "shopt -s checkwinsize",
        "",
        "# Terminal Colors: Clean directory colors (disable green/black highlight on other-writable/sticky dirs)",
        "if command -v dircolors >/dev/null 2>&1; then",
        "    eval \"\$(dircolors -b 2>/dev/null)\"",
        "fi",
        "if [ -n \"\$LS_COLORS\" ]; then",
        "    export LS_COLORS=\"\$(echo \"\$LS_COLORS\" | sed 's/ow=[0-9;]*/ow=01;34/g; s/tw=[0-9;]*/tw=01;34/g; s/st=[0-9;]*/st=01;34/g'):ow=01;34:tw=01;34:st=01;34:\"",
        "else",
        "    export LS_COLORS=\"rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:ca=00:tw=01;34:ow=01;34:st=01;34:ex=01;32:\"",
        "fi",
        "",
        "# Aliases",
        "alias ls='ls --color=auto'",
        "alias ll='ls -la --color=auto'",
        "alias update='sudo apt-get update'",
        "alias install='sudo apt-get install -y'",
        "alias clear='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias cls='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias install-tools='/usr/local/bin/install-tools'",
        "alias pkg-install='/usr/local/bin/pkg-install'",
        "alias fix-perms='/usr/local/bin/fix-permissions'",
        "alias force-rm='/usr/local/bin/force-rm'",
        "alias jupyter-start='/usr/local/bin/jupyter-start'",
        "alias jupyter-lab='/usr/local/bin/jupyter-start'",
        "alias pkg-install-python='/usr/local/bin/pkg-install-python'",
        "alias pkg-uninstall-python='/usr/local/bin/pkg-uninstall-python'",
        "alias conda-sync='/usr/local/bin/conda-sync-packages'",
        "alias conda-manager='/usr/local/bin/conda-manager'",
        "",
        "# Standard Ubuntu green prompt for normal user with $ sign",
        "PS1='\\[\\033[1;32m\\]ubuntu@mobilelinux\\[\\033[0m\\]:\\[\\033[1;34m\\]\\w\\[\\033[0m\\]\$ '\n"
    ).joinToString("\n")

    private fun getRootBashrc(): String = listOf(
        "# MobileLinux ~/.bashrc for root user",
        "export TERM=xterm-256color",
        "export COLORTERM=truecolor",
        "export LANG=C.UTF-8",
        "export LC_ALL=C.UTF-8",
        "export USER=root",
        "export LOGNAME=root",
        "export HOME=/root",
        "export TMPDIR=/tmp",
        "export PATH=\"/root/.local/bin:/home/ubuntu/.local/bin:/root/go/bin:/home/ubuntu/go/bin:/root/.cargo/bin:/home/ubuntu/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games\"",
        "shopt -s checkwinsize",
        "",
        "# Terminal Colors: Clean directory colors (disable green/black highlight on other-writable/sticky dirs)",
        "if command -v dircolors >/dev/null 2>&1; then",
        "    eval \"\$(dircolors -b 2>/dev/null)\"",
        "fi",
        "if [ -n \"\$LS_COLORS\" ]; then",
        "    export LS_COLORS=\"\$(echo \"\$LS_COLORS\" | sed 's/ow=[0-9;]*/ow=01;34/g; s/tw=[0-9;]*/tw=01;34/g; s/st=[0-9;]*/st=01;34/g'):ow=01;34:tw=01;34:st=01;34:\"",
        "else",
        "    export LS_COLORS=\"rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:ca=00:tw=01;34:ow=01;34:st=01;34:ex=01;32:\"",
        "fi",
        "",
        "# Aliases",
        "alias ls='ls --color=auto'",
        "alias ll='ls -la --color=auto'",
        "alias update='apt-get update'",
        "alias install='apt-get install -y'",
        "alias clear='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias cls='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias install-tools='/usr/local/bin/install-tools'",
        "alias pkg-install='/usr/local/bin/pkg-install'",
        "alias fix-perms='/usr/local/bin/fix-permissions'",
        "alias force-rm='/usr/local/bin/force-rm'",
        "alias jupyter-start='/usr/local/bin/jupyter-start'",
        "alias jupyter-lab='/usr/local/bin/jupyter-start'",
        "alias pkg-install-python='/usr/local/bin/pkg-install-python'",
        "alias pkg-uninstall-python='/usr/local/bin/pkg-uninstall-python'",
        "alias conda-sync='/usr/local/bin/conda-sync-packages'",
        "alias conda-manager='/usr/local/bin/conda-manager'",
        "",
        "# Standard Ubuntu red prompt for root user with # sign",
        "PS1='\\[\\033[1;31m\\]root@mobilelinux\\[\\033[0m\\]:\\[\\033[1;34m\\]\\w\\[\\033[0m\\]# '\n"
    ).joinToString("\n")

    private fun getProfileContent(): String = listOf(
        "if [ -f \"\$HOME/.bashrc\" ]; then",
        "    . \"\$HOME/.bashrc\"",
        "fi\n"
    ).joinToString("\n")

    private fun getShellLauncher(): String = listOf(
        "#!/bin/bash",
        "export TERM=xterm-256color",
        "export COLORTERM=truecolor",
        "export HOME=/home/ubuntu",
        "export USER=ubuntu",
        "export LOGNAME=ubuntu",
        "export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/go/bin:/root/go/bin:/home/ubuntu/.cargo/bin:/root/.cargo/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/root/miniconda3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"",
        "export LANG=C.UTF-8",
        "export LC_ALL=C.UTF-8",
        "export TMPDIR=/tmp",
        "cd /home/ubuntu",
        "",
        "# Ensure /dev/shm, /run/shm, and /tmp have correct sticky permissions for multiprocessing",
        "chmod 1777 /dev/shm /run/shm /tmp 2>/dev/null || true",
        "",
        "# Ensure user home directory is writable",
        "chmod u+w /home/ubuntu 2>/dev/null || true",
        "",
        "# Clean up any obsolete internal shell script from user home directory",
        "rm -f /home/ubuntu/mobilelinux-shell.sh /root/mobilelinux-shell.sh 2>/dev/null || true",
        "",
        "# Storage symlinks in /home/ubuntu",
        "if [ ! -e /home/ubuntu/MobileLinux ]; then",
        "    if [ -d /sdcard/MobileLinux ]; then",
        "        ln -s /sdcard/MobileLinux /home/ubuntu/MobileLinux 2>/dev/null || true",
        "    elif [ -d /sdcard/Download/MobileLinux ]; then",
        "        ln -s /sdcard/Download/MobileLinux /home/ubuntu/MobileLinux 2>/dev/null || true",
        "    fi",
        "fi",
        "if [ ! -e /home/ubuntu/downloads ] && [ -d /sdcard/Download ]; then",
        "    ln -s /sdcard/Download /home/ubuntu/downloads 2>/dev/null || true",
        "fi",
        "if [ ! -e /home/ubuntu/sdcard ] && [ -d /sdcard ]; then",
        "    ln -s /sdcard /home/ubuntu/sdcard 2>/dev/null || true",
        "fi",
        "",
        "# Storage symlinks in /root",
        "if [ ! -e /root/MobileLinux ]; then",
        "    if [ -d /sdcard/MobileLinux ]; then",
        "        ln -s /sdcard/MobileLinux /root/MobileLinux 2>/dev/null || true",
        "    elif [ -d /sdcard/Download/MobileLinux ]; then",
        "        ln -s /sdcard/Download/MobileLinux /root/MobileLinux 2>/dev/null || true",
        "    fi",
        "fi",
        "if [ ! -e /root/downloads ] && [ -d /sdcard/Download ]; then",
        "    ln -s /sdcard/Download /root/downloads 2>/dev/null || true",
        "fi",
        "if [ ! -e /root/sdcard ] && [ -d /sdcard ]; then",
        "    ln -s /sdcard /root/sdcard 2>/dev/null || true",
        "fi",
        "",
        "# Clean one-line welcome banner ONCE at session start",
        "printf \"\\r\\033[1;36mMobileLinux\\033[0m \\033[0;37m(Ubuntu 24.04 ARM64)\\033[0m — \\033[0;32m~/MobileLinux\\033[0m\\r\\n\\r\\n\"",
        "",
        "# Launch interactive shell with dynamic PTY bridge",
        "if [ -x /usr/bin/python3 ] && [ -f /usr/local/bin/mobilelinux-pty.py ]; then",
        "    exec /usr/bin/python3 /usr/local/bin/mobilelinux-pty.py /usr/bin/bash --login -i",
        "elif [ -x /usr/bin/python3 ]; then",
        "    python3 -c \"import pty, sys; sys.exit(pty.spawn(['/usr/bin/bash', '--login', '-i']))\" 2>/dev/null && exit 0",
        "fi",
        "",
        "if [ -x /usr/bin/stdbuf ]; then",
        "    exec /usr/bin/stdbuf -i0 -o0 -e0 /usr/bin/bash --login -i",
        "fi",
        "",
        "exec /usr/bin/bash --login -i\n"
    ).joinToString("\n")

    private fun getPtyBridgeScript(): String = listOf(
        "#!/usr/bin/env python3",
        "import os",
        "import sys",
        "import pty",
        "import fcntl",
        "import termios",
        "import struct",
        "import select",
        "import errno",
        "import re",
        "",
        "def main():",
        "    cols = int(os.environ.get('COLUMNS', 80))",
        "    rows = int(os.environ.get('LINES', 24))",
        "    if cols <= 0: cols = 80",
        "    if rows <= 0: rows = 24",
        "",
        "    master_fd, slave_fd = pty.openpty()",
        "",
        "    try:",
        "        fcntl.ioctl(master_fd, termios.TIOCSWINSZ, struct.pack('HHHH', rows, cols, 0, 0))",
        "    except Exception:",
        "        pass",
        "",
        "    pid = os.fork()",
        "    if pid == 0:",
        "        os.close(master_fd)",
        "        os.setsid()",
        "        try:",
        "            fcntl.ioctl(slave_fd, termios.TIOCSCTTY, 0)",
        "        except Exception:",
        "            pass",
        "        os.dup2(slave_fd, 0)",
        "        os.dup2(slave_fd, 1)",
        "        os.dup2(slave_fd, 2)",
        "        if slave_fd > 2:",
        "            os.close(slave_fd)",
        "        cmd = sys.argv[1:] if len(sys.argv) > 1 else ['/bin/bash', '--login', '-i']",
        "        os.execvp(cmd[0], cmd)",
        "        sys.exit(1)",
        "",
        "    os.close(slave_fd)",
        "    pattern = re.compile(bytes([0, 27]) + rb'\\[9999;(\\d+);(\\d+)R' + bytes([0]))",
        "    stdin_fd = sys.stdin.fileno()",
        "",
        "    try:",
        "        while True:",
        "            try:",
        "                rlist, _, _ = select.select([stdin_fd, master_fd], [], [])",
        "            except (select.error, OSError) as e:",
        "                if getattr(e, 'errno', None) == errno.EINTR:",
        "                    continue",
        "                break",
        "",
        "            if stdin_fd in rlist:",
        "                try:",
        "                    data = os.read(stdin_fd, 4096)",
        "                except OSError:",
        "                    break",
        "                if not data:",
        "                    break",
        "",
        "                m = pattern.search(data)",
        "                if m:",
        "                    try:",
        "                        new_rows = int(m.group(1))",
        "                        new_cols = int(m.group(2))",
        "                        if new_rows > 0 and new_cols > 0:",
        "                            fcntl.ioctl(master_fd, termios.TIOCSWINSZ, struct.pack('HHHH', new_rows, new_cols, 0, 0))",
        "                    except Exception:",
        "                        pass",
        "                    data = pattern.sub(b'', data)",
        "",
        "                if data:",
        "                    try:",
        "                        os.write(master_fd, data)",
        "                    except OSError:",
        "                        break",
        "",
        "            if master_fd in rlist:",
        "                try:",
        "                    data = os.read(master_fd, 4096)",
        "                except OSError as e:",
        "                    if e.errno == errno.EIO:",
        "                        break",
        "                    break",
        "                if not data:",
        "                    break",
        "                try:",
        "                    os.write(sys.stdout.fileno(), data)",
        "                except OSError:",
        "                    break",
        "    finally:",
        "        try:",
        "            os.close(master_fd)",
        "        except Exception:",
        "            pass",
        "        try:",
        "            _, status = os.waitpid(pid, 0)",
        "            exit_code = os.waitstatus_to_exitcode(status) if hasattr(os, 'waitstatus_to_exitcode') else (status >> 8)",
        "            sys.exit(exit_code)",
        "        except Exception:",
        "            sys.exit(0)",
        "",
        "if __name__ == '__main__':",
        "    main()\n"
    ).joinToString("\n")

    private fun getPythonWrapperScript(): String = listOf(
        "#!/bin/bash",
        "if [ -x /usr/bin/python3 ]; then",
        "    exec /usr/bin/python3 \"\$@\"",
        "elif [ -x /bin/python3 ]; then",
        "    exec /bin/python3 \"\$@\"",
        "else",
        "    echo \"[MobileLinux] python3 is not found. Run: sudo apt-get update && sudo apt-get install -y python3\"",
        "    exit 127",
        "fi\n"
    ).joinToString("\n")

    private fun getPipWrapperScript(): String = listOf(
        "#!/bin/bash",
        "export PATH=\"/home/ubuntu/.local/bin:/root/.local/bin:/home/ubuntu/miniforge3/bin:/home/ubuntu/miniforge3/condabin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:\$PATH\"",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then",
        "    exec \"\$CONDA_PREFIX/bin/pip\" \"\$@\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/pip ]; then",
        "    exec /home/ubuntu/miniforge3/bin/pip \"\$@\"",
        "elif [ -x /home/ubuntu/.local/bin/pip ]; then",
        "    exec /home/ubuntu/.local/bin/pip \"\$@\"",
        "elif /usr/bin/python3 -m pip --version >/dev/null 2>&1; then",
        "    exec /usr/bin/python3 -m pip \"\$@\"",
        "elif [ -x /usr/bin/pip3 ]; then",
        "    exec /usr/bin/pip3 \"\$@\"",
        "fi",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m pip is not installed yet. Installing python3-pip...\"",
        "export DEBIAN_FRONTEND=noninteractive",
        "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* /var/lib/dpkg/updates/* 2>/dev/null || true",
        "(sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends python3-pip || ((sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true update || true) && sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends python3-pip))",
        "if /usr/bin/python3 -m pip --version >/dev/null 2>&1; then",
        "    exec /usr/bin/python3 -m pip \"\$@\"",
        "elif [ -x /usr/bin/pip3 ]; then",
        "    exec /usr/bin/pip3 \"\$@\"",
        "else",
        "    echo -e \"\\033[1;31m[MobileLinux]\\033[0m Failed to install pip. Check internet connection.\"",
        "    exit 1",
        "fi\n"
    ).joinToString("\n")

    private fun getSmartToolWrapperScript(cmdName: String, pkgName: String): String = listOf(
        "#!/bin/bash",
        "if [ -x \"/usr/bin/$cmdName\" ]; then",
        "    exec \"/usr/bin/$cmdName\" \"\$@\"",
        "fi",
        "if [ -x \"/bin/$cmdName\" ]; then",
        "    exec \"/bin/$cmdName\" \"\$@\"",
        "fi",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m '$cmdName' is not installed yet. Installing $pkgName...\"",
        "export DEBIAN_FRONTEND=noninteractive",
        "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true",
        "if (sudo apt-get install -y --no-install-recommends \"$pkgName\" || ((sudo apt-get update || true) && sudo apt-get install -y --no-install-recommends \"$pkgName\")); then",
        "    echo -e \"\\033[1;32m[MobileLinux]\\033[0m '$cmdName' installed successfully!\\n\"",
        "    if [ -x \"/usr/bin/$cmdName\" ]; then",
        "        exec \"/usr/bin/$cmdName\" \"\$@\"",
        "    elif [ -x \"/bin/$cmdName\" ]; then",
        "        exec \"/bin/$cmdName\" \"\$@\"",
        "    fi",
        "else",
        "    echo -e \"\\033[1;31m[MobileLinux]\\033[0m Failed to install $pkgName. Please check internet connection.\"",
        "    exit 1",
        "fi\n"
    ).joinToString("\n")

    private fun getInstallToolsScript(): String = listOf(
        "#!/bin/bash",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m Installing essential Linux CLI tools...\"",
        "echo -e \"\\033[0;37mPackages: nano vim-tiny git python3-pip htop tree unzip zip\\033[0m\\n\"",
        "export DEBIAN_FRONTEND=noninteractive",
        "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true",
        "if (sudo apt-get install -y --no-install-recommends nano vim-tiny git python3-pip htop tree unzip zip || ((sudo apt-get update || true) && sudo apt-get install -y --no-install-recommends nano vim-tiny git python3-pip htop tree unzip zip)); then",
        "    sudo mkdir -p /etc/mobilelinux 2>/dev/null || true",
        "    sudo touch /etc/mobilelinux/.tools_installed 2>/dev/null || true",
        "    echo -e \"\\n\\033[1;32m[MobileLinux] All essential tools installed successfully!\\033[0m\"",
        "else",
        "    echo -e \"\\n\\033[1;31m[MobileLinux] Installation failed. Check internet connection and try again.\\033[0m\"",
        "fi\n"
    ).joinToString("\n")

    private fun getPkgInstallScript(): String = listOf(
        "#!/bin/bash",
        "if [ \$# -eq 0 ]; then",
        "    echo \"Usage: pkg-install <package_name> [packages...]\"",
        "    exit 1",
        "fi",
        "export DEBIAN_FRONTEND=noninteractive",
        "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true",
        "(sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends \"\$@\" || ((sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true update || true) && sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends \"\$@\"))\n"
    ).joinToString("\n")

    private fun getPkgUninstallPythonScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Smart Multi-Environment Python Package Uninstaller & Fast Purger",
        "PIP_PKG=\"\$1\"",
        "APT_PKG=\"\$2\"",
        "MODULE_NAME=\"\$3\"",
        "if [ -z \"\$PIP_PKG\" ]; then",
        "    echo \"Usage: pkg-uninstall-python <pip_package_name> [apt_package_name] [module_name]\"",
        "    exit 1",
        "fi",
        "# Auto-resolve Python import module name if not explicitly provided",
        "if [ -z \"\$MODULE_NAME\" ]; then",
        "    case \"\$PIP_PKG\" in",
        "        opencv-python|opencv-contrib-python) MODULE_NAME=\"cv2\" ;;",
        "        scikit-learn) MODULE_NAME=\"sklearn\" ;;",
        "        Pillow) MODULE_NAME=\"PIL\" ;;",
        "        beautifulsoup4) MODULE_NAME=\"bs4\" ;;",
        "        PyYAML) MODULE_NAME=\"yaml\" ;;",
        "        sherlock-project) MODULE_NAME=\"sherlock\" ;;",
        "        *) MODULE_NAME=\"\${PIP_PKG//-/_}\" ;;",
        "    esac",
        "fi",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m Purging \\033[1;31m\$PIP_PKG\\033[0m across all environments...\"",
        "# 1. Clean APT locks and purge ALL possible APT package names for this module",
        "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true",
        "export DEBIAN_FRONTEND=noninteractive",
        "TARGETS=\"\"",
        "[ -n \"\$APT_PKG\" ] && TARGETS=\"\$TARGETS \$APT_PKG\"",
        "TARGETS=\"\$TARGETS python3-\$PIP_PKG python-\$PIP_PKG\"",
        "echo -e \"\\033[1;34m[MobileLinux]\\033[0m Removing APT packages (\$TARGETS)...\"",
        "sudo apt-get -o DPkg::Lock::Timeout=10 purge -y \$TARGETS 2>&1 || true",
        "sudo apt-get clean 2>/dev/null || true",
        "# 2. System Python (pip3 uninstall with multiple methods)",
        "echo -e \"\\033[1;34m[MobileLinux]\\033[0m Removing from system pip3...\"",
        "pip3 uninstall -y --break-system-packages \"\$PIP_PKG\" 2>&1 || true",
        "python3 -m pip uninstall -y --break-system-packages \"\$PIP_PKG\" 2>&1 || true",
        "# 3. Purge ALL Python site-packages directories directly (most reliable)",
        "echo -e \"\\033[1;34m[MobileLinux]\\033[0m Cleaning Python site-packages...\"",
        "rm -rf /home/ubuntu/.local/lib/python*/site-packages/\${PIP_PKG}* /home/ubuntu/.local/lib/python*/site-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "rm -rf /root/.local/lib/python*/site-packages/\${PIP_PKG}* /root/.local/lib/python*/site-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "rm -rf /usr/local/lib/python*/dist-packages/\${PIP_PKG}* /usr/local/lib/python*/dist-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "rm -rf /usr/local/lib/python*/site-packages/\${PIP_PKG}* /usr/local/lib/python*/site-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "rm -rf /usr/lib/python3/dist-packages/\${PIP_PKG}* /usr/lib/python3/dist-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "rm -rf /usr/lib/python*/dist-packages/\${PIP_PKG}* /usr/lib/python*/dist-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "rm -rf /usr/lib/python*/site-packages/\${PIP_PKG}* /usr/lib/python*/site-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "# 4. Conda base environments (Miniforge3 / Miniconda) - fast pip uninstall & site-packages purge",
        "echo -e \"\\033[1;34m[MobileLinux]\\033[0m Cleaning Conda environments...\"",
        "SEEN_DIRS=\" \"",
        "for conda_base in /home/ubuntu/miniforge3 /root/miniconda3 /opt/conda; do",
        "    if [ -d \"\$conda_base\" ]; then",
        "        SEEN_DIRS=\"\$SEEN_DIRS\$conda_base \"",
        "        if [ -x \"\$conda_base/bin/pip\" ]; then",
        "            \"\$conda_base/bin/pip\" uninstall -y \"\$PIP_PKG\" 2>/dev/null || true",
        "        fi",
        "        rm -rf \"\$conda_base\"/lib/python*/site-packages/\${PIP_PKG}* \"\$conda_base\"/lib/python*/site-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "        if [ -x \"\$conda_base/bin/conda\" ]; then",
        "            if \"\$conda_base/bin/conda\" list 2>/dev/null | grep -E -q \"^\${PIP_PKG}[[:space:]]\"; then",
        "                \"\$conda_base/bin/conda\" remove -y -q \"\$PIP_PKG\" 2>/dev/null || true",
        "            fi",
        "        fi",
        "    fi",
        "done",
        "# 5. Custom Conda environments",
        "for env_dir in /home/ubuntu/miniforge3/envs/* /root/miniconda3/envs/* /home/ubuntu/.conda/envs/* /root/.conda/envs/*; do",
        "    if [ -d \"\$env_dir\" ]; then",
        "        if [[ \"\$SEEN_DIRS\" != *\" \$env_dir \"* ]]; then",
        "            SEEN_DIRS=\"\$SEEN_DIRS\$env_dir \"",
        "            if [ -x \"\$env_dir/bin/pip\" ]; then",
        "                \"\$env_dir/bin/pip\" uninstall -y \"\$PIP_PKG\" 2>/dev/null || true",
        "            fi",
        "            rm -rf \"\$env_dir\"/lib/python*/site-packages/\${PIP_PKG}* \"\$env_dir\"/lib/python*/site-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "        fi",
        "    fi",
        "done",
        "# 6. Active Conda Prefix if set",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -d \"\$CONDA_PREFIX\" ]; then",
        "    if [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then",
        "        \"\$CONDA_PREFIX/bin/pip\" uninstall -y \"\$PIP_PKG\" 2>/dev/null || true",
        "    fi",
        "    rm -rf \"\$CONDA_PREFIX\"/lib/python*/site-packages/\${PIP_PKG}* \"\$CONDA_PREFIX\"/lib/python*/site-packages/\${MODULE_NAME}* 2>/dev/null || true",
        "fi",
        "# 7. Python introspection force wipe — ask Python itself where it finds the module, then delete it",
        "for py in python3 /home/ubuntu/miniforge3/bin/python /root/miniconda3/bin/python; do",
        "    [ -x \"\$py\" ] || continue",
        "    LOC=\"\$(\"\$py\" -c \"import \$MODULE_NAME; import os; print(os.path.dirname(getattr(\$MODULE_NAME, '__file__', '')))\" 2>/dev/null)\"",
        "    if [ -n \"\$LOC\" ] && [ -d \"\$LOC\" ]; then",
        "        rm -rf \"\$LOC\" \"\${LOC}.dist-info\" \"\${LOC}.egg-info\" \"\${LOC}\"-*.dist-info 2>/dev/null || true",
        "        PARENT=\"\$(dirname \"\$LOC\")\"",
        "        rm -rf \"\$PARENT/\${PIP_PKG}\"* \"\$PARENT/\${MODULE_NAME}\"* \"\$PARENT/\${PIP_PKG}-\"*.dist-info 2>/dev/null || true",
        "    fi",
        "    FILE_LOC=\"\$(\"\$py\" -c \"import \$MODULE_NAME; print(getattr(\$MODULE_NAME, '__file__', ''))\" 2>/dev/null)\"",
        "    if [ -n \"\$FILE_LOC\" ] && [ -f \"\$FILE_LOC\" ]; then",
        "        rm -f \"\$FILE_LOC\" 2>/dev/null || true",
        "    fi",
        "done",
        "# 8. Remove legacy wrappers or binary symlinks",
        "rm -f \"/usr/local/bin/\$PIP_PKG\" \"/usr/local/bin/\${PIP_PKG}3\" \"/usr/local/bin/\$MODULE_NAME\" \"/usr/bin/\$PIP_PKG\" \"/usr/bin/\$MODULE_NAME\" 2>/dev/null || true",
        "rm -f \"/home/ubuntu/.local/bin/\$PIP_PKG\" \"/home/ubuntu/.local/bin/\$MODULE_NAME\" \"/root/.local/bin/\$PIP_PKG\" \"/root/.local/bin/\$MODULE_NAME\" 2>/dev/null || true",
        "for cb in /home/ubuntu/miniforge3/bin /root/miniconda3/bin /home/ubuntu/miniforge3/envs/*/bin /root/miniconda3/envs/*/bin; do",
        "    rm -f \"\$cb/\$PIP_PKG\" \"\$cb/\${PIP_PKG}3\" \"\$cb/\$MODULE_NAME\" 2>/dev/null || true",
        "done",
        "# 9. Clear pip/package caches",
        "find /home/ubuntu/.cache /root/.cache /tmp -name \"*\${PIP_PKG}*\" -o -name \"*\${MODULE_NAME}*\" -exec rm -rf {} + 2>/dev/null || true",
        "# 10. STRICT post-removal verification — exit 1 if package is STILL importable by any Python",
        "echo -e \"\\033[1;34m[MobileLinux]\\033[0m Verifying removal...\"",
        "STILL_INSTALLED=0",
        "if python3 -c \"import \$MODULE_NAME\" >/dev/null 2>&1; then",
        "    STILL_INSTALLED=1",
        "    echo -e \"\\033[1;33m[MobileLinux]\\033[0m Still importable via system python3\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c \"import \$MODULE_NAME\" >/dev/null 2>&1; then",
        "    STILL_INSTALLED=1",
        "    echo -e \"\\033[1;33m[MobileLinux]\\033[0m Still importable via Miniforge3 python\"",
        "elif [ -x /root/miniconda3/bin/python ] && /root/miniconda3/bin/python -c \"import \$MODULE_NAME\" >/dev/null 2>&1; then",
        "    STILL_INSTALLED=1",
        "    echo -e \"\\033[1;33m[MobileLinux]\\033[0m Still importable via Miniconda3 python\"",
        "fi",
        "if [ \$STILL_INSTALLED -eq 0 ]; then",
        "    echo -e \"\\033[1;32m[MobileLinux]\\033[0m ✓ \$PIP_PKG permanently uninstalled and purged from all environments!\\n\"",
        "    exit 0",
        "else",
        "    echo -e \"\\033[1;31m[MobileLinux]\\033[0m ✗ \$PIP_PKG could not be fully removed — still importable. Package may be system-protected.\\n\"",
        "    exit 1",
        "fi\n"
    ).joinToString("\n")

    private fun getPkgInstallPythonScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Smart Multi-Environment Python Package Installer",
        "PIP_PKG=\"\$1\"",
        "APT_PKG=\"\$2\"",
        "if [ -z \"\$PIP_PKG\" ]; then",
        "    echo \"Usage: pkg-install-python <pip_package_name> [apt_package_name]\"",
        "    exit 1",
        "fi",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m Starting installation of \\033[1;32m\$PIP_PKG\\033[0m...\"",
        "INSTALLED_ANY=0",
        "# 1. System APT installation if APT package name provided",
        "if [ -n \"\$APT_PKG\" ]; then",
        "    echo -e \"\\033[1;34m[MobileLinux]\\033[0m Checking APT package \$APT_PKG...\"",
        "    export DEBIAN_FRONTEND=noninteractive",
        "    if sudo apt-get -o DPkg::Lock::Timeout=10 install -y --no-install-recommends \"\$APT_PKG\" 2>&1; then",
        "        INSTALLED_ANY=1",
        "        echo -e \"\\033[1;32m[MobileLinux]\\033[0m Installed via APT: \$APT_PKG\"",
        "    else",
        "        echo -e \"\\033[1;33m[MobileLinux]\\033[0m Updating package lists and retrying APT install...\"",
        "        sudo apt-get -o DPkg::Lock::Timeout=10 update 2>&1 || true",
        "        if sudo apt-get -o DPkg::Lock::Timeout=10 install -y --no-install-recommends \"\$APT_PKG\" 2>&1; then",
        "            INSTALLED_ANY=1",
        "            echo -e \"\\033[1;32m[MobileLinux]\\033[0m Installed via APT: \$APT_PKG\"",
        "        fi",
        "    fi",
        "fi",
        "# 2. If not installed via APT, fallback to system pip3",
        "if [ \$INSTALLED_ANY -eq 0 ]; then",
        "    echo -e \"\\033[1;34m[MobileLinux]\\033[0m Installing \$PIP_PKG via pip3...\"",
        "    if ! command -v pip3 >/dev/null 2>&1; then",
        "        echo -e \"\\033[1;33m[MobileLinux]\\033[0m Setting up pip3...\"",
        "        sudo apt-get -o DPkg::Lock::Timeout=10 install -y python3-pip 2>&1 || true",
        "    fi",
        "    if command -v pip3 >/dev/null 2>&1; then",
        "        if pip3 install --break-system-packages --prefer-binary --no-cache-dir --default-timeout=30 \"\$PIP_PKG\" 2>&1; then",
        "            INSTALLED_ANY=1",
        "            echo -e \"\\033[1;32m[MobileLinux]\\033[0m Installed via pip3: \$PIP_PKG\"",
        "        fi",
        "    fi",
        "fi",
        "# 3. Synchronize with Conda environments (Miniforge3 / Miniconda) if present",
        "SEEN_DIRS=\" \"",
        "for conda_base in /home/ubuntu/miniforge3 /root/miniconda3 /opt/conda; do",
        "    if [ -d \"\$conda_base\" ]; then",
        "        SEEN_DIRS=\"\$SEEN_DIRS\$conda_base \"",
        "        if \"\$conda_base/bin/python\" -c \"import \$PIP_PKG\" >/dev/null 2>&1; then",
        "            echo -e \"\\033[1;32m[MobileLinux]\\033[0m \$PIP_PKG is already available in Conda: \$conda_base\"",
        "            INSTALLED_ANY=1",
        "            continue",
        "        fi",
        "        if [ -x \"\$conda_base/bin/pip\" ]; then",
        "            echo -e \"\\033[1;34m[MobileLinux]\\033[0m Installing \$PIP_PKG into Conda (\$conda_base)...\"",
        "            if \"\$conda_base/bin/pip\" install --prefer-binary --no-cache-dir --default-timeout=30 \"\$PIP_PKG\" 2>&1; then",
        "                INSTALLED_ANY=1",
        "            fi",
        "        fi",
        "    fi",
        "done",
        "# Custom Conda environments",
        "for env_pip in /home/ubuntu/miniforge3/envs/*/bin/pip /root/miniconda3/envs/*/bin/pip; do",
        "    if [ -x \"\$env_pip\" ]; then",
        "        ENV_DIR=\"\$(dirname \"\$(dirname \"\$env_pip\")\")\"",
        "        if [[ \"\$SEEN_DIRS\" != *\" \$ENV_DIR \"* ]]; then",
        "            SEEN_DIRS=\"\$SEEN_DIRS\$ENV_DIR \"",
        "            if \"\$ENV_DIR/bin/python\" -c \"import \$PIP_PKG\" >/dev/null 2>&1; then",
        "                continue",
        "            fi",
        "            \"\$env_pip\" install --prefer-binary --no-cache-dir --default-timeout=30 \"\$PIP_PKG\" 2>&1 || true",
        "        fi",
        "    fi",
        "done",
        "# Active CONDA_PREFIX if set",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then",
        "    if ! \"\$CONDA_PREFIX/bin/python\" -c \"import \$PIP_PKG\" >/dev/null 2>&1; then",
        "        \"\$CONDA_PREFIX/bin/pip\" install --prefer-binary --no-cache-dir --default-timeout=30 \"\$PIP_PKG\" 2>&1 || true",
        "    fi",
        "fi",
        "# 4. Strict verification across environments",
        "VERIFIED=0",
        "if python3 -c \"import \$PIP_PKG\" >/dev/null 2>&1; then",
        "    VERIFIED=1",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c \"import \$PIP_PKG\" >/dev/null 2>&1; then",
        "    VERIFIED=1",
        "elif [ -x /root/miniconda3/bin/python ] && /root/miniconda3/bin/python -c \"import \$PIP_PKG\" >/dev/null 2>&1; then",
        "    VERIFIED=1",
        "elif [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ] && \"\$CONDA_PREFIX/bin/python\" -c \"import \$PIP_PKG\" >/dev/null 2>&1; then",
        "    VERIFIED=1",
        "fi",
        "if [ \$VERIFIED -eq 1 ]; then",
        "    if [ ! -x \"/usr/local/bin/\$PIP_PKG\" ] && [ ! -x \"/usr/bin/\$PIP_PKG\" ]; then",
        "        MOD_NAME=\"\${PIP_PKG//-/_}\"",
        "        cat << 'EOF' > \"/usr/local/bin/\$PIP_PKG\"",
        "#!/bin/bash",
        "PY=\"\"",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ]; then",
        "    PY=\"\$CONDA_PREFIX/bin/python\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ]; then",
        "    PY=\"/home/ubuntu/miniforge3/bin/python\"",
        "elif [ -x /root/miniconda3/bin/python ]; then",
        "    PY=\"/root/miniconda3/bin/python\"",
        "elif command -v python3 >/dev/null 2>&1; then",
        "    PY=\"\$(command -v python3)\"",
        "fi",
        "is_installed() {",
        "    [ -n \"\$PY\" ] && \"\$PY\" -c \"import MOD_NAME_TAG\" >/dev/null 2>&1",
        "}",
        "get_version() {",
        "    [ -n \"\$PY\" ] && \"\$PY\" -c \"import MOD_NAME_TAG as _m; print(getattr(_m, '__version__', 'installed'))\" 2>/dev/null",
        "}",
        "case \"\$1\" in",
        "    -v|--version|version)",
        "        if is_installed; then",
        "            echo \"PKG_NAME_TAG \$(get_version)\"",
        "            exit 0",
        "        else",
        "            echo -e \"\\033[1;31m[MobileLinux]\\033[0m PKG_NAME_TAG is not installed in active Python (\$PY).\"",
        "            exit 1",
        "        fi",
        "        ;;",
        "    -c)",
        "        shift",
        "        if is_installed; then",
        "            exec \"\$PY\" -c \"import MOD_NAME_TAG; \$*\"",
        "        else",
        "            echo -e \"\\033[1;31m[MobileLinux]\\033[0m PKG_NAME_TAG is not installed in active Python (\$PY).\"",
        "            exit 1",
        "        fi",
        "        ;;",
        "    *)",
        "        if is_installed; then",
        "            VER=\"\$(get_version)\"",
        "            echo -e \"\\033[1;36m┌─[MobileLinux]─[PKG_NAME_TAG]\\033[0m\"",
        "            echo -e \"\\033[1;36m│\\033[0m Version:       \$VER\"",
        "            echo -e \"\\033[1;36m│\\033[0m Active Python: \$PY\"",
        "            echo -e \"\\033[1;36m│\\033[0m Starting Python interactive shell with MOD_NAME_TAG imported...\"",
        "            echo -e \"\\033[1;36m└──────────────────────────────────────────────\\033[0m\"",
        "            exec \"\$PY\" -i -c \"import MOD_NAME_TAG; print('>>> PKG_NAME_TAG '\$VER' imported. Type exit() to quit.')\"",
        "        else",
        "            echo -e \"\\033[1;31m[MobileLinux]\\033[0m PKG_NAME_TAG is not installed in active Python (\$PY).\"",
        "            exit 1",
        "        fi",
        "        ;;",
        "esac",
        "EOF",
        "        sed -i \"s/MOD_NAME_TAG/\$MOD_NAME/g\" \"/usr/local/bin/\$PIP_PKG\" 2>/dev/null || true",
        "        sed -i \"s/PKG_NAME_TAG/\$PIP_PKG/g\" \"/usr/local/bin/\$PIP_PKG\" 2>/dev/null || true",
        "        chmod 755 \"/usr/local/bin/\$PIP_PKG\" 2>/dev/null || true",
        "    fi",
        "    echo -e \"\\033[1;32m[MobileLinux]\\033[0m ✓ \$PIP_PKG installation complete and verified!\\n\"",
        "    exit 0",
        "else",
        "    echo -e \"\\033[1;31m[MobileLinux]\\033[0m ✗ \$PIP_PKG installation could not be verified.\\n\"",
        "    exit 1",
        "fi\n"
    ).joinToString("\n")

    private fun getCondaSyncPackagesScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Conda Environment Package Synchronizer",
        "TARGET_ENV=\"\$1\"",
        "if [ -z \"\$TARGET_ENV\" ]; then",
        "    if [ -n \"\$CONDA_DEFAULT_ENV\" ] && [ \"\$CONDA_DEFAULT_ENV\" != \"base\" ]; then",
        "        TARGET_ENV=\"\$CONDA_DEFAULT_ENV\"",
        "    else",
        "        echo \"Usage: conda-sync-packages <conda_environment_name>\"",
        "        echo \"Existing custom environments:\"",
        "        ls -1 /home/ubuntu/miniforge3/envs 2>/dev/null || echo \"(none found)\"",
        "        exit 1",
        "    fi",
        "fi",
        "ENV_PIP=\"\"",
        "ENV_PYTHON=\"\"",
        "if [ -x \"/home/ubuntu/miniforge3/envs/\$TARGET_ENV/bin/pip\" ]; then",
        "    ENV_PIP=\"/home/ubuntu/miniforge3/envs/\$TARGET_ENV/bin/pip\"",
        "    ENV_PYTHON=\"/home/ubuntu/miniforge3/envs/\$TARGET_ENV/bin/python\"",
        "elif [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/pip\" ]; then",
        "    ENV_PIP=\"\$CONDA_PREFIX/bin/pip\"",
        "    ENV_PYTHON=\"\$CONDA_PREFIX/bin/python\"",
        "fi",
        "if [ -z \"\$ENV_PIP\" ] || [ ! -x \"\$ENV_PIP\" ]; then",
        "    echo \"Could not find pip for environment '\$TARGET_ENV'.\"",
        "    exit 1",
        "fi",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m Syncing essential packages to \\033[1;33m\$TARGET_ENV\\033[0m...\"",
        "for pkg in numpy pandas scipy matplotlib ipykernel; do",
        "    if [ -x \"\$ENV_PYTHON\" ] && \"\$ENV_PYTHON\" -c \"import \$pkg\" >/dev/null 2>&1; then",
        "        echo -e \"\\033[1;32m[MobileLinux]\\033[0m \$pkg already exists in \$TARGET_ENV\"",
        "        continue",
        "    fi",
        "    echo -e \"Syncing \$pkg...\"",
        "    \"\$ENV_PIP\" install --prefer-binary --no-cache-dir --default-timeout=30 \"\$pkg\" 2>/dev/null || true",
        "done",
        "echo -e \"\\033[1;32m[MobileLinux]\\033[0m ✓ Sync complete for environment '\$TARGET_ENV'!\\n\"\n"
    ).joinToString("\n")

    private fun getInstallCondaScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Automated Miniforge3 / Conda Installer & Configurator",
        "# NOTE: Do NOT use 'set -e' — proot/mobile has transient errors that should not abort installation",
        "",
        "INSTALL_DIR=\"/home/ubuntu/miniforge3\"",
        "CONDA_BIN=\"\$INSTALL_DIR/bin/conda\"",
        "TMP_INSTALLER=\"/home/ubuntu/.miniforge_installer.sh\"",
        "",
        "echo -e \"\\033[1;36m[MobileLinux] [  5%] Preparing Miniforge3 / Conda installer...\\033[0m\"",
        "",
        "# 1. Fast Path: If already installed, immediately configure, auto-activate and exit successfully",
        "if [ -x \"\$CONDA_BIN\" ] || [ -x \"/root/miniconda3/bin/conda\" ] || [ -x \"/opt/conda/bin/conda\" ]; then",
        "    if [ ! -x \"\$CONDA_BIN\" ] && [ -x \"/root/miniconda3/bin/conda\" ]; then",
        "        INSTALL_DIR=\"/root/miniconda3\"",
        "        CONDA_BIN=\"\$INSTALL_DIR/bin/conda\"",
        "    elif [ ! -x \"\$CONDA_BIN\" ] && [ -x \"/opt/conda/bin/conda\" ]; then",
        "        INSTALL_DIR=\"/opt/conda\"",
        "        CONDA_BIN=\"\$INSTALL_DIR/bin/conda\"",
        "    fi",
        "    echo -e \"\\033[1;32m[MobileLinux] [ 80%] Conda already installed at \$INSTALL_DIR. Configuring...\\033[0m\"",
        "    mkdir -p /usr/local/bin",
        "    cat > /usr/local/bin/conda << 'EOF_WRAP'",
        "#!/bin/sh",
        "exec /home/ubuntu/miniforge3/bin/conda \"\$@\"",
        "EOF_WRAP",
        "    chmod +x /usr/local/bin/conda 2>/dev/null || true",
        "    if [ -x \"\$INSTALL_DIR/bin/mamba\" ]; then",
        "        cat > /usr/local/bin/mamba << 'EOF_MAMBA'",
        "#!/bin/sh",
        "exec /home/ubuntu/miniforge3/bin/mamba \"\$@\"",
        "EOF_MAMBA",
        "        chmod +x /usr/local/bin/mamba 2>/dev/null || true",
        "    fi",
        "    mkdir -p /home/ubuntu /root",
        "    cat << 'EOF_RC' > /home/ubuntu/.condarc",
        "always_copy: true",
        "auto_activate_base: true",
        "notify_outdated_conda: false",
        "EOF_RC",
        "    cp -f /home/ubuntu/.condarc /root/.condarc 2>/dev/null || true",
        "    \"\$CONDA_BIN\" init bash 2>/dev/null || true",
        "    echo -e \"\\033[1;32m[MobileLinux] [100%] ✓ Miniforge3 / Conda installed successfully and activated!\\033[0m\"",
        "    exit 0",
        "fi",
        "",
        "# 2. Ensure downloader exists",
        "if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then",
        "    echo -e \"\\033[1;33m[MobileLinux] [  8%] Installing network tools...\\033[0m\"",
        "    export DEBIAN_FRONTEND=noninteractive",
        "    sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true update -y || true",
        "    sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends curl ca-certificates || true",
        "fi",
        "",
        "# 3. Clean previous incomplete downloads",
        "rm -f \"\$TMP_INSTALLER\" /tmp/miniforge.sh /tmp/Miniforge3-Linux-aarch64.sh",
        "",
        "# 4. Download with live percentage reporting",
        "URL1=\"https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-Linux-aarch64.sh\"",
        "URL2=\"https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-aarch64.sh\"",
        "DOWNLOAD_OK=0",
        "",
        "echo -e \"\\033[1;36m[MobileLinux] [ 10%] Downloading Conda installer (ARM64)...\\033[0m\"",
        "",
        "# Try Python 3 streaming downloader for clean, live percentage feedback",
        "if command -v python3 >/dev/null 2>&1; then",
        "    python3 -c \"",
        "import sys, time, urllib.request",
        "urls = ['\\\$URL1', '\\\$URL2']",
        "dst = '\\\$TMP_INSTALLER'",
        "done = False",
        "for url in urls:",
        "    try:",
        "        print(f'[MobileLinux] [ 12%] Connecting to mirror: {url.split(\\\"/\\\")[2]}...', flush=True)",
        "        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Linux; Android)'})",
        "        with urllib.request.urlopen(req, timeout=30) as resp, open(dst, 'wb') as out:",
        "            total = int(resp.headers.get('content-length', 0))",
        "            downloaded = 0",
        "            last_pct = 12",
        "            last_t = time.time()",
        "            while True:",
        "                chunk = resp.read(1048576)",
        "                if not chunk: break",
        "                out.write(chunk)",
        "                downloaded += len(chunk)",
        "                now = time.time()",
        "                if total > 0:",
        "                    pct = int(12 + (downloaded / total) * 46)",
        "                    if pct >= last_pct + 4 or (now - last_t) >= 2.0:",
        "                        mb = downloaded // 1048576",
        "                        tot_mb = total // 1048576",
        "                        print(f'[MobileLinux] [ {pct}%] Downloading Conda installer: {mb}MB / {tot_mb}MB ({pct}%)...', flush=True)",
        "                        last_pct = pct",
        "                        last_t = now",
        "            done = True",
        "            break",
        "    except Exception as e:",
        "        print(f'[MobileLinux] Mirror download notice: {e}', flush=True)",
        "        continue",
        "if not done:",
        "    sys.exit(1)",
        "\" && DOWNLOAD_OK=1",
        "fi",
        "",
        "# Fallback to curl or wget if Python downloader was not used or failed",
        "if [ \"\$DOWNLOAD_OK\" -ne 1 ]; then",
        "    if command -v curl >/dev/null 2>&1; then",
        "        echo -e \"\\033[1;36m[MobileLinux] [ 20%] Downloading installer via curl...\\033[0m\"",
        "        if curl -# -fL --retry 3 --connect-timeout 20 \"\$URL1\" -o \"\$TMP_INSTALLER\" 2>&1; then",
        "            DOWNLOAD_OK=1",
        "        elif curl -# -fL --retry 3 --connect-timeout 20 -k \"\$URL1\" -o \"\$TMP_INSTALLER\" 2>&1; then",
        "            DOWNLOAD_OK=1",
        "        elif curl -# -fL --retry 3 --connect-timeout 20 \"\$URL2\" -o \"\$TMP_INSTALLER\" 2>&1; then",
        "            DOWNLOAD_OK=1",
        "        fi",
        "    elif command -v wget >/dev/null 2>&1; then",
        "        echo -e \"\\033[1;36m[MobileLinux] [ 20%] Downloading installer via wget...\\033[0m\"",
        "        if wget --tries=3 --timeout=20 -O \"\$TMP_INSTALLER\" \"\$URL1\" 2>&1; then",
        "            DOWNLOAD_OK=1",
        "        elif wget --tries=3 --timeout=20 -O \"\$TMP_INSTALLER\" \"\$URL2\" 2>&1; then",
        "            DOWNLOAD_OK=1",
        "        fi",
        "    fi",
        "fi",
        "",
        "if [ \"\$DOWNLOAD_OK\" -ne 1 ] || [ ! -f \"\$TMP_INSTALLER\" ]; then",
        "    echo -e \"\\033[1;31m[MobileLinux] Failed to download Conda installer. Check network connection.\\033[0m\"",
        "    exit 1",
        "fi",
        "",
        "FILE_SIZE=\$(wc -c < \"\$TMP_INSTALLER\" 2>/dev/null || echo 0)",
        "if [ \"\$FILE_SIZE\" -lt 20000000 ]; then",
        "    echo -e \"\\033[1;31m[MobileLinux] Downloaded installer is incomplete (\$FILE_SIZE bytes).\\033[0m\"",
        "    rm -f \"\$TMP_INSTALLER\"",
        "    exit 1",
        "fi",
        "",
        "echo -e \"\\033[1;36m[MobileLinux] [ 60%] Installer downloaded successfully (\$((FILE_SIZE / 1048576))MB).\\033[0m\"",
        "",
        "# 5. Run the installer",
        "echo -e \"\\033[1;36m[MobileLinux] [ 65%] Unpacking Conda packages into \$INSTALL_DIR (this may take 1-2 minutes)...\\033[0m\"",
        "bash \"\$TMP_INSTALLER\" -b -p \"\$INSTALL_DIR\" -u",
        "rm -f \"\$TMP_INSTALLER\"",
        "",
        "# 6. Verify installation",
        "if [ ! -x \"\$CONDA_BIN\" ]; then",
        "    echo -e \"\\033[1;31m[MobileLinux] Installation finished but \$CONDA_BIN not found.\\033[0m\"",
        "    exit 1",
        "fi",
        "",
        "chmod -R u+rx \"\$INSTALL_DIR/bin\" 2>/dev/null || true",
        "echo -e \"\\033[1;36m[MobileLinux] [ 85%] Extracting Conda package binaries complete.\\033[0m\"",
        "",
        "# 7. Configure Conda & Auto-activation",
        "echo -e \"\\033[1;36m[MobileLinux] [ 88%] Configuring Conda & auto-activation...\\033[0m\"",
        "mkdir -p /home/ubuntu /root",
        "cat << 'EOF' > /home/ubuntu/.condarc",
        "always_copy: true",
        "auto_activate_base: true",
        "notify_outdated_conda: false",
        "EOF",
        "cp -f /home/ubuntu/.condarc /root/.condarc 2>/dev/null || true",
        "",
        "# Global command symlinks in /usr/local/bin",
        "mkdir -p /usr/local/bin",
        "cat > /usr/local/bin/conda << 'EOF_WRAP'",
        "#!/bin/sh",
        "exec /home/ubuntu/miniforge3/bin/conda \"\$@\"",
        "EOF_WRAP",
        "chmod +x /usr/local/bin/conda 2>/dev/null || true",
        "",
        "if [ -x \"\$INSTALL_DIR/bin/mamba\" ]; then",
        "    cat > /usr/local/bin/mamba << 'EOF_MAMBA'",
        "#!/bin/sh",
        "exec /home/ubuntu/miniforge3/bin/mamba \"\$@\"",
        "EOF_MAMBA",
        "    chmod +x /usr/local/bin/mamba 2>/dev/null || true",
        "fi",
        "",
        "# Run conda init",
        "\"\$CONDA_BIN\" init bash 2>/dev/null || true",
        "",
        "# Inject conda initialize block into /home/ubuntu/.bashrc if missing",
        "if ! grep -q \"conda initialize\" /home/ubuntu/.bashrc 2>/dev/null; then",
        "    cat >> /home/ubuntu/.bashrc << 'BASHRC_EOF'",
        "",
        "# >>> conda initialize >>>",
        "# !! Contents within this block are managed by 'conda init' !!",
        "__conda_setup=\"\$('/home/ubuntu/miniforge3/bin/conda' 'shell.bash' 'hook' 2> /dev/null)\"",
        "if [ \$? -eq 0 ]; then",
        "    eval \"\$__conda_setup\"",
        "else",
        "    if [ -f \"/home/ubuntu/miniforge3/etc/profile.d/conda.sh\" ]; then",
        "        . \"/home/ubuntu/miniforge3/etc/profile.d/conda.sh\"",
        "    else",
        "        export PATH=\"/home/ubuntu/miniforge3/bin:\$PATH\"",
        "    fi",
        "fi",
        "unset __conda_setup",
        "# <<< conda initialize <<<",
        "",
        "# MobileLinux: Auto-activate Conda environment",
        "if [ -z \"\$CONDA_DEFAULT_ENV\" ] && type conda >/dev/null 2>&1; then",
        "    conda activate base 2>/dev/null || true",
        "fi",
        "BASHRC_EOF",
        "fi",
        "",
        "# Inject into /root/.bashrc as well",
        "if ! grep -q \"conda initialize\" /root/.bashrc 2>/dev/null; then",
        "    cp -f /home/ubuntu/.bashrc /root/.bashrc 2>/dev/null || true",
        "fi",
        "",
        "echo -e \"\\033[1;36m[MobileLinux] [ 95%] Finalizing Conda setup...\\033[0m\"",
        "echo -e \"\\033[1;32m[MobileLinux] [100%] ✓ Miniforge3 / Conda installed successfully and activated!\\033[0m\\n\"\n"
    ).joinToString("\n")

    private fun getInstallJupyterScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Automated JupyterLab & Notebook Installer",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m Installing JupyterLab & Notebook...\"",
        "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* /var/lib/dpkg/updates/* /var/cache/debconf/*.lock 2>/dev/null || true",
        "export DEBIAN_FRONTEND=noninteractive",
        "if ! command -v pip3 >/dev/null 2>&1 && ! command -v pip >/dev/null 2>&1 && ! python3 -m pip --version >/dev/null 2>&1; then",
        "    echo -e \"\\033[1;34m[MobileLinux]\\033[0m Installing python3-pip...\"",
        "    sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends python3-pip 2>&1 || {",
        "        sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true update -y 2>&1 || true",
        "        sudo apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends python3-pip 2>&1 || true",
        "    }",
        "fi",
        "# 1. Clean up conflicting Debian system packages lacking pip RECORD files (prevents jsonschema collision)",
        "sudo rm -rf /usr/lib/python3/dist-packages/jsonschema* /usr/lib/python3/dist-packages/rpds* /usr/lib/python3/dist-packages/referencing* 2>/dev/null || true",
        "# 2. Single-pass fast install with pre-compiled wheels & bytecode skip for mobile flash speed",
        "echo -e \"\\033[1;34m[MobileLinux]\\033[0m Installing Notebook, JupyterLab & IPykernel via Pip...\"",
        "PIP_CMD=\"pip3\"",
        "if ! command -v pip3 >/dev/null 2>&1; then",
        "    if command -v pip >/dev/null 2>&1; then",
        "        PIP_CMD=\"pip\"",
        "    else",
        "        PIP_CMD=\"python3 -m pip\"",
        "    fi",
        "fi",
        "\$PIP_CMD install --break-system-packages --ignore-installed --prefer-binary --no-compile notebook jupyterlab ipykernel 2>&1",
        "PIP_EXIT=\$?",
        "# 3. If Conda / Miniforge is present, register the Conda Python kernel",
        "if [ -x /home/ubuntu/miniforge3/bin/python ]; then",
        "    echo -e \"\\033[1;34m[MobileLinux]\\033[0m Registering Conda base kernel...\"",
        "    /home/ubuntu/miniforge3/bin/python -m ipykernel install --user --name conda_base --display-name \"Python (Conda)\" 2>/dev/null || true",
        "fi",
        "# 4. Apply MobileLinux touch & menu fix for Jupyter Notebook 7",
        "if [ -x /usr/local/bin/fix-jupyter-mobile ]; then",
        "    /usr/local/bin/fix-jupyter-mobile >/dev/null 2>&1 || true",
        "fi",
        "# 5. Verification: ensure notebook is actually functional",
        "if [ \$PIP_EXIT -eq 0 ] || python3 -c \"import notebook\" 2>/dev/null || python3 -c \"import jupyterlab\" 2>/dev/null; then",
        "    echo -e \"\\033[1;32m[MobileLinux]\\033[0m ✓ JupyterLab & Notebook installed successfully!\"",
        "    echo -e \"\\033[1;36m[MobileLinux]\\033[0m Run \\033[1;33mjupyter notebook\\033[0m or \\033[1;33mjupyter-start\\033[0m in terminal to launch.\"",
        "    exit 0",
        "else",
        "    echo -e \"\\033[1;31m[MobileLinux]\\033[0m ✗ Jupyter installation failed.\"",
        "    exit 1",
        "fi\n"
    ).joinToString("\n")

    private fun getJupyterNotebookDispatcherScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Smart Jupyter Notebook Dispatcher",
        "if [ -x /usr/local/bin/fix-jupyter-mobile ]; then",
        "    /usr/local/bin/fix-jupyter-mobile >/dev/null 2>&1 || true",
        "fi",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ] && \"\$CONDA_PREFIX/bin/python\" -c \"import notebook\" 2>/dev/null; then",
        "    exec \"\$CONDA_PREFIX/bin/python\" -m notebook \"\$@\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c \"import notebook\" 2>/dev/null; then",
        "    exec /home/ubuntu/miniforge3/bin/python -m notebook \"\$@\"",
        "elif [ -x /root/miniconda3/bin/python ] && /root/miniconda3/bin/python -c \"import notebook\" 2>/dev/null; then",
        "    exec /root/miniconda3/bin/python -m notebook \"\$@\"",
        "fi",
        "if python3 -c \"import notebook\" 2>/dev/null; then",
        "    exec python3 -m notebook \"\$@\"",
        "elif python3 -c \"import jupyterlab\" 2>/dev/null; then",
        "    exec python3 -m jupyterlab \"\$@\"",
        "fi",
        "echo -e \"\\033[1;31m[MobileLinux]\\033[0m Jupyter Notebook is not installed yet.\"",
        "echo -e \"Run \\033[1;33minstall-jupyter\\033[0m in terminal or install 'JupyterLab & Notebook' from 'Libraries & Packages' in the app.\"",
        "exit 1\n"
    ).joinToString("\n")

    private fun getJupyterLabDispatcherScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Smart JupyterLab Dispatcher",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ] && \"\$CONDA_PREFIX/bin/python\" -c \"import jupyterlab\" 2>/dev/null; then",
        "    exec \"\$CONDA_PREFIX/bin/python\" -m jupyterlab \"\$@\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c \"import jupyterlab\" 2>/dev/null; then",
        "    exec /home/ubuntu/miniforge3/bin/python -m jupyterlab \"\$@\"",
        "elif [ -x /root/miniconda3/bin/python ] && /root/miniconda3/bin/python -c \"import jupyterlab\" 2>/dev/null; then",
        "    exec /root/miniconda3/bin/python -m jupyterlab \"\$@\"",
        "fi",
        "if python3 -c \"import jupyterlab\" 2>/dev/null; then",
        "    exec python3 -m jupyterlab \"\$@\"",
        "elif python3 -c \"import notebook\" 2>/dev/null; then",
        "    exec python3 -m notebook \"\$@\"",
        "fi",
        "echo -e \"\\033[1;31m[MobileLinux]\\033[0m JupyterLab is not installed yet.\"",
        "echo -e \"Run \\033[1;33minstall-jupyter\\033[0m in terminal or install 'JupyterLab & Notebook' from 'Libraries & Packages' in the app.\"",
        "exit 1\n"
    ).joinToString("\n")

    private fun getJupyterDispatcherScript(): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Smart Jupyter Dispatcher",
        "SUB=\"\$1\"",
        "if [ \"\$SUB\" = \"notebook\" ]; then",
        "    shift",
        "    exec /usr/local/bin/jupyter-notebook \"\$@\"",
        "elif [ \"\$SUB\" = \"lab\" ]; then",
        "    shift",
        "    exec /usr/local/bin/jupyter-lab \"\$@\"",
        "fi",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ] && \"\$CONDA_PREFIX/bin/python\" -c \"import jupyter_core\" 2>/dev/null; then",
        "    exec \"\$CONDA_PREFIX/bin/python\" -m jupyter \"\$@\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ] && /home/ubuntu/miniforge3/bin/python -c \"import jupyter_core\" 2>/dev/null; then",
        "    exec /home/ubuntu/miniforge3/bin/python -m jupyter \"\$@\"",
        "elif python3 -c \"import jupyter_core\" 2>/dev/null; then",
        "    exec python3 -m jupyter \"\$@\"",
        "fi",
        "echo -e \"\\033[1;31m[MobileLinux]\\033[0m Jupyter is not installed yet.\"",
        "echo -e \"Run \\033[1;33minstall-jupyter\\033[0m in terminal or install 'JupyterLab & Notebook' from 'Libraries & Packages' in the app.\"",
        "exit 1\n"
    ).joinToString("\n")

    data class PythonCliConfig(
        val cmdName: String,
        val displayName: String,
        val importModule: String,
        val moduleAlias: String,
        val pipName: String
    )

    private val pythonCliConfigs = listOf(
        PythonCliConfig("numpy", "NumPy", "numpy", "np", "numpy"),
        PythonCliConfig("pandas", "Pandas", "pandas", "pd", "pandas"),
        PythonCliConfig("scipy", "SciPy", "scipy", "sp", "scipy"),
        PythonCliConfig("sklearn", "Scikit-Learn", "sklearn", "sklearn", "scikit-learn"),
        PythonCliConfig("scikit-learn", "Scikit-Learn", "sklearn", "sklearn", "scikit-learn"),
        PythonCliConfig("torch", "PyTorch", "torch", "torch", "torch"),
        PythonCliConfig("pytorch", "PyTorch", "torch", "torch", "torch"),
        PythonCliConfig("matplotlib", "Matplotlib", "matplotlib", "plt", "matplotlib"),
        PythonCliConfig("seaborn", "Seaborn", "seaborn", "sns", "seaborn"),
        PythonCliConfig("polars", "Polars", "polars", "pl", "polars"),
        PythonCliConfig("sympy", "SymPy", "sympy", "sp", "sympy")
    )

    private fun getPythonCliWrapperScript(cfg: PythonCliConfig): String = listOf(
        "#!/bin/bash",
        "# MobileLinux Smart Python CLI Utility for ${cfg.displayName}",
        "PY=\"\"",
        "if [ -n \"\$CONDA_PREFIX\" ] && [ -x \"\$CONDA_PREFIX/bin/python\" ]; then",
        "    PY=\"\$CONDA_PREFIX/bin/python\"",
        "elif [ -x /home/ubuntu/miniforge3/bin/python ]; then",
        "    PY=\"/home/ubuntu/miniforge3/bin/python\"",
        "elif [ -x /root/miniconda3/bin/python ]; then",
        "    PY=\"/root/miniconda3/bin/python\"",
        "elif command -v python3 >/dev/null 2>&1; then",
        "    PY=\"\$(command -v python3)\"",
        "fi",
        "",
        "is_installed() {",
        "    [ -n \"\$PY\" ] && \"\$PY\" -c \"import ${cfg.importModule}\" >/dev/null 2>&1",
        "}",
        "",
        "get_version() {",
        "    [ -n \"\$PY\" ] && \"\$PY\" -c \"import ${cfg.importModule} as _m; print(getattr(_m, '__version__', 'installed'))\" 2>/dev/null",
        "}",
        "",
        "case \"\$1\" in",
        "    -v|--version|version)",
        "        if is_installed; then",
        "            VER=\"\$(get_version)\"",
        "            echo \"${cfg.displayName} \$VER\"",
        "            exit 0",
        "        else",
        "            echo -e \"\\033[1;31m[MobileLinux]\\033[0m ${cfg.displayName} is not installed in active Python (\$PY).\"",
        "            echo -e \"Install it from 'Libraries & Packages' or run: \\033[1;33mpip3 install ${cfg.pipName}\\033[0m\"",
        "            exit 1",
        "        fi",
        "        ;;",
        "    -h|--help|help)",
        "        echo \"${cfg.displayName} CLI Utility (${cfg.cmdName})\"",
        "        echo \"Usage: ${cfg.cmdName} [OPTIONS]\"",
        "        echo \"  -v, --version    Show installed ${cfg.displayName} version\"",
        "        echo \"  -i, --info       Show package location, Python path, and environment\"",
        "        echo \"  -c '<code>'      Execute Python code with ${cfg.displayName} pre-imported as ${cfg.moduleAlias}\"",
        "        echo \"  -h, --help       Show this help message\"",
        "        echo \"  (no args)        Show status and launch interactive Python with ${cfg.displayName} imported\"",
        "        exit 0",
        "        ;;",
        "    -c)",
        "        shift",
        "        if is_installed; then",
        "            exec \"\$PY\" -c \"import ${cfg.importModule} as ${cfg.moduleAlias}; \$*\"",
        "        else",
        "            echo -e \"\\033[1;31m[MobileLinux]\\033[0m ${cfg.displayName} is not installed in active Python (\$PY).\"",
        "            exit 1",
        "        fi",
        "        ;;",
        "    -i|--info|info)",
        "        if is_installed; then",
        "            VER=\"\$(get_version)\"",
        "            LOC=\"\$(\"\$PY\" -c \"import ${cfg.importModule} as _m; print(getattr(_m, '__file__', ''))\" 2>/dev/null)\"",
        "            echo -e \"\\033[1;36m┌─[MobileLinux]─[${cfg.displayName}]\\033[0m\"",
        "            echo -e \"\\033[1;36m│\\033[0m \\033[1;32mVersion:\\033[0m       \$VER\"",
        "            echo -e \"\\033[1;36m│\\033[0m \\033[1;34mLocation:\\033[0m      \$LOC\"",
        "            echo -e \"\\033[1;36m│\\033[0m \\033[1;34mActive Python:\\033[0m \$PY\"",
        "            [ -n \"\$CONDA_DEFAULT_ENV\" ] && echo -e \"\\033[1;36m│\\033[0m \\033[1;33mConda Env:\\033[0m     \$CONDA_DEFAULT_ENV\"",
        "            echo -e \"\\033[1;36m│\\033[0m Python usage:\"",
        "            echo -e \"\\033[1;36m│\\033[0m   python3 -c 'import ${cfg.importModule} as ${cfg.moduleAlias}'\"",
        "            echo -e \"\\033[1;36m└──────────────────────────────────────────────\\033[0m\"",
        "            exit 0",
        "        else",
        "            echo -e \"\\033[1;31m[MobileLinux]\\033[0m ${cfg.displayName} is not installed in active Python (\$PY).\"",
        "            exit 1",
        "        fi",
        "        ;;",
        "    *)",
        "        if is_installed; then",
        "            VER=\"\$(get_version)\"",
        "            echo -e \"\\033[1;36m┌─[MobileLinux]─[${cfg.displayName}]\\033[0m\"",
        "            echo -e \"\\033[1;36m│\\033[0m \\033[1;32mVersion:\\033[0m       \$VER\"",
        "            echo -e \"\\033[1;36m│\\033[0m \\033[1;34mActive Python:\\033[0m \$PY\"",
        "            [ -n \"\$CONDA_DEFAULT_ENV\" ] && echo -e \"\\033[1;36m│\\033[0m \\033[1;33mConda Env:\\033[0m     \$CONDA_DEFAULT_ENV\"",
        "            echo -e \"\\033[1;36m│\\033[0m Starting Python interactive shell with ${cfg.displayName} imported...\"",
        "            echo -e \"\\033[1;36m└──────────────────────────────────────────────\\033[0m\"",
        "            exec \"\$PY\" -i -c \"import ${cfg.importModule} as ${cfg.moduleAlias}; print('>>> ${cfg.displayName} '\$VER' imported as \'${cfg.moduleAlias}\'. Type exit() to quit.')\"",
        "        else",
        "            echo -e \"\\033[1;31m[MobileLinux]\\033[0m ${cfg.displayName} is not installed in active Python (\$PY).\"",
        "            echo -e \"Install it from 'Libraries & Packages' or run: \\033[1;33mpip3 install ${cfg.pipName}\\033[0m\"",
        "            exit 1",
        "        fi",
        "        ;;",
        "esac\n"
    ).joinToString("\n")

    private fun getMobileLinuxRmPyScript(): String = listOf(
        "#!/usr/bin/env python3",
        "import sys",
        "import os",
        "import stat",
        "",
        "def force_remove_path(target):",
        "    if not os.path.exists(target) and not os.path.islink(target):",
        "        return",
        "    if os.path.islink(target) or not os.path.isdir(target):",
        "        try:",
        "            os.chmod(target, stat.S_IWUSR | stat.S_IRUSR)",
        "        except Exception:",
        "            pass",
        "        try:",
        "            os.unlink(target)",
        "        except Exception:",
        "            pass",
        "        return",
        "    for root, dirs, files in os.walk(target, topdown=False, followlinks=False):",
        "        for fname in files:",
        "            fpath = os.path.join(root, fname)",
        "            try:",
        "                os.chmod(fpath, stat.S_IWUSR | stat.S_IRUSR)",
        "            except Exception:",
        "                pass",
        "            try:",
        "                os.unlink(fpath)",
        "            except Exception:",
        "                pass",
        "        for dname in dirs:",
        "            dpath = os.path.join(root, dname)",
        "            try:",
        "                os.chmod(dpath, stat.S_IWUSR | stat.S_IRUSR | stat.S_IXUSR)",
        "            except Exception:",
        "                pass",
        "            try:",
        "                os.rmdir(dpath)",
        "            except Exception:",
        "                pass",
        "    try:",
        "        os.chmod(target, stat.S_IWUSR | stat.S_IRUSR | stat.S_IXUSR)",
        "    except Exception:",
        "        pass",
        "    try:",
        "        os.rmdir(target)",
        "    except Exception:",
        "        pass",
        "",
        "if __name__ == '__main__':",
        "    for arg in sys.argv[1:]:",
        "        if not arg.startswith('-'):",
        "            force_remove_path(os.path.expanduser(arg))\n"
    ).joinToString("\n")

    private fun getSmartRmScript(): String = listOf(
        "#!/bin/bash",
        "# Smart rm wrapper: delegates to /bin/rm first.",
        "# On recursive failure due to read-only dirs or broken symlinks,",
        "# falls back to python3 bottom-up unlinker. Uses /bin/rm internally",
        "# to avoid recursive self-calls.",
        "TMP_ERR=\"/tmp/.ml_rm_err.\$\$\"",
        "/bin/rm \"\$@\" 2>\"\$TMP_ERR\"",
        "EXIT_CODE=\$?",
        "if [ \$EXIT_CODE -eq 0 ]; then",
        "    /bin/rm -f \"\$TMP_ERR\" 2>/dev/null",
        "    exit 0",
        "fi",
        "IS_RECURSIVE=0",
        "for arg in \"\$@\"; do",
        "    case \"\$arg\" in",
        "        -*r*|-*R*|--recursive)",
        "            IS_RECURSIVE=1",
        "            ;;",
        "    esac",
        "done",
        "if [ \$IS_RECURSIVE -eq 1 ] && [ -x /usr/bin/python3 ] && [ -f /usr/local/bin/mobilelinux-rm.py ]; then",
        "    /bin/rm -f \"\$TMP_ERR\" 2>/dev/null",
        "    exec /usr/bin/python3 /usr/local/bin/mobilelinux-rm.py \"\$@\"",
        "fi",
        "cat \"\$TMP_ERR\" >&2",
        "/bin/rm -f \"\$TMP_ERR\" 2>/dev/null",
        "exit \$EXIT_CODE\n"
    ).joinToString("\n")

    private fun getForceRmScript(): String = listOf(
        "#!/bin/bash",
        "if [ \$# -eq 0 ]; then",
        "    echo \"Usage: force-rm <file_or_directory> [...]\"",
        "    exit 1",
        "fi",
        "if [ -x /usr/local/bin/mobilelinux-rm.py ]; then",
        "    exec /usr/bin/python3 /usr/local/bin/mobilelinux-rm.py \"\$@\"",
        "else",
        "    for item in \"\$@\"; do",
        "        [ -e \"\$item\" ] || [ -L \"\$item\" ] || continue",
        "        chmod -R u+w \"\$item\" 2>/dev/null || true",
        "        /bin/rm -rf \"\$item\" 2>/dev/null || true",
        "    done",
        "fi\n"
    ).joinToString("\n")

    private fun getFixPermissionsScript(): String = listOf(
        "#!/bin/bash",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m Repairing file and directory permissions...\"",
        "chmod 1777 /dev/shm /run/shm /tmp 2>/dev/null || true",
        "if [ -x /usr/bin/python3 ]; then",
        "    python3 -c '",
        "import os, stat",
        "def fix_tree(root_dir):",
        "    if not os.path.isdir(root_dir):",
        "        return",
        "    for root, dirs, files in os.walk(root_dir, topdown=True, followlinks=False):",
        "        try:",
        "            os.chmod(root, stat.S_IRWXU | stat.S_IRGRP | stat.S_IXGRP | stat.S_IROTH | stat.S_IXOTH)",
        "        except Exception:",
        "            pass",
        "        for f in files:",
        "            fp = os.path.join(root, f)",
        "            if not os.path.islink(fp):",
        "                try:",
        "                    mode = os.stat(fp).st_mode",
        "                    os.chmod(fp, mode | stat.S_IRUSR | stat.S_IWUSR)",
        "                except Exception:",
        "                    pass",
        "fix_tree(\"/home/ubuntu\")",
        "fix_tree(\"/root\")",
        "' 2>/dev/null",
        "else",
        "    chmod -R u+rwX,go-w /home/ubuntu /root 2>/dev/null || true",
        "fi",
        "chmod 755 /home/ubuntu /root /home/ubuntu/go /root/go /home/ubuntu/.local /home/ubuntu/.cargo 2>/dev/null || true",
        "chmod -R go-w /home/ubuntu/go /root/go 2>/dev/null || true",
        "echo -e \"\\033[1;32m[MobileLinux]\\033[0m Permissions restored successfully ✓\"\n"
    ).joinToString("\n")

    @Volatile
    var isInstallingTools = false
        private set

    private fun installEssentialToolsInBackground() {
        val marker = File(rootfsDir, "etc/mobilelinux/.tools_installed")
        if (marker.exists()) return
        if (isInstallingTools) return

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val isConnected = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork
                val capabilities = cm?.getNetworkCapabilities(network)
                capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                cm?.activeNetworkInfo?.isConnectedOrConnecting == true
            }
            if (!isConnected) {
                Log.d(TAG, "No network connection; deferred auto-install of essential tools")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity check notice: ${e.message}")
        }

        isInstallingTools = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Wait 4 seconds after session starts so interactive terminal loads immediately without apt lock contention
                delay(4000)
                if (marker.exists()) {
                    isInstallingTools = false
                    return@launch
                }
                Log.d(TAG, "Starting background auto-installation of essential tools...")
                val etcMl = File(rootfsDir, "etc/mobilelinux")
                ensureRealDirectory(etcMl)

                val installCmd = "export DEBIAN_FRONTEND=noninteractive && " +
                        "rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true; " +
                        "(apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true update || true) && " +
                        "apt-get -o DPkg::Lock::Timeout=60 -o Acquire::ForceIPv4=true install -y --no-install-recommends curl wget ca-certificates nano vim-tiny git python3-pip htop tree unzip zip && " +
                        "touch /etc/mobilelinux/.tools_installed"

                val result = runCommand(installCmd)
                if (result.first == 0) {
                    Log.i(TAG, "Essential tools auto-installed successfully! ✓")
                } else {
                    Log.w(TAG, "Background auto-install exit code ${result.first}: ${result.second}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background auto-install exception: ${e.message}")
            } finally {
                isInstallingTools = false
            }
        }
    }

    /**
     * MobileLinux touch & menu interaction script for Jupyter Notebook 7.
     * Fixes Lumino menu pointer event loss, bypasses mobile popup blockers,
     * and adds convenient touch-friendly quick action buttons.
     */
    private fun getMobileJupyterTouchScript(): String = listOf(
        "<script id=\"mobilelinux-touch-patch\">",
        "/* MobileLinux Touch & Menu Fix for Jupyter Notebook 7 */",
        "(function() {",
        "    if (window.__ml_touch_init) return;",
        "    window.__ml_touch_init = true;",
        "",
        "    // 1. Polyfill window.open to bypass mobile popup blockers",
        "    var origOpen = window.open;",
        "    window.open = function(url, target, features) {",
        "        if (!url) {",
        "            var fakeWin = {",
        "                opener: null,",
        "                location: {",
        "                    set href(val) { if (val) window.location.href = val; },",
        "                    get href() { return window.location.href; }",
        "                },",
        "                focus: function() {},",
        "                close: function() {}",
        "            };",
        "            try {",
        "                var w = origOpen ? origOpen.call(window, '', target, features) : null;",
        "                if (w) return w;",
        "            } catch(e) {}",
        "            return fakeWin;",
        "        }",
        "        try {",
        "            var w = origOpen ? origOpen.call(window, url, target, features) : null;",
        "            if (!w) { window.location.href = url; }",
        "            return w;",
        "        } catch(e) {",
        "            window.location.href = url;",
        "            return null;",
        "        }",
        "    };",
        "",
        "    function runJupyterCmd(cmd, args) {",
        "        if (window.jupyterapp && window.jupyterapp.commands) {",
        "            try {",
        "                return window.jupyterapp.commands.execute(cmd, args);",
        "            } catch(e) {",
        "                console.warn('[MobileLinux] Command failed:', cmd, e);",
        "            }",
        "        }",
        "        return null;",
        "    }",
        "",
        "    function createNotebookFallback() {",
        "        var base = (window.jupyterConfigData && window.jupyterConfigData.baseUrl) || '/';",
        "        if (!base.endsWith('/')) base += '/';",
        "        fetch(base + 'api/contents/', {",
        "            method: 'POST',",
        "            headers: { 'Content-Type': 'application/json' },",
        "            body: JSON.stringify({ type: 'notebook' })",
        "        })",
        "        .then(function(r) { return r.json(); })",
        "        .then(function(data) {",
        "            if (data && data.path) {",
        "                window.location.href = base + 'notebooks/' + encodeURI(data.path);",
        "            }",
        "        })",
        "        .catch(function(err) {",
        "            console.error('[MobileLinux] Failed to create notebook via API:', err);",
        "        });",
        "    }",
        "",
        "    function createFolderFallback() {",
        "        var base = (window.jupyterConfigData && window.jupyterConfigData.baseUrl) || '/';",
        "        if (!base.endsWith('/')) base += '/';",
        "        fetch(base + 'api/contents/', {",
        "            method: 'POST',",
        "            headers: { 'Content-Type': 'application/json' },",
        "            body: JSON.stringify({ type: 'directory' })",
        "        })",
        "        .then(function() {",
        "            if (window.jupyterapp && window.jupyterapp.commands) {",
        "                window.jupyterapp.commands.execute('filebrowser:refresh');",
        "            } else {",
        "                window.location.reload();",
        "            }",
        "        })",
        "        .catch(function(err) {",
        "            console.error('[MobileLinux] Failed to create folder via API:', err);",
        "        });",
        "    }",
        "",
        "    // 2. Touch interaction handler for Lumino Menus & MenuBars",
        "    var startX = 0, startY = 0;",
        "",
        "    document.addEventListener('touchstart', function(e) {",
        "        if (e.touches && e.touches.length === 1) {",
        "            startX = e.touches[0].clientX;",
        "            startY = e.touches[0].clientY;",
        "        }",
        "    }, { capture: true, passive: true });",
        "",
        "    document.addEventListener('touchend', function(e) {",
        "        var touch = e.changedTouches && e.changedTouches[0];",
        "        if (!touch) return;",
        "        var dx = Math.abs(touch.clientX - startX);",
        "        var dy = Math.abs(touch.clientY - startY);",
        "        if (dx > 15 || dy > 15) return;",
        "",
        "        var target = e.target;",
        "        if (!target) return;",
        "",
        "        var menuItem = target.closest('.lm-Menu-item');",
        "        if (menuItem) {",
        "            e.preventDefault();",
        "            e.stopPropagation();",
        "",
        "            var rect = menuItem.getBoundingClientRect();",
        "            var cx = rect.left + rect.width / 2;",
        "            var cy = rect.top + rect.height / 2;",
        "",
        "            menuItem.dispatchEvent(new MouseEvent('mousemove', {",
        "                bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy",
        "            }));",
        "",
        "            setTimeout(function() {",
        "                menuItem.dispatchEvent(new MouseEvent('mouseup', {",
        "                    bubbles: true, cancelable: true, view: window, button: 0, clientX: cx, clientY: cy",
        "                }));",
        "                menuItem.dispatchEvent(new MouseEvent('click', {",
        "                    bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy",
        "                }));",
        "",
        "                var labelElem = menuItem.querySelector('.lm-Menu-itemLabel');",
        "                var text = (labelElem ? labelElem.textContent : menuItem.textContent || '').trim().toLowerCase();",
        "                var cmd = menuItem.getAttribute('data-command');",
        "",
        "                if (cmd) {",
        "                    runJupyterCmd(cmd);",
        "                } else if (text.indexOf('python 3') !== -1 || text.indexOf('ipykernel') !== -1 || text === 'notebook') {",
        "                    if (!runJupyterCmd('notebook:create-new', { isLauncher: true })) {",
        "                        createNotebookFallback();",
        "                    }",
        "                } else if (text.indexOf('folder') !== -1) {",
        "                    if (!runJupyterCmd('filebrowser:create-new-directory')) {",
        "                        createFolderFallback();",
        "                    }",
        "                } else if (text.indexOf('terminal') !== -1) {",
        "                    runJupyterCmd('terminal:create-new');",
        "                } else if (text.indexOf('console') !== -1) {",
        "                    runJupyterCmd('console:create');",
        "                } else if (text.indexOf('file') !== -1) {",
        "                    runJupyterCmd('filebrowser:create-new-file');",
        "                }",
        "            }, 40);",
        "            return;",
        "        }",
        "",
        "        var menuBarItem = target.closest('.lm-MenuBar-item');",
        "        if (menuBarItem) {",
        "            var rectB = menuBarItem.getBoundingClientRect();",
        "            var bx = rectB.left + rectB.width / 2;",
        "            var by = rectB.top + rectB.height / 2;",
        "            menuBarItem.dispatchEvent(new MouseEvent('mousedown', {",
        "                bubbles: true, cancelable: true, view: window, button: 0, clientX: bx, clientY: by",
        "            }));",
        "            return;",
        "        }",
        "    }, { capture: true, passive: false });",
        "",
        "    // 3. Inject Touch-Friendly Action Toolbar for Mobile on Dashboard/Tree",
        "    function addMobileActionToolbar() {",
        "        if (document.getElementById('mobilelinux-touch-bar')) return;",
        "        if (!document.body) return;",
        "        var p = window.location.pathname || '';",
        "        if (p.indexOf('/tree') === -1 && p !== '/' && !p.endsWith('/')) return;",
        "",
        "        var bar = document.createElement('div');",
        "        bar.id = 'mobilelinux-touch-bar';",
        "        bar.innerHTML = [",
        "            '<style>',",
        "            '#mobilelinux-touch-bar { position: fixed; bottom: 24px; right: 18px; display: flex; flex-direction: column; gap: 10px; z-index: 10000; }',",
        "            '.ml-touch-btn { display: flex; align-items: center; justify-content: center; gap: 6px; padding: 11px 18px; border-radius: 24px; font-size: 14px; font-weight: 600; font-family: system-ui, -apple-system, sans-serif; box-shadow: 0 4px 14px rgba(0,0,0,0.35); border: none; cursor: pointer; transition: transform 0.15s ease; user-select: none; -webkit-user-select: none; touch-action: manipulation; }',",
        "            '.ml-touch-btn:active { transform: scale(0.93); }',",
        "            '.ml-btn-nb { background: #1976d2; color: #fff; }',",
        "            '.ml-btn-folder { background: #388e3c; color: #fff; }',",
        "            '.ml-btn-lab { background: #f57c00; color: #fff; font-size: 12px; padding: 7px 14px; }',",
        "            '.lm-Menu-item { min-height: 44px !important; padding: 10px 18px !important; font-size: 15px !important; touch-action: manipulation !important; }',",
        "            '.lm-MenuBar-item { min-height: 38px !important; padding: 8px 14px !important; font-size: 14px !important; touch-action: manipulation !important; }',",
        "            '</style>',",
        "            '<button class=\"ml-touch-btn ml-btn-nb\" id=\"ml-action-new-nb\">＋ Notebook</button>',",
        "            '<button class=\"ml-touch-btn ml-btn-folder\" id=\"ml-action-new-folder\">＋ Folder</button>',",
        "            '<button class=\"ml-touch-btn ml-btn-lab\" id=\"ml-action-open-lab\">⚡ Open Lab</button>'",
        "        ].join('\\n');",
        "",
        "        document.body.appendChild(bar);",
        "",
        "        document.getElementById('ml-action-new-nb').addEventListener('click', function(ev) {",
        "            ev.preventDefault(); ev.stopPropagation();",
        "            if (!runJupyterCmd('notebook:create-new', { isLauncher: true })) {",
        "                createNotebookFallback();",
        "            }",
        "        });",
        "",
        "        document.getElementById('ml-action-new-folder').addEventListener('click', function(ev) {",
        "            ev.preventDefault(); ev.stopPropagation();",
        "            if (!runJupyterCmd('filebrowser:create-new-directory')) {",
        "                createFolderFallback();",
        "            }",
        "        });",
        "",
        "        document.getElementById('ml-action-open-lab').addEventListener('click', function(ev) {",
        "            ev.preventDefault(); ev.stopPropagation();",
        "            var base = (window.jupyterConfigData && window.jupyterConfigData.baseUrl) || '/';",
        "            if (!base.endsWith('/')) base += '/';",
        "            window.location.href = base + 'lab';",
        "        });",
        "    }",
        "",
        "    if (document.readyState === 'loading') {",
        "        document.addEventListener('DOMContentLoaded', addMobileActionToolbar);",
        "    } else {",
        "        addMobileActionToolbar();",
        "    }",
        "    setTimeout(addMobileActionToolbar, 1000);",
        "    setTimeout(addMobileActionToolbar, 2500);",
        "})();",
        "</script>"
    ).joinToString("\n")

    /**
     * Generates /usr/local/bin/fix-jupyter-mobile script that patches Jupyter templates in PRoot.
     */
    private fun getFixJupyterMobileScript(): String {
        val touchScript = getMobileJupyterTouchScript()
        return listOf(
            "#!/bin/bash",
            "# MobileLinux - Fix Jupyter Notebook 7 Touch & Menu Interaction on Android",
            "python3 -c '",
            "import os, glob",
            "",
            "PATCH = \"\"\"" + touchScript.replace("\"", "\\\"").replace("\$", "\\\$") + "\"\"\"",
            "",
            "search_dirs = [",
            "    \"/usr/local/lib/python3*/dist-packages/notebook/templates\",",
            "    \"/usr/lib/python3*/dist-packages/notebook/templates\",",
            "    \"/home/ubuntu/miniforge3/lib/python3*/site-packages/notebook/templates\",",
            "    \"/root/miniconda3/lib/python3*/site-packages/notebook/templates\"",
            "]",
            "",
            "count = 0",
            "for pattern in search_dirs:",
            "    for tdir in glob.glob(pattern):",
            "        if os.path.isdir(tdir):",
            "            for tfile in glob.glob(os.path.join(tdir, \"*.html\")):",
            "                try:",
            "                    with open(tfile, \"r\", encoding=\"utf-8\") as f:",
            "                        content = f.read()",
            "                    if \"mobilelinux-touch-patch\" not in content and \"</body>\" in content:",
            "                        new_content = content.replace(\"</body>\", PATCH + \"\\n</body>\")",
            "                        with open(tfile, \"w\", encoding=\"utf-8\") as f:",
            "                            f.write(new_content)",
            "                        count += 1",
            "                        print(f\"[MobileLinux] Patched: {tfile}\")",
            "                except Exception as e:",
            "                    print(f\"[MobileLinux] Error patching {tfile}: {e}\")",
            "",
            "print(f\"[MobileLinux] Mobile touch patch completed: {count} templates patched.\")",
            "'\n"
        ).joinToString("\n")
    }

    /**
     * Patches Jupyter Notebook 7 templates (tree.html, notebooks.html, etc.) to include
     * the MobileLinux touch and menu event fix, window.open polyfill, and mobile quick action toolbar.
     */
    private fun patchJupyterTemplatesForMobile(rootfsDir: File) {
        try {
            val patchMarker = "mobilelinux-touch-patch"
            val patchScript = getMobileJupyterTouchScript()
            val candidateBases = listOf(
                File(rootfsDir, "usr/local/lib"),
                File(rootfsDir, "usr/lib"),
                File(rootfsDir, "home/ubuntu/miniforge3/lib"),
                File(rootfsDir, "root/miniconda3/lib")
            )
            for (base in candidateBases) {
                if (!base.exists() || !base.isDirectory) continue
                base.walkTopDown()
                    .maxDepth(8)
                    .filter { it.isFile && it.name.endsWith(".html") && it.parentFile?.name == "templates" && it.parentFile?.parentFile?.name == "notebook" }
                    .forEach { htmlFile ->
                        try {
                            val content = htmlFile.readText()
                            if (!content.contains(patchMarker) && content.contains("</body>")) {
                                val patched = content.replace("</body>", "$patchScript\n</body>")
                                safeWriteFile(htmlFile, patched)
                                Log.i(TAG, "Patched Jupyter Notebook mobile template: ${htmlFile.path}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Notice: could not patch ${htmlFile.path}: ${e.message}")
                        }
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: patchJupyterTemplatesForMobile: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: UbuntuRuntime? = null

        fun getInstance(context: Context): UbuntuRuntime {
            return instance ?: synchronized(this) {
                instance ?: UbuntuRuntime(context.applicationContext).also { instance = it }
            }
        }
    }
}
