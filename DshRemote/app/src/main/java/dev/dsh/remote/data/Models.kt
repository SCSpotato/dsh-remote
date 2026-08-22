package dev.dsh.remote.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.dsh.remote.ui.Strings

@Serializable
data class WorkspaceView(
    val workspaceId: String,
    val path: String,
    val title: String,
    val sessionIds: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class WorkspaceListValue(
    val items: List<WorkspaceView> = emptyList(),
    val archivedSessionIds: List<String> = emptyList(),
)

@Serializable
data class DirEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val mtime: Double = 0.0,
)

@Serializable
data class DirectoryListingValue(
    val path: String,
    val parent: String = "",
    val entries: List<DirEntry> = emptyList(),
)

@Serializable
data class SessionSummary(
    val sessionId: String,
    val updatedAt: Long = 0,
    val running: Boolean = false,
    val blank: Boolean = false,
    val cwd: String? = null,
    val agentPreset: String? = null,
    val parentSessionId: String? = null,
    val origin: String? = null,
    val pendingInteraction: String? = null,
    val projections: SessionProjections? = null,
) {
    val title: String get() = projections?.values?.title ?: sessionId
    val isSubagent: Boolean get() = origin == "subagent"
}

@Serializable
data class SessionProjections(
    val asOfSeq: Long = 0,
    val values: ProjectionValues = ProjectionValues(),
)

@Serializable
data class ProjectionValues(
    val title: String? = null,
    val sessionStats: SessionStats? = null,
    val goal: GoalProjection? = null,
    val todos: JsonElement? = null,
    val permissions: Permissions? = null,
    val plan: Plan? = null,
    val tokenUsage: JsonElement? = null,
    val contextPressure: JsonElement? = null,
    val contextBreakdown: JsonElement? = null,
)

@Serializable
data class GoalProjection(
    val goal: GoalInfo? = null,
    val roundsStarted: Int = 0,
)

@Serializable
data class GoalInfo(
    val id: String = "",
    val revision: Int = 0,
    val objective: String = "",
    val phase: String = "",
    val maxGoalRounds: Int = 0,
)

@Serializable
data class SessionStats(
    val turns: Int = 0,
    val steps: Int = 0,
    val llmMs: Long = 0,
    val toolMs: Long = 0,
    val ttftMs: Long = 0,
    val ttftSteps: Int = 0,
    val decodeMs: Long = 0,
    val decodeTokens: Long = 0,
)

@Serializable
data class TokenUsageView(
    val uncachedInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
) {
    val billedInput: Long get() = uncachedInputTokens + cacheReadTokens + cacheWriteTokens
}

/** Per-turn token usage accumulated from `assistant/message` events. */
data class TurnUsage(
    val inputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val outputTokens: Long = 0,
) {
    fun plus(o: TurnUsage): TurnUsage =
        TurnUsage(inputTokens + o.inputTokens, cacheReadTokens + o.cacheReadTokens, outputTokens + o.outputTokens)
}

private const val USD_TO_CNY = 7.2

/**
 * Estimate the turn cost in CNY (¥) using DeepSeek pricing (per 1M tokens, USD):
 * deepseek-chat (V3): miss 0.27, hit 0.07, out 1.10; deepseek-reasoner (R1): miss 0.55, hit 0.14, out 2.19.
 */
fun estimateCostCny(usage: TurnUsage, model: String?): Double {
    val m = model?.lowercase() ?: ""
    val r1 = m.contains("r1") || m.contains("reasoner")
    val miss = if (r1) 0.55 else 0.27
    val hit = if (r1) 0.14 else 0.07
    val out = if (r1) 2.19 else 1.10
    val freshInput = (usage.inputTokens - usage.cacheReadTokens).coerceAtLeast(0)
    val usd = (freshInput * miss + usage.cacheReadTokens * hit + usage.outputTokens * out) / 1_000_000.0
    return usd * USD_TO_CNY
}

/** Compact token count: 1234 -> "1.2k", 1234567 -> "1.2M". */
fun fmtTokens(n: Long): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", n / 1000.0)
    else -> n.toString()
}

@Serializable
data class ContextPressureView(
    val pressureTokens: Long = 0,
    val projectedTokens: Long = 0,
    val contextWindow: Long = 0,
)

