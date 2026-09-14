package com.mobilelinux.ide.extension

import android.content.Context
import android.util.Log
import com.mobilelinux.runtime.UbuntuRuntime
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages persistent background interactive kernel sessions (e.g. Python for Jupyter Notebooks)
 * running inside the Ubuntu PRoot userland environment.
 */
class KernelSessionManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "KernelSessionManager"

        @Volatile
        private var instance: KernelSessionManager? = null

        fun getInstance(context: Context): KernelSessionManager {
            return instance ?: synchronized(this) {
                instance ?: KernelSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessions = ConcurrentHashMap<String, ActiveKernelSession>()

    class ActiveKernelSession(
        val sessionId: String,
        val process: Process,
        val writer: BufferedWriter,
        val reader: BufferedReader
    ) {
        val pendingCallbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()
        var isAlive: Boolean = true
    }

    private fun ensureKernelScriptInstalled(): Boolean {
        try {
            val runtime = UbuntuRuntime.getInstance(context)
            val rootfs = runtime.rootfsDir
            val target = File(rootfs, "usr/local/bin/kernel_runner.py")
            val assetBytes = context.assets.open("scripts/kernel_runner.py").use { it.readBytes() }
            if (!target.exists() || target.length() != assetBytes.size.toLong()) {
                target.parentFile?.mkdirs()
                target.writeBytes(assetBytes)
                target.setReadable(true, false)
                target.setExecutable(true, false)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install kernel_runner.py to rootfs", e)
            return false
        }
    }

    fun startSession(sessionId: String, onReady: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                ensureKernelScriptInstalled()
                val runtime = UbuntuRuntime.getInstance(context)
                val process = runtime.createSessionProcess(
                    sessionId = "kernel_$sessionId",
                    execCommand = "python3 -u /usr/local/bin/kernel_runner.py"
                )

                val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))

                val session = ActiveKernelSession(sessionId, process, writer, reader)
                sessions[sessionId] = session

                // Start reader loop
                scope.launch {
                    try {
                        var line: String? = null
                        while (session.isAlive && reader.readLine().also { line = it } != null) {
                            val lineStr = line?.trim() ?: continue
                            if (lineStr.isEmpty()) continue
                            try {
                                val json = JSONObject(lineStr)
                                val id = json.optString("id", "")
                                val callback = session.pendingCallbacks.remove(id)
                                callback?.invoke(json)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse kernel line: $lineStr", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Kernel reader closed for $sessionId: ${e.message}")
                    } finally {
                        session.isAlive = false
                        sessions.remove(sessionId)
                    }
                }

                onReady(true, "Kernel ready")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start kernel session $sessionId", e)
                onReady(false, e.message ?: "Failed to start kernel")
            }
        }
    }

    fun execute(sessionId: String, code: String, reqId: String, onResult: (JSONObject) -> Unit) {
        val session = sessions[sessionId]
        if (session == null || !session.isAlive) {
            // Auto-start session if needed
            startSession(sessionId) { ok, msg ->
                if (ok) {
                    execute(sessionId, code, reqId, onResult)
                } else {
                    val err = JSONObject()
                    err.put("id", reqId)
                    err.put("status", "error")
                    err.put("error", "Kernel session start failed: $msg")
                    onResult(err)
                }
            }
            return
        }

        session.pendingCallbacks[reqId] = onResult
        scope.launch {
            try {
                val req = JSONObject()
                req.put("action", "execute")
                req.put("id", reqId)
                req.put("code", code)
                synchronized(session.writer) {
                    session.writer.write(req.toString())
                    session.writer.newLine()
                    session.writer.flush()
                }
            } catch (e: Exception) {
                session.pendingCallbacks.remove(reqId)
                val err = JSONObject()
                err.put("id", reqId)
                err.put("status", "error")
                err.put("error", e.message ?: "Write failed")
                onResult(err)
            }
        }
    }

    fun restart(sessionId: String, reqId: String, onResult: (JSONObject) -> Unit) {
        val session = sessions[sessionId]
        if (session == null || !session.isAlive) {
            startSession(sessionId) { ok, msg ->
                val res = JSONObject()
                res.put("id", reqId)
                res.put("status", if (ok) "ok" else "error")
                onResult(res)
            }
            return
        }

        session.pendingCallbacks[reqId] = onResult
        scope.launch {
            try {
                val req = JSONObject()
                req.put("action", "restart")
                req.put("id", reqId)
                synchronized(session.writer) {
                    session.writer.write(req.toString())
                    session.writer.newLine()
                    session.writer.flush()
                }
            } catch (e: Exception) {
                session.pendingCallbacks.remove(reqId)
                val err = JSONObject()
                err.put("id", reqId)
                err.put("status", "error")
                onResult(err)
            }
        }
    }

    fun stop(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        session.isAlive = false
        try {
            session.writer.write("{\"action\":\"exit\"}\n")
            session.writer.flush()
        } catch (ignored: Exception) {}
        try {
            session.process.destroy()
        } catch (ignored: Exception) {}
    }

    data class ManagedProcess(
        val processId: String,
        val process: Process,
        val writer: BufferedWriter,
        val reader: BufferedReader,
        val errorReader: BufferedReader
    ) {
        var isAlive: Boolean = true
    }

    private val managedProcesses = ConcurrentHashMap<String, ManagedProcess>()

    fun spawnProcess(
        processId: String,
        command: String,
        workingDir: String? = null,
        onOutput: (processId: String, event: String, data: String) -> Unit
    ) {
        scope.launch {
            try {
                val runtime = UbuntuRuntime.getInstance(context)
                val process = runtime.createSessionProcess(
                    sessionId = "proc_$processId",
                    execCommand = command,
                    initialWorkingDir = workingDir
                )

                val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                val errReader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))

                val mp = ManagedProcess(processId, process, writer, reader, errReader)
                managedProcesses[processId] = mp

                onOutput(processId, "spawned", "Process started")

                // Stdout reader
                scope.launch {
                    try {
                        var line: String? = null
                        while (mp.isAlive && reader.readLine().also { line = it } != null) {
                            val l = line ?: continue
                            onOutput(processId, "stdout", l)
                        }
                    } catch (ignored: Exception) {
                    } finally {
                        if (mp.isAlive) {
                            val code = try { process.exitValue() } catch (_: Exception) { 0 }
                            onOutput(processId, "exit", code.toString())
                            mp.isAlive = false
                            managedProcesses.remove(processId)
                        }
                    }
                }

                // Stderr reader
                scope.launch {
                    try {
                        var line: String? = null
                        while (mp.isAlive && errReader.readLine().also { line = it } != null) {
                            val l = line ?: continue
                            onOutput(processId, "stderr", l)
                        }
                    } catch (ignored: Exception) {}
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to spawn process $processId: ${e.message}", e)
                onOutput(processId, "error", e.message ?: "Failed to spawn process")
            }
        }
    }

    fun writeProcessStdin(processId: String, data: String): Boolean {
        val mp = managedProcesses[processId] ?: return false
        return try {
            synchronized(mp.writer) {
                mp.writer.write(data)
                mp.writer.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun killProcess(processId: String): Boolean {
        val mp = managedProcesses.remove(processId) ?: return false
        mp.isAlive = false
        try {
            mp.process.destroy()
        } catch (ignored: Exception) {}
        return true
    }

    fun stopAll() {
        for (id in sessions.keys) {
            stop(id)
        }
        for (id in managedProcesses.keys) {
            killProcess(id)
        }
    }
}
