package dev.dsh.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsh.remote.data.SessionEvent
import dev.dsh.remote.data.SessionStats
import dev.dsh.remote.data.TimelineSpan
import dev.dsh.remote.data.TokenUsageView
import dev.dsh.remote.data.ContextPressureView
import dev.dsh.remote.data.ContextBreakdownView
import dev.dsh.remote.data.textOf
import dev.dsh.remote.data.timelineSpansOf
import dev.dsh.remote.data.toolResultText
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import dev.dsh.remote.ui.theme.DshAmber
import dev.dsh.remote.ui.theme.DshContextMessages
import dev.dsh.remote.ui.theme.DshContextSystem
import dev.dsh.remote.ui.theme.DshContextTools
import dev.dsh.remote.ui.theme.DshGreen
import dev.dsh.remote.ui.theme.DshLaneInput
import dev.dsh.remote.ui.theme.DshLaneModel
import dev.dsh.remote.ui.theme.DshLaneTool
import dev.dsh.remote.ui.theme.DshRed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val LANE_INPUT = 0
private const val LANE_MODEL = 1
private const val LANE_TOOL = 2

private val TRAJECTORY_TYPES = setOf(
    "turn/start", "turn/end", "user/message", "assistant/message",
    "tool/call", "tool/result", "todo/write", "compaction/summary",
)

