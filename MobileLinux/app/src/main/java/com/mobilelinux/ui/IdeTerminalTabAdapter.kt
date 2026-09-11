package com.mobilelinux.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R

data class IdeTerminalTabItem(
    val sessionId: String,
    var name: String
)

class IdeTerminalTabAdapter(
    private val onTabClick: (IdeTerminalTabItem) -> Unit,
    private val onTabClose: (IdeTerminalTabItem) -> Unit,
    private val onTabLongClick: (IdeTerminalTabItem) -> Unit
) : RecyclerView.Adapter<IdeTerminalTabAdapter.TabViewHolder>() {

    private val tabs = mutableListOf<IdeTerminalTabItem>()
    var activeSessionId: String? = null
        private set

    fun submitTabs(newTabs: List<IdeTerminalTabItem>, activeId: String?) {
        tabs.clear()
        tabs.addAll(newTabs)
        activeSessionId = activeId
        notifyDataSetChanged()
    }

    fun setActiveId(id: String?) {
        if (activeSessionId != id) {
            activeSessionId = id
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_terminal_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(tabs[position])
    }

    override fun getItemCount(): Int = tabs.size

    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.tab_container)
        private val icon: ImageView = itemView.findViewById(R.id.tab_icon)
        private val title: TextView = itemView.findViewById(R.id.tab_title)
        private val closeBtn: ImageView = itemView.findViewById(R.id.tab_close_btn)

        fun bind(item: IdeTerminalTabItem) {
            val isActive = item.sessionId == activeSessionId
            title.text = item.name

            container.setBackgroundResource(
                if (isActive) R.drawable.bg_terminal_tab_active else R.drawable.bg_terminal_tab_inactive
            )

            val activeColor = ContextCompat.getColor(itemView.context, R.color.accent_blue)
            val textPrimary = ContextCompat.getColor(itemView.context, R.color.text_primary)
            val textSecondary = ContextCompat.getColor(itemView.context, R.color.text_secondary)

            icon.setColorFilter(if (isActive) activeColor else textSecondary)
            title.setTextColor(if (isActive) textPrimary else textSecondary)
            closeBtn.setColorFilter(if (isActive) textPrimary else textSecondary)

            container.setOnClickListener {
                onTabClick(item)
            }

            container.setOnLongClickListener {
                onTabLongClick(item)
                true
            }

            closeBtn.setOnClickListener {
                onTabClose(item)
            }
        }
    }
}
