package com.mobilelinux.service

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.mobilelinux.MobileLinuxApp
import com.mobilelinux.model.LinuxPackage
import com.mobilelinux.model.PackageCategory
import com.mobilelinux.model.PackageRepository
import com.mobilelinux.runtime.UbuntuRuntime
import com.mobilelinux.util.PackageProgressParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton manager for robust, persistent package installations and uninstalls.
 *
 * Runs on an Application-level CoroutineScope backed by a PowerManager WakeLock
 * and the LinuxService foreground service. Ensures package installations never stop
 * when the user navigates away from the Libraries screen or minimizes the app.
 */
class PackageInstallationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PkgInstallManager"

        @Volatile
        private var instance: PackageInstallationManager? = null

        fun getInstance(context: Context): PackageInstallationManager {
            return instance ?: synchronized(this) {
                instance ?: PackageInstallationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtime: UbuntuRuntime = UbuntuRuntime.getInstance(context)

    // WakeLock to keep mobile CPU alive during long package downloads & extractions
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "MobileLinux::PackageInstallationWakeLock"
    ).apply {
        setReferenceCounted(false)
    }

    // Thread-safe queues
    private val installQueue = ArrayDeque<LinuxPackage>()
    private val lock = Any()

    @Volatile
    var currentPackage: LinuxPackage? = null
        private set

    @Volatile
    var isQueueProcessing: Boolean = false
        private set

    @Volatile
    var isUninstallRunning: Boolean = false
        private set

    // Real-time state cache for quick UI sync upon returning to LibrariesActivity
    val activeStates = ConcurrentHashMap<String, InstallProgressUpdate>()

    private val _progressEvents = MutableSharedFlow<InstallProgressUpdate>(replay = 10, extraBufferCapacity = 64)
    val progressEvents: SharedFlow<InstallProgressUpdate> = _progressEvents.asSharedFlow()

    data class InstallProgressUpdate(
        val packageId: String,
        val percent: Int,
        val stage: String,
        val isInstalling: Boolean,
        val isInstalled: Boolean,
        val isUninstalling: Boolean = false,
        val isFailed: Boolean = false,
        val isActivated: Boolean = false,
        val isQueued: Boolean = false,
        val queuePosition: Int = 0
    )

    fun isAnyInstallInProgress(): Boolean {
        return isQueueProcessing || currentPackage != null || isUninstallRunning
    }

    fun getQueuedPackages(): List<LinuxPackage> = synchronized(lock) {
        installQueue.toList()
    }

    /**
     * Enqueues a package for installation. Executes immediately if queue is idle.
     */
    fun enqueueInstall(pkg: LinuxPackage, onQueued: ((Int) -> Unit)? = null) {
        synchronized(lock) {
            if (currentPackage?.id == pkg.id || installQueue.any { it.id == pkg.id }) {
                Log.d(TAG, "${pkg.name} is already queued or installing.")
                return
            }

            if (!isQueueProcessing && currentPackage == null) {
                isQueueProcessing = true
                currentPackage = pkg
                acquireWakeLock()
                startLinuxServiceIfNeeded()
                executeInstall(pkg)
            } else {
                installQueue.addLast(pkg)
                val queuePos = installQueue.size
                pkg.isInstalling = true
                pkg.progressPercent = -1
                pkg.statusText = "Queued (Pending #$queuePos in line)"

                val update = InstallProgressUpdate(
                    packageId = pkg.id,
                    percent = -1,
                    stage = pkg.statusText,
                    isInstalling = true,
                    isInstalled = false,
                    isQueued = true,
                    queuePosition = queuePos
                )
                activeStates[pkg.id] = update
                _progressEvents.tryEmit(update)
                onQueued?.invoke(queuePos)
            }
        }
    }

    private fun executeInstall(pkg: LinuxPackage) {
        pkg.isInstalling = true
        pkg.progressPercent = 5
        pkg.statusText = "Starting installation..."

        val initialUpdate = InstallProgressUpdate(
            packageId = pkg.id,
            percent = 5,
            stage = pkg.statusText,
            isInstalling = true,
            isInstalled = false
        )
        activeStates[pkg.id] = initialUpdate
        _progressEvents.tryEmit(initialUpdate)

        MobileLinuxApp.notifyScreenKeepOnChanged()

        scope.launch {
            val parser = PackageProgressParser(pkg.name)
            var lastUpdateMs = 0L

            // Smooth progress ticker for long silent phases
            val tickerJob = launch {
                while (pkg.isInstalling) {
                    delay(2000)
                    if (pkg.isInstalling && pkg.progressPercent in 65..91) {
                        val next = pkg.progressPercent + 1
                        pkg.progressPercent = next
                        if (pkg.statusText.contains("unpacking", ignoreCase = true) ||
                            pkg.statusText.contains("installing", ignoreCase = true) ||
                            !pkg.statusText.contains("%")) {
                            pkg.statusText = "Unpacking & configuring files ($next%)..."
                        }
                        emitProgress(pkg)
                    }
                }
            }

            try {
                // Priority 1: Immediately cancel any background essential tools install to avoid APT lock contention
                runtime.cancelEssentialToolsInstall()

                // Priority 2: Atomic lock cleaning and debconf reset
                runtime.cleanupAptLocks()
                runtime.runCommand(
                    "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* /var/lib/dpkg/updates/* /var/cache/debconf/*.lock /var/cache/debconf/*-lock 2>/dev/null; " +
                    "sudo dpkg --configure -a 2>/dev/null || true"
                )

                // Priority 3: Ensure pip.conf break-system-packages
                runtime.runCommand("sudo mkdir -p /etc && printf '[global]\\nbreak-system-packages = true\\n' | sudo tee /etc/pip.conf >/dev/null 2>&1 || true")

                Log.d(TAG, "Starting install command for ${pkg.id}: ${pkg.installCommand.take(80)}...")
                val result = runtime.runCommand(pkg.installCommand) { line ->
                    val parsed = parser.parseLine(line)
                    val now = System.currentTimeMillis()
                    if (parsed.percent != pkg.progressPercent || now - lastUpdateMs > 250) {
                        lastUpdateMs = now
                        pkg.progressPercent = parsed.percent
                        pkg.statusText = if (parsed.percent > 0) {
                            "${parsed.stage} (${parsed.percent}%)"
                        } else {
                            parsed.stage
                        }
                        emitProgress(pkg)
                        updateServiceNotification("Installing ${pkg.name} (${pkg.progressPercent}%)")
                    }
                }

                val isSuccessExit = result.first == 0
                val isCondaDetected = (pkg.id == "miniconda") && (
                    runtime.isCondaInstalled() || withContext(Dispatchers.IO) {
                        runtime.runCommand(
                            "[ -x /home/ubuntu/miniforge3/bin/conda ] || [ -x /home/ubuntu/miniconda3/bin/conda ] || [ -x /home/ubuntu/anaconda3/bin/conda ] || [ -x /root/miniconda3/bin/conda ] || [ -x /root/miniforge3/bin/conda ] || [ -x /root/anaconda3/bin/conda ] || [ -x /opt/conda/bin/conda ]"
                        ).first == 0
                    }
                )

                val prefs = context.getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)

                if (pkg.id == "miniconda") {
                    if (isCondaDetected) {
                        pkg.isInstalling = false
                        pkg.isInstalled = true
                        pkg.isActivated = true
                        pkg.progressPercent = 100
                        pkg.statusText = "Active & Ready (base)"
                        val currentSet = getCachedInstalledIds(prefs)
                        currentSet.add(pkg.id)
                        prefs.edit().putStringSet("installed_ids", currentSet).putBoolean("conda_active", true).apply()

                        emitProgress(pkg)
                        showToast("Miniconda3 installed and activated successfully! (base) is active.")

                        scope.launch {
                            runtime.configureCondaEnvironment()
                        }
                    } else {
                        pkg.isInstalling = false
                        pkg.isInstalled = false
                        pkg.isActivated = false
                        pkg.progressPercent = -1
                        pkg.statusText = if (isSuccessExit) "Install completed but binary missing" else "Install failed (Exit code: ${result.first})"
                        val currentSet = getCachedInstalledIds(prefs)
                        currentSet.remove(pkg.id)
                        prefs.edit().putStringSet("installed_ids", currentSet).putBoolean("conda_active", false).apply()

                        emitProgress(pkg, isFailed = true)
                        showToast(if (isSuccessExit) "Conda installation finished, but binary not found." else "Failed to install Conda. Exit code: ${result.first}")
                    }
                } else if (isSuccessExit) {
                    val checkResult = runtime.runCommand(pkg.checkInstalledCommand)
                    if (checkResult.first == 0) {
                        pkg.isInstalling = false
                        pkg.isInstalled = true
                        pkg.progressPercent = 100
                        pkg.statusText = "Installed and ready"
                        val currentSet = getCachedInstalledIds(prefs)
                        currentSet.add(pkg.id)
                        prefs.edit().putStringSet("installed_ids", currentSet).apply()

                        if (pkg.id == "xfce4-desktop" || pkg.id == "jupyterlab" || pkg.id == "jupyter" || pkg.category == PackageCategory.DESKTOP_APPS) {
                            try {
                                runtime.installCommandWrappers()
                                if (pkg.id == "jupyterlab" || pkg.id == "jupyter") {
                                    runtime.patchJupyterTemplatesForMobile()
                                }
                            } catch (ignored: Exception) {}
                        }

                        emitProgress(pkg)
                        showToast("${pkg.name} installed successfully.")
                    } else {
                        pkg.isInstalling = false
                        pkg.isInstalled = false
                        pkg.progressPercent = -1
                        pkg.statusText = "Install completed, check failed"
                        val currentSet = getCachedInstalledIds(prefs)
                        currentSet.remove(pkg.id)
                        prefs.edit().putStringSet("installed_ids", currentSet).apply()

                        emitProgress(pkg, isFailed = true)
                        showToast("${pkg.name} install process completed, but package check failed.")
                    }
                } else {
                    Log.e(TAG, "Install error for ${pkg.id} (code ${result.first}): ${result.second}")
                    pkg.isInstalling = false
                    pkg.isInstalled = false
                    pkg.progressPercent = -1
                    val errorSnippet = result.second.lines()
                        .map { it.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "").trim() }
                        .filter { it.isNotBlank() && (it.startsWith("E:") || it.startsWith("npm error") || it.contains("error:", ignoreCase = true) || it.contains("failed", ignoreCase = true) || it.contains("not found", ignoreCase = true)) }
                        .lastOrNull()?.take(70)
                        ?: result.second.lines().map { it.trim() }.filter { it.isNotBlank() }.lastOrNull()?.take(70)

                    pkg.statusText = if (!errorSnippet.isNullOrBlank()) {
                        "Failed (${result.first}): $errorSnippet"
                    } else {
                        "Install failed (Exit code: ${result.first})"
                    }

                    emitProgress(pkg, isFailed = true)
                    showToast("Failed to install ${pkg.name}. Exit code: ${result.first}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during install of ${pkg.name}: ${e.message}", e)
                pkg.isInstalling = false
                pkg.progressPercent = -1
                pkg.statusText = "Error: ${e.message}"
                emitProgress(pkg, isFailed = true)
                showToast("Error installing ${pkg.name}: ${e.message}")
            } finally {
                tickerJob.cancel()
                currentPackage = null
                processNextInQueue()
            }
        }
    }

    private fun processNextInQueue() {
        synchronized(lock) {
            if (installQueue.isNotEmpty()) {
                val nextPkg = installQueue.removeFirst()
                // Update queue positions for remaining items
                installQueue.forEachIndexed { index, queuedPkg ->
                    val pos = index + 1
                    queuedPkg.statusText = "Queued (Pending #$pos in line)"
                    val update = InstallProgressUpdate(
                        packageId = queuedPkg.id,
                        percent = -1,
                        stage = queuedPkg.statusText,
                        isInstalling = true,
                        isInstalled = false,
                        isQueued = true,
                        queuePosition = pos
                    )
                    activeStates[queuedPkg.id] = update
                    _progressEvents.tryEmit(update)
                }
                currentPackage = nextPkg
                executeInstall(nextPkg)
            } else {
                isQueueProcessing = false
                currentPackage = null
                releaseWakeLock()
                MobileLinuxApp.notifyScreenKeepOnChanged()
                updateServiceNotification(null)
            }
        }
    }

    /**
     * Executes clean uninstall of a package.
     */
    fun enqueueUninstall(pkg: LinuxPackage, onFinished: ((Boolean) -> Unit)? = null) {
        if (isUninstallRunning || isQueueProcessing) {
            showToast("Please wait until active operations finish...")
            return
        }

        isUninstallRunning = true
        pkg.isUninstalling = true
        pkg.progressPercent = 10
        pkg.statusText = "Starting uninstallation..."

        val initialUpdate = InstallProgressUpdate(
            packageId = pkg.id,
            percent = 10,
            stage = pkg.statusText,
            isInstalling = false,
            isInstalled = false,
            isUninstalling = true
        )
        activeStates[pkg.id] = initialUpdate
        _progressEvents.tryEmit(initialUpdate)

        acquireWakeLock()
        startLinuxServiceIfNeeded()
        updateServiceNotification("Uninstalling ${pkg.name}...")

        scope.launch {
            val parser = PackageProgressParser(pkg.name)
            var lastUpdateMs = 0L

            try {
                runtime.cleanupAptLocks()
                if (pkg.id != "jupyterlab" && pkg.id != "miniconda") {
                    runtime.runCommand(
                        "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* /var/lib/dpkg/updates/* /var/cache/debconf/*.lock /var/cache/debconf/*-lock 2>/dev/null; " +
                        "sudo dpkg --configure -a 2>/dev/null || true",
                        timeoutSeconds = 15L
                    )
                }

                if (pkg.id == "miniconda") {
                    runtime.purgeCondaFromHost()
                }

                val uninstallCmd = PackageRepository.getUninstallCommand(pkg)
                Log.d(TAG, "Executing uninstall: $uninstallCmd")

                val result = runtime.runCommand(uninstallCmd) { line ->
                    val parsed = parser.parseLine(line)
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMs > 250) {
                        lastUpdateMs = now
                        pkg.progressPercent = parsed.percent
                        pkg.statusText = if (parsed.percent > 0) "Purging... (${parsed.percent}%)" else "Removing files..."
                        emitProgress(pkg, isUninstalling = true)
                    }
                }

                val prefs = context.getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                val currentSet = getCachedInstalledIds(prefs)
                currentSet.remove(pkg.id)

                if (pkg.id == "miniconda") {
                    prefs.edit().putStringSet("installed_ids", currentSet).putBoolean("conda_active", false).apply()
                    pkg.isActivated = false
                } else {
                    prefs.edit().putStringSet("installed_ids", currentSet).apply()
                }

                pkg.isInstalled = false
                pkg.isUninstalling = false
                pkg.progressPercent = -1
                pkg.statusText = ""

                val success = result.first == 0
                emitProgress(pkg, isUninstalling = false)
                showToast("${pkg.name} uninstalled and cleaned successfully.")
                onFinished?.invoke(success)
            } catch (e: Exception) {
                Log.e(TAG, "Uninstall failed for ${pkg.id}: ${e.message}", e)
                pkg.isUninstalling = false
                pkg.progressPercent = -1
                pkg.statusText = "Uninstall error: ${e.message}"
                emitProgress(pkg, isFailed = true, isUninstalling = false)
                showToast("Failed to uninstall ${pkg.name}: ${e.message}")
                onFinished?.invoke(false)
            } finally {
                isUninstallRunning = false
                releaseWakeLock()
                updateServiceNotification(null)
            }
        }
    }

    private fun emitProgress(pkg: LinuxPackage, isFailed: Boolean = false, isUninstalling: Boolean = false) {
        val update = InstallProgressUpdate(
            packageId = pkg.id,
            percent = pkg.progressPercent,
            stage = pkg.statusText,
            isInstalling = pkg.isInstalling,
            isInstalled = pkg.isInstalled,
            isUninstalling = isUninstalling || pkg.isUninstalling,
            isFailed = isFailed,
            isActivated = pkg.isActivated,
            isQueued = false
        )
        activeStates[pkg.id] = update
        _progressEvents.tryEmit(update)
    }

    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(2 * 60 * 60 * 1000L) // 2 hours max safety limit
                Log.d(TAG, "Acquired installer WakeLock")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire installer WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "Released installer WakeLock")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release installer WakeLock: ${e.message}")
        }
    }

    private fun startLinuxServiceIfNeeded() {
        try {
            LinuxService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure LinuxService is started: ${e.message}")
        }
    }

    private fun updateServiceNotification(status: String?) {
        try {
            LinuxService.updateCustomStatus(context, status)
        } catch (ignored: Exception) {}
    }

    private fun showToast(msg: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun getCachedInstalledIds(prefs: SharedPreferences): MutableSet<String> {
        val raw = prefs.getStringSet("installed_ids", null) ?: emptySet()
        return HashSet(raw)
    }
}
