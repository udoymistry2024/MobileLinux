package com.mobilelinux.ide.extension

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mobilelinux.R

class ExtensionsAdapter(
    private var allExtensions: List<InstalledExtension>,
    private val onToggle: (InstalledExtension, Boolean) -> Unit,
    private val onDelete: (InstalledExtension) -> Unit
) : RecyclerView.Adapter<ExtensionsAdapter.ViewHolder>() {

    private var displayedExtensions: List<InstalledExtension> = allExtensions

    fun updateList(newList: List<InstalledExtension>, query: String? = null) {
        allExtensions = newList
        filter(query)
    }

    fun filter(query: String?) {
        displayedExtensions = if (query.isNullOrBlank()) {
            allExtensions
        } else {
            val q = query.trim().lowercase()
            allExtensions.filter {
                it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.id.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_extension_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ext = displayedExtensions[position]
        val context = holder.itemView.context

        holder.tvName.text = ext.name
        holder.tvVersion.text = "v${ext.version}"
        holder.tvAuthor.text = if (ext.author.isNotBlank()) "by ${ext.author}" else "by Developer"
        holder.tvDesc.text = ext.description.ifBlank { "No description provided." }

        // Icon
        val iconFile = ext.iconFile
        if (iconFile != null && iconFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    holder.ivIcon.setImageBitmap(bitmap)
                    holder.ivIcon.imageTintList = null
                } else {
                    holder.ivIcon.setImageResource(R.drawable.ic_extension)
                    holder.ivIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.accent_cyan)
                }
            } catch (e: Exception) {
                holder.ivIcon.setImageResource(R.drawable.ic_extension)
                holder.ivIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.accent_cyan)
            }
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_extension)
            holder.ivIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.accent_cyan)
        }

        // Enable / Disable Switch
        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = ext.isEnabled
        updateStatusView(holder, ext.isEnabled)

        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ext.isEnabled = isChecked
            updateStatusView(holder, isChecked)
            onToggle(ext, isChecked)
        }

        // Delete button
        holder.btnDelete.setOnClickListener {
            onDelete(ext)
        }
    }

    private fun updateStatusView(holder: ViewHolder, isEnabled: Boolean) {
        val context = holder.itemView.context
        if (isEnabled) {
            holder.tvStatus.text = "Active & Ready"
            holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
        } else {
            holder.tvStatus.text = "Disabled"
            holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }

    override fun getItemCount(): Int = displayedExtensions.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_extension_icon)
        val tvName: TextView = itemView.findViewById(R.id.tv_extension_name)
        val tvVersion: TextView = itemView.findViewById(R.id.tv_extension_version)
        val tvAuthor: TextView = itemView.findViewById(R.id.tv_extension_author)
        val tvDesc: TextView = itemView.findViewById(R.id.tv_extension_desc)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_extension_status)
        val switchEnabled: SwitchMaterial = itemView.findViewById(R.id.switch_extension_enabled)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_extension_delete)
    }
}
