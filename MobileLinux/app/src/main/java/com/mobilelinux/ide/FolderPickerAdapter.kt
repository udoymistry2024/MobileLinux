package com.mobilelinux.ide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R
import java.io.File

/**
 * Adapter for browsing subfolders in the interactive folder picker modal.
 */
class FolderPickerAdapter(
    private var folders: List<File>,
    private val onFolderClick: (File) -> Unit
) : RecyclerView.Adapter<FolderPickerAdapter.FolderViewHolder>() {

    fun updateFolders(newFolders: List<File>) {
        folders = newFolders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picker_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.tvName.text = folder.name
        holder.itemView.setOnClickListener {
            onFolderClick(folder)
        }
    }

    override fun getItemCount(): Int = folders.size

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_picker_folder_name)
    }
}
