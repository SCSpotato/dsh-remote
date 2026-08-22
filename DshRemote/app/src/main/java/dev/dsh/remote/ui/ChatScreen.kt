package dev.dsh.remote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.dsh.remote.data.ChatItem
import dev.dsh.remote.data.textOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import dev.dsh.remote.ui.theme.DshAmber
import dev.dsh.remote.ui.theme.DshGreen
import dev.dsh.remote.ui.theme.DshGreen
import dev.dsh.remote.ui.theme.DshRed
import dev.dsh.remote.ui.theme.DshUserText

@Composable
fun ChatScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val chatItems by vm.chatItems.collectAsState()
    val running by vm.running.collectAsState()
    val models by vm.models.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val streamingReasoning by vm.streamingReasoning.collectAsState()
    val loadingOlder by vm.loadingOlder.collectAsState()
    val loadingSession by vm.loadingSession.collectAsState()
    val currentSessionId by vm.currentSessionId.collectAsState()
    // Only surface decisions that belong to the session currently on screen;
    // the home screen keeps a global "待决策" list so nothing is lost.
    val allApprovals by vm.pendingApprovals.collectAsState()
    val allQuestions by vm.pendingQuestions.collectAsState()
    val pendingApprovals = allApprovals.filter { it.sessionId == currentSessionId }
    val pendingQuestions = allQuestions.filter { it.sessionId == currentSessionId }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val totalItems = chatItems.size +
        (if (streaming.isNotEmpty() || streamingReasoning.isNotEmpty()) 1 else 0) +
        pendingApprovals.size + pendingQuestions.size
    val followTail = remember { mutableStateOf(true) }

    // Follow the tail only while the user is parked at the bottom. The moment
    // they scroll away it unlocks; scrolling back to the very end re-locks.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val atEnd = last != null && last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 1
            atEnd to listState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collect { (atEnd, scrolling) ->
                if (atEnd) followTail.value = true
                else if (scrolling) followTail.value = false
            }
    }

    LaunchedEffect(chatItems.size, streaming.length, streamingReasoning.length, pendingApprovals.size, pendingQuestions.size) {
        if (totalItems > 0 && followTail.value) listState.scrollToBottom()
    }

    // Load older history when the user scrolls to the top.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> if (index <= 1) vm.loadOlder() }
    }

    // Entering the chat (fresh composition or right after a session loads) → jump to bottom.
    LaunchedEffect(loadingSession) {
        if (!loadingSession) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > 0 }
            listState.scrollToBottom()
        }
    }

    val showJumpToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val last = info.visibleItemsInfo.lastOrNull()
            val atEnd = last != null && last.index >= total - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 1
            !atEnd
        }
    }

    if (loadingSession) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(Strings.str("loading"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                if (loadingOlder) {
                    item(key = "loading-older") {
                        Text(Strings.str("load_earlier"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(chatItems) { item ->
                    ChatRow(
                        item,
                        vm::fullReasoning,
                        onFork = { seq -> vm.forkSession(seq) },
                        onCopy = { text -> vm.copyText(text) },
                    )
                }
                if (streamingReasoning.isNotEmpty() || streaming.isNotEmpty()) {
                    item(key = "streaming") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            if (streamingReasoning.isNotEmpty()) {
                                ReasoningBox(
                                    lastLineSummary(streamingReasoning),
                                    onExpanded = { scope.launch { listState.scrollToBottom() } },
                                ) { streamingReasoning }
                            }
                            if (streaming.isNotEmpty()) MarkdownText(streaming)
                        }
                    }
                }
                // Inline decisions: plan review / ask-user questions / approvals sit in
                // the conversation flow (below the messages) rather than covering them.
                items(pendingApprovals, key = { "pending-a-${it.rpcId}" }) { a ->
                    ApprovalCard(vm, a)
                }
                items(pendingQuestions, key = { "pending-q-${it.rpcId}" }) { q ->
                    val first = q.questions.firstOrNull()
                    if (first != null && first.intent?.kind == "plan-review" && first.detail != null) {
                        PlanReviewCard(vm, q, first)
                    } else {
                        QuestionCard(vm, q)
                    }
                }
            }
            if (showJumpToBottom) {
                IconButton(
                    onClick = { scope.launch { listState.scrollToBottom() } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .size(40.dp),
                ) {
                    DshIcon(
                        DshIcons.ChevronDown,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 20.dp,
                        contentDescription = Strings.str("jump_bottom"),
                    )
                }
            }
        }
        Composer(
            vm = vm,
            currentModel = models?.current?.model ?: Strings.str("choose_model"),
            running = running,
        )
    }
}

private const val SCROLL_BOTTOM_OFFSET = 1_000_000_000

private data class PendingImage(val mediaType: String, val base64: String, val name: String?, val bitmap: Bitmap?)

private fun decodeThumbnail(bytes: ByteArray, maxDim: Int = 256): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private suspend fun LazyListState.scrollToBottom() {
    val last = layoutInfo.totalItemsCount - 1
    if (last >= 0) scrollToItem(last, scrollOffset = SCROLL_BOTTOM_OFFSET)
}

@Composable
private fun ReasoningBox(summary: String, onExpanded: () -> Unit = {}, fullProvider: () -> String) {
    var expanded by remember { mutableStateOf(false) }
    var fullText by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                if (expanded) {
                    if (fullText == null) fullText = fullProvider()
                    onExpanded()
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DshIcon(DshIcons.Think, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                Strings.str("reasoning"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) Strings.str("collapse") else Strings.str("expand"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            DshIcon(
                if (expanded) DshIcons.ChevronUp else DshIcons.ChevronDown,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 12.dp,
            )
        }
        Spacer(Modifier.height(2.dp))
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    fullText ?: summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private fun lastLineSummary(text: String): String {
    val line = text.lines().lastOrNull { it.isNotBlank() } ?: ""
    return if (line.length > 80) line.take(80) + "…" else line
}

@Composable
private fun ModeRow(vm: AppViewModel) {
    val plan by vm.currentPlan.collectAsState()
    val permissions by vm.currentPermissions.collectAsState()
    var permMenu by remember { mutableStateOf(false) }
    var cmdMenu by remember { mutableStateOf(false) }
    val sessionId by vm.currentSessionId.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .clickable { cmdMenu = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Strings.str("commands"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                DshIcon(
                    DshIcons.ChevronDown,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 14.dp,
                )
            }
            DropdownMenu(expanded = cmdMenu, onDismissRequest = { cmdMenu = false }) {
                CommandItem("/plan", Strings.str("cmd_plan") + if (plan?.active == true) Strings.str("plan_active") else "") {
                    vm.insertCommand(if (plan?.active == true) "/plan off" else "/plan")
                    cmdMenu = false
                }
                CommandItem("/goal", Strings.str("cmd_goal")) {
                    vm.insertCommand("/goal")
                    cmdMenu = false
                }
                CommandItem("/compact", Strings.str("cmd_compact")) {
                    vm.insertCommand("/compact")
                    cmdMenu = false
                }
            }
        }
        Box {
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .clickable { permMenu = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    permissions?.currentValue ?: Strings.str("permissions"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                DshIcon(
                    DshIcons.ChevronDown,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 14.dp,
                )
            }
            DropdownMenu(expanded = permMenu, onDismissRequest = { permMenu = false }) {
                for (opt in permissions?.options.orEmpty()) {
                    DropdownMenuItem(
                        text = { Text(opt.name) },
                        onClick = {
                            sessionId?.let { vm.setPermission(it, opt.value) }
                            permMenu = false
                        },
                    )
                }
            }
        }
    }
}

/** Highlights leading slash-command tokens (e.g. "/plan") in amber inside the composer. */
private class SlashCommandTransformation(val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '/') {
                var j = i + 1
                while (j < text.length && !text[j].isWhitespace()) j++
                builder.pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold))
                builder.append(text.subSequence(i, j))
                builder.pop()
                i = j
            } else {
                var j = i
                while (j < text.length && text[j] != '/') j++
                builder.append(text.subSequence(i, j))
                i = j
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
private fun CommandItem(name: String, description: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun ChatRow(item: ChatItem, fullReasoningOf: (Long) -> String, onFork: (Long) -> Unit, onCopy: (String) -> Unit) {
    when (item) {
        is ChatItem.User -> {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .widthIn(max = 320.dp),
                ) {
                    MarkdownText(item.text, color = DshUserText)
                }
            }
        }
        is ChatItem.Assistant -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                if (item.reasoning.isNotBlank()) {
                    ReasoningBox(item.reasoning) { fullReasoningOf(item.seq) }
                    Spacer(Modifier.height(4.dp))
                }
                if (item.text.isNotBlank()) {
                    MarkdownText(item.text)
                }
                // Fork / copy only after the LAST output of a turn.
                if (item.isTurnEnd) {
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                .clickable { onFork(item.seq) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            DshIcon(DshIcons.Branch, tint = MaterialTheme.colorScheme.primary, size = 14.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                Strings.str("fork"),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                .clickable { onCopy(item.text) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            DshIcon(DshIcons.Copy, tint = MaterialTheme.colorScheme.primary, size = 14.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                Strings.str("copy"),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        is ChatItem.Tool -> {
            ToolCard(item)
        }
        is ChatItem.Todo -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (t in item.items) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    when (t.status) {
                                        "completed" -> DshGreen
                                        "in_progress" -> DshAmber
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.content,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        is ChatItem.Meta -> {
            Text(
                item.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        is ChatItem.Cost -> {
            Text(
                item.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
        is ChatItem.Deliverables -> {
            DeliverablesRow(item.paths)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliverablesRow(paths: List<String>) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            Strings.str("deliverables"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (p in paths.take(6)) {
                Text(
                    p.substringAfterLast('/').substringAfterLast('\\'),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (paths.size > 6) {
                Text(
                    Strings.str("more_files", paths.size - 6),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun toolIcon(card: String?, name: String): Int = when (card) {
    "diff" -> DshIcons.Edit
    "terminal" -> DshIcons.Api
    "search" -> DshIcons.Search
    "web" -> DshIcons.Globe
    "fs" -> DshIcons.Browse
    else -> when {
        name.startsWith("edit") || name.startsWith("write") || name.startsWith("str_replace") -> DshIcons.Edit
        name.startsWith("read") || name == "glob" || name.startsWith("list") -> DshIcons.Browse
        name == "bash" || name == "pwsh" || name.startsWith("terminal") || name.startsWith("subagent") -> DshIcons.Api
        name.startsWith("search") || name.startsWith("grep") -> DshIcons.Search
        name.startsWith("web") || name == "fetch" -> DshIcons.Globe
        else -> DshIcons.Sparkle
    }
}

@Composable
private fun ToolCard(item: ChatItem.Tool) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val isError = item.isError
    val displayName = when (item.name) {
        "tool/error" -> Strings.str("tool_exec_error")
        "tool/result" -> Strings.str("tool_result")
        else -> item.name
    }
    val summary = item.summary ?: item.arguments.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: ""
    val icon = toolIcon(item.card, item.name)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { expanded = !expanded }
            .background(
                when {
                    isError -> DshRed.copy(alpha = 0.12f)
                    item.isResult -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    else -> DshAmber.copy(alpha = 0.12f)
                },
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isError) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(DshRed, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            val nameColor = when {
                isError -> DshRed
                item.isResult -> DshGreen
                else -> DshAmber
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DshIcon(icon, tint = nameColor, size = 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    displayName,
                    color = nameColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.weight(1f))
            if (item.arguments.isNotBlank()) {
                IconButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("tool", item.arguments))
                    android.widget.Toast.makeText(context, Strings.str("copied"), android.widget.Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(28.dp)) {
                    DshIcon(DshIcons.Copy, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp, contentDescription = Strings.str("copy"))
                }
            }
            Text(
                if (expanded) Strings.str("collapse") else Strings.str("expand"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            DshIcon(
                if (expanded) DshIcons.ChevronUp else DshIcons.ChevronDown,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 12.dp,
            )
        }
        if (!expanded && summary.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            if (item.diffOld != null && item.diffNew != null) {
                DiffView(item)
            } else if (item.arguments.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        item.arguments,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private sealed class DiffLine {
    data class Context(val text: String) : DiffLine()
    data class Removed(val text: String) : DiffLine()
    data class Added(val text: String) : DiffLine()
}

private fun computeDiff(old: String, new: String): List<DiffLine> {
    val o = old.lines()
    val n = new.lines()
    var prefix = 0
    while (prefix < o.size && prefix < n.size && o[prefix] == n[prefix]) prefix++
    var suffix = 0
    while (suffix < o.size - prefix && suffix < n.size - prefix &&
        o[o.size - 1 - suffix] == n[n.size - 1 - suffix]) suffix++
    val out = ArrayList<DiffLine>()
    for (i in 0 until prefix) out.add(DiffLine.Context(o[i]))
    for (i in prefix until o.size - suffix) out.add(DiffLine.Removed(o[i]))
    for (i in prefix until n.size - suffix) out.add(DiffLine.Added(n[i]))
    for (i in o.size - suffix until o.size) out.add(DiffLine.Context(o[i]))
    return out
}

@Composable
private fun DiffView(item: ChatItem.Tool) {
    val lines = remember(item.diffOld, item.diffNew) { computeDiff(item.diffOld ?: "", item.diffNew ?: "") }
    val added = lines.count { it is DiffLine.Added }
    val removed = lines.count { it is DiffLine.Removed }
    Column(Modifier.fillMaxWidth()) {
        if (item.diffPath != null) {
            Text(
                item.diffPath,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                .padding(6.dp),
        ) {
            lines.forEach { line ->
                val (prefix, color, text) = when (line) {
                    is DiffLine.Added -> Triple("+ ", DshGreen, line.text)
                    is DiffLine.Removed -> Triple("- ", DshRed, line.text)
                    is DiffLine.Context -> Triple("  ", MaterialTheme.colorScheme.onSurfaceVariant, line.text)
                }
                Text(
                    prefix + text,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "└ +$added -$removed · 1 file",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun Composer(vm: AppViewModel, currentModel: String, running: Boolean) {
    val draft by vm.draft.collectAsState()
    val queue by vm.queueItems.collectAsState()
    var modelMenu by remember { mutableStateOf(false) }
    val models by vm.models.collectAsState()
    val agentPresets by vm.agentPresets.collectAsState()
    var presetMenu by remember { mutableStateOf(false) }
    var effortMenu by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadAgentPresets() }

    val currentEfforts = models?.groups?.flatMap { it.models }
        ?.firstOrNull { it.id == models?.current?.model }?.reasoning?.efforts.orEmpty()

    val sessions by vm.sessions.collectAsState()
    val currentPreset = sessions.firstOrNull { it.sessionId == vm.currentSessionId.value }?.agentPreset
    val currentEffort = models?.current?.reasoningEffort

    val context = LocalContext.current
    val pickScope = rememberCoroutineScope()
    var pendingImage by remember { mutableStateOf<PendingImage?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickScope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                    val mediaType = context.contentResolver.getType(uri) ?: "image/png"
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val bitmap = decodeThumbnail(bytes)
                    pendingImage = PendingImage(mediaType, base64, uri.lastPathSegment, bitmap)
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
    ) {
        ModeRow(vm)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .clickable { modelMenu = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(currentModel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    DshIcon(DshIcons.ChevronDown, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
                }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    for (group in models?.groups.orEmpty()) {
                        for (m in group.models) {
                            DropdownMenuItem(
                                text = { Text("${group.name} / ${m.name}") },
                                onClick = {
                                    val sessionId = vm.currentSessionId.value
                                    if (sessionId != null) vm.selectModel(sessionId, group.id, m.id, m.reasoning?.defaultEffort)
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }
            }
            if (agentPresets.isNotEmpty()) {
                Box {
                    Row(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .clickable { presetMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(currentPreset ?: Strings.str("preset"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        DshIcon(DshIcons.ChevronDown, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
                    }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        for (p in agentPresets) {
                            DropdownMenuItem(
                                text = { Text(p.name ?: p.id) },
                                onClick = { vm.selectAgentPreset(p.id); presetMenu = false },
                            )
                        }
                    }
                }
            }
            if (currentEfforts.isNotEmpty()) {
                Box {
                    Row(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .clickable { effortMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(currentEffort ?: Strings.str("reasoning_effort"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        DshIcon(DshIcons.ChevronDown, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
                    }
                    DropdownMenu(expanded = effortMenu, onDismissRequest = { effortMenu = false }) {
                        for (e in currentEfforts) {
                            DropdownMenuItem(
                                text = { Text(e.name) },
                                onClick = { vm.selectReasoningEffort(e.id); effortMenu = false },
                            )
                        }
                    }
                }
            }
        }
        if (queue.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DshIcon(DshIcons.Queue, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        Strings.str("msg_queue", queue.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                queue.forEachIndexed { i, item ->
                    val msg = textOf(item.message.content)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}. ${msg.take(60)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editingId = item.id; editText = msg },
                        )
                        DshIcon(
                            DshIcons.CloseFill,
                            tint = DshRed,
                            size = 14.dp,
                            modifier = Modifier
                                .clickable { vm.removeQueued(item.id) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        pendingImage?.let { img ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                img.bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = Strings.str("attachment"),
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    img.name ?: Strings.str("image_attachment"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { pendingImage = null }) { Text(Strings.str("remove"), style = MaterialTheme.typography.labelSmall, color = DshRed) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { vm.setDraft(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(Strings.str("input_hint")) },
                maxLines = 5,
                visualTransformation = SlashCommandTransformation(DshAmber),
            )
            if (running) {
                IconButton(
                    onClick = { vm.cancel() },
                    modifier = Modifier.size(48.dp),
                ) {
                    DshIcon(
                        DshIcons.Stop,
                        tint = DshRed,
                        size = 20.dp,
                        contentDescription = Strings.str("stop"),
                    )
                }
            }
            IconButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.size(48.dp),
            ) {
                DshIcon(DshIcons.Paperclip, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
            }
            IconButton(
                onClick = {
                    val img = pendingImage
                    val sessionId = vm.currentSessionId.value
                    if (img != null && sessionId != null) {
                        vm.sendImageText(sessionId, img.mediaType, img.base64, img.name, draft)
                        pendingImage = null
                    } else if (running) vm.enqueue(draft) else vm.send(draft)
                },
                enabled = draft.isNotBlank() || pendingImage != null,
                modifier = Modifier.size(48.dp),
            ) {
                DshIcon(
                    DshIcons.Send,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 20.dp,
                    contentDescription = if (running) Strings.str("enqueue") else Strings.str("send"),
                )
            }
        }
    }

    editingId?.let { id ->
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text(Strings.str("edit_queue_msg")) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.editQueued(id, editText); editingId = null }) { Text(Strings.str("ok")) }
            },
            dismissButton = {
                TextButton(onClick = { editingId = null }) { Text(Strings.str("cancel")) }
            },
        )
    }
}
