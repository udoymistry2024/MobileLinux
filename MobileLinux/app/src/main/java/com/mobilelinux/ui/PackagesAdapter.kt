package com.mobilelinux.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R
import com.mobilelinux.model.LinuxPackage

class PackagesAdapter(
    private val onInstallClick: (LinuxPackage) -> Unit,
    private val onLaunchClick: (LinuxPackage) -> Unit,
    private val onActivateClick: (LinuxPackage) -> Unit,
    private val onCopyClick: (LinuxPackage) -> Unit,
    private val onUninstallClick: (LinuxPackage) -> Unit
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
        private val tvInstallingLabel: TextView = itemView.findViewById(R.id.tv_installing_label)
        private val pbPkgHorizontal: ProgressBar = itemView.findViewById(R.id.pb_pkg_horizontal)
        private val layoutInstalled: LinearLayout = itemView.findViewById(R.id.layout_installed)
        private val btnUninstall: ImageButton = itemView.findViewById(R.id.btn_uninstall)
        private val btnCopyCmd: ImageButton = itemView.findViewById(R.id.btn_copy_cmd)
        private val btnLaunch: Button = itemView.findViewById(R.id.btn_launch)
        private val btnActivate: Button = itemView.findViewById(R.id.btn_activate)
        private val btnInstall: Button = itemView.findViewById(R.id.btn_install)

        fun bind(pkg: LinuxPackage) {
            tvName.text = pkg.name
            tvVersion.text = pkg.version
            tvCategory.text = pkg.category.displayName
            tvDesc.text = pkg.description

            // Universal Copy Command Icon for all packages
            btnCopyCmd.visibility = View.VISIBLE
            btnCopyCmd.setOnClickListener {
                onCopyClick(pkg)
            }

            // Permanent Delete / Clean Uninstall Button
            btnUninstall.setOnClickListener {
                onUninstallClick(pkg)
            }

            if (pkg.id == "miniconda") {
                when {
                    pkg.isUninstalling -> {
                        layoutInstalling.visibility = View.VISIBLE
                        btnInstall.visibility = View.GONE
                        btnActivate.visibility = View.GONE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnUninstall.visibility = View.GONE
                        tvInstallingLabel.text = "Uninstalling..."
                        pbPkgHorizontal.visibility = View.VISIBLE
                        pbPkgHorizontal.isIndeterminate = true
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Uninstalling Conda & environments..."
                    }
                    pkg.isInstalling -> {
                        layoutInstalling.visibility = View.VISIBLE
                        btnInstall.visibility = View.GONE
                        btnActivate.visibility = View.GONE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnUninstall.visibility = View.GONE
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Installing Conda in background..."

                        if (pkg.progressPercent >= 0) {
                            tvInstallingLabel.text = "Installing ${pkg.progressPercent}%"
                            pbPkgHorizontal.visibility = View.VISIBLE
                            pbPkgHorizontal.isIndeterminate = false
                            pbPkgHorizontal.progress = pkg.progressPercent
                        } else {
                            tvInstallingLabel.text = if (pkg.statusText.contains("Queue", ignoreCase = true) || pkg.statusText.contains("Pending", ignoreCase = true)) "In Queue" else "Installing..."
                            pbPkgHorizontal.visibility = View.VISIBLE
                            pbPkgHorizontal.isIndeterminate = true
                        }
                    }
                    pkg.isActivating -> {
                        layoutInstalling.visibility = View.VISIBLE
                        btnInstall.visibility = View.GONE
                        btnActivate.visibility = View.GONE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnUninstall.visibility = View.GONE
                        tvInstallingLabel.text = "Activating..."
                        pbPkgHorizontal.visibility = View.VISIBLE
                        pbPkgHorizontal.isIndeterminate = true
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Activating Conda base environment..."
                    }
                    pkg.isInstalled && !pkg.isActivated -> {
                        // Conda is installed but not yet activated -> show Red Activate button + Trash icon
                        layoutInstalling.visibility = View.GONE
                        pbPkgHorizontal.visibility = View.GONE
                        btnInstall.visibility = View.GONE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnActivate.visibility = View.VISIBLE
                        btnUninstall.visibility = View.VISIBLE
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Installed. Click Activate to enable."
                    }
                    pkg.isInstalled && pkg.isActivated -> {
                        // Conda is installed and active -> show Installed badge + Trash icon
                        layoutInstalling.visibility = View.GONE
                        pbPkgHorizontal.visibility = View.GONE
                        btnInstall.visibility = View.GONE
                        btnActivate.visibility = View.GONE
                        layoutInstalled.visibility = View.VISIBLE
                        btnUninstall.visibility = View.VISIBLE
                        btnLaunch.visibility = if (pkg.launchUrl != null) View.VISIBLE else View.GONE
                        tvStatus.text = "Active & Ready (base)"
                    }
                    else -> {
                        // Conda not yet installed
                        layoutInstalling.visibility = View.GONE
                        pbPkgHorizontal.visibility = View.GONE
                        btnInstall.visibility = View.VISIBLE
                        btnActivate.visibility = View.GONE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnUninstall.visibility = View.GONE
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Ready to install"
                    }
                }
            } else {
                btnActivate.visibility = View.GONE
                when {
                    pkg.isUninstalling -> {
                        layoutInstalling.visibility = View.VISIBLE
                        btnInstall.visibility = View.GONE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnUninstall.visibility = View.GONE
                        tvInstallingLabel.text = "Uninstalling..."
                        pbPkgHorizontal.visibility = View.VISIBLE
                        pbPkgHorizontal.isIndeterminate = true
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Uninstalling ${pkg.name}..."
                    }
                    pkg.isInstalling -> {
                        layoutInstalling.visibility = View.VISIBLE
                        btnInstall.visibility = View.GONE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnUninstall.visibility = View.GONE
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Installing in background..."

                        if (pkg.progressPercent >= 0) {
                            tvInstallingLabel.text = "Installing ${pkg.progressPercent}%"
                            pbPkgHorizontal.visibility = View.VISIBLE
                            pbPkgHorizontal.isIndeterminate = false
                            pbPkgHorizontal.progress = pkg.progressPercent
                        } else {
                            tvInstallingLabel.text = if (pkg.statusText.contains("Queue", ignoreCase = true) || pkg.statusText.contains("Pending", ignoreCase = true)) "In Queue" else "Installing..."
                            pbPkgHorizontal.visibility = View.VISIBLE
                            pbPkgHorizontal.isIndeterminate = true
                        }
                    }
                    pkg.isInstalled -> {
                        layoutInstalling.visibility = View.GONE
                        pbPkgHorizontal.visibility = View.GONE
                        btnInstall.visibility = View.GONE
                        layoutInstalled.visibility = View.VISIBLE
                        btnUninstall.visibility = View.VISIBLE
                        btnLaunch.visibility = if (pkg.launchUrl != null) View.VISIBLE else View.GONE
                        tvStatus.text = if (pkg.statusText.isNotEmpty() && !pkg.statusText.contains("Uninstall", ignoreCase = true)) pkg.statusText else "Installed and ready"
                    }
                    else -> {
                        layoutInstalling.visibility = View.GONE
                        pbPkgHorizontal.visibility = View.GONE
                        btnInstall.visibility = View.VISIBLE
                        layoutInstalled.visibility = View.GONE
                        btnLaunch.visibility = View.GONE
                        btnUninstall.visibility = View.GONE
                        tvStatus.text = if (pkg.statusText.isNotEmpty()) pkg.statusText else "Ready to install"
                    }
                }
            }

            btnInstall.setOnClickListener {
                onInstallClick(pkg)
            }

            btnActivate.setOnClickListener {
                onActivateClick(pkg)
            }

            btnLaunch.setOnClickListener {
                onLaunchClick(pkg)
            }
        }
    }
}

