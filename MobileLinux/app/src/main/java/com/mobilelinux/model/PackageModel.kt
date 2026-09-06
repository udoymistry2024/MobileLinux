package com.mobilelinux.model

enum class PackageCategory(val displayName: String) {
    ALL("All"),
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
    var isInstalled: Boolean = false,
    var isInstalling: Boolean = false,
    var statusText: String = "",
    var progressPercent: Int = -1
)
