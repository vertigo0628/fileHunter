package com.vertigo.opposite

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vertigo.opposite.ui.theme.OppositeAppTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 101 && grantResult == PackageManager.PERMISSION_GRANTED) {
                // Shizuku permission granted – UI will react via ShizukuHelper.checkPermission()
            }
        }

    // Launcher for Android 11+ "All files access" settings screen
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)

        // Request MANAGE_EXTERNAL_STORAGE on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.fromParts("package", packageName, null)
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    // Fallback: open general all-files settings if specific URI not supported
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        }

        setContent {
            OppositeAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
    }
}
