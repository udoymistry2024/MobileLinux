package com.mobilelinux.ide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mobilelinux.runtime.UbuntuRuntime
import com.mobilelinux.util.StorageHelper
import kotlinx.coroutines.*
import java.io.*

/**
 * Manages executing code files directly inside the Ubuntu PRoot Linux engine.
 * Streams stdout/stderr in real-time and supports interactive stdin input.
 */
class CodeRunner(private val context: Context) {

    private val TAG = "CodeRunner"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentProcess: Process? = null
    private var processWriter: BufferedWriter? = null
    private var runnerJob: Job? = null

    var isRunning: Boolean = false
        private set

    /**
     * Translates an Android host File path to its path inside the Ubuntu container.
     */
    fun toLinuxPath(file: File): String {
        val rootfs = UbuntuRuntime.getInstance(context).rootfsDir.absolutePath
        val path = file.absolutePath

        if (path.startsWith(rootfs)) {
            var rel = path.removePrefix(rootfs)
            if (!rel.startsWith("/")) rel = "/$rel"
            return rel
        }

        // Shared /sdcard path
        val preferredShared = StorageHelper.getPreferredSharedDir(context).absolutePath
        if (path.startsWith(preferredShared)) {
            val rel = path.removePrefix(preferredShared)
            return "/sdcard/MobileLinux$rel"
        }

        val sdcard = android.os.Environment.getExternalStorageDirectory()?.absolutePath
        if (sdcard != null && path.startsWith(sdcard)) {
            val rel = path.removePrefix(sdcard)
            return "/sdcard$rel"
        }

        return path
    }

    /**
     * Builds execution command based on file extension.
     */
    fun buildCommandForFile(linuxPath: String): String {
        val f = File(linuxPath)
        val ext = f.extension.lowercase()
        val name = f.name
        val baseName = f.nameWithoutExtension

        return when (ext) {
            "py" -> "python3 \"$name\""
            "js", "mjs" -> "node \"$name\""
            "cjs" -> "node \"$name\""
            "ts" -> "ts-node \"$name\" 2>/dev/null || npx -y ts-node \"$name\""
            "c" -> "gcc \"$name\" -o \"$baseName.out\" -lm && \"./$baseName.out\""
            "cpp", "cc", "cxx" -> "g++ \"$name\" -o \"$baseName.out\" -lm && \"./$baseName.out\""
            "rs" -> "rustc \"$name\" -o \"$baseName.out\" && \"./$baseName.out\""
            "go" -> "go run \"$name\""
            "sh", "bash" -> "bash \"$name\""
            "php" -> "php \"$name\""
            else -> "bash \"$name\""
        }
    }

    /**
     * Runs the specified file in Ubuntu PRoot.
     */
    fun runFile(
        file: File,
        customCommand: String? = null,
        onStart: (cmd: String) -> Unit,
        onOutput: (text: String) -> Unit,
        onFinished: (exitCode: Int, durationMs: Long) -> Unit
    ) {
        stop()

        val linuxPath = toLinuxPath(file)
        val linuxDir = File(linuxPath).parent ?: "/home/ubuntu"
        val cmd = customCommand ?: buildCommandForFile(linuxPath)
        val fullShellCommand = "cd \"$linuxDir\" && $cmd"

        isRunning = true
        onStart(cmd)

        runnerJob = CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            var exitCode = -1

            try {
                val runtime = UbuntuRuntime.getInstance(context)
                val sessionId = "ide_run_${System.currentTimeMillis() % 100000}"
                val process = runtime.createSessionProcess(
                    sessionId = sessionId,
                    cols = 80,
                    rows = 24,
                    execCommand = fullShellCommand
                )
                currentProcess = process
                processWriter = BufferedWriter(OutputStreamWriter(process.outputStream))

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val buffer = CharArray(1024)
                var readChars = 0

                while (process.isAlive && reader.read(buffer).also { readChars = it } != -1) {
                    val text = String(buffer, 0, readChars)
                    mainHandler.post { onOutput(text) }
                }

                // Read remaining output
                while (reader.read(buffer).also { readChars = it } != -1) {
                    val text = String(buffer, 0, readChars)
                    mainHandler.post { onOutput(text) }
                }

                exitCode = process.waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Execution error: ${e.message}", e)
                mainHandler.post {
                    onOutput("\n[Runtime Error: ${e.localizedMessage}]\n")
                }
                exitCode = -1
            } finally {
                isRunning = false
                currentProcess = null
                processWriter = null
                val duration = System.currentTimeMillis() - startTime
                mainHandler.post { onFinished(exitCode, duration) }
            }
        }
    }

    /**
     * Sends interactive user input (stdin) to the running process.
     */
    fun sendInput(text: String): Boolean {
        return try {
            val writer = processWriter ?: return false
            writer.write(text)
            if (!text.endsWith("\n")) {
                writer.write("\n")
            }
            writer.flush()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send input to process: ${e.message}")
            false
        }
    }

    /**
     * Terminates the currently running process.
     */
    fun stop() {
        try {
            runnerJob?.cancel()
            runnerJob = null
            currentProcess?.destroyForcibly()
            currentProcess = null
            processWriter = null
            isRunning = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping runner: ${e.message}")
        }
    }
}
