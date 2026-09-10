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

        // 3. Multi-language runners with smart availability checks and apt installation hints
        return when (ext) {
            "py", "pyw" -> {
                "if command -v python3 >/dev/null 2>&1; then python3 \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m python3 is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y python3\\033[0m\"; fi"
            }
            "js", "mjs", "cjs" -> {
                "if command -v node >/dev/null 2>&1; then node \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m Node.js is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y nodejs npm\\033[0m\"; fi"
            }
            "ts", "mts", "cts" -> {
                "if command -v tsx >/dev/null 2>&1; then tsx \"$name\"; elif command -v ts-node >/dev/null 2>&1; then ts-node \"$name\"; elif command -v npx >/dev/null 2>&1; then npx -y tsx \"$name\" 2>/dev/null || npx -y ts-node \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m TypeScript runner not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y nodejs npm && npm install -g tsx\\033[0m\"; fi"
            }
            "c" -> {
                "if command -v gcc >/dev/null 2>&1; then gcc \"$name\" -o \"$baseName.out\" -lm && \"./$baseName.out\"; else echo -e \"\\033[1;31m[Error]\\033[0m gcc compiler is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y build-essential\\033[0m\"; fi"
            }
            "cpp", "cc", "cxx", "cp", "c++" -> {
                "if command -v g++ >/dev/null 2>&1; then g++ -std=c++17 \"$name\" -o \"$baseName.out\" -lm && \"./$baseName.out\"; else echo -e \"\\033[1;31m[Error]\\033[0m g++ compiler is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y build-essential\\033[0m\"; fi"
            }
            "rs" -> {
                "if [ -f \"Cargo.toml\" ] && command -v cargo >/dev/null 2>&1; then cargo run; elif command -v rustc >/dev/null 2>&1; then rustc \"$name\" -o \"$baseName.out\" && \"./$baseName.out\"; else echo -e \"\\033[1;31m[Error]\\033[0m rustc is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y rustc cargo\\033[0m\"; fi"
            }
            "go" -> {
                "if command -v go >/dev/null 2>&1; then go run \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m Go is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y golang\\033[0m\"; fi"
            }
            "java" -> {
                "if command -v java >/dev/null 2>&1; then java \"$name\" 2>/dev/null || (javac \"$name\" && java \"$baseName\"); else echo -e \"\\033[1;31m[Error]\\033[0m Java JDK is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y default-jdk\\033[0m\"; fi"
            }
            "kt", "kts" -> {
                if (ext == "kts") {
                    "if command -v kotlinc >/dev/null 2>&1; then kotlinc -script \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m Kotlin is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y kotlin default-jdk\\033[0m\"; fi"
                } else {
                    "if command -v kotlinc >/dev/null 2>&1; then kotlinc \"$name\" -include-runtime -d \"$baseName.jar\" && java -jar \"$baseName.jar\"; else echo -e \"\\033[1;31m[Error]\\033[0m Kotlin is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y kotlin default-jdk\\033[0m\"; fi"
                }
            }
            "sh", "bash" -> "bash \"$name\""
            "zsh" -> "zsh \"$name\" 2>/dev/null || bash \"$name\""
            "php" -> {
                "if command -v php >/dev/null 2>&1; then php \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m php is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y php-cli\\033[0m\"; fi"
            }
            "rb" -> {
                "if command -v ruby >/dev/null 2>&1; then ruby \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m ruby is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y ruby-full\\033[0m\"; fi"
            }
            "pl", "pm" -> {
                "if command -v perl >/dev/null 2>&1; then perl \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m perl is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y perl\\033[0m\"; fi"
            }
            "lua" -> {
                "if command -v lua >/dev/null 2>&1; then lua \"$name\"; elif command -v lua5.4 >/dev/null 2>&1; then lua5.4 \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m lua is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y lua5.4\\033[0m\"; fi"
            }
            "r" -> {
                "if command -v Rscript >/dev/null 2>&1; then Rscript \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m R is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt update && sudo apt install -y r-base\\033[0m\"; fi"
            }
            "dart" -> {
                "if command -v dart >/dev/null 2>&1; then dart run \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m Dart is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y dart\\033[0m\"; fi"
            }
            "swift" -> {
                "if command -v swift >/dev/null 2>&1; then swift \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m Swift is not installed.\\033[0m\"; fi"
            }
            "hs" -> {
                "if command -v runhaskell >/dev/null 2>&1; then runhaskell \"$name\"; elif command -v ghc >/dev/null 2>&1; then ghc \"$name\" -o \"$baseName.out\" && \"./$baseName.out\"; else echo -e \"\\033[1;31m[Error]\\033[0m GHC/Haskell is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y ghc\\033[0m\"; fi"
            }
            "jl" -> {
                "if command -v julia >/dev/null 2>&1; then julia \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m Julia is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y julia\\033[0m\"; fi"
            }
            "cs" -> {
                "if command -v dotnet >/dev/null 2>&1; then dotnet run; elif command -v csc >/dev/null 2>&1 && command -v mono >/dev/null 2>&1; then csc \"$name\" && mono \"$baseName.exe\"; else echo -e \"\\033[1;31m[Error]\\033[0m .NET / Mono is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y mono-complete\\033[0m\"; fi"
            }
            "sql" -> {
                "if command -v sqlite3 >/dev/null 2>&1; then sqlite3 < \"$name\"; else echo -e \"\\033[1;31m[Error]\\033[0m sqlite3 is not installed.\\n\\033[1;33m[Hint]\\033[0m Run: \\033[1;32msudo apt install -y sqlite3\\033[0m\"; fi"
            }
            "out", "bin" -> {
                "chmod +x \"$name\" && \"./$name\""
            }
            else -> {
                if (hostFile?.canExecute() == true) {
                    "chmod +x \"$name\" && \"./$name\""
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
