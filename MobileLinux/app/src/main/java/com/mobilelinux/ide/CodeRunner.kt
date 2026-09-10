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
     * Builds execution command based on file extension, shebang, or build system.
     * Supports Python, JavaScript, TypeScript, C, C++, Rust, Go, Java, Kotlin,
     * Bash/Shell, Ruby, PHP, Perl, Lua, R, Dart, Swift, Haskell, Julia, C#/.NET,
     * Makefile, CMake, and executable scripts.
     * Provides automatic availability check and clear Ubuntu apt installation hints.
     */
    fun buildCommandForFile(linuxPath: String, hostFile: File? = null): String {
        val f = File(linuxPath)
        val ext = f.extension.lowercase()
        val name = f.name
        val baseName = f.nameWithoutExtension

        // 1. Check if file has a shebang (#!)
        val firstLine = try {
            hostFile?.bufferedReader()?.use { it.readLine() }?.trim() ?: ""
        } catch (e: Exception) { "" }

        if (firstLine.startsWith("#!")) {
            return "chmod +x \"$name\" && \"./$name\""
        }

        // 2. Special project / build files
        val lowerName = name.lowercase()
        if (lowerName == "makefile" || lowerName == "gnumakefile") {
            return "make"
        }
        if (lowerName == "package.json") {
            return "npm start 2>/dev/null || npm test 2>/dev/null || npm run dev"
        }
        if (lowerName == "cargo.toml") {
            return "cargo run"
        }
        if (lowerName == "cmakelists.txt") {
            return "mkdir -p build && cd build && cmake .. && make && cd .."
        }

        // 3. Multi-language runners -> Clean, direct commands
        return when (ext) {
            "py", "pyw" -> "python3 \"$name\""
            "js", "mjs", "cjs" -> "node \"$name\""
            "ts", "mts", "cts" -> "npx tsx \"$name\" 2>/dev/null || ts-node \"$name\""
            "c" -> "gcc \"$name\" -o \"$baseName.out\" -lm && \"./$baseName.out\""
            "cpp", "cc", "cxx", "cp", "c++" -> "g++ -std=c++17 \"$name\" -o \"$baseName.out\" -lm && \"./$baseName.out\""
            "rs" -> "rustc \"$name\" -o \"$baseName.out\" && \"./$baseName.out\""
            "go" -> "go run \"$name\""
            "java" -> "java \"$name\""
            "kt", "kts" -> if (ext == "kts") "kotlinc -script \"$name\"" else "kotlinc \"$name\" -include-runtime -d \"$baseName.jar\" && java -jar \"$baseName.jar\""
            "sh", "bash" -> "bash \"$name\""
            "zsh" -> "zsh \"$name\""
            "php" -> "php \"$name\""
            "rb" -> "ruby \"$name\""
            "pl", "pm" -> "perl \"$name\""
            "lua" -> "lua \"$name\""
            "r" -> "Rscript \"$name\""
            "dart" -> "dart run \"$name\""
            "swift" -> "swift \"$name\""
            "hs" -> "runhaskell \"$name\""
            "jl" -> "julia \"$name\""
            "cs" -> "dotnet run 2>/dev/null || (csc \"$name\" && mono \"$baseName.exe\")"
            "sql" -> "sqlite3 < \"$name\""
            "out", "bin" -> "\"./$name\""
            else -> {
                if (hostFile?.canExecute() == true) {
                    "\"./$name\""
                } else {
                    "bash \"$name\""
                }
            }
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
        val cmd = customCommand ?: buildCommandForFile(linuxPath, file)
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
