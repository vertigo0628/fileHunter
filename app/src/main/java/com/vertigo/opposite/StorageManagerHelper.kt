package com.vertigo.opposite

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume

object StorageManagerHelper {
    
    data class StorageInfo(val title: String, val path: String)

    fun getStorageVolumes(context: Context): List<StorageInfo> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = storageManager.storageVolumes
        val result = mutableListOf<StorageInfo>()
        
        for (volume in volumes) {
            val description = volume.getDescription(context)
            val path = getVolumePath(volume)
            if (path != null) {
                result.add(StorageInfo(description, path))
            } else if (volume.isPrimary) {
                result.add(StorageInfo(description, Environment.getExternalStorageDirectory().absolutePath))
            }
        }
        
        // Always add the Android/data specific vault for testing Shizuku
        result.add(StorageInfo("App Data (Restricted)", Environment.getExternalStorageDirectory().absolutePath + "/Android/data"))
        
        return result
    }

    private fun getVolumePath(volume: StorageVolume): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val directoryFile = volume.directory
                if (directoryFile != null) {
                    return directoryFile.absolutePath
                }
            }
            // Fallback to reflection
            val method = volume.javaClass.getMethod("getPath")
            val path = method.invoke(volume) as? String
            if (path != null) return path
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
