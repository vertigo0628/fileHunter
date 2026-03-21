package com.vertigo.opposite

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

object ShizukuHelper {

    /**
     * Just checking if the Shizuku app is running in the background and alive.
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns true only when Shizuku is available, v11+, and permission is granted.
     */
    fun checkPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            if (Shizuku.isPreV11()) return false
            if (Shizuku.getVersion() < 11) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ask the user nicely for Shizuku access. If they already said yes, this does nothing.
     */
    fun requestPermission(requestCode: Int) {
        if (!isShizukuAvailable()) return
        try {
            if (!Shizuku.isPreV11() && !checkPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // -------------------------------------------------------------------------
    // File listing
    // -------------------------------------------------------------------------

    fun listFiles(context: Context, dirPath: String, useShizuku: Boolean = false): List<FileModel> {
        return if (useShizuku) listFilesViaShizuku(context, dirPath) else listFilesNormal(context, dirPath)
    }

    private fun listFilesNormal(context: Context, dirPath: String): List<FileModel> {
        val dir = File(dirPath)
        val files = dir.listFiles() ?: return emptyList()
        val isVault = File(dir, ".nomedia").exists()
        val isAppData = dirPath.contains("/Android/data")

        return files.map { file ->
            val isHidden = file.name.startsWith(".")
            val isPhoto = isVault &&
                !file.isDirectory &&
                (file.name.endsWith(".bin") || file.name.endsWith(".tmp")) &&
                HexChecker.isJpeg(file.absolutePath, false)

            val label = if (isAppData && file.isDirectory) {
                getAppLabel(context, file.name)
            } else null

            FileModel(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = file.length(),
                isVault = isVault,
                isPhoto = isPhoto,
                isHidden = isHidden,
                label = label
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    // This is the heavy lifter. It runs a shell command (ls) as a super-user
    // so we can see inside folders that Android normally blocks us from seeing.
    private fun listFilesViaShizuku(context: Context, dirPath: String): List<FileModel> {
        val lsOutput = executeShizukuCommand("ls -lA \"$dirPath\"")
        val lines = lsOutput.split("\n").filter { it.isNotBlank() }

        val lsHiddenOutput = executeShizukuCommand("ls -a \"$dirPath\"")
        val isVault = lsHiddenOutput.lines().any { it.trim() == ".nomedia" }
        val isAppData = dirPath.contains("/Android/data")

        val result = mutableListOf<FileModel>()

        for (line in lines) {
            if (line.startsWith("total ")) continue
            val parts = line.split(Regex("\\s+"), limit = 9)
            if (parts.size < 8) continue

            val name = parts.last().trim()
            if (name == "." || name == "..") continue

            val permissions = parts[0]
            val sizeStr = parts.find { it.matches(Regex("\\d+")) }
            val size = sizeStr?.toLongOrNull() ?: 0L
            val isDirectory = permissions.startsWith("d")
            val isHidden = name.startsWith(".")
            val absolutePath = if (dirPath.endsWith("/")) "$dirPath$name" else "$dirPath/$name"

            val isPhoto = isVault &&
                !isDirectory &&
                (name.endsWith(".bin") || name.endsWith(".tmp")) &&
                HexChecker.isJpeg(absolutePath, true)

            val label = if (isAppData && isDirectory) {
                getAppLabel(context, name)
            } else null

            result.add(
                FileModel(
                    name = name,
                    path = absolutePath,
                    isDirectory = isDirectory,
                    size = size,
                    isVault = isVault,
                    isPhoto = isPhoto,
                    isHidden = isHidden,
                    label = label
                )
            )
        }

        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /**
     * The heart of the Deep Scan feature!
     * This runs a fast search across the entire drive looking for sneaky .nomedia files.
     * Every folder that has one is flagged as a "Vault".
     */
    fun findVaults(context: Context, rootPath: String): List<FileModel> {
        val result = mutableListOf<FileModel>()
        
        // Using 'find' is waaaay faster than checking folders in Kotlin.
        // We tell it to find the .nomedia files, then just give us the name of the folder they live in.
        val command = "find \"$rootPath\" -type f -name \".nomedia\" -exec dirname {} \\; 2>/dev/null | sort -u"
        val output = executeShizukuCommand(command)
        val lines = output.split("\n").filter { it.isNotBlank() }

        for (line in lines) {
            val dirPath = line.trim()
            val dirName = File(dirPath).name
            val isAppData = dirPath.contains("/Android/data")

            val label = if (isAppData) {
                // Try to extract the package name from the path.
                // e.g., /storage/emulated/0/Android/data/com.example.app/files -> com.example.app
                val parts = dirPath.split("/")
                val dataIndex = parts.indexOf("data")
                if (dataIndex != -1 && dataIndex + 1 < parts.size) {
                    val pkgName = parts[dataIndex + 1]
                    getAppLabel(context, pkgName)?.let { "$it ($dirName)" }
                } else null
            } else null

            result.add(
                FileModel(
                    name = dirName,
                    path = dirPath,
                    isDirectory = true,
                    size = 0L,
                    isVault = true, // We know it's a vault because it has a .nomedia
                    isPhoto = false,
                    isHidden = dirName.startsWith("."),
                    label = label ?: ("Vault in $dirName")
                )
            )
        }

        return result.sortedBy { it.name.lowercase() }
    }

    private fun getAppLabel(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // File Access / Copying
    // -------------------------------------------------------------------------

    /**
     * Copies a file from a potentially restricted path to the app's cache directory.
     * Uses Shizuku to read the source file and writes to the destination.
     */
    fun copyFileToCache(sourcePath: String, context: Context): File? {
        val sourceFile = File(sourcePath)
        val destFile = File(context.cacheDir, "temp_" + sourceFile.name)

        return try {
            val inputStream = if (sourcePath.contains("/Android/data")) {
                // Restricted: use Shizuku shell to read
                val process = newProcessViaReflection("cat \"$sourcePath\"")
                process?.inputStream
            } else {
                // Normal access
                FileInputStream(sourcePath)
            }

            if (inputStream == null) return null

            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Executes a shell command via Shizuku using reflection to access newProcess.
     */
    internal fun executeShizukuCommand(command: String): String {
        return try {
            val process = newProcessViaReflection(command) ?: return ""
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * This is our secret weapon. On newer Androids, Shizuku tries to hide the 'newProcess' function
     * from Kotlin programmers (making it "private").
     * We use a trick called Reflection to say "I know it's private, but let me use it anyway!"
     */
    private fun newProcessViaReflection(command: String): Process? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null as Array<String>?,
                null as String?
            ) as? Process
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
