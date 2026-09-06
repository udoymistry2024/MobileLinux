package com.mobilelinux.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.mobilelinux.terminal.SpecialKey
import com.google.android.material.button.MaterialButton

/**
 * Horizontal scrollable extra keys bar shown above the software keyboard.
 * Provides quick access to Ctrl, Alt, Shift, Tab, ESC, arrow keys, and common symbols.
 *
 * Sticky modifier buttons (Ctrl, Alt, Shift) change color when active,
 * enabling combination keys like Ctrl+C, Ctrl+Z, Shift+Tab across software & hardware keyboards.
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var onKeyListener: ((KeyAction) -> Unit)? = null

    sealed class KeyAction {
        data class SpecialKeyAction(val key: SpecialKey) : KeyAction()
        data class TextAction(val text: String) : KeyAction()
        data class CtrlAction(val char: Char) : KeyAction()
        object ToggleCtrl : KeyAction()
        object ToggleAlt : KeyAction()
        object ToggleShift : KeyAction()
        object PasteAction : KeyAction()
    }

    private val keyDefs = listOf(
        KeyDef("ESC") { KeyAction.SpecialKeyAction(SpecialKey.ESC) },
        KeyDef("Tab") { KeyAction.SpecialKeyAction(SpecialKey.TAB) },
        KeyDef("Ctrl") { KeyAction.ToggleCtrl },
        KeyDef("Alt") { KeyAction.ToggleAlt },
        KeyDef("Shift") { KeyAction.ToggleShift },
        KeyDef("Paste") { KeyAction.PasteAction },
        KeyDef("↑") { KeyAction.SpecialKeyAction(SpecialKey.ARROW_UP) },
        KeyDef("↓") { KeyAction.SpecialKeyAction(SpecialKey.ARROW_DOWN) },
        KeyDef("←") { KeyAction.SpecialKeyAction(SpecialKey.ARROW_LEFT) },
        KeyDef("→") { KeyAction.SpecialKeyAction(SpecialKey.ARROW_RIGHT) },
        KeyDef("C-c") { KeyAction.CtrlAction('C') },
        KeyDef("C-z") { KeyAction.CtrlAction('Z') },
        KeyDef("C-d") { KeyAction.CtrlAction('D') },
        KeyDef("C-l") { KeyAction.CtrlAction('L') },
        KeyDef("C-a") { KeyAction.CtrlAction('A') },
        KeyDef("C-e") { KeyAction.CtrlAction('E') },
        KeyDef("C-x") { KeyAction.CtrlAction('X') },
        KeyDef("Home") { KeyAction.SpecialKeyAction(SpecialKey.HOME) },
        KeyDef("End") { KeyAction.SpecialKeyAction(SpecialKey.END) },
        KeyDef("PgUp") { KeyAction.SpecialKeyAction(SpecialKey.PAGE_UP) },
        KeyDef("PgDn") { KeyAction.SpecialKeyAction(SpecialKey.PAGE_DOWN) },
        KeyDef("Del") { KeyAction.SpecialKeyAction(SpecialKey.DELETE) },
        KeyDef("|") { KeyAction.TextAction("|") },
        KeyDef("~") { KeyAction.TextAction("~") },
        KeyDef("/") { KeyAction.TextAction("/") },
        KeyDef("-") { KeyAction.TextAction("-") },
        KeyDef("_") { KeyAction.TextAction("_") },
        KeyDef("=") { KeyAction.TextAction("=") },
        KeyDef(":") { KeyAction.TextAction(":") },
        KeyDef(";") { KeyAction.TextAction(";") },
        KeyDef("\"") { KeyAction.TextAction("\"") },
        KeyDef("'") { KeyAction.TextAction("'") },
        KeyDef("`") { KeyAction.TextAction("`") },
        KeyDef("[") { KeyAction.TextAction("[") },
        KeyDef("]") { KeyAction.TextAction("]") },
        KeyDef("{") { KeyAction.TextAction("{") },
        KeyDef("}") { KeyAction.TextAction("}") },
        KeyDef("(") { KeyAction.TextAction("(") },
        KeyDef(")") { KeyAction.TextAction(")") },
        KeyDef("<") { KeyAction.TextAction("<") },
        KeyDef(">") { KeyAction.TextAction(">") },
        KeyDef("\\") { KeyAction.TextAction("\\") },
        KeyDef("#") { KeyAction.TextAction("#") },
        KeyDef("$") { KeyAction.TextAction("$") },
        KeyDef("@") { KeyAction.TextAction("@") },
        KeyDef("!") { KeyAction.TextAction("!") },
        KeyDef("&") { KeyAction.TextAction("&") },
        KeyDef("*") { KeyAction.TextAction("*") },
        KeyDef("%") { KeyAction.TextAction("%") },
        KeyDef("^") { KeyAction.TextAction("^") },
        KeyDef("?") { KeyAction.TextAction("?") }
    )

    private data class KeyDef(val label: String, val action: () -> KeyAction)

    private lateinit var container: LinearLayout
    private val keyButtons = mutableMapOf<String, MaterialButton>()

    init {
        setupView()
    }

    private fun setupView() {
        isHorizontalScrollBarEnabled = false
        isSmoothScrollingEnabled = true
        setBackgroundColor(Color.parseColor("#161B22"))

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
        }
        addView(container)

        keyDefs.forEach { keyDef ->
            val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = keyDef.label
                textSize = 12f
                isAllCaps = false
                // CRITICAL: Prevent buttons from stealing focus from TerminalView / soft keyboard
                isFocusable = false
                isFocusableInTouchMode = false
                setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = dpToPx(34)
                insetTop = 0
                insetBottom = 0
                cornerRadius = dpToPx(6)
                strokeWidth = dpToPx(1)
                strokeColor = ColorStateList.valueOf(Color.parseColor("#30363D"))
                setTextColor(Color.parseColor("#C9D1D9"))
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                tag = keyDef.label

                setOnClickListener {
                    onKeyListener?.invoke(keyDef.action())
                }
            }
            keyButtons[keyDef.label] = btn
            container.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(34)
            ).apply { setMargins(dpToPx(3), 0, dpToPx(3), 0) })
        }
    }

    /**
     * Updates the visual highlight state of modifier buttons (Ctrl, Alt, Shift).
     */
    fun setModifierActive(key: String, active: Boolean) {
        val btn = keyButtons[key] ?: return
        if (active) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1F6FEB"))
            btn.setTextColor(Color.WHITE)
            btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#58A6FF"))
            btn.strokeWidth = dpToPx(2)
            btn.setTypeface(null, Typeface.BOLD)
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btn.setTextColor(Color.parseColor("#C9D1D9"))
            btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#30363D"))
            btn.strokeWidth = dpToPx(1)
            btn.setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }
}
