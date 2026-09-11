package com.mobilelinux.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.mobilelinux.R

/**
 * Modern custom dark-themed dialog matching MobileLinux design system.
 */
class CustomDialog private constructor(
    private val context: Context,
    private val builder: Builder
) {
    private var dialog: Dialog? = null

    fun show(): Dialog {
        val dlg = Dialog(context, R.style.Theme_MobileLinux_CustomDialog)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_custom_dialog, null)

        // Window background transparent so the rounded card corners render properly
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Icon Badge
        val iconContainer = view.findViewById<FrameLayout>(R.id.dialog_icon_container)
        val ivIcon = view.findViewById<ImageView>(R.id.dialog_icon)
        if (builder.icon != null) {
            ivIcon.setImageResource(builder.icon!!)
            val tint = builder.iconTint ?: ContextCompat.getColor(context, R.color.accent_blue)
            ivIcon.setColorFilter(tint)
            // Calculate a 13% opacity version of the tint for the circular background
            val bgTint = (tint and 0x00FFFFFF) or 0x22000000
            iconContainer.backgroundTintList = ColorStateList.valueOf(bgTint)
            iconContainer.visibility = View.VISIBLE
        } else {
            iconContainer.visibility = View.GONE
        }

        // Title
        val tvTitle = view.findViewById<TextView>(R.id.dialog_title)
        if (!builder.title.isNullOrBlank()) {
            tvTitle.text = builder.title
            tvTitle.visibility = View.VISIBLE
        } else {
            tvTitle.visibility = View.GONE
        }

        // Message Description
        val tvMsg = view.findViewById<TextView>(R.id.dialog_message)
        if (!builder.message.isNullOrBlank()) {
            tvMsg.text = builder.message
            tvMsg.visibility = View.VISIBLE
        } else {
            tvMsg.visibility = View.GONE
        }

        // Custom View Container (e.g. for inputs or progress indicators)
        val customContainer = view.findViewById<FrameLayout>(R.id.dialog_custom_container)
        if (builder.customView != null) {
            val cv = builder.customView!!
            (cv.parent as? ViewGroup)?.removeView(cv)
            customContainer.addView(cv)
            customContainer.visibility = View.VISIBLE
        } else {
            customContainer.visibility = View.GONE
        }

        // Positive Button (Primary action)
        val btnPos = view.findViewById<MaterialButton>(R.id.btn_dialog_positive)
        if (builder.positiveText != null) {
            btnPos.text = builder.positiveText
            if (builder.positiveDestructive) {
                btnPos.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent_red))
            }
            btnPos.setOnClickListener {
                val shouldDismiss = builder.positiveActionWithResult?.invoke() ?: run {
                    builder.positiveAction?.invoke()
                    true
                }
                if (shouldDismiss) {
                    dlg.dismiss()
                }
            }
            btnPos.visibility = View.VISIBLE
        } else {
            btnPos.visibility = View.GONE
        }

        // Negative Button (Secondary or destructive action)
        val btnNeg = view.findViewById<MaterialButton>(R.id.btn_dialog_negative)
        if (builder.negativeText != null) {
            btnNeg.text = builder.negativeText
            if (builder.negativeDestructive) {
                btnNeg.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
                btnNeg.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent_red))
            }
            btnNeg.setOnClickListener {
                dlg.dismiss()
                builder.negativeAction?.invoke()
            }
            btnNeg.visibility = View.VISIBLE
        } else {
            btnNeg.visibility = View.GONE
        }

        // Neutral Button (Cancel action, placed on far left)
        val btnNeu = view.findViewById<MaterialButton>(R.id.btn_dialog_neutral)
        if (builder.neutralText != null) {
            btnNeu.text = builder.neutralText
            btnNeu.setOnClickListener {
                dlg.dismiss()
                builder.neutralAction?.invoke()
            }
            btnNeu.visibility = View.VISIBLE
        } else {
            btnNeu.visibility = View.GONE
        }

        dlg.setContentView(view)
        dlg.setCancelable(builder.cancelable)
        dlg.setCanceledOnTouchOutside(builder.cancelable)

        // Ensure proper dialog width matching device screen
        val dm = context.resources.displayMetrics
        val width = (dm.widthPixels - (48 * dm.density).toInt()).coerceAtMost((380 * dm.density).toInt())
        dlg.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dlg.show()
        this.dialog = dlg
        return dlg
    }

    class Builder(private val context: Context) {
        var icon: Int? = null
        var iconTint: Int? = null
        var title: String? = null
        var message: String? = null
        var customView: View? = null
        var positiveText: String? = null
        var positiveAction: (() -> Unit)? = null
        var positiveActionWithResult: (() -> Boolean)? = null
        var positiveDestructive: Boolean = false
        var negativeText: String? = null
        var negativeAction: (() -> Unit)? = null
        var negativeDestructive: Boolean = false
        var neutralText: String? = null
        var neutralAction: (() -> Unit)? = null
        var cancelable: Boolean = true

        fun setIcon(@DrawableRes iconRes: Int, tint: Int? = null) = apply {
            this.icon = iconRes
            this.iconTint = tint
        }
        fun setTitle(title: String) = apply { this.title = title }
        fun setMessage(message: String) = apply { this.message = message }
        fun setView(view: View) = apply { this.customView = view }
        fun setPositiveButton(text: String, destructive: Boolean = false, action: (() -> Unit)? = null) = apply {
            this.positiveText = text
            this.positiveDestructive = destructive
            this.positiveAction = action
            this.positiveActionWithResult = null
        }
        fun setPositiveButtonWithResult(text: String, destructive: Boolean = false, action: () -> Boolean) = apply {
            this.positiveText = text
            this.positiveDestructive = destructive
            this.positiveActionWithResult = action
            this.positiveAction = null
        }
        fun setNegativeButton(text: String, destructive: Boolean = false, action: (() -> Unit)? = null) = apply {
            this.negativeText = text
            this.negativeDestructive = destructive
            this.negativeAction = action
        }
        fun setNeutralButton(text: String, action: (() -> Unit)? = null) = apply {
            this.neutralText = text
            this.neutralAction = action
        }
        fun setCancelable(cancelable: Boolean) = apply { this.cancelable = cancelable }

        fun show(): Dialog = CustomDialog(context, this).show()
    }
}
