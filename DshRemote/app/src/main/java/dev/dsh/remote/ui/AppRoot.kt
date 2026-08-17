package dev.dsh.remote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun AppRoot(vm: AppViewModel) {
    var showSettings by remember { mutableStateOf(false) }

    // When the app returns to the foreground, re-establish streams and re-check state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onAppResumed()
    }

    if (showSettings) {
        SettingsScreen(vm, onBack = { showSettings = false })
    } else {
        MainScreen(vm, onOpenSettings = { showSettings = true })
    }
}