@Serializable
data class ContextBreakdownView(
    val systemTokens: Long = 0,
    val toolsTokens: Long = 0,
    val messageTokens: Long = 0,
) {
    val total: Long get() = systemTokens + toolsTokens + messageTokens
}

@Serializable
data class Permissions(
    val options: List<PermissionOption> = emptyList(),
    val currentValue: String? = null,
)

@Serializable
data class PermissionOption(
    val value: String,
    val name: String,
)

@Serializable
data class Plan(
    val active: Boolean = false,
    val pending: Boolean = false,
)

@Serializable
data class SessionListValue(
    val items: List<SessionSummary> = emptyList(),
)

@Serializable
data class SessionEvent(
    val type: String,
    val seq: Long = 0,
    val time: Long = 0,
    val data: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class HistoryEntry(
    val event: SessionEvent,
    val view: JsonElement? = null,
)

@Serializable
data class SessionHistoryValue(
    val events: List<HistoryEntry> = emptyList(),
    val hasMore: Boolean = false,
)

@Serializable
data class ModelSelection(
    val provider: String,
    val model: String,
    val reasoningEffort: String? = null,
)

@Serializable
data class SessionModelsValue(
    val current: ModelSelection? = null,
    val routable: Boolean = false,
    val groups: List<ModelGroup> = emptyList(),
)

@Serializable
data class ModelGroup(
    val id: String,
    val name: String,
    val models: List<ModelInfo> = emptyList(),
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val reasoning: ReasoningInfo? = null,
)

@Serializable
data class ReasoningInfo(
    val efforts: List<ReasoningEffort> = emptyList(),
    val defaultEffort: String? = null,
)

@Serializable
data class ReasoningEffort(
    val id: String,
    val name: String,
)

@Serializable
data class SessionCreateValue(
    val sessionId: String,
    val agentPreset: String? = null,
)

@Serializable
data class PromptAcceptedValue(
    val accepted: Boolean = true,
)

@Serializable
data class GoalRef(
    val id: String? = null,
)

@Serializable
data class JobView(
    val id: String = "",
    val kind: String = "",
    val label: String = "",
    val status: String = "",
    val detail: String? = null,
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
)

/** One entry of the host's native inbox queue (carried by `session/queue`). */
@Serializable
data class QueueItem(
    val id: String = "",
    val placement: String = "",   // queued | steering | context
    val message: QueueMessage = QueueMessage(),
)

@Serializable
data class QueueMessage(
    val id: String = "",
    val role: String = "",
    val content: JsonArray = JsonArray(emptyList()),
)

@Serializable
data class QuestionItem(
    val id: String = "",
    val question: String = "",
    val header: String? = null,
    val detail: String? = null,
    val options: List<QuestionOption> = emptyList(),
    val multiSelect: Boolean = false,
    val intent: QuestionIntent? = null,
)

@Serializable
data class QuestionIntent(
    val kind: String = "",
    val approve: String? = null,
)

@Serializable
data class QuestionOption(
    val label: String = "",
    val description: String? = null,
)

// ---- chat surface model ----------------------------------------------------

sealed class ChatItem {
    data class User(val text: String, val seq: Long) : ChatItem()
    data class Assistant(val text: String, val seq: Long, val reasoning: String = "", val isTurnEnd: Boolean = false) : ChatItem()
    data class Tool(
        val name: String,
        val arguments: String,
        val seq: Long,
        val isResult: Boolean = false,
        val card: String? = null,
        val summary: String? = null,
        val isError: Boolean = false,
        val diffPath: String? = null,
        val diffOld: String? = null,
        val diffNew: String? = null,
    ) : ChatItem()
    data class Meta(val text: String, val seq: Long = 0) : ChatItem()
    data class Cost(val text: String, val seq: Long = 0) : ChatItem()
    data class Todo(val items: List<TodoItem>) : ChatItem()
    data class Deliverables(val paths: List<String>) : ChatItem()
}

@Serializable
data class TodoItem(val content: String = "", val status: String = "")

@Serializable
data class DeepseekBalanceResponse(
    val is_available: Boolean = false,
    val balance_infos: List<DeepseekBalanceInfo> = emptyList(),
)

@Serializable
data class DeepseekBalanceInfo(
    val currency: String = "",
    val total_balance: String = "",
    val granted_balance: String = "",
    val topped_up_balance: String = "",
)

/** Extract visible text from a content block array. */
fun textOf(content: JsonElement?): String {
    if (content !is JsonArray) return ""
    return content.mapNotNull { block ->
        val obj = block as? JsonObject ?: return@mapNotNull null
        if (obj["type"]?.jsonPrimitive?.content == "text") {
            obj["text"]?.jsonPrimitive?.content
        } else null
    }.joinToString("")
}

fun reasoningOf(content: JsonElement?): String {
    if (content !is JsonArray) return ""
    return content.mapNotNull { block ->
        val obj = block as? JsonObject ?: return@mapNotNull null
        if (obj["type"]?.jsonPrimitive?.content == "reasoning") {
            obj["text"]?.jsonPrimitive?.content
        } else null
    }.joinToString("")
}

/** Extract the visible text from a tool/result event (nested tool-result block). */
fun toolResultText(data: JsonObject): String {
    val msg = data["message"]?.jsonObject ?: return ""
    val outer = msg["content"] as? JsonArray ?: return ""
    val block = outer.firstOrNull() as? JsonObject ?: return ""
    return textOf(block["content"])
}

/** One-line collapsed summary of the reasoning (first non-empty line, truncated). */
fun reasoningSummaryOf(content: JsonElement?): String {
    if (content !is JsonArray) return ""
    for (block in content) {
        val obj = block as? JsonObject ?: continue
        if (obj["type"]?.jsonPrimitive?.content == "reasoning") {
            val text = obj["text"]?.jsonPrimitive?.content ?: ""
            val line = text.lines().firstOrNull { it.isNotBlank() } ?: ""
            return if (line.length > 80) line.take(80) + "…" else line
        }
    }
    return ""
}

/** Max chars of tool args/result kept in chat items (full text stays in raw events). */
private const val MAX_TOOL_TEXT = 8000

/** Extract (card, summary) from a host-computed tool view, or null if not a tool view. */
fun toolViewInfo(view: JsonElement?): Pair<String, String>? {
    val obj = view as? JsonObject ?: return null
    val inner = obj["view"] as? JsonObject ?: return null
    val card = inner["card"]?.jsonPrimitive?.content ?: return null
    val summary: String = when (card) {
        "diff" -> {
            val diffs = inner["diffs"] as? JsonArray
            val path = (diffs?.firstOrNull() as? JsonObject)?.get("path")?.jsonPrimitive?.content
                ?: inner["title"]?.jsonPrimitive?.content?.removePrefix("Edit ") ?: ""
            "Edit · $path"
        }
        "terminal" -> inner["title"]?.jsonPrimitive?.content?.lineSequence()?.firstOrNull() ?: Strings.str("tool_cmd")
        "generic" -> inner["title"]?.jsonPrimitive?.content ?: ""
        "search" -> {
            val shape = inner["shape"]?.jsonPrimitive?.content
            val total = inner["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val paths = inner["paths"] as? JsonArray
            val files = inner["files"] as? JsonArray
            when {
                shape == "paths" && paths != null -> Strings.str("paths_count", paths.size)
                files != null && total > 0 -> Strings.str("matches_files", total, files.size)
                total > 0 -> Strings.str("matches_count", total)
                else -> Strings.str("search")
            }
        }
        "web" -> {
            val kind = inner["kind"]?.jsonPrimitive?.content
            val sources = inner["sources"] as? JsonArray
            val url = inner["url"]?.jsonPrimitive?.content
            when (kind) {
                "search" -> Strings.str("sources_count", sources?.size ?: 0)
                "fetch" -> Strings.str("fetched", url ?: "")
                else -> Strings.str("web")
            }
        }
        else -> inner["title"]?.jsonPrimitive?.content ?: ""
    }
    return card to summary
}

data class ToolDiff(val path: String, val oldText: String, val newText: String)

/** Extract diff hunks from a diff tool view (empty for non-diff cards). */
fun toolDiffInfo(view: JsonElement?): List<ToolDiff> {
    val obj = view as? JsonObject ?: return emptyList()
    val inner = obj["view"] as? JsonObject ?: return emptyList()
    if (inner["card"]?.jsonPrimitive?.content != "diff") return emptyList()
    val diffs = inner["diffs"] as? JsonArray ?: return emptyList()
    return diffs.mapNotNull { d ->
        val o = d as? JsonObject ?: return@mapNotNull null
        ToolDiff(
            path = o["path"]?.jsonPrimitive?.content ?: "",
            oldText = o["oldText"]?.jsonPrimitive?.content ?: "",
            newText = o["newText"]?.jsonPrimitive?.content ?: "",
        )
    }
}

/** File paths a tool view reports as created or changed (deliverables / 产物). */
fun producedPathsOf(view: JsonElement?): List<String> {
    val obj = view as? JsonObject ?: return emptyList()
    val inner = obj["view"] as? JsonObject ?: return emptyList()
    val card = inner["card"]?.jsonPrimitive?.content ?: return emptyList()
    val isDiff = card == "diff"
    val isGenericEdit = card == "generic" && inner["kind"]?.jsonPrimitive?.content == "edit"
    if (!isDiff && !isGenericEdit) return emptyList()
    val locations = inner["locations"] as? JsonArray ?: return emptyList()
    return locations.mapNotNull { (it as? JsonObject)?.get("path")?.jsonPrimitive?.content }
}

/** Fold raw session events into a renderable chat item list (optionally with host tool views). */
fun foldChat(events: List<SessionEvent>, viewBySeq: Map<Long, JsonElement> = emptyMap()): List<ChatItem> {
    val out = ArrayList<ChatItem>()
    var pendingCall: ChatItem.Tool? = null
    val produced = LinkedHashSet<String>()
    for (ev in events) {
        when (ev.type) {
            "turn/start" -> produced.clear()
            "user/message" -> {
                val text = textOf(ev.data["content"])
                if (text.isNotBlank()) {
                    val source = ev.data["source"]?.jsonObject
                    val kind = source?.get("kind")?.jsonPrimitive?.content ?: "user"
                    if (kind == "user") {
                        out.add(ChatItem.User(text, ev.seq))
                    } else {
                        // Host/plugin notices (e.g. "background job … finished") are not
                        // the user's words — show them as a subtle system line instead of
                        // a user bubble.
                        val summary = source?.get("summary")?.jsonPrimitive?.content
                        out.add(ChatItem.Meta(summary?.takeIf { it.isNotBlank() } ?: text, ev.seq))
                    }
                }
            }
            "assistant/message" -> {
                pendingCall?.let { out.add(it) }; pendingCall = null
                val msg = ev.data["message"]?.jsonObject
                val content = msg?.get("content")
                val text = textOf(content)
                val reasoningSummary = reasoningSummaryOf(content)
                if (text.isNotBlank() || reasoningSummary.isNotBlank()) {
                    out.add(ChatItem.Assistant(text, ev.seq, reasoningSummary))
                }
            }
            "tool/call" -> {
                pendingCall?.let { out.add(it) }
                val args = (ev.data["arguments"]?.jsonPrimitive?.content ?: "").take(MAX_TOOL_TEXT)
                val info = toolViewInfo(viewBySeq[ev.seq])
                produced.addAll(producedPathsOf(viewBySeq[ev.seq]))
                pendingCall = ChatItem.Tool(
                    name = ev.data["name"]?.jsonPrimitive?.content ?: "tool",
                    arguments = args,
                    seq = ev.seq,
                    card = info?.first,
                    summary = info?.second ?: args.lineSequence().firstOrNull { it.isNotBlank() }?.take(80),
                )
            }
            "tool/result" -> {
                val isError = ev.data["error"] != null
                val resultText = toolResultText(ev.data).take(MAX_TOOL_TEXT)
                val info = toolViewInfo(viewBySeq[ev.seq])
                val diff = toolDiffInfo(viewBySeq[ev.seq]).firstOrNull()
                val call = pendingCall
                pendingCall = null
                if (call != null) {
                    // Merge the call + result into a single card.
                    out.add(call.copy(
                        isResult = true,
                        isError = isError,
                        arguments = resultText.ifBlank { call.arguments },
                        card = info?.first ?: call.card,
                        summary = info?.second ?: call.summary,
                        diffPath = diff?.path,
                        diffOld = diff?.oldText,
                        diffNew = diff?.newText,
                    ))
                } else {
                    out.add(ChatItem.Tool(
                        name = if (isError) "tool/error" else "tool/result",
                        arguments = resultText,
                        seq = ev.seq,
                        isResult = true,
                        isError = isError,
                        card = info?.first,
                        summary = info?.second ?: resultText.lineSequence().firstOrNull { it.isNotBlank() }?.take(80),
                        diffPath = diff?.path,
                        diffOld = diff?.oldText,
                        diffNew = diff?.newText,
                    ))
                }
            }
            "todo/write" -> {
                pendingCall?.let { out.add(it) }; pendingCall = null
                val todos = ev.data["todos"]?.let { arr ->
                    if (arr is JsonArray) arr.mapNotNull { t ->
                        val o = t as? JsonObject ?: return@mapNotNull null
                        TodoItem(
                            content = o["content"]?.jsonPrimitive?.content ?: "",
                            status = o["status"]?.jsonPrimitive?.content ?: "",
                        )
                    } else emptyList()
                } ?: emptyList()
                if (todos.isNotEmpty()) out.add(ChatItem.Todo(todos))
            }
            "turn/end" -> {
                pendingCall?.let { out.add(it) }; pendingCall = null
                // Mark the final assistant message of this turn as the turn end,
                // so fork / copy actions render only after the turn's last output.
                val lastAssistant = out.indexOfLast { it is ChatItem.Assistant }
                if (lastAssistant >= 0) {
                    val a = out[lastAssistant] as ChatItem.Assistant
                    out[lastAssistant] = a.copy(isTurnEnd = true)
                }
                if (produced.isNotEmpty()) {
                    out.add(ChatItem.Deliverables(produced.toList()))
                    produced.clear()
                }
            }
            else -> {
                pendingCall?.let { out.add(it) }; pendingCall = null
            }
        }
    }
    pendingCall?.let { out.add(it) }
    return out
}

/** One timeline block for the trajectory overview (lane: 0 input, 1 model, 2 tools). */
data class TimelineSpan(val lane: Int, val isError: Boolean, val time: Long = 0, val durationMs: Long = 0)

/** Project events into the DSH three-lane trajectory timeline (sequence order + timing). */
fun timelineSpansOf(events: List<SessionEvent>): List<TimelineSpan> {
    val recs = ArrayList<Pair<Int, SessionEvent>>()
    for (ev in events) {
        val lane = when (ev.type) {
            "user/message" -> 0
            "assistant/message", "compaction/summary" -> 1
            "tool/call", "tool/result" -> 2
            else -> continue
        }
        recs.add(lane to ev)
    }
    val spans = ArrayList<TimelineSpan>(recs.size)
    for (i in recs.indices) {
        val (lane, ev) = recs[i]
        val isError = ev.type == "tool/result" && ev.data["error"] != null
        val dur = if (i + 1 < recs.size) (recs[i + 1].second.time - ev.time).coerceAtLeast(0) else 0L
        spans.add(TimelineSpan(lane, isError, ev.time, dur))
    }
    return spans
}

@Serializable
data class SubagentEntry(
    val kind: String = "child",
    val id: String,
    val mode: String? = null,
    val activity: String? = null,
    val hasChildren: Boolean = false,
    val label: String? = null,
    val reason: String? = null,
)

@Serializable
data class SubagentListValue(
    val entries: List<SubagentEntry> = emptyList(),
    val parentAvailable: Boolean = false,
)

@Serializable
data class AgentPresetEntry(
    val id: String,
    val trust: String = "user",
    val isDefault: Boolean = false,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class AgentPresetListValue(
    val presets: List<AgentPresetEntry> = emptyList(),
    val authorable: Boolean = false,
    val hasDocument: Boolean = false,
)

@Serializable
data class SessionSearchItem(val sessionId: String, val snippet: String = "")

@Serializable
data class SessionSearchValue(val items: List<SessionSearchItem> = emptyList(), val hasMore: Boolean = false)

/** Result of one executed slash command (host `commands.execute`). */
@Serializable
data class CommandResult(
    val kind: String = "success",
    val text: String? = null,
)

@Serializable
data class CommandExecuteValue(
    val commandId: String = "",
    val result: CommandResult = CommandResult(),
)

/** One entry of the host command directory (`commands.list`). */
@Serializable
data class CommandInput(
    val hint: String = "",
)

@Serializable
data class CommandDescriptor(
    val name: String = "",
    val description: String = "",
    val input: CommandInput? = null,
)
