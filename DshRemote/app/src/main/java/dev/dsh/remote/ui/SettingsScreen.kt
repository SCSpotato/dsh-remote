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
    var url by remember { mutableStateOf(serverUrl) }
    var showBalance by remember { mutableStateOf(false) }

    if (showBalance) {
        BalanceScreen(vm, onBack = { showBalance = false })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                DshIcon(DshIcons.ChevronLeft, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp, contentDescription = "返回")
            }
            Text("设置", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))

        // ---- connection ----
        Text("服务器地址", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("https://host:port") },
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "例如 https://desktop-e0lt97r.tailcf2bf3.ts.net:8443",
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
            Text("保存并连接")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (connected) "● 已连接" else "● 未连接",
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

        // ---- appearance ----
        Text("外观", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        AppearanceRow(current = themePreference, onSelect = vm::setThemePreference)

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- notifications ----
        Text("通知", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        SettingToggleRow(
            title = "任务完成提醒",
            subtitle = "回合完成时发送通知并播放提示音",
            checked = notifyDone,
            onCheckedChange = { vm.setNotifyDone(it) },
        )
        Spacer(Modifier.height(8.dp))
        SettingToggleRow(
            title = "提问 / 批准提醒",
            subtitle = "AI 提问或请求批准时发送通知",
            checked = notifyPrompt,
            onCheckedChange = { vm.setNotifyPrompt(it) },
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- DeepSeek platform ----
        Text("DeepSeek 平台", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showBalance = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("查询余额 / 用量")
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- about ----
        Text("关于", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "DSH Remote v${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AppearanceRow(current: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "light" to "浅色",
        "dark" to "深色",
        "system" to "跟随系统",
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
