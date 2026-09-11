package com.mobilelinux.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.mobilelinux.R

/**
 * Utility object for showing custom dark-themed anchored popup menus across all Activities.
 *
 * The popup appears directly below the anchor view, right-aligned to the anchor's right edge
 * (or clamped to screen bounds), with a compact, adaptive width that fits the content perfectly.
 */
object MenuHelper {

    /** Represents a single menu entry. Use [Divider] for visual separators. */
    sealed class Item {
        data class Action(
            @DrawableRes val icon: Int?,
            val title: String,
            val subtitle: String? = null,
            val badge: String? = null,
            val destructive: Boolean = false,
            val enabled: Boolean = true,
            val onClick: () -> Unit
        ) : Item()

        /** Visual horizontal divider between item groups. */
        object Divider : Item()
    }

    /**
     * Displays a custom dark-themed anchored popup menu attached to [anchor].
     *
     * @param context   Activity or Fragment context
     * @param anchor    The view to anchor the popup to (e.g. three-dot button)
     * @param title     Optional section header shown above all items
     * @param items     List of [Item.Action] and/or [Item.Divider]
     */
    fun show(
        context: Context,
        anchor: View,
        title: String? = null,
        items: List<Item>
    ) {
        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.layout_popup_menu, null)
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density

        // ── Optional title ────────────────────────────────────────────────────
        val tvTitle      = popupView.findViewById<TextView>(R.id.tv_popup_title)
        val dividerTitle = popupView.findViewById<View>(R.id.divider_popup_title)
        if (!title.isNullOrBlank()) {
            tvTitle.text = title.uppercase()
            tvTitle.visibility = View.VISIBLE
            dividerTitle.visibility = View.VISIBLE
        }

        // ── Menu items ────────────────────────────────────────────────────────
        val container = popupView.findViewById<LinearLayout>(R.id.container_popup_items)

        for (item in items) {
            when (item) {
                is Item.Divider -> {
                    val divider = View(context).apply {
                        val dp1 = (1 * density).toInt()
                        val v4  = (4 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp1
                        ).also { it.setMargins(0, v4, 0, v4) }
                        setBackgroundColor(0xFF30363D.toInt())
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    container.addView(divider)
                }

                is Item.Action -> {
                    val row = inflater.inflate(R.layout.item_popup_menu_row, container, false)

                    // Icon
                    val ivIcon = row.findViewById<ImageView>(R.id.iv_popup_item_icon)
                    if (item.icon != null) {
                        ivIcon.setImageResource(item.icon)
                        val tint = when {
                            !item.enabled    -> 0xFF484F58.toInt()
                            item.destructive -> 0xFFFF7B72.toInt()
                            else             -> 0xFF8B949E.toInt()
                        }
                        ivIcon.setColorFilter(tint)
                        ivIcon.visibility = View.VISIBLE
                    } else {
                        ivIcon.visibility = View.GONE
                    }

                    // Title
                    val tvLabel = row.findViewById<TextView>(R.id.tv_popup_item_title)
                    tvLabel.text = item.title
                    tvLabel.setTextColor(
                        when {
                            !item.enabled    -> 0xFF484F58.toInt()
                            item.destructive -> 0xFFFF7B72.toInt()
                            else             -> 0xFFE6EDF3.toInt()
                        }
                    )

                    // Subtitle (optional)
                    val tvSub = row.findViewById<TextView>(R.id.tv_popup_item_subtitle)
                    if (!item.subtitle.isNullOrBlank()) {
                        tvSub.text = item.subtitle
                        tvSub.visibility = View.VISIBLE
                    }

                    // Badge / checkmark (optional)
                    val tvBadge = row.findViewById<TextView>(R.id.tv_popup_item_badge)
                    if (!item.badge.isNullOrBlank()) {
                        tvBadge.text = item.badge
                        tvBadge.visibility = View.VISIBLE
                    }

                    // Attach item data to view tag
                    row.tag = item

                    if (!item.enabled) {
                        row.isClickable = false
                        row.isFocusable = false
                        row.alpha = 0.45f
                    }

                    container.addView(row)
                }
            }
        }

        // ── Calculate compact, natural width ──────────────────────────────────
        // Measure each item's actual text length + fixed chrome (icon, padding, margin)
        var maxContentWidth = 0
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val actionItem = row.tag as? Item.Action
            if (actionItem != null) {
                val tvLabel = row.findViewById<TextView>(R.id.tv_popup_item_title)
                tvLabel.measure(unspec, unspec)
                var rowW = tvLabel.measuredWidth.toFloat()

                val tvSub = row.findViewById<TextView>(R.id.tv_popup_item_subtitle)
                if (tvSub.visibility == View.VISIBLE) {
                    tvSub.measure(unspec, unspec)
                    rowW = maxOf(rowW, tvSub.measuredWidth.toFloat())
                }

                val tvBadge = row.findViewById<TextView>(R.id.tv_popup_item_badge)
                if (tvBadge.visibility == View.VISIBLE) {
                    tvBadge.measure(unspec, unspec)
                    rowW += tvBadge.measuredWidth + (8 * density)
                }

                if (actionItem.icon != null) {
                    rowW += (18 + 12) * density
                }

                // Row padding (14dp * 2) + Row margin (4dp * 2) + slight breathing room (8dp)
                rowW += (28 + 8 + 8) * density

                if (rowW.toInt() > maxContentWidth) {
                    maxContentWidth = rowW.toInt()
                }
            }
        }

