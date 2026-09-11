package com.mobilelinux.ide

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R
import java.io.File

/**
 * Adapter for browsing directories and files in the interactive folder picker modal.
 */
class FolderPickerAdapter(
    private var items: List<File>,
    private val onFolderClick: (File) -> Unit
) : RecyclerView.Adapter<FolderPickerAdapter.FolderViewHolder>() {

    fun updateFolders(newItems: List<File>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picker_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val file = items[position]
        holder.tvName.text = file.name
        val context = holder.itemView.context

        if (file.isDirectory) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder)
            holder.ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent_blue))
            holder.ivArrow.visibility = View.VISIBLE
            holder.itemView.isClickable = true
            holder.itemView.setOnClickListener {
                onFolderClick(file)
            }
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_file)
            holder.ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
            holder.ivArrow.visibility = View.GONE
            holder.itemView.isClickable = false
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = items.size

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_picker_folder_name)
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_picker_item_icon)
        val ivArrow: ImageView = itemView.findViewById(R.id.iv_picker_item_arrow)
    }
}
