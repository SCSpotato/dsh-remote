package dev.dsh.remote.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.dsh.remote.data.DirEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FileBrowserScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    pickMode: Boolean = false,
    onPickDirectory: (String) -> Unit = {},
) {
    val path by vm.dirPath.collectAsState()
    val parent by vm.dirParent.collectAsState()
    val entries by vm.dirEntries.collectAsState()
    val loading by vm.dirLoading.collectAsState()
    val error by vm.dirError.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageName by remember { mutableStateOf("") }
    var actionError by remember { mutableStateOf<String?>(null) }
    var downloaded by remember { mutableStateOf<Pair<String, File>?>(null) } // name -> saved file
    var downloading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var fileMenuTarget by remember { mutableStateOf<DirEntry?>(null) }
    var renameTarget by remember { mutableStateOf<DirEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DirEntry?>(null) }
    var showMkdir by remember { mutableStateOf(false) }
    var mkdirText by remember { mutableStateOf("") }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val dir = path ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val name = queryDisplayName(context, uri) ?: "upload-${System.currentTimeMillis()}"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                }
                if (bytes.isEmpty()) {
                    actionError = Strings.str("cannot_read_file")
                    return@launch
                }
                if (bytes.size > 30_000_000) {
                    actionError = Strings.str("file_too_large")
                    return@launch
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                vm.uploadFile(dir, name, b64) { err ->
                    if (err != null) actionError = err
                }
            } catch (e: Exception) {
                actionError = e.message ?: Strings.str("upload_failed")
            } finally {
                uploading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                DshIcon(DshIcons.ChevronLeft, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp, contentDescription = Strings.str("back"))
            }
            if (parent != null) {
                IconButton(onClick = { vm.navigateUp() }) {
                    DshIcon(DshIcons.ChevronUp, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp, contentDescription = Strings.str("parent_dir"))
                }
            }
            Text(
                path ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (pickMode) {
                TextButton(onClick = { path?.let(onPickDirectory) }) { Text(Strings.str("choose_this_dir")) }
            } else {
                TextButton(
                    enabled = path != null,
                    onClick = { mkdirText = ""; showMkdir = true },
                ) { Text(Strings.str("new_folder")) }
                TextButton(
                    enabled = !uploading,
                    onClick = { uploadLauncher.launch("*/*") },
                ) { Text(if (uploading) Strings.str("uploading") else Strings.str("upload")) }
            }
            if (downloading || uploading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(Strings.str("loading"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.listDir(path) }) { Text(Strings.str("retry")) }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(entries, key = { it.path }) { e ->
                        FileRow(
                            e,
                            onClick = {
                                if (e.isDirectory) {
                                    vm.listDir(e.path)
                                } else if (!pickMode) {
                                    scope.launch {
                                        downloading = true
                                        val saved = try {
                                            val base = context.getExternalFilesDir(null) ?: context.cacheDir
                                            val dir = File(base, "DshDownloads").apply { mkdirs() }
                                            val target = File(dir, e.name)
                                            vm.downloadFileTo(e.path, target)
                                            target
                                        } catch (ex: Exception) {
                                            actionError = ex.message ?: Strings.str("download_failed")
                                            null
                                        } finally {
                                            downloading = false
                                        }
                                        if (saved == null) return@launch
                                        withContext(Dispatchers.Main) {
                                            if (isInAppImage(e.name)) {
                                                val bmp = decodeScaledFile(saved.absolutePath, 1600)
                                                if (bmp != null) {
                                                    imageBitmap = bmp
                                                    imageName = e.name
                                                } else {
                                                    downloaded = e.name to saved
                                                }
                                            } else {
                                                downloaded = e.name to saved
                                            }
                                        }
                                    }
                                }
                            },
                            onLongClick = { if (!pickMode) fileMenuTarget = e },
                        )
                    }
                    if (entries.isEmpty()) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(Strings.str("empty_dir"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    imageBitmap?.let { bmp ->
        AlertDialog(
            onDismissRequest = { imageBitmap = null },
            title = { Text(imageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = imageName,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { imageBitmap = null }) { Text(Strings.str("close")) } },
        )
    }

    downloaded?.let { (name, file) ->
        AlertDialog(
            onDismissRequest = { downloaded = null },
            title = { Text(Strings.str("downloaded")) },
            text = { Text(Strings.str("saved_to", file.absolutePath), style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    openDownloadedFile(context, file, name) { actionError = it }
                    downloaded = null
                }) { Text(Strings.str("open")) }
            },
            dismissButton = { TextButton(onClick = { downloaded = null }) { Text(Strings.str("close")) } },
        )
    }

    fileMenuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { fileMenuTarget = null },
            title = { Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = {
                        fileMenuTarget = null
                        renameTarget = target
                        renameText = target.name
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DshIcon(DshIcons.Edit, tint = MaterialTheme.colorScheme.onSurface, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(Strings.str("rename"))
                        }
                    }
                    TextButton(onClick = {
                        fileMenuTarget = null
                        vm.copyFile(target.path) { err -> if (err != null) actionError = err }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DshIcon(DshIcons.Copy, tint = MaterialTheme.colorScheme.onSurface, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(Strings.str("copy"))
                        }
                    }
                    TextButton(onClick = {
                        fileMenuTarget = null
                        deleteTarget = target
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DshIcon(DshIcons.Trash, tint = MaterialTheme.colorScheme.error, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(Strings.str("delete"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fileMenuTarget = null }) { Text(Strings.str("cancel")) } },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(Strings.str("rename")) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    renameTarget = null
                    if (newName.isNotEmpty() && newName != target.name) {
                        vm.renameFile(target.path, newName) { err -> if (err != null) actionError = err }
                    }
                }) { Text(Strings.str("ok")) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(Strings.str("cancel")) } },
        )
    }

    if (showMkdir) {
        AlertDialog(
            onDismissRequest = { showMkdir = false },
            title = { Text(Strings.str("new_folder")) },
            text = {
                OutlinedTextField(
                    value = mkdirText,
                    onValueChange = { mkdirText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = mkdirText.trim()
                    showMkdir = false
                    if (name.isNotEmpty()) vm.mkdir(name) { err -> if (err != null) actionError = err }
                }) { Text(Strings.str("create")) }
            },
            dismissButton = { TextButton(onClick = { showMkdir = false }) { Text(Strings.str("cancel")) } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(Strings.str("delete")) },
            text = { Text(Strings.str("delete_confirm_fmt", target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    vm.deleteFile(target.path) { err -> if (err != null) actionError = err }
                }) { Text(Strings.str("delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(Strings.str("cancel")) } },
        )
    }

    actionError?.let { msg ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text(Strings.str("op_failed")) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text(Strings.str("ok")) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(e: DirEntry, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DshIcon(
            if (e.isDirectory) DshIcons.Folder else DshIcons.Browse,
            tint = if (e.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            size = 20.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            e.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (!e.isDirectory && e.size > 0) {
            Text(
                fmtSize(e.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun isInAppImage(name: String): Boolean = when (name.substringAfterLast('.', "").lowercase()) {
    "png", "jpg", "jpeg", "gif", "webp", "bmp" -> true
    else -> false
}

/** Resolve the real display name (with extension) for a content:// URI. */
private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
} catch (_: Exception) { null }

private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "svg" -> "image/svg+xml"
    "apk" -> "application/vnd.android.package-archive"
    "pdf" -> "application/pdf"
    "txt", "md", "log" -> "text/plain"
    "json" -> "application/json"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

/** Open an already-downloaded file with the system (installer for APKs). */
private fun openDownloadedFile(context: Context, file: File, name: String, onError: (String) -> Unit) {
    try {
        val uri = FileProvider.getUriForFile(context, "dev.dsh.remote.fileprovider", file)
        val mime = mimeOf(name)

        if (mime == "application/vnd.android.package-archive" &&
            android.os.Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        onError(e.message ?: Strings.str("cannot_open_file"))
    }
}

private fun fmtSize(size: Long): String = when {
    size >= 1024 * 1024 -> "${size / (1024 * 1024)}MB"
    size >= 1024 -> "${size / 1024}KB"
    else -> "${size}B"
}

private fun decodeScaledFile(path: String, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)
}