        if (tvTitle.visibility == View.VISIBLE) {
            tvTitle.measure(unspec, unspec)
            val titleW = tvTitle.measuredWidth + (32 * density).toInt()
            if (titleW > maxContentWidth) {
                maxContentWidth = titleW
            }
        }

        // Keep width compact and natural: min 168dp, max 235dp
        val minMenuWidth = (168 * density).toInt()
        val maxMenuWidth = (235 * density).toInt()
        val finalWidth = maxContentWidth.coerceIn(minMenuWidth, maxMenuWidth)

        // ── Construct PopupWindow with exact pixel width ──────────────────────
        val popup = PopupWindow(
            popupView,
            finalWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true  // focusable — dismisses on back or outside touch
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16f * density
            animationStyle = R.style.PopupMenuAnimation
        }

        // Attach click listeners now that popup instance is created
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val actionItem = row.tag as? Item.Action
            if (actionItem != null && actionItem.enabled) {
                row.setOnClickListener {
                    popup.dismiss()
                    actionItem.onClick()
                }
            }
        }

        // ── Calculate Anchor and Screen Coordinates ───────────────────────────
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenMargin = (6 * density).toInt()

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val anchorX = anchorLoc[0]
        val anchorY = anchorLoc[1]
        val anchorW = anchor.width
        val anchorH = anchor.height

        // Right-align popup to anchor's right edge by default:
        // popup's right edge = anchorX + anchorW
        // Therefore popup's left (X) = anchorX + anchorW - finalWidth
        var popupX = anchorX + anchorW - finalWidth

        // Clamp to screen edges:
        if (popupX + finalWidth > screenWidth - screenMargin) {
            popupX = screenWidth - screenMargin - finalWidth
        }
        if (popupX < screenMargin) {
            popupX = screenMargin
        }

        // Y position: directly below the anchor view
        val gap = (4 * density).toInt()
        var popupY = anchorY + anchorH + gap

        // Measure actual popup height to check for bottom-screen overflow
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(finalWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = popupView.measuredHeight

        if (popupY + popupHeight > screenHeight - screenMargin && anchorY - popupHeight - gap >= screenMargin) {
            // Flip above the anchor if it doesn't fit below
            popupY = anchorY - popupHeight - gap
        }

        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, popupX, popupY)
    }

    /**
     * Fallback overload when anchor view is not directly available.
     * Anchors to decorView or top of screen.
     */
    fun show(
        context: Context,
        title: String? = null,
        items: List<Item>
    ) {
        val activity = context as? android.app.Activity
        val decor = activity?.window?.decorView
        if (decor != null) {
            show(context, decor, title, items)
        }
    }
}