@Composable
fun TrajectoryScreen(vm: AppViewModel) {
    val events by vm.events.collectAsState()
    val stats by vm.currentStats.collectAsState()
    val tokenUsage by vm.currentTokenUsage.collectAsState()
    val contextPressure by vm.currentContextPressure.collectAsState()
    val contextBreakdown by vm.currentContextBreakdown.collectAsState()
    val fullSpans by vm.fullTimelineSpans.collectAsState()
    val sessionId by vm.currentSessionId.collectAsState()
    val listState = rememberLazyListState()
    var timelineMode by remember { mutableStateOf("sequence") }

    // Fetch the full paginated history once so the timeline covers start→end.
    LaunchedEffect(sessionId) {
        val sid = sessionId
        if (sid != null) vm.ensureFullTimeline(sid)
    }

    val displayEvents = remember(events) { events.filter { it.type in TRAJECTORY_TYPES } }
    // Prefer the complete timeline; fall back to loaded events while it fills in.
    val spans = if (fullSpans.isNotEmpty()) fullSpans else remember(events) { timelineSpansOf(events) }

    // Ledger shows the most recent records; reveal more older records on scroll-to-top.
    var visibleCount by remember { mutableIntStateOf(5) }
    val visibleEvents = remember(displayEvents, visibleCount) {
        displayEvents.takeLast(visibleCount.coerceIn(0, displayEvents.size))
    }

    // Turn collapse + tool-call detail.
    var collapsedTurns by remember { mutableStateOf(setOf<String>()) }
    var toolDetail by remember { mutableStateOf<SessionEvent?>(null) }
    val shownEvents = remember(visibleEvents, collapsedTurns) {
        val out = ArrayList<SessionEvent>()
        var skip = false
        for (ev in visibleEvents) {
            when (ev.type) {
                "turn/start" -> {
                    val turn = ev.data["turn"]?.jsonPrimitive?.content ?: ""
                    skip = turn in collapsedTurns
                    out.add(ev)
                }
                "turn/end" -> if (!skip) out.add(ev)
                else -> if (!skip) out.add(ev)
            }
        }
        out
    }

    // Start the ledger at the bottom (most recent records) once per session.
    LaunchedEffect(sessionId) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index <= 0) {
                    if (visibleCount < displayEvents.size) visibleCount += 5
                    else vm.loadOlder()
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        TimelineModeRow(timelineMode) { timelineMode = it }
        TimelineBar(spans, timelineMode)
        StatsBar(stats, tokenUsage)
        ContextMeter(contextPressure, contextBreakdown)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
        ) {
            items(shownEvents, key = { it.seq }) { ev ->
                TrajectoryRow(
                    ev,
                    collapsedTurns = collapsedTurns,
                    onToggleTurn = { turn ->
                        collapsedTurns = if (turn in collapsedTurns) collapsedTurns - turn else collapsedTurns + turn
                    },
                    onToolClick = { toolDetail = it },
                )
            }
        }
    }

    toolDetail?.let { ev ->
        AlertDialog(
            onDismissRequest = { toolDetail = null },
            title = { Text(ev.data["name"]?.jsonPrimitive?.content ?: Strings.str("tool_label")) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    val args = ev.data["arguments"]?.jsonPrimitive?.content ?: ""
                    if (args.isNotBlank()) {
                        Text(Strings.str("params"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text(args, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ev.type == "tool/result") {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.str("result"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text(
                            toolResultText(ev.data).ifBlank { if (ev.data["error"] != null) Strings.str("exec_error") else Strings.str("no_output") },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { toolDetail = null }) { Text(Strings.str("close")) } },
        )
    }
}

@Composable
private fun spanColor(lane: Int): Color = when (lane) {
    LANE_INPUT -> DshLaneInput
    LANE_MODEL -> DshLaneModel
    else -> DshLaneTool
}

private data class RenderedSpan(val lane: Int, val isError: Boolean, val leftFrac: Float, val widthFrac: Float)

private fun layoutSpans(spans: List<TimelineSpan>, mode: String): List<RenderedSpan> {
    if (spans.isEmpty()) return emptyList()
    val total = spans.size
    return when (mode) {
        "duration" -> {
            val totalDur = spans.sumOf { it.durationMs.coerceAtLeast(1) }.toFloat()
            if (totalDur <= 0f) spans.mapIndexed { i, s -> RenderedSpan(s.lane, s.isError, i.toFloat() / total, 1f / total) }
            else {
                var acc = 0f
                spans.map { s ->
                    val w = s.durationMs.coerceAtLeast(1).toFloat() / totalDur
                    val left = acc
                    acc += w
                    RenderedSpan(s.lane, s.isError, left, w)
                }
            }
        }
        "time" -> {
            val min = spans.minOf { it.time }
            val range = (spans.maxOf { it.time } - min).coerceAtLeast(1).toFloat()
            val fixedW = (1f / total).coerceAtMost(0.02f)
            spans.map { s -> RenderedSpan(s.lane, s.isError, (s.time - min) / range, fixedW) }
        }
        "actual" -> {
            val min = spans.minOf { it.time }
            val range = (spans.maxOf { it.time } - min).coerceAtLeast(1).toFloat()
            spans.map { s -> RenderedSpan(s.lane, s.isError, (s.time - min) / range, s.durationMs.coerceAtLeast(1).toFloat() / range) }
        }
        else -> spans.mapIndexed { i, s -> RenderedSpan(s.lane, s.isError, i.toFloat() / total, 1f / total) }
    }
}

@Composable
private fun TimelineModeRow(mode: String, onSelect: (String) -> Unit) {
    val modes = listOf("sequence" to Strings.str("mode_sequence"), "time" to Strings.str("mode_time"), "duration" to Strings.str("mode_duration"), "actual" to Strings.str("mode_actual"))
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((m, label) in modes) {
            val selected = mode == m
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else null,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onSelect(m) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Three parallel lanes (input / model / tools) in one 50dp strip, matching DSH. */
@Composable
private fun TimelineBar(spans: List<TimelineSpan>, mode: String) {
    val rendered = remember(spans, mode) { layoutSpans(spans, mode) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            Modifier.width(44.dp).fillMaxHeight().padding(end = 3.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            TimelineLaneLabel(Strings.str("lane_input"), 7.dp, DshLaneInput)
            TimelineLaneLabel(Strings.str("lane_model"), 21.dp, DshLaneModel)
            TimelineLaneLabel(Strings.str("lane_tool"), 35.dp, DshLaneTool)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val trackWidth = maxWidth
            for (r in rendered) {
                val x = trackWidth * r.leftFrac
                val w = (trackWidth * r.widthFrac - 2.dp).coerceAtLeast(2.dp)
                Box(
                    Modifier
                        .offset(x = x, y = (7 + r.lane * 14).dp)
                        .width(w)
                        .height(8.dp)
                        .background(
                            if (r.isError) DshRed else spanColor(r.lane),
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun TimelineLaneLabel(text: String, top: Dp, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier.offset(y = top),
    )
}

@Composable
private fun StatsBar(stats: SessionStats?, tokenUsage: TokenUsageView?) {
    if (stats == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCell(Strings.str("stat_turns"), stats.turns.toString())
        StatCell(Strings.str("stat_steps"), stats.steps.toString())
        StatCell(Strings.str("stat_model"), fmtMs(stats.llmMs))
        StatCell(Strings.str("stat_tool"), fmtMs(stats.toolMs))
        StatCell(Strings.str("stat_ttft"), fmtMs(if (stats.ttftSteps > 0) stats.ttftMs / stats.ttftSteps else 0))
        if (tokenUsage != null) {
            StatCell(Strings.str("stat_input_tokens"), fmtTokens(tokenUsage.billedInput))
            StatCell(Strings.str("stat_cache_hits"), cacheHitPercent(tokenUsage))
            StatCell(Strings.str("stat_output_tokens"), fmtTokens(tokenUsage.outputTokens))
        } else {
            StatCell(Strings.str("stat_output_tokens"), stats.decodeTokens.toString())
        }
    }
}

private fun cacheHitPercent(usage: TokenUsageView): String {
    val billed = usage.billedInput
    return if (billed <= 0) "—" else "${usage.cacheReadTokens * 100 / billed}%"
}

private fun fmtTokens(n: Long): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(java.util.Locale.US, "%.0fk", n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun StatCell(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

/** Context occupancy meter: "上下文已用 X% · ~used / window" + a category bar. */
@Composable
private fun ContextMeter(pressure: ContextPressureView?, breakdown: ContextBreakdownView?) {
    val window = pressure?.contextWindow ?: return
    if (window <= 0) return
    val used = (pressure.projectedTokens.takeIf { it > 0 } ?: pressure.pressureTokens)
    if (used <= 0) return
    val percent = (used * 100f / window).coerceIn(0f, 100f)

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Strings.str("ctx_used", percent.toInt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "~${fmtTokens(used)} / ${fmtTokens(window)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            val total = breakdown?.total ?: 0L
            if (breakdown != null && total > 0) {
                val f = percent / 100f
                SegmentBox(f * breakdown.systemTokens / total, DshContextSystem)
                SegmentBox(f * breakdown.toolsTokens / total, DshContextTools)
                SegmentBox(f * breakdown.messageTokens / total, DshContextMessages)
            } else {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent / 100f)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
        }
        if (breakdown != null && breakdown.total > 0) {
            Spacer(Modifier.height(6.dp))
            ContextLegendRow(Strings.str("ctx_system"), breakdown.systemTokens, DshContextSystem)
            ContextLegendRow(Strings.str("ctx_tools"), breakdown.toolsTokens, DshContextTools)
            ContextLegendRow(Strings.str("ctx_messages"), breakdown.messageTokens, DshContextMessages)
        }
    }
}

@Composable
private fun SegmentBox(fraction: Float, color: Color) {
    if (fraction <= 0f) return
    Box(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(fraction.coerceAtMost(1f))
            .background(color, RoundedCornerShape(1.dp)),
    )
}

@Composable
private fun ContextLegendRow(label: String, tokens: Long, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            "~${fmtTokens(tokens)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun TrajectoryRow(
    ev: SessionEvent,
    collapsedTurns: Set<String>,
    onToggleTurn: (String) -> Unit,
    onToolClick: (SessionEvent) -> Unit,
) {
    val mono = FontFamily.Monospace
    when (ev.type) {
        "turn/start" -> {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val turn = ev.data["turn"]?.jsonPrimitive?.content ?: ""
            val collapsed = turn in collapsedTurns
            Row(
                Modifier.fillMaxWidth().clickable { onToggleTurn(turn) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DshIcon(
                    if (collapsed) DshIcons.ChevronRight else DshIcons.ChevronDown,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 12.dp,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    Strings.str("turn_fmt", turn),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        "turn/end" -> {
            val reason = ev.data["reason"]?.jsonObject?.get("kind")?.jsonPrimitive?.content ?: ""
            Text(
                Strings.str("turn_end_fmt", reason),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        "user/message" -> {
            Text(
                Strings.str("you_prefix", textOf(ev.data["content"])),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        "assistant/message" -> {
            val msg = ev.data["message"]?.jsonObject
            val text = textOf(msg?.get("content"))
            if (text.isNotBlank()) {
                Text(text, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        "tool/call" -> {
            val name = ev.data["name"]?.jsonPrimitive?.content ?: "tool"
            val step = ev.data["step"]?.jsonPrimitive?.content ?: ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onToolClick(ev) }.padding(vertical = 1.dp),
            ) {
                DshIcon(DshIcons.Sparkle, tint = DshAmber, size = 12.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "$name  (step $step)",
                    color = DshAmber,
                    fontFamily = mono,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        "tool/result" -> {
            val isError = ev.data["error"] != null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onToolClick(ev) }.padding(vertical = 1.dp),
            ) {
                DshIcon(
                    if (isError) DshIcons.CloseFill else DshIcons.Check,
                    tint = if (isError) MaterialTheme.colorScheme.error else DshGreen,
                    size = 12.dp,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    Strings.str("tool_result"),
                    color = if (isError) MaterialTheme.colorScheme.error else DshGreen,
                    fontFamily = mono,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        "todo/write" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                DshIcon(DshIcons.Checklist, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 12.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    Strings.str("todo_update"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        else -> {
            // skip
        }
    }
}

private fun fmtMs(ms: Long): String = when {
    ms >= 60_000 -> Strings.str("min_sec_fmt", ms / 60_000, (ms % 60_000) / 1000)
    ms >= 1000 -> Strings.str("secs_dec_fmt", ms / 1000, (ms % 1000) / 100)
    else -> "${ms}ms"
}
