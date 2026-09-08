package com.mobilelinux.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mobilelinux.R

/**
 * Movable, resizable, and collapsible floating overlay dock for Desktop Mode.
 * Allows one-tap left click, right click, scroll up/down, keyboard toggle, and clean exit.
 */
class FloatingDesktopControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onLeftClick()
        fun onLeftClickHold(isHeld: Boolean)
        fun onRightClick()
        fun onScrollUp()
        fun onScrollDown()
        fun onToggleKeyboard()
        fun onExitDesktop()
    }

    var listener: Listener? = null

    private val dockContainer: LinearLayout
    private val expandedLayout: LinearLayout
    private val btnDragHandle: View
    private val btnLeftClick: TextView
    private val btnRightClick: TextView
    private val btnScrollUp: TextView
    private val btnScrollDown: TextView
    private val btnToggleKeyboard: TextView
    private val btnExitDesktop: TextView
    private val btnToggleCollapse: TextView

    private var isCollapsed = false
    private var isLeftClickHeld = false

    // Drag tracking variables
    private var dX = 0f
    private var dY = 0f
    private var isDragging = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_floating_desktop_controls, this, true)

        dockContainer = findViewById(R.id.layout_dock_container)
        expandedLayout = findViewById(R.id.layout_expanded_buttons)
        btnDragHandle = findViewById(R.id.btn_drag_handle)
        btnLeftClick = findViewById(R.id.btn_left_click)
        btnRightClick = findViewById(R.id.btn_right_click)
        btnScrollUp = findViewById(R.id.btn_scroll_up)
        btnScrollDown = findViewById(R.id.btn_scroll_down)
        btnToggleKeyboard = findViewById(R.id.btn_toggle_keyboard)
        btnExitDesktop = findViewById(R.id.btn_exit_desktop)
        btnToggleCollapse = findViewById(R.id.btn_toggle_collapse)

        setupDragHandling()
        setupClickListeners()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandling() {
        btnDragHandle.setOnTouchListener { _, event ->
            val parentView = parent as? ViewGroup ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = this@FloatingDesktopControlsView.x - event.rawX
                    dY = this@FloatingDesktopControlsView.y - event.rawY
                    isDragging = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        var newX = event.rawX + dX
                        var newY = event.rawY + dY

                        val maxX = (parentView.width - this@FloatingDesktopControlsView.width).toFloat().coerceAtLeast(0f)
                        val maxY = (parentView.height - this@FloatingDesktopControlsView.height).toFloat().coerceAtLeast(0f)

                        newX = newX.coerceIn(0f, maxX)
                        newY = newY.coerceIn(0f, maxY)

                        this@FloatingDesktopControlsView.x = newX
                        this@FloatingDesktopControlsView.y = newY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        // Left Click button supports both normal tap and long-press hold to drag
        btnLeftClick.setOnClickListener {
            if (isLeftClickHeld) {
                // Release hold if already held
                isLeftClickHeld = false
                btnLeftClick.isSelected = false
                btnLeftClick.text = "L-Click"
                listener?.onLeftClickHold(false)
            } else {
                listener?.onLeftClick()
            }
        }

        btnLeftClick.setOnLongClickListener {
            isLeftClickHeld = !isLeftClickHeld
            btnLeftClick.isSelected = isLeftClickHeld
            btnLeftClick.text = if (isLeftClickHeld) "HOLDING" else "L-Click"
            listener?.onLeftClickHold(isLeftClickHeld)
            true
        }

        btnRightClick.setOnClickListener {
            listener?.onRightClick()
        }

        btnScrollUp.setOnClickListener {
            listener?.onScrollUp()
        }

        btnScrollDown.setOnClickListener {
            listener?.onScrollDown()
        }

        btnToggleKeyboard.setOnClickListener {
            listener?.onToggleKeyboard()
        }

        btnExitDesktop.setOnClickListener {
            listener?.onExitDesktop()
        }

        btnToggleCollapse.setOnClickListener {
            toggleCollapse()
        }
    }

    private fun toggleCollapse() {
        isCollapsed = !isCollapsed
        expandedLayout.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        btnToggleCollapse.text = if (isCollapsed) "▶" else "◀"
    }

    fun releaseHold() {
        if (isLeftClickHeld) {
            isLeftClickHeld = false
            btnLeftClick.isSelected = false
            btnLeftClick.text = "L-Click"
            listener?.onLeftClickHold(false)
        }
    }
}
