package com.mobilelinux.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R
import com.mobilelinux.model.LinuxPackage

class PackagesAdapter(
    private val onInstallClick: (LinuxPackage) -> Unit,
    private val onLaunchClick: (LinuxPackage) -> Unit
) : RecyclerView.Adapter<PackagesAdapter.PackageViewHolder>() {

    private val items = mutableListOf<LinuxPackage>()

    fun submitList(newItems: List<LinuxPackage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateItem(pkgId: String) {
        val index = items.indexOfFirst { it.id == pkgId }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_package_card, parent, false)
        return PackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PackageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_pkg_name)
        private val tvVersion: TextView = itemView.findViewById(R.id.tv_pkg_version)
        private val tvCategory: TextView = itemView.findViewById(R.id.tv_pkg_category)
        private val tvDesc: TextView = itemView.findViewById(R.id.tv_pkg_desc)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_pkg_status)
        private val layoutInstalling: LinearLayout = itemView.findViewById(R.id.layout_installing)
        private val layoutInstalled: LinearLayout = itemView.findViewById(R.id.layout_installed)
        private val btnInstall: Button = itemView.findViewById(R.id.btn_install)
        private val btnLaunch: Button = itemView.findViewById(R.id.btn_launch)

        fun bind(pkg: LinuxPackage) {
            tvName.text = pkg.name
            tvVersion.text = pkg.version
            tvCategory.text = "${pkg.category.emoji} ${pkg.category.displayName}"
            tvDesc.text = pkg.description

            when {
                pkg.isInstalling -> {
                    layoutInstalling.visibility = View.VISIBLE
                    btnInstall.visibility = View.GONE
                    layoutInstalled.visibility = View.GONE
                    btnLaunch.visibility = View.GONE
                    tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Installing in background..."
                }
                pkg.isInstalled -> {
                    layoutInstalling.visibility = View.GONE
                    btnInstall.visibility = View.GONE
                    layoutInstalled.visibility = View.VISIBLE
                    btnLaunch.visibility = if (pkg.launchUrl != null) View.VISIBLE else View.GONE
                    tvStatus.text = "Installed and ready to use ✓"
                }
                else -> {
                    layoutInstalling.visibility = View.GONE
                    btnInstall.visibility = View.VISIBLE
                    layoutInstalled.visibility = View.GONE
                    btnLaunch.visibility = View.GONE
                    tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Ready to install"
                }
            }

            btnInstall.setOnClickListener {
                onInstallClick(pkg)
            }

            btnLaunch.setOnClickListener {
                onLaunchClick(pkg)
            }
        }
    }
}
