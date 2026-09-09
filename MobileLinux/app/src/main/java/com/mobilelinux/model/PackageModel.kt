package com.mobilelinux.model

enum class PackageCategory(val displayName: String) {
    ALL("All"),
    DESKTOP_APPS("Desktop Apps"),
    CYBER_SECURITY("Cyber Security"),
    DATA_SCIENCE("AI & Data Science"),
    RUNTIMES("Languages & Runtimes"),
    DEV_TOOLS("Developer Tools"),
    DATABASES("Databases & Web"),
    UTILITIES("System & Networking")
}

data class LinuxPackage(
    val id: String,
    val name: String,
    val category: PackageCategory,
    val version: String,
    val description: String,
    val installCommand: String,
    val checkInstalledCommand: String,
    val launchUrl: String? = null,
    val uninstallCommand: String? = null,
    @Volatile var isInstalled: Boolean = false,
    @Volatile var isInstalling: Boolean = false,
    @Volatile var isUninstalling: Boolean = false,
    @Volatile var isActivated: Boolean = false,
    @Volatile var isActivating: Boolean = false,
    @Volatile var statusText: String = "",
    @Volatile var progressPercent: Int = -1
)
