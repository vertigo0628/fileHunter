package com.vertigo.opposite

// Just a simple box to hold all the info we need about a file or folder we find
data class FileModel(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val isVault: Boolean = false,
    val isPhoto: Boolean = false,
    val isHidden: Boolean = false,
    val label: String? = null
) {
    val displaySize: String
        get() = if (isDirectory) "Folder" else formatSize(size)

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
