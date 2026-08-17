package dev.dsh.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dsh.remote.data.ChatItem
import dev.dsh.remote.data.SubagentEntry
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons

@Composable
fun SubagentScreen(vm: AppViewModel, onBack: () -> Unit) {
    val subagents by vm.subagents.collectAsState()
    val chat by vm.subagentChat.collectAsState()
    val selectedId by vm.subagentId.collectAsState()

    LaunchedEffect(Unit) { vm.loadSubagents() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (selectedId != null) vm.subagentBack() else onBack()
            }) {
                DshIcon(DshIcons.ChevronLeft, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp, contentDescription = "返回")
            }
            Text(if (selectedId == null) "子代理" else "子代理对话", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        if (selectedId == null) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(subagents, key = { it.id }) { e ->
                    SubagentRow(e, onClick = { vm.openSubagent(e) }, onInterrupt = { vm.interruptSubagent(e) })
                }
                if (subagents.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("暂无子代理", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(chat) { item -> SubagentChatRow(item) }
            }
        }
    }
}

@Composable
private fun SubagentRow(e: SubagentEntry, onClick: () -> Unit, onInterrupt: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(8.dp).height(8.dp).background(if (e.activity == "running") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(e.label ?: e.id, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(e.activity ?: e.mode ?: e.kind, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        if (e.activity == "running" && e.mode == "continuable") {
            TextButton(onClick = onInterrupt) { Text("中断", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun SubagentChatRow(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            Box(Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                MarkdownText(item.text, color = dev.dsh.remote.ui.theme.DshUserText)
            }
        }
        is ChatItem.Assistant -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (item.text.isNotBlank()) MarkdownText(item.text)
        }
        is ChatItem.Tool -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            DshIcon(
                if (item.isResult) DshIcons.Check else DshIcons.Sparkle,
                tint = dev.dsh.remote.ui.theme.DshAmber,
                size = 12.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                item.name,
                color = dev.dsh.remote.ui.theme.DshAmber,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> {}
    }
}
