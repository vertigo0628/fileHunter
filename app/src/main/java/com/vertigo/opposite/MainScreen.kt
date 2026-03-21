package com.vertigo.opposite

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.vertigo.opposite.ui.theme.GhostIconColor
import com.vertigo.opposite.ui.theme.LighterRed
import com.vertigo.opposite.ui.theme.PhotoIconColor
import com.vertigo.opposite.ui.theme.SystemIconColor
import com.vertigo.opposite.ui.theme.VaultIconColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.activity.compose.BackHandler

data class ScreenState(val path: String, val files: List<FileModel>)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Our app's memory: where we are, what we see, and where we've been
    var currentPath by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<FileModel>>(emptyList()) }
    var history by remember { mutableStateOf<List<ScreenState>>(emptyList()) } // This powers the "Back" button
    
    // Feature toggles and state
    var showHiddenMedia by remember { mutableStateOf(false) }
    var isShizukuPermitted by remember { mutableStateOf(ShizukuHelper.checkPermission()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load root storage volumes on init
    LaunchedEffect(Unit) {
        val storageVolumes = StorageManagerHelper.getStorageVolumes(context)
        files = storageVolumes.map {
            FileModel(
                name = it.title,
                path = it.path,
                isDirectory = true,
                size = 0,
                isVault = false,
                isPhoto = false,
                isHidden = false
            )
        }
    }

    // This grabs the files for whatever folder we want to look at next
    fun loadPath(path: String) {
        coroutineScope.launch {
            isLoading = true
            
            // If we're looking inside Android/data, we need our Shizuku superpower
            val isRestricted = path.contains("/Android/data")
            val loadedFiles = withContext(Dispatchers.IO) {
                ShizukuHelper.listFiles(context, path, isRestricted && isShizukuPermitted)
            }
            
            // Save where we are before we move, so we can go back later!
            if (currentPath != path && currentPath.isNotEmpty()) {
                history = history + ScreenState(currentPath, files)
            }
            files = loadedFiles
            currentPath = path
            isLoading = false
        }
    }

    // The magical button that searches the entire phone for hidden secrets
    fun runDeepScan() {
        if (!isShizukuPermitted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Shizuku is required for Deep Scan")
            }
            return
        }
        coroutineScope.launch {
            isLoading = true
            snackbarHostState.showSnackbar("Scanning for vaults...", duration = SnackbarDuration.Short)
            
            // Go find 'em all!
            val vaults = withContext(Dispatchers.IO) {
                ShizukuHelper.findVaults(context, "/storage/emulated/0")
            }
            
            // Save our current spot so the user can easily back out
            if (currentPath.isNotEmpty()) {
                 history = history + ScreenState(currentPath, files)
            }
            
            files = vaults
            currentPath = "Deep Scan Results"
            isLoading = false
        }
    }

    // Copies a file out of the vault into a safe cache so other apps (like a Gallery) can see it
    fun openFile(file: FileModel) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Opening ${file.name}...", duration = SnackbarDuration.Short)
            
            // Do the heavy lifting in the background
            val tempFile = withContext(Dispatchers.IO) {
                ShizukuHelper.copyFileToCache(file.path, context)
            }

            if (tempFile != null && tempFile.exists()) {
                try {
                    val authority = "${context.packageName}.provider"
                    val uri: Uri = FileProvider.getUriForFile(context, authority, tempFile)
                    
                    val extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(tempFile).toString())
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error: No app found to open this file")
                }
            } else {
                snackbarHostState.showSnackbar("Failed to prepare file for opening")
            }
        }
    }

    fun goBack() {
        if (history.isNotEmpty()) {
            val prevState = history.last()
            history = history.dropLast(1)
            currentPath = prevState.path
            files = prevState.files
        }
    }

    BackHandler(enabled = history.isNotEmpty()) {
        goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "fileHunter (~By vertiGO!)",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        TextButton(
                            onClick = { runDeepScan() },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Deep Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Hunter",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = showHiddenMedia,
                            onCheckedChange = { showHiddenMedia = it }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // ── Shizuku status banner ──────────────────────────────────────
            if (!isShizukuPermitted) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Shizuku not active — restricted paths unavailable",
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (ShizukuHelper.isShizukuAvailable()) {
                                    ShizukuHelper.requestPermission(101)
                                    isShizukuPermitted = ShizukuHelper.checkPermission()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Check Permission", fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Breadcrumb / Back bar ──────────────────────────────────────
            if (currentPath.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { goBack() },
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "← $currentPath",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 13.sp
                    )
                }
            }

            // ── Loading indicator ──────────────────────────────────────────
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── File list ─────────────────────────────────────────────────
            val visibleFiles = if (showHiddenMedia) files else files.filter { !it.isHidden }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleFiles, key = { it.path }) { file ->
                    FileListItem(
                        file = file,
                        showHiddenMedia = showHiddenMedia,
                        onClick = {
                            if (file.isDirectory) {
                                loadPath(file.path)
                            } else {
                                openFile(file)
                            }
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
}

@Composable
fun FileListItem(file: FileModel, showHiddenMedia: Boolean, onClick: () -> Unit) {

    val backgroundColor = if (showHiddenMedia && file.isVault) {
        LighterRed.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }

    val icon = when {
        file.isVault && file.isDirectory -> Icons.Default.Lock
        file.isPhoto                     -> Icons.Default.Image
        file.isDirectory                 -> Icons.Default.Folder
        else                             -> Icons.Default.InsertDriveFile
    }

    val iconTint = when {
        file.isVault && file.isDirectory -> VaultIconColor
        file.isPhoto                     -> PhotoIconColor
        file.isHidden                    -> GhostIconColor
        file.isDirectory                 -> SystemIconColor
        else                             -> SystemIconColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(36.dp)
                .padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.label ?: file.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (file.label != null) file.name else file.path,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = file.displaySize,
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
