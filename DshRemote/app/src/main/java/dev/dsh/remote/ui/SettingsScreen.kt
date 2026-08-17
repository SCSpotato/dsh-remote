package dev.dsh.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dsh.remote.BuildConfig
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val serverUrl by vm.serverUrl.collectAsState()
    val connected by vm.connected.collectAsState()
    val error by vm.error.collectAsState()
    val notifyDone by vm.notifyDone.collectAsState()
    val notifyPrompt by vm.notifyPrompt.collectAsState()
    val themePreference by vm.themePreference.collectAsState()
    val language by vm.language.collectAsState()
    var url by remember { mutableStateOf(serverUrl) }
    var showBalance by remember { mutableStateOf(false) }

    if (showBalance) {
        BalanceScreen(vm, onBack = { showBalance = false })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                DshIcon(DshIcons.ChevronLeft, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp, contentDescription = Strings.str("back"))
            }
            Text(Strings.str("settings"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))

        // ---- connection ----
        Text(Strings.str("server_url"), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(Strings.str("server_url_hint")) },
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            Strings.str("server_url_example"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                vm.setServerUrl(url)
                vm.connect(url)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(Strings.str("save_and_connect"))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (connected) Strings.str("connected") else Strings.str("disconnected"),
            color = if (connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- language ----
        Text(Strings.str("language"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LanguageRow(current = language, onSelect = vm::setLanguage)

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- appearance ----
        Text(Strings.str("appearance"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        AppearanceRow(current = themePreference, onSelect = vm::setThemePreference)

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- notifications ----
        Text(Strings.str("notifications"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        SettingToggleRow(
            title = Strings.str("notify_done"),
            subtitle = Strings.str("notify_done_sub"),
            checked = notifyDone,
            onCheckedChange = { vm.setNotifyDone(it) },
        )
        Spacer(Modifier.height(8.dp))
        SettingToggleRow(
            title = Strings.str("notify_prompt"),
            subtitle = Strings.str("notify_prompt_sub"),
            checked = notifyPrompt,
            onCheckedChange = { vm.setNotifyPrompt(it) },
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- DeepSeek platform ----
        Text(Strings.str("deepseek_platform"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showBalance = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(Strings.str("check_balance"))
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- about ----
        Text(Strings.str("about"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "DSH Remote v${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LanguageRow(current: String, onSelect: (String) -> Unit) {
    val options = listOf(
        Strings.ZH to "中文",
        Strings.EN to "English",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val selected = current == value
            Column(
                Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun AppearanceRow(current: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "light" to Strings.str("light"),
        "dark" to Strings.str("dark"),
        "system" to Strings.str("follow_system"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val selected = current == value
            Column(
                Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DshIcon(
                    when (value) {
                        "light" -> DshIcons.Light
                        "dark" -> DshIcons.Dark
                        else -> DshIcons.FollowSystem
                    },
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
