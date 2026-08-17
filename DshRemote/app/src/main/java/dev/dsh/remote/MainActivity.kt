package dev.dsh.remote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.dsh.remote.service.DshForegroundService
import dev.dsh.remote.ui.AppRoot
import dev.dsh.remote.ui.AppViewModel
import dev.dsh.remote.ui.theme.DshTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        requestNotificationPermission()
        // A tapped notification deep-links into its conversation.
        intent.getStringExtra(DshForegroundService.EXTRA_SESSION_ID)?.let { vm.openSessionWhenReady(it) }
        setContent {
            val themePreference by vm.themePreference.collectAsState()
            DshTheme(preference = themePreference) {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Notification tapped while the activity is already alive → jump to that session.
        intent.getStringExtra(DshForegroundService.EXTRA_SESSION_ID)?.let { vm.openSessionWhenReady(it) }
    }

    /** Persist uncaught crashes to a file so they can be diagnosed after the fact. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null) ?: cacheDir
                val file = File(dir, "crash-log.txt")
                val entry = buildString {
                    appendLine("=== ${java.util.Date()} ===")
                    appendLine(throwable.toString())
                    for (el in throwable.stackTrace) appendLine("  at $el")
                    appendLine()
                }
                val existing = if (file.exists()) file.readText() else ""
                file.writeText(existing + entry)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}
