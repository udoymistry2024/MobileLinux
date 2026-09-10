package com.mobilelinux.ide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R

/**
 * Adapter for the horizontal open tabs strip in Code IDE.
 */
class IdeTabsAdapter(
    private val tabs: MutableList<IdeTab>,
    private var selectedIndex: Int = 0,
    private val onTabClick: (index: Int) -> Unit,
    private val onTabClose: (index: Int) -> Unit
) : RecyclerView.Adapter<IdeTabsAdapter.TabViewHolder>() {

    fun getSelectedIndex(): Int = selectedIndex

    fun setSelectedIndex(index: Int) {
        val oldIndex = selectedIndex
        selectedIndex = index
        if (oldIndex in tabs.indices) notifyItemChanged(oldIndex)
        if (selectedIndex in tabs.indices) notifyItemChanged(selectedIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ide_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        val isSelected = position == selectedIndex
        val context = holder.itemView.context

        holder.tabTitle.text = tab.title
        holder.tabDirtyDot.visibility = if (tab.isDirty) View.VISIBLE else View.GONE

        // Active tab styling
        if (isSelected) {
            holder.tabContainer.setBackgroundResource(R.drawable.bg_ide_tab_active)
            holder.tabTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            holder.tabCloseBtn.setColorFilter(ContextCompat.getColor(context, R.color.text_primary))
        } else {
            holder.tabContainer.setBackgroundResource(R.drawable.bg_ide_tab_inactive)
            holder.tabTitle.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            holder.tabCloseBtn.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
        }

        // Language icon tinting
        val iconColor = when (tab.mode) {
            "python" -> R.color.accent_yellow
            "javascript", "typescript" -> R.color.accent_orange
            "c_cpp" -> R.color.accent_blue
            "sh" -> R.color.accent_green
            "html", "css" -> R.color.accent_purple
            "rust" -> R.color.accent_red
            else -> R.color.accent_cyan
        }
        holder.tabIcon.setColorFilter(ContextCompat.getColor(context, iconColor))

        holder.tabContainer.setOnClickListener {
            onTabClick(holder.adapterPosition)
        }

        holder.tabCloseBtn.setOnClickListener {
            onTabClose(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = tabs.size

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tabContainer: View = itemView.findViewById(R.id.tab_container)
        val tabIcon: ImageView = itemView.findViewById(R.id.tab_icon)
        val tabTitle: TextView = itemView.findViewById(R.id.tab_title)
        val tabDirtyDot: TextView = itemView.findViewById(R.id.tab_dirty_dot)
        val tabCloseBtn: ImageView = itemView.findViewById(R.id.tab_close_btn)
    }
}
