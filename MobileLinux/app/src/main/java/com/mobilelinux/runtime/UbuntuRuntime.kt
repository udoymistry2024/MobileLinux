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
                safeWriteFile(envFile, "LANG=C.UTF-8\nLC_ALL=C.UTF-8\nPATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games\"\n")
                envFile.setReadable(true, false)

                val localeConf = File(etcDir, "locale.conf")
                safeWriteFile(localeConf, "LANG=C.UTF-8\nLC_ALL=C.UTF-8\n")
                localeConf.setReadable(true, false)

                val profileD = File(etcDir, "profile.d")
                ensureRealDirectory(profileD)
                val localeProfile = File(profileD, "00-locale.sh")
                safeWriteFile(localeProfile, "export LANG=C.UTF-8\nexport LC_ALL=C.UTF-8\n")
                localeProfile.setReadable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "Locale config notice: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: ensureBashConfigured: ${e.message}")
        }
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
        cmd.addAll(listOf(
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
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "SHELL=/usr/bin/bash",
            "USER=ubuntu",
            "LOGNAME=ubuntu",
            "ANDROID_HOST=true",
            "MOBILELINUX_MODE=proot",
            "MOBILELINUX_SESSION=$sessionId"
        ))

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
                append("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ")
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
     */
    suspend fun runCommand(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val process = createSessionProcess(
                sessionId = "cmd_${System.currentTimeMillis()}",
                execCommand = command
            )
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Pair(exitCode, output)
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command", e)
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    private fun installCommandWrappers() {
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

            Log.d(TAG, "Command wrappers installed ✓")
        } catch (e: Exception) {
            Log.w(TAG, "Wrappers install notice: ${e.message}")
        }
    }

    private fun installBashEnvironments() {
        try {
            val ubuntuHome = File(rootfsDir, "home/ubuntu")
            ensureRealDirectory(ubuntuHome)
            ubuntuHome.setWritable(true, false)
            ubuntuHome.setReadable(true, false)
            ubuntuHome.setExecutable(true, false)

            val ubuntuBashrc = File(ubuntuHome, ".bashrc")
            if (!ubuntuBashrc.exists()) {
                safeWriteFile(ubuntuBashrc, getUbuntuBashrc())
            } else {
                val existing = ubuntuBashrc.readText()
                if (!existing.contains("alias clear=")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("alias clear='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    sb.append("alias cls='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    safeWriteFile(ubuntuBashrc, sb.toString())
                }
            }
            ubuntuBashrc.setReadable(true, false)

            val ubuntuProfile = File(ubuntuHome, ".profile")
            if (!ubuntuProfile.exists()) {
                safeWriteFile(ubuntuProfile, getProfileContent())
            }
            ubuntuProfile.setReadable(true, false)

            val rootHome = File(rootfsDir, "root")
            ensureRealDirectory(rootHome)
            rootHome.setWritable(true, false)
            rootHome.setReadable(true, false)
            rootHome.setExecutable(true, false)

            val rootBashrc = File(rootHome, ".bashrc")
            if (!rootBashrc.exists()) {
                safeWriteFile(rootBashrc, getRootBashrc())
            } else {
                val existing = rootBashrc.readText()
                if (!existing.contains("alias clear=")) {
                    val sb = StringBuilder(existing)
                    if (!existing.endsWith("\n") && existing.isNotEmpty()) sb.append("\n")
                    sb.append("alias clear='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    sb.append("alias cls='printf \"\\033[H\\033[2J\\033[3J\"'\n")
                    safeWriteFile(rootBashrc, sb.toString())
                }
            }
            rootBashrc.setReadable(true, false)

            val rootProfile = File(rootHome, ".profile")
            if (!rootProfile.exists()) {
                safeWriteFile(rootProfile, getProfileContent())
            }
            rootProfile.setReadable(true, false)

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

            Log.d(TAG, "Bash environments configured ✓")
        } catch (e: Exception) {
            Log.w(TAG, "Bash environment install notice: ${e.message}")
        }
    }

    private fun getSudoScript(): String = listOf(
        "#!/bin/bash",
        "export LANG=C.UTF-8",
        "export LC_ALL=C.UTF-8",
        "if [ \$# -eq 0 ]; then",
        "    echo \"usage: sudo [-h] [-i | -s] [command]\"",
        "    exit 1",
        "fi",
        "case \"\$1\" in",
        "    su)",
        "        shift",
        "        export USER=root",
        "        export LOGNAME=root",
        "        export HOME=/root",
        "        export LANG=C.UTF-8",
        "        export LC_ALL=C.UTF-8",
        "        if [ \"\$1\" = \"-\" ] || [ \"\$1\" = \"-l\" ] || [ \"\$1\" = \"--login\" ]; then",
        "            cd /root",
        "        fi",
        "        exec /bin/bash --login -i",
        "        ;;",
        "    -i|--login|-s|--shell)",
        "        shift",
        "        cd /root",
        "        export USER=root",
        "        export LOGNAME=root",
        "        export HOME=/root",
        "        export LANG=C.UTF-8",
        "        export LC_ALL=C.UTF-8",
        "        if [ \$# -gt 0 ]; then",
        "            exec /bin/bash --login -c \"\$*\"",
        "        else",
        "            exec /bin/bash --login -i",
        "        fi",
        "        ;;",
        "    bash|/bin/bash|/usr/bin/bash|sh|/bin/sh)",
        "        shift",
        "        export USER=root",
        "        export LOGNAME=root",
        "        export HOME=/root",
        "        export LANG=C.UTF-8",
        "        export LC_ALL=C.UTF-8",
        "        if [ \$# -gt 0 ]; then",
        "            exec /bin/bash \"\$@\"",
        "        else",
        "            exec /bin/bash --login -i",
        "        fi",
        "        ;;",
        "    *)",
        "        export USER=root",
        "        export LOGNAME=root",
        "        export HOME=/root",
        "        export LANG=C.UTF-8",
        "        export LC_ALL=C.UTF-8",
        "        exec \"\$@\"",
        "        ;;",
        "esac\n"
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
        "export PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games\"",
        "shopt -s checkwinsize",
        "",
        "# Aliases",
        "alias ls='ls --color=auto'",
        "alias ll='ls -la --color=auto'",
        "alias update='sudo apt-get update'",
        "alias install='sudo apt-get install -y'",
        "alias python='python3'",
        "alias pip='python3 -m pip'",
        "alias clear='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias cls='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias install-tools='/usr/local/bin/install-tools'",
        "alias pkg-install='/usr/local/bin/pkg-install'",
        "alias fix-perms='/usr/local/bin/fix-permissions'",
        "alias force-rm='/usr/local/bin/force-rm'",
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
        "export PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games\"",
        "shopt -s checkwinsize",
        "",
        "# Aliases",
        "alias ls='ls --color=auto'",
        "alias ll='ls -la --color=auto'",
        "alias update='apt-get update'",
        "alias install='apt-get install -y'",
        "alias python='python3'",
        "alias pip='python3 -m pip'",
        "alias clear='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias cls='printf \"\\033[H\\033[2J\\033[3J\"'",
        "alias install-tools='/usr/local/bin/install-tools'",
        "alias pkg-install='/usr/local/bin/pkg-install'",
        "alias fix-perms='/usr/local/bin/fix-permissions'",
        "alias force-rm='/usr/local/bin/force-rm'",
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
        "export PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"",
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
        "if /usr/bin/python3 -m pip --version >/dev/null 2>&1; then",
        "    exec /usr/bin/python3 -m pip \"\$@\"",
        "elif [ -x /usr/bin/pip3 ]; then",
        "    exec /usr/bin/pip3 \"\$@\"",
        "fi",
        "echo -e \"\\033[1;36m[MobileLinux]\\033[0m pip is not installed yet. Installing python3-pip...\"",
        "export DEBIAN_FRONTEND=noninteractive",
        "sudo apt-get update -y && sudo apt-get install -y --no-install-recommends python3-pip",
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
        "if sudo apt-get update -y && sudo apt-get install -y --no-install-recommends $pkgName; then",
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
        "if sudo apt-get update -y && sudo apt-get install -y --no-install-recommends nano vim-tiny git python3-pip htop tree unzip zip; then",
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
        "sudo apt-get update -y && sudo apt-get install -y --no-install-recommends \"\$@\"\n"
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
        "' 2>/dev/null",
        "else",
        "    chmod -R u+rwX /home/ubuntu 2>/dev/null || true",
        "fi",
        "echo -e \"\\033[1;32m[MobileLinux]\\033[0m Permissions restored successfully ✓\"\n"
    ).joinToString("\n")

    @Volatile
    private var isInstallingTools = false

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
                        "apt-get update -y && " +
                        "apt-get install -y --no-install-recommends nano vim-tiny git python3-pip htop tree unzip zip && " +
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
