package com.mobilelinux.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * High-performance, native RFB (VNC 3.8) Canvas View for MobileLinux Desktop Mode.
 * Renders the Linux XFCE4 desktop directly from TigerVNC over localhost:5901 with
 * trackpad gesture simulation, OTG hardware mouse & keyboard support, and low-latency local streaming.
 */
class VncCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "VncCanvasView"
        const val DEFAULT_VNC_PORT = 5901
        const val DEFAULT_FB_WIDTH = 1280
        const val DEFAULT_FB_HEIGHT = 720
    }

    interface ConnectionListener {
        fun onConnected(width: Int, height: Int)
        fun onDisconnected(reason: String?)
        fun onError(error: String)
    }

    var connectionListener: ConnectionListener? = null

    // Framebuffer dimensions and Bitmap
    @Volatile var fbWidth: Int = DEFAULT_FB_WIDTH
        private set
    @Volatile var fbHeight: Int = DEFAULT_FB_HEIGHT
        private set

    @Volatile private var framebufferBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E")
        textSize = 15f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    // Cursor rendering
    @Volatile var cursorX: Float = DEFAULT_FB_WIDTH / 2f
    @Volatile var cursorY: Float = DEFAULT_FB_HEIGHT / 2f
    @Volatile var isCursorVisible: Boolean = true
    private val cursorPath = Path()
    private val cursorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val cursorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Networking & Threading
    private val isRunning = AtomicBoolean(false)
    private var networkThread: Thread? = null
    private var vncSocket: Socket? = null
    private var dataInput: DataInputStream? = null
    private var dataOutput: DataOutputStream? = null

    // Event queue for outgoing pointer/key messages
    private val outgoingEvents = LinkedBlockingQueue<ByteArray>(256)
    private var eventWriterThread: Thread? = null

    // Active button mask: Bit 0=L, Bit 1=M, Bit 2=R, Bit 3=WheelUp, Bit 4=WheelDown
    @Volatile private var currentButtonMask = 0

    // Touch & Trackpad gesture tracking
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartTime = 0L
    private var isTwoFingerGesture = false
    private var initialTwoFingerDist = 0f
    private var lastTwoFingerY = 0f

    // Sensitivity & Acceleration for touch trackpad
    var pointerSensitivity: Float = 1.35f

    // Status message
    @Volatile private var statusMessage: String = "Connecting to Linux Desktop..."

    // Destination render rect
    private val destRect = RectF()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        buildCursorPath()
    }

    private fun buildCursorPath() {
        // Standard OS mouse cursor arrow
        cursorPath.reset()
        cursorPath.moveTo(0f, 0f)
        cursorPath.lineTo(0f, 22f)
        cursorPath.lineTo(5.5f, 17f)
        cursorPath.lineTo(10.5f, 26f)
        cursorPath.lineTo(13.5f, 24.5f)
        cursorPath.lineTo(8.5f, 15.5f)
        cursorPath.lineTo(16f, 15.5f)
        cursorPath.close()
    }

    fun connect(host: String = "127.0.0.1", port: Int = DEFAULT_VNC_PORT) {
        if (isRunning.get()) return
        isRunning.set(true)
        statusMessage = "Connecting to Desktop on $host:$port..."
        invalidate()

        networkThread = Thread({
            runVncClient(host, port)
        }, "VncClientThread").apply { start() }

        eventWriterThread = Thread({
            runEventWriter()
        }, "VncEventWriterThread").apply { start() }
    }

    fun disconnect() {
        if (!isRunning.getAndSet(false)) return
        statusMessage = "Disconnecting..."
        try { vncSocket?.close() } catch (ignored: Exception) {}
        outgoingEvents.clear()
        networkThread?.interrupt()
        eventWriterThread?.interrupt()
        val bmp = framebufferBitmap
        framebufferBitmap = null
        try { bmp?.recycle() } catch (ignored: Exception) {}
        postInvalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disconnect()
    }

    private fun runEventWriter() {
        while (isRunning.get()) {
            try {
                val packet = outgoingEvents.take()
                val out = dataOutput ?: continue
                synchronized(out) {
                    out.write(packet)
                    out.flush()
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Event writer error: ${e.message}")
            }
        }
    }

    private fun runVncClient(host: String, port: Int) {
        try {
            // Retry loop for server startup readiness (TigerVNC might take ~1-2 seconds to boot)
            var socket: Socket? = null
            var attempts = 0
            while (isRunning.get() && attempts < 25) {
                try {
                    socket = Socket(host, port).apply {
                        tcpNoDelay = true
                        soTimeout = 0 // Infinite read timeout for frame stream
                    }
                    break
                } catch (e: Exception) {
                    attempts++
                    Thread.sleep(300)
                }
            }

            if (socket == null || !isRunning.get()) {
                throw Exception("Could not connect to VNC server on $host:$port after $attempts attempts.")
            }

            vncSocket = socket
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 65536))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 16384))
            dataInput = input
            dataOutput = output

            // 1. Protocol Version Handshake (RFB 003.008\n)
            val versionBuf = ByteArray(12)
            input.readFully(versionBuf)
            val serverVersion = String(versionBuf)
            Log.d(TAG, "VNC Server Version: $serverVersion")
            output.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
            output.flush()

            // 2. Security Handshake
            val numSecTypes = input.readUnsignedByte()
            if (numSecTypes == 0) {
                val reasonLen = input.readInt()
                val reasonBuf = ByteArray(reasonLen)
                input.readFully(reasonBuf)
                throw Exception("VNC Auth failed from server: ${String(reasonBuf)}")
            }
            val secTypes = ByteArray(numSecTypes)
            input.readFully(secTypes)

            // Prefer Type 1 (None) or Type 2 (VNC Auth)
            var chosenType = 1
            var hasNone = false
            for (t in secTypes) {
                if (t.toInt() == 1) hasNone = true
            }
            if (!hasNone && secTypes.contains(2)) {
                chosenType = 2
            }

            output.writeByte(chosenType)
            output.flush()

            if (chosenType == 1) {
                // For RFB 3.8, Type 1 also expects SecurityResult (4 bytes int: 0=OK)
                val secResult = input.readInt()
                if (secResult != 0) {
                    throw Exception("Security handshake failed. Code: $secResult")
                }
            } else if (chosenType == 2) {
                // VNC DES Auth (if fallback configured)
                val challenge = ByteArray(16)
                input.readFully(challenge)
                // In MobileLinux TigerVNC is started with -SecurityTypes None, so type 1 is standard
                throw Exception("Password authentication required but not configured.")
            }

            // 3. ClientInit (Shared flag: 1)
            output.writeByte(1)
            output.flush()

            // 4. ServerInit
            val width = input.readUnsignedShort()
            val height = input.readUnsignedShort()
            fbWidth = width
            fbHeight = height
            Log.d(TAG, "Desktop Initialized: ${width}x${height}")

            // Skip server pixel format (16 bytes)
            input.skipBytes(16)

            // Desktop Name
            val nameLen = input.readInt()
            val nameBuf = ByteArray(nameLen)
            input.readFully(nameBuf)
            val desktopName = String(nameBuf)
            Log.d(TAG, "Desktop Name: $desktopName")

            // Allocate or update Android Framebuffer Bitmap
            framebufferBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            cursorX = width / 2f
            cursorY = height / 2f
            statusMessage = ""
            post {
                connectionListener?.onConnected(width, height)
                invalidate()
            }

            // 5. SetPixelFormat (Request 32-bit ARGB8888, Little-Endian)
            sendSetPixelFormat(output)

            // 6. SetEncodings (Raw = 0, DesktopSize = -223)
            sendSetEncodings(output)

            // 7. Initial FramebufferUpdateRequest (Full screen, non-incremental = 0)
            sendFramebufferUpdateRequest(output, 0, 0, 0, width, height)

            // 8. Main Frame Processing Loop
            val pixelBuffer = IntArray(width * height)
            val rawByteBuffer = ByteArray(width * height * 4)

            while (isRunning.get()) {
                val msgType = input.readUnsignedByte()
                if (msgType == 0) {
                    // FramebufferUpdate
                    input.readByte() // padding
                    val numRects = input.readUnsignedShort()

                    for (r in 0 until numRects) {
                        val rx = input.readUnsignedShort()
                        val ry = input.readUnsignedShort()
                        val rw = input.readUnsignedShort()
                        val rh = input.readUnsignedShort()
                        val encoding = input.readInt()

                        if (encoding == 0) {
                            // Raw Encoding: 32-bit pixels (rw * rh * 4 bytes)
                            val bytesToRead = rw * rh * 4
                            input.readFully(rawByteBuffer, 0, bytesToRead)

                            var byteIdx = 0
                            val rectPixelCount = rw * rh
                            for (p in 0 until rectPixelCount) {
                                val b = rawByteBuffer[byteIdx].toInt() and 0xFF
                                val g = rawByteBuffer[byteIdx + 1].toInt() and 0xFF
                                val rCol = rawByteBuffer[byteIdx + 2].toInt() and 0xFF
                                pixelBuffer[p] = (0xFF shl 24) or (rCol shl 16) or (g shl 8) or b
                                byteIdx += 4
                            }

                            framebufferBitmap?.setPixels(pixelBuffer, 0, rw, rx, ry, rw, rh)
                        } else if (encoding == -223) {
                            // DesktopSize Pseudo-Encoding
                            fbWidth = rw
                            fbHeight = rh
                            framebufferBitmap = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                        } else {
                            Log.w(TAG, "Unsupported encoding: $encoding")
                        }
                    }

                    postInvalidate()

                    // Request next incremental update
                    sendFramebufferUpdateRequest(output, 1, 0, 0, fbWidth, fbHeight)
                } else if (msgType == 1) {
                    // SetColourMapEntries
                    input.readByte()
                    input.skipBytes(2) // firstColor
                    val numColors = input.readUnsignedShort()
                    input.skipBytes(numColors * 6)
                } else if (msgType == 2) {
                    // Bell
                } else if (msgType == 3) {
                    // ServerCutText (Clipboard from desktop)
                    input.skipBytes(3)
                    val cutLen = input.readInt()
                    input.skipBytes(cutLen)
                }
            }

        } catch (e: Exception) {
            if (isRunning.get()) {
                Log.e(TAG, "VNC Client loop error: ${e.message}", e)
                post {
                    statusMessage = "Connection lost: ${e.message}"
                    connectionListener?.onError(e.message ?: "Connection error")
                    invalidate()
                }
            }
        } finally {
            isRunning.set(false)
            try { vncSocket?.close() } catch (ignored: Exception) {}
            post { connectionListener?.onDisconnected(statusMessage) }
        }
    }

    private fun sendSetPixelFormat(out: DataOutputStream) {
        synchronized(out) {
            out.writeByte(0) // message-type 0 = SetPixelFormat
            out.writeByte(0); out.writeByte(0); out.writeByte(0) // 3 bytes padding
            out.writeByte(32) // bits-per-pixel
            out.writeByte(24) // depth
            out.writeByte(0)  // big-endian = 0 (little-endian)
            out.writeByte(1)  // true-colour = 1
            out.writeShort(255) // red-max
            out.writeShort(255) // green-max
            out.writeShort(255) // blue-max
            out.writeByte(16) // red-shift
            out.writeByte(8)  // green-shift
            out.writeByte(0)  // blue-shift
            out.writeByte(0); out.writeByte(0); out.writeByte(0) // 3 bytes padding
            out.flush()
        }
    }

    private fun sendSetEncodings(out: DataOutputStream) {
        synchronized(out) {
            out.writeByte(2) // message-type 2 = SetEncodings
            out.writeByte(0) // padding
            out.writeShort(2) // 2 encodings
            out.writeInt(0) // Raw
            out.writeInt(-223) // DesktopSize
            out.flush()
        }
    }

    private fun sendFramebufferUpdateRequest(out: DataOutputStream, incremental: Int, x: Int, y: Int, w: Int, h: Int) {
        synchronized(out) {
            out.writeByte(3) // message-type 3 = FramebufferUpdateRequest
            out.writeByte(incremental)
            out.writeShort(x)
            out.writeShort(y)
            out.writeShort(w)
            out.writeShort(h)
            out.flush()
        }
    }

    /**
     * Sends a VNC PointerEvent (type 5) with mouse coordinates and button mask.
     */
    fun sendPointer(buttonMask: Int, x: Int = cursorX.toInt(), y: Int = cursorY.toInt()) {
        val clampedX = x.coerceIn(0, (fbWidth - 1).coerceAtLeast(0))
        val clampedY = y.coerceIn(0, (fbHeight - 1).coerceAtLeast(0))
        currentButtonMask = buttonMask

        val packet = ByteArray(6)
        packet[0] = 5 // message-type 5
        packet[1] = buttonMask.toByte()
        packet[2] = ((clampedX shr 8) and 0xFF).toByte()
        packet[3] = (clampedX and 0xFF).toByte()
        packet[4] = ((clampedY shr 8) and 0xFF).toByte()
        packet[5] = (clampedY and 0xFF).toByte()

        outgoingEvents.offer(packet)
    }

    /**
     * Sends Left Click (press and immediate release) at current cursor position.
     */
    fun sendLeftClick() {
        sendPointer(currentButtonMask or 0x01)
        postDelayed({
            sendPointer(currentButtonMask and 0x01.inv())
        }, 30)
    }

    /**
     * Holds Left Click button down (for dragging windows/files/selection).
     */
    fun setLeftClickHold(isHeld: Boolean) {
        currentButtonMask = if (isHeld) {
            currentButtonMask or 0x01
        } else {
            currentButtonMask and 0x01.inv()
        }
        sendPointer(currentButtonMask)
    }

    /**
     * Sends Right Click (press and immediate release) at current cursor position.
     */
    fun sendRightClick() {
        sendPointer(currentButtonMask or 0x04)
        postDelayed({
            sendPointer(currentButtonMask and 0x04.inv())
        }, 30)
    }

    /**
     * Sends Mouse Scroll Wheel Up.
     */
    fun sendScrollUp() {
        sendPointer(currentButtonMask or 0x08)
        postDelayed({
            sendPointer(currentButtonMask and 0x08.inv())
        }, 30)
    }

    /**
     * Sends Mouse Scroll Wheel Down.
     */
    fun sendScrollDown() {
        sendPointer(currentButtonMask or 0x10)
        postDelayed({
            sendPointer(currentButtonMask and 0x10.inv())
        }, 30)
    }

    /**
     * Sends a VNC KeyEvent (type 4).
     */
    fun sendKey(down: Boolean, keysym: Int) {
        val packet = ByteArray(8)
        packet[0] = 4 // message-type 4
        packet[1] = if (down) 1 else 0
        packet[2] = 0; packet[3] = 0 // padding
        packet[4] = ((keysym shr 24) and 0xFF).toByte()
        packet[5] = ((keysym shr 16) and 0xFF).toByte()
        packet[6] = ((keysym shr 8) and 0xFF).toByte()
        packet[7] = (keysym and 0xFF).toByte()

        outgoingEvents.offer(packet)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp = framebufferBitmap
        if (bmp != null && !bmp.isRecycled) {
            // Compute scaling to fit landscape screen cleanly
            val scaleX = width.toFloat() / fbWidth
            val scaleY = height.toFloat() / fbHeight
            val scale = minOf(scaleX, scaleY)

            val renderW = fbWidth * scale
            val renderH = fbHeight * scale
            val offsetX = (width - renderW) / 2f
            val offsetY = (height - renderH) / 2f

            destRect.set(offsetX, offsetY, offsetX + renderW, offsetY + renderH)
            canvas.drawBitmap(bmp, null, destRect, bitmapPaint)

            // Draw Virtual Mouse Cursor
            if (isCursorVisible) {
                val screenCursorX = offsetX + (cursorX * scale)
                val screenCursorY = offsetY + (cursorY * scale)

                canvas.save()
                canvas.translate(screenCursorX, screenCursorY)
                canvas.drawPath(cursorPath, cursorFillPaint)
                canvas.drawPath(cursorPath, cursorStrokePaint)
                canvas.restore()
            }
        } else {
            // Draw loading/connecting message
            canvas.drawColor(Color.parseColor("#0D1117"))
            canvas.drawText(statusMessage, width / 2f, height / 2f, statusPaint)
        }
    }

    /**
     * Handles Touchscreen Laptop-Trackpad Simulation.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isRunning.get()) return super.onTouchEvent(event)

        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                touchStartTime = System.currentTimeMillis()
                isTwoFingerGesture = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 2) {
                    isTwoFingerGesture = true
                    initialTwoFingerDist = abs(event.getY(0) - event.getY(1))
                    lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTwoFingerGesture && pointerCount >= 2) {
                    // Two-finger drag -> Scroll Wheel
                    val currentAvgY = (event.getY(0) + event.getY(1)) / 2f
                    val deltaY = currentAvgY - lastTwoFingerY
                    if (abs(deltaY) > 20) {
                        if (deltaY > 0) sendScrollDown() else sendScrollUp()
                        lastTwoFingerY = currentAvgY
                    }
                } else if (pointerCount == 1) {
                    // Single-finger relative trackpad movement
                    val dx = (event.x - lastTouchX) * pointerSensitivity
                    val dy = (event.y - lastTouchY) * pointerSensitivity

                    cursorX = (cursorX + dx).coerceIn(0f, (fbWidth - 1).toFloat())
                    cursorY = (cursorY + dy).coerceIn(0f, (fbHeight - 1).toFloat())

                    lastTouchX = event.x
                    lastTouchY = event.y

                    sendPointer(currentButtonMask, cursorX.toInt(), cursorY.toInt())
                    postInvalidate()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (isTwoFingerGesture && pointerCount == 2) {
                    val duration = System.currentTimeMillis() - touchStartTime
                    // Two-finger tap -> Right Click
                    if (duration < 350) {
                        sendRightClick()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - touchStartTime
                val dist = abs(event.x - touchDownX) + abs(event.y - touchDownY)

                if (!isTwoFingerGesture && duration < 250 && dist < 20) {
                    // Single tap -> Left Click
                    sendLeftClick()
                }
                isTwoFingerGesture = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isTwoFingerGesture = false
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * Handles Physical OTG Mouse Input (Hover, Hardware Click, Scroll Wheel).
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isRunning.get()) return super.onGenericMotionEvent(event)

        if (event.source and InputDevice.SOURCE_MOUSE != 0 || event.source and InputDevice.SOURCE_STYLUS != 0) {
            val scaleX = width.toFloat() / fbWidth
            val scaleY = height.toFloat() / fbHeight
            val scale = minOf(scaleX, scaleY)

            val renderW = fbWidth * scale
            val renderH = fbHeight * scale
            val offsetX = (width - renderW) / 2f
            val offsetY = (height - renderH) / 2f

            val mouseX = ((event.x - offsetX) / scale).coerceIn(0f, (fbWidth - 1).toFloat())
            val mouseY = ((event.y - offsetY) / scale).coerceIn(0f, (fbHeight - 1).toFloat())

            cursorX = mouseX
            cursorY = mouseY

            var buttonMask = 0
            val buttons = event.buttonState
            if (buttons and MotionEvent.BUTTON_PRIMARY != 0) buttonMask = buttonMask or 0x01
            if (buttons and MotionEvent.BUTTON_SECONDARY != 0) buttonMask = buttonMask or 0x04
            if (buttons and MotionEvent.BUTTON_TERTIARY != 0) buttonMask = buttonMask or 0x02

            // Handle Scroll Wheel
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll > 0) buttonMask = buttonMask or 0x08
            else if (vScroll < 0) buttonMask = buttonMask or 0x10

            sendPointer(buttonMask, cursorX.toInt(), cursorY.toInt())
            postInvalidate()
            return true
        }

        return super.onGenericMotionEvent(event)
    }
}
