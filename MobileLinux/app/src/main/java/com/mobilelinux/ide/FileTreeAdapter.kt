package com.mobilelinux.ide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R
import java.io.File

data class FileTreeNode(
    val file: File,
    val level: Int,
    val isDirectory: Boolean,
    var isExpanded: Boolean = false
)

/**
 * Hierarchical tree adapter for Code IDE's left file manager drawer.
 */
class FileTreeAdapter(
    private var rootDir: File,
    private val onFileClick: (File) -> Unit,
    private val onOpenAsProject: (folder: File) -> Unit,
    private val onNewFile: (parentDir: File) -> Unit,
    private val onNewFolder: (parentDir: File) -> Unit,
    private val onRename: (target: File) -> Unit,
    private val onDelete: (target: File) -> Unit,
    private val onCopyPath: (target: File) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.NodeViewHolder>() {

    private val visibleNodes = mutableListOf<FileTreeNode>()

    fun getRootDir(): File = rootDir

    init {
        reload()
    }

    fun setRootDir(newRoot: File) {
        rootDir = newRoot
        reload()
    }

    fun reload() {
        visibleNodes.clear()
        loadChildren(rootDir, 0)
        notifyDataSetChanged()
    }

    private fun loadChildren(dir: File, level: Int): List<FileTreeNode> {
        val children = dir.listFiles()?.toList() ?: emptyList()
        // Sort: directories first, then alphabetical (ignoring hidden files by default unless needed)
        val sorted = children
            .filter { !it.name.startsWith(".") || it.name == ".bashrc" }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        val nodes = sorted.map { f ->
            FileTreeNode(
                file = f,
                level = level,
                isDirectory = f.isDirectory,
                isExpanded = false
            )
        }
        if (level == 0) {
            visibleNodes.addAll(nodes)
        }
        return nodes
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_tree, parent, false)
        return NodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        val node = visibleNodes[position]
        val context = holder.itemView.context

        // Indentation
        val indentPx = (node.level * 16 * context.resources.displayMetrics.density).toInt()
        holder.indentSpace.layoutParams.width = indentPx
        holder.indentSpace.requestLayout()

        holder.tvName.text = node.file.name

        if (node.isDirectory) {
            holder.ivChevron.visibility = View.VISIBLE
            holder.ivChevron.rotation = if (node.isExpanded) 90f else 0f
            holder.ivTypeIcon.setImageResource(R.drawable.ic_folder)
            holder.ivTypeIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_blue))
        } else {
            holder.ivChevron.visibility = View.INVISIBLE
            holder.ivTypeIcon.setImageResource(R.drawable.ic_file)

            val mode = IdeTab.detectMode(node.file.name)
            val iconColor = when (mode) {
                "python" -> R.color.accent_yellow
                "javascript", "typescript" -> R.color.accent_orange
                "c_cpp" -> R.color.accent_blue
                "sh" -> R.color.accent_green
                "html", "css" -> R.color.accent_purple
                "rust" -> R.color.accent_red
                else -> R.color.text_secondary
            }
            holder.ivTypeIcon.setColorFilter(ContextCompat.getColor(context, iconColor))
        }

        // Node click
        holder.itemView.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener
            val currentNode = visibleNodes[currentPos]

            if (currentNode.isDirectory) {
                toggleFolder(currentPos, currentNode)
            } else {
                onFileClick(currentNode.file)
            }
        }

        // Context menu
        holder.ivMoreBtn.setOnClickListener { v ->
            showContextMenu(v, node.file)
        }
    }

    private fun toggleFolder(pos: Int, node: FileTreeNode) {
        if (node.isExpanded) {
            // Collapse: remove all descendants
            node.isExpanded = false
            var removeCount = 0
            val checkLevel = node.level
            for (i in (pos + 1) until visibleNodes.size) {
                if (visibleNodes[i].level > checkLevel) {
                    removeCount++
                } else {
                    break
                }
            }
            for (i in 0 until removeCount) {
                visibleNodes.removeAt(pos + 1)
            }
            notifyItemRangeRemoved(pos + 1, removeCount)
            notifyItemChanged(pos)
        } else {
            // Expand: load children
            node.isExpanded = true
            val children = loadChildren(node.file, node.level + 1)
            visibleNodes.addAll(pos + 1, children)
            notifyItemRangeInserted(pos + 1, children.size)
            notifyItemChanged(pos)
        }
    }

    private fun showContextMenu(anchor: View, file: File) {
        val popup = PopupMenu(anchor.context, anchor)
        if (file.isDirectory) {
            popup.menu.add(0, 10, 0, "📂 Open as Project")
            popup.menu.add(0, 1, 1, "New File Inside")
            popup.menu.add(0, 2, 2, "New Folder Inside")
        } else {
            popup.menu.add(0, 3, 0, "Open File")
        }
        popup.menu.add(0, 4, 3, "Rename")
        popup.menu.add(0, 5, 4, "Delete")
        popup.menu.add(0, 6, 5, "Copy Path")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                10 -> onOpenAsProject(file)
                1 -> onNewFile(file)
                2 -> onNewFolder(file)
                3 -> onFileClick(file)
                4 -> onRename(file)
                5 -> onDelete(file)
                6 -> onCopyPath(file)
            }
            true
        }
        popup.show()
    }

    override fun getItemCount(): Int = visibleNodes.size

    class NodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val indentSpace: View = itemView.findViewById(R.id.indent_space)
        val ivChevron: ImageView = itemView.findViewById(R.id.iv_chevron)
        val ivTypeIcon: ImageView = itemView.findViewById(R.id.iv_type_icon)
        val tvName: TextView = itemView.findViewById(R.id.tv_name)
        val ivMoreBtn: ImageView = itemView.findViewById(R.id.iv_more_btn)
    }
}
