package dev.dsh.remote.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dsh.remote.data.ChatItem
import dev.dsh.remote.data.DshApi
import dev.dsh.remote.data.QuestionItem
import dev.dsh.remote.data.SessionEvent
import dev.dsh.remote.data.SessionHistoryValue
import dev.dsh.remote.data.SessionModelsValue
import dev.dsh.remote.data.GoalProjection
import dev.dsh.remote.data.JobView
import dev.dsh.remote.data.Permissions
import dev.dsh.remote.data.Plan
import dev.dsh.remote.data.QueueItem
import dev.dsh.remote.data.SessionStats
import dev.dsh.remote.data.TodoItem
import dev.dsh.remote.data.TokenUsageView
import dev.dsh.remote.data.TurnUsage
import dev.dsh.remote.data.ModelPrice
import dev.dsh.remote.data.Prices
import dev.dsh.remote.data.estimateCostCny
import dev.dsh.remote.data.fmtTokens
import dev.dsh.remote.data.ContextPressureView
import dev.dsh.remote.data.ContextBreakdownView
import dev.dsh.remote.data.SessionSummary
import dev.dsh.remote.data.SettingsStore
import dev.dsh.remote.data.WorkspaceView
import dev.dsh.remote.data.foldChat
import dev.dsh.remote.data.reasoningOf
import dev.dsh.remote.data.reasoningSummaryOf
import dev.dsh.remote.data.textOf
import dev.dsh.remote.data.imagesOf
import dev.dsh.remote.data.ImageRef
import dev.dsh.remote.data.TimelineSpan
import dev.dsh.remote.data.timelineSpansOf
import dev.dsh.remote.data.DirEntry
import dev.dsh.remote.data.DirectoryListingValue
import dev.dsh.remote.data.DeepseekBalanceResponse
import dev.dsh.remote.data.SubagentEntry
import dev.dsh.remote.data.SubagentListValue
import dev.dsh.remote.net.RpcClient
import dev.dsh.remote.net.TrustAll
import dev.dsh.remote.net.WsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PendingQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<QuestionItem>,
)

data class PendingApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val reason: String?,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)
    private val json = dev.dsh.remote.net.DshJson.json

    val serverUrl: StateFlow<String> = settings.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_URL)

    /** Whether to post a notification (with sound) when a task completes. */
    val notifyDone: StateFlow<Boolean> = settings.notifyDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Whether to post notifications for questions / approval requests. */
    val notifyPrompt: StateFlow<Boolean> = settings.notifyPrompt
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setNotifyDone(v: Boolean) { viewModelScope.launch { settings.setNotifyDone(v) } }
    fun setNotifyPrompt(v: Boolean) { viewModelScope.launch { settings.setNotifyPrompt(v) } }

    /** UI appearance preference: "system" | "light" | "dark". */
    val themePreference: StateFlow<String> = settings.themePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setThemePreference(v: String) { viewModelScope.launch { settings.setThemePreference(v) } }

    /** UI language preference: "zh" | "en". */
    val language: StateFlow<String> = settings.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "zh")

    fun setLanguage(v: String) {
        Strings.setLang(v)
        viewModelScope.launch { settings.setLanguage(v) }
    }

    /** DeepSeek platform API key (for balance/usage queries). */
    val deepseekApiKey: StateFlow<String> = settings.deepseekApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setDeepseekApiKey(key: String) { viewModelScope.launch { settings.setDeepseekApiKey(key) } }

    private val _deepseekBalance = MutableStateFlow<DeepseekBalanceResponse?>(null)
    val deepseekBalance: StateFlow<DeepseekBalanceResponse?> = _deepseekBalance.asStateFlow()
    private val _deepseekBalanceLoading = MutableStateFlow(false)
    val deepseekBalanceLoading: StateFlow<Boolean> = _deepseekBalanceLoading.asStateFlow()
    private val _deepseekBalanceError = MutableStateFlow<String?>(null)
    val deepseekBalanceError: StateFlow<String?> = _deepseekBalanceError.asStateFlow()

    fun queryDeepseekBalance() {
        viewModelScope.launch {
            val key = settings.deepseekApiKey.first()
            if (key.isBlank()) {
                _deepseekBalanceError.value = Strings.str("fill_api_key")
                return@launch
            }
            _deepseekBalanceLoading.value = true
            _deepseekBalanceError.value = null
            try {
                val result = withContext(Dispatchers.Default) {
                    val client = TrustAll.client().newBuilder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val req = okhttp3.Request.Builder()
                        .url("https://api.deepseek.com/user/balance")
                        .header("Authorization", "Bearer $key")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                        val body = resp.body?.string() ?: throw Exception("empty response")
                        json.decodeFromString<DeepseekBalanceResponse>(body)
                    }
                }
                _deepseekBalance.value = result
            } catch (e: Exception) {
                _deepseekBalanceError.value = e.message ?: Strings.str("query_failed")
            } finally {
                _deepseekBalanceLoading.value = false
            }
        }
    }

    /** Fetch the current DeepSeek platform balance total (¥), or null when unavailable. */
    private suspend fun fetchBalanceTotal(): Double? {
        val key = settings.deepseekApiKey.first()
        if (key.isBlank()) return null
        return try {
            withContext(Dispatchers.Default) {
                val client = TrustAll.client().newBuilder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val req = okhttp3.Request.Builder()
                    .url("https://api.deepseek.com/user/balance")
                    .header("Authorization", "Bearer $key")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    val body = resp.body?.string() ?: throw Exception("empty response")
                    json.decodeFromString<DeepseekBalanceResponse>(body)
                }
            }.balance_infos.sumOf { it.total_balance.toDoubleOrNull() ?: 0.0 }
        } catch (_: Exception) {
            null
        }
    }

    /** Show the real cost of a finished turn: remaining balance + balance difference. */
    private fun queryTurnCost(tu: TurnUsage?) {
        viewModelScope.launch {
            val total = fetchBalanceTotal()
            if (total != null) {
                val prev = lastBalance
                val text = if (prev != null) {
                    val cost = (prev - total).coerceAtLeast(0.0)
                    Strings.str("balance_and_cost", fmtYuan(total), fmtYuan(cost))
                } else {
                    Strings.str("balance_only", fmtYuan(total))
                }
                _chatItems.value = _chatItems.value + ChatItem.Cost(text)
                lastBalance = total
            } else if (tu != null && (tu.inputTokens + tu.outputTokens) > 0) {
                // No API key / balance unavailable → fall back to a token estimate.
                val model = _models.value?.current?.model
                val cost = estimateCostCny(tu, model)
                val costText = Strings.str(
                    "turn_cost",
                    String.format(java.util.Locale.US, "%.3f", cost),
                    fmtTokens(tu.inputTokens),
                    fmtTokens(tu.outputTokens),
                )
                _chatItems.value = _chatItems.value + ChatItem.Cost(costText)
            }
        }
    }

    private fun fmtYuan(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _fullTimelineSpans = MutableStateFlow<List<TimelineSpan>>(emptyList())
    val fullTimelineSpans: StateFlow<List<TimelineSpan>> = _fullTimelineSpans.asStateFlow()

    // Archived session ids (removed conversations, filtered from the list).
    private val _archivedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedSessionIds: StateFlow<Set<String>> = _archivedSessionIds.asStateFlow()

    // File browser state.
    private val _dirPath = MutableStateFlow<String?>(null)
    val dirPath: StateFlow<String?> = _dirPath.asStateFlow()
    private val _dirParent = MutableStateFlow<String?>(null)
    val dirParent: StateFlow<String?> = _dirParent.asStateFlow()
    private val _dirEntries = MutableStateFlow<List<DirEntry>>(emptyList())
    val dirEntries: StateFlow<List<DirEntry>> = _dirEntries.asStateFlow()
    private val _dirLoading = MutableStateFlow(false)
    val dirLoading: StateFlow<Boolean> = _dirLoading.asStateFlow()
    private val _dirError = MutableStateFlow<String?>(null)
    val dirError: StateFlow<String?> = _dirError.asStateFlow()

    // Subagent panel state.
    private val _subagents = MutableStateFlow<List<SubagentEntry>>(emptyList())
    val subagents: StateFlow<List<SubagentEntry>> = _subagents.asStateFlow()
    private val _subagentChat = MutableStateFlow<List<ChatItem>>(emptyList())
    val subagentChat: StateFlow<List<ChatItem>> = _subagentChat.asStateFlow()
    private val _subagentId = MutableStateFlow<String?>(null)
    val subagentId: StateFlow<String?> = _subagentId.asStateFlow()

    // Per-parent subagent catalog (subagent.list) for the sidebar tree — carries
    // mode/activity so continuable subagents stay resumable while one-shot ones drop.
    private val _subagentChildrenByParent = MutableStateFlow<Map<String, List<SubagentEntry>>>(emptyMap())
    val subagentChildrenByParent: StateFlow<Map<String, List<SubagentEntry>>> = _subagentChildrenByParent.asStateFlow()

    // Agent presets.
    private val _agentPresets = MutableStateFlow<List<dev.dsh.remote.data.AgentPresetEntry>>(emptyList())
    val agentPresets: StateFlow<List<dev.dsh.remote.data.AgentPresetEntry>> = _agentPresets.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceView>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceView>> = _workspaces.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _chatItems = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatItems: StateFlow<List<ChatItem>> = _chatItems.asStateFlow()

    /** Decoded chat-image thumbnails keyed by attachmentId. */
    private val _imageCache = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val imageCache: StateFlow<Map<String, Bitmap>> = _imageCache.asStateFlow()

    private val _streaming = MutableStateFlow("")
    val streaming: StateFlow<String> = _streaming.asStateFlow()

    private val _streamingReasoning = MutableStateFlow("")
    val streamingReasoning: StateFlow<String> = _streamingReasoning.asStateFlow()

    private val _events = MutableStateFlow<List<SessionEvent>>(emptyList())
    val events: StateFlow<List<SessionEvent>> = _events.asStateFlow()

    val currentStats: StateFlow<SessionStats?> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            sessions.firstOrNull { it.sessionId == id }?.projections?.values?.sessionStats
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentTokenUsage: StateFlow<TokenUsageView?> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            decodeProjection(sessions.firstOrNull { it.sessionId == id }?.projections?.values?.tokenUsage, TokenUsageView.serializer())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentContextPressure: StateFlow<ContextPressureView?> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            decodeProjection(sessions.firstOrNull { it.sessionId == id }?.projections?.values?.contextPressure, ContextPressureView.serializer())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentContextBreakdown: StateFlow<ContextBreakdownView?> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            decodeProjection(sessions.firstOrNull { it.sessionId == id }?.projections?.values?.contextBreakdown, ContextBreakdownView.serializer())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentTodos: StateFlow<List<TodoItem>> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            decodeProjection(sessions.firstOrNull { it.sessionId == id }?.projections?.values?.todos, ListSerializer(TodoItem.serializer()))
                ?: emptyList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun <T> decodeProjection(el: JsonElement?, serializer: kotlinx.serialization.KSerializer<T>): T? =
        el?.let { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() }

    val currentPlan: StateFlow<Plan?> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            sessions.firstOrNull { it.sessionId == id }?.projections?.values?.plan
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentPermissions: StateFlow<Permissions?> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            sessions.firstOrNull { it.sessionId == id }?.projections?.values?.permissions
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentGoal: StateFlow<GoalProjection?> =
        combine(_currentSessionId, _sessions) { id, sessions ->
            sessions.firstOrNull { it.sessionId == id }?.projections?.values?.goal
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _models = MutableStateFlow<SessionModelsValue?>(null)
    val models: StateFlow<SessionModelsValue?> = _models.asStateFlow()

    private val _pendingQuestions = MutableStateFlow<List<PendingQuestion>>(emptyList())
    val pendingQuestions: StateFlow<List<PendingQuestion>> = _pendingQuestions.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<PendingApproval>>(emptyList())
    val pendingApprovals: StateFlow<List<PendingApproval>> = _pendingApprovals.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _jobs = MutableStateFlow<List<JobView>>(emptyList())
    val jobs: StateFlow<List<JobView>> = _jobs.asStateFlow()

    /** Session ids → finished-at timestamp whose turn finished while NOT watching them. */
    private val _finishedSessions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val finishedSessions: StateFlow<Map<String, Long>> = _finishedSessions.asStateFlow()

    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    /** Pending messages sitting in the host's native inbox queue (placement "queued"). */
    val queueItems: StateFlow<List<QueueItem>> = _queueItems.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    // Per-session drafts (WeChat-style), keyed by sessionId; persisted to DataStore.
    // ConcurrentHashMap: accessed from both the UI thread and WS frame handlers.
    private val draftBySession = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var draftsLoaded = false
    private var draftPersistJob: Job? = null

    private val _loadingSession = MutableStateFlow(false)
    val loadingSession: StateFlow<Boolean> = _loadingSession.asStateFlow()

    private suspend fun ensureDraftsLoaded() {
        if (draftsLoaded) return
        draftsLoaded = true
        try {
            draftBySession.putAll(settings.loadDrafts())
        } catch (_: Exception) {
        }
    }

    fun setDraft(text: String) {
        _draft.value = text
        _currentSessionId.value?.let { sid ->
            draftBySession[sid] = text
            persistDrafts()
        }
    }

    /** Insert a slash command into the composer as text so the user can review it before sending. */
    fun insertCommand(command: String) {
        val current = _draft.value
        val sep = if (current.isEmpty() || current.last() == ' ' || current.last() == '\n') "" else " "
        setDraft(if (current.isEmpty()) "$command " else "$current$sep$command ")
    }

    private fun persistDrafts() {
        draftPersistJob?.cancel()
        val snapshot = draftBySession.toMap()
        draftPersistJob = viewModelScope.launch {
            delay(400)
            try {
                settings.saveDrafts(snapshot)
            } catch (_: Exception) {
            }
        }
    }

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private var api: DshApi? = null
    private var baseUrl: String? = null
    private var wsClient: okhttp3.OkHttpClient? = null
    private var lastSeqBySession = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val runningBySession = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val queueBySession = java.util.concurrent.ConcurrentHashMap<String, List<QueueItem>>()
    private val jobsBySession = java.util.concurrent.ConcurrentHashMap<String, List<JobView>>()
    private var _oldestSeq: Long? = null
    private var fullTimelineSessionId: String? = null
    private var turnUsage: TurnUsage? = null
    private var lastBalance: Double? = null
    private var muxJob: kotlinx.coroutines.Job? = null
    private var hostJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val MAX_EVENTS = 800

        /** Leading slash-command token, mirroring the host `parseCommand` grammar. */
        private val COMMAND_LINE_REGEX = Regex("^/([a-z][a-z0-9_-]*)(?=$|[\\s])")

        /** Remote `prices.json` used to refresh the cost table on app start. */
        private val PRICES_URL = "https://raw.githubusercontent.com/SCSpotato/dsh-remote/main/prices.json"
    }

    init {
        // Apply the saved language before any UI composes, then auto-connect.
        viewModelScope.launch { Strings.setLang(settings.language.first()) }
        fetchPrices()
        connect()
    }

    /** Fetch the latest pricing table from `prices.json`; built-in defaults are kept on failure. */
    private fun fetchPrices() {
        viewModelScope.launch {
            try {
                val client = TrustAll.client()
                val req = okhttp3.Request.Builder().url(PRICES_URL).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val text = resp.body?.string() ?: return@launch
                        parsePrices(json.parseToJsonElement(text).jsonObject)
                    }
                }
            } catch (_: Exception) {
                Prices.resetDefaults()
            }
        }
    }

    private fun parsePrices(obj: JsonObject) {
        try {
            obj["default"]?.jsonObject?.let { d ->
                Prices.default = ModelPrice(
                    inputMiss = d["inputMiss"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: Prices.default.inputMiss,
                    inputHit = d["inputHit"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: Prices.default.inputHit,
                    output = d["output"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: Prices.default.output,
                )
            }
            obj["models"]?.jsonObject?.let { ms ->
                for ((name, v) in ms) {
                    val mv = v.jsonObject
                    Prices.byModel[name] = ModelPrice(
                        inputMiss = mv["inputMiss"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.5,
                        inputHit = mv["inputHit"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.05,
                        output = mv["output"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 4.5,
                    )
                }
            }
        } catch (_: Exception) {
            Prices.resetDefaults()
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch { settings.setServerUrl(url) }
    }

    private fun startForegroundService() {
        val ctx = getApplication<Application>()
        val intent = android.content.Intent(ctx, dev.dsh.remote.service.DshForegroundService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
    }

    fun connect(url: String? = null) {
        viewModelScope.launch {
            _error.value = null
            _connecting.value = true
            try {
                val url2 = (url ?: serverUrl.first()).trimEnd('/')
                val client = TrustAll.client()
                val rpc = RpcClient(url2, client)
                val dshApi = DshApi(rpc)
                api = dshApi
                baseUrl = url2
                wsClient = client
                _connected.value = true
                startForegroundService()

                // Baseline network + JSON decode off the main thread so the
                // connecting spinner keeps animating while the host is slow.
                val (wsItems, sessions, archived) = withContext(Dispatchers.Default) {
                    val ws = dshApi.workspaceList()
                    val s = dshApi.sessionList()
                    Triple(ws.items, s, ws.archivedSessionIds.toSet())
                }
                _workspaces.value = wsItems
                _sessions.value = sessions
                _archivedSessionIds.value = archived
                seedRunning(sessions)
                refreshSubagentChildren(sessions)

                collectFrames(url2, client)

                // Refresh the DeepSeek balance if a key is already saved.
                if (settings.deepseekApiKey.first().isNotBlank()) {
                    queryDeepseekBalance()
                }
            } catch (e: Exception) {
                _connected.value = false
                _error.value = e.message ?: "connection failed"
            } finally {
                _connecting.value = false
            }
        }
    }

    private fun collectFrames(url: String, client: okhttp3.OkHttpClient) {
        // Guard against duplicate WebSocket connections when connect() runs again.
        muxJob?.cancel()
        hostJob?.cancel()
        val ws = WsClient(url, client)
        muxJob = viewModelScope.launch {
            ws.frames("/api/events.mux").collect { frame ->
                val type = frame.payload["type"]?.jsonPrimitive?.content ?: return@collect
                val sessionId = frame.payload["sessionId"]?.jsonPrimitive?.content
                when (type) {
                    "session/event" -> if (sessionId == _currentSessionId.value) {
                        val ev = json.decodeFromJsonElement<SessionEvent>(frame.payload["event"] ?: return@collect)
                        appendLiveEvent(ev)
                    }
                    "question/requested" -> {
                        val questions = json.decodeFromJsonElement<List<QuestionItem>>(
                            frame.payload["questions"] ?: return@collect
                        )
                        if (_pendingQuestions.value.none { it.rpcId == frame.rpcId }) {
                            _pendingQuestions.value = _pendingQuestions.value + PendingQuestion(
                                rpcId = frame.rpcId,
                                sessionId = sessionId ?: "",
                                questions = questions,
                            )
                            // Jump to the session that needs the decision so the
                            // plan-review / ask-user card has its conversation behind it.
                            if (sessionId != null && sessionId != _currentSessionId.value) openSession(sessionId)
                        }
                    }
                    "approval/requested" -> {
                        if (_pendingApprovals.value.none { it.rpcId == frame.rpcId }) {
                            _pendingApprovals.value = _pendingApprovals.value + PendingApproval(
                                rpcId = frame.rpcId,
                                sessionId = sessionId ?: "",
                                approvalId = frame.payload["approvalId"]?.jsonPrimitive?.content ?: "",
                                toolName = frame.payload["toolName"]?.jsonPrimitive?.content ?: "",
                                reason = frame.payload["reason"]?.jsonPrimitive?.content,
                            )
                            if (sessionId != null && sessionId != _currentSessionId.value) openSession(sessionId)
                        }
                    }
                    "question/resolved" -> {
                        val qRpcId = frame.payload["questionRpcId"]?.jsonPrimitive?.content
                        if (qRpcId != null) {
                            _pendingQuestions.value = _pendingQuestions.value.filterNot { it.rpcId == qRpcId }
                        }
                    }
                    "approval/resolved" -> {
                        val approvalId = frame.payload["approvalId"]?.jsonPrimitive?.content
                        if (approvalId != null) {
                            _pendingApprovals.value = _pendingApprovals.value.filterNot { it.approvalId == approvalId }
                        }
                    }
                    "session/jobs" -> {
                        val jobsEl = frame.payload["jobs"]
                        val jobs = if (jobsEl != null) {
                            try { json.decodeFromJsonElement<List<JobView>>(jobsEl) } catch (_: Exception) { emptyList() }
                        } else emptyList()
                        if (sessionId != null) jobsBySession[sessionId] = jobs
                        if (sessionId == _currentSessionId.value) _jobs.value = jobs
                    }
                    "session/queue" -> {
                        val itemsEl = frame.payload["items"]
                        val items = if (itemsEl != null) {
                            try { json.decodeFromJsonElement<List<QueueItem>>(itemsEl) } catch (_: Exception) { emptyList() }
                        } else emptyList()
                        val pending = items.filter { it.placement == "queued" }
                        // The mux stream broadcasts every session's queue, so cache
                        // each one; a session switch can then show it without a refetch.
                        if (sessionId != null) queueBySession[sessionId] = pending
                        if (sessionId == _currentSessionId.value) _queueItems.value = pending
                    }
                    "session/projection" -> {
                        // Projections (todos, stats, goal, tokens, context…) changed:
                        // re-pull the session list so the right panel stays live.
                        refreshSessions()
                        refreshModels()
                    }
                }
            }
        }
        hostJob = viewModelScope.launch {
            ws.frames("/api/events.host").collect { frame ->
                val type = frame.payload["type"]?.jsonPrimitive?.content ?: return@collect
                when (type) {
                    "host/session-status" -> {
                        val sid = frame.payload["sessionId"]?.jsonPrimitive?.content
                        val isRunning = frame.payload["running"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        if (sid != null) {
                            val wasRunning = runningBySession[sid]
                            runningBySession[sid] = isRunning
                            if (wasRunning == true && !isRunning && sid != _currentSessionId.value) {
                                // A conversation finished while not being watched.
                                _finishedSessions.value = _finishedSessions.value + (sid to System.currentTimeMillis())
                            }
                        }
                        if (sid == _currentSessionId.value) {
                            _running.value = isRunning
                        }
                        refreshSessions()
                    }
                    "host/session-added", "host/session-removed" -> refreshSessions()
                    "host/workspace-changed", "host/workspace-removed",
                    "host/workspace-order-changed", "host/archived-sessions-changed" -> refreshAll()
                }
            }
        }
    }

    private fun appendLiveEvent(ev: SessionEvent) {
        val sessionId = _currentSessionId.value ?: return
        // De-duplicate: drop events already seen in the loaded history or delivered twice
        // (e.g. when the WS reconnect replays a frame).
        val lastSeq = lastSeqBySession[sessionId] ?: 0L
        if (ev.seq <= lastSeq) return
        lastSeqBySession[sessionId] = ev.seq

        // assistant/chunk events are transient streaming deltas — process them but do not
        // store them (they are the most numerous and would bloat memory over a long session).
        if (ev.type == "assistant/chunk") {
            val chunk = ev.data["chunk"]?.jsonObject
            val chunkType = chunk?.get("type")?.jsonPrimitive?.content
            val text = chunk?.get("text")?.jsonPrimitive?.content ?: ""
            when (chunkType) {
                "text-delta" -> if (text.isNotEmpty()) _streaming.value = _streaming.value + text
                "reasoning-delta" -> if (text.isNotEmpty()) _streamingReasoning.value = _streamingReasoning.value + text
            }
            return
        }

        _events.value = (_events.value + ev).takeLast(MAX_EVENTS)
        when (ev.type) {
            "assistant/message" -> {
                val msg = ev.data["message"]?.jsonObject
                val content = msg?.get("content")
                val text = textOf(content)
                val reasoningSummary = reasoningSummaryOf(content)
                val images = imagesOf(content)
                if (text.isNotBlank() || reasoningSummary.isNotBlank() || images.isNotEmpty()) {
                    _chatItems.value = _chatItems.value + ChatItem.Assistant(text, ev.seq, reasoningSummary, images = images)
                }
                _currentSessionId.value?.let { sid -> images.forEach { loadImage(sid, it) } }
                // Accumulate per-turn token usage for the cost line.
                ev.data["usage"]?.jsonObject?.let { u ->
                    val usage = TurnUsage(
                        inputTokens = u["inputTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        cacheReadTokens = u["cacheReadTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        outputTokens = u["outputTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    )
                    turnUsage = turnUsage?.plus(usage) ?: usage
                }
                _streaming.value = ""
                _streamingReasoning.value = ""
            }
            "turn/start" -> {
                turnUsage = null
                _streaming.value = ""
                _streamingReasoning.value = ""
            }
            "turn/end" -> {
                _streaming.value = ""
                _streamingReasoning.value = ""
                // Mark the final assistant message of the turn so fork / copy
                // actions render only after the turn's last output.
                val items = _chatItems.value
                val idx = items.indexOfLast { it is ChatItem.Assistant }
                if (idx >= 0) {
                    val a = items[idx] as ChatItem.Assistant
                    if (!a.isTurnEnd) {
                        _chatItems.value = items.toMutableList().also { it[idx] = a.copy(isTurnEnd = true) }
                    }
                }
                // Show the real cost of this turn from the balance difference;
                // fall back to a token estimate when balance isn't available.
                val tu = turnUsage
                turnUsage = null
                queryTurnCost(tu)
            }
            "tool/call" -> {
                val folded = foldChat(listOf(ev))
                if (folded.isNotEmpty()) _chatItems.value = _chatItems.value + folded
            }
            "tool/result" -> {
                val resultItem = foldChat(listOf(ev)).firstOrNull() as? ChatItem.Tool
                val last = _chatItems.value.lastOrNull()
                if (last is ChatItem.Tool && !last.isResult && resultItem != null) {
                    // Merge the live call + result into a single card.
                    _chatItems.value = _chatItems.value.dropLast(1) + last.copy(
                        isResult = true,
                        isError = resultItem.isError,
                        arguments = resultItem.arguments.ifBlank { last.arguments },
                        card = resultItem.card ?: last.card,
                        summary = resultItem.summary ?: last.summary,
                        diffPath = resultItem.diffPath ?: last.diffPath,
                        diffOld = resultItem.diffOld ?: last.diffOld,
                        diffNew = resultItem.diffNew ?: last.diffNew,
                    )
                } else if (resultItem != null) {
                    _chatItems.value = _chatItems.value + resultItem
                }
            }
            else -> {
                val folded = foldChat(listOf(ev))
                if (folded.isNotEmpty()) {
                    _chatItems.value = _chatItems.value + folded
                    // A live user/message (image) reaches this branch; kick off
                    // image loading here too, otherwise the thumbnail stays on
                    // its "loading" placeholder until the session is reopened.
                    _currentSessionId.value?.let { sid -> loadImagesFor(folded, sid) }
                }
            }
        }
    }

    /**
     * Load one chat image and cache the full-resolution bitmap by attachmentId.
     * The cached bitmap is NOT a tiny thumbnail: tapping it opens the original
     * (DSH normalizes stored images to a 2048px long edge), so decoding below
     * that ceiling keeps the tap-to-zoom view sharp.
     *
     * Retries with backoff: the durable attachment may not be published yet when
     * a live user/message first arrives, so a transient miss is retried rather
     * than leaving the thumbnail on its "loading" placeholder forever.
     */
    private fun loadImage(sessionId: String, ref: ImageRef, attempt: Int = 0) {
        if (ref.attachmentId.isEmpty()) return
        if (_imageCache.value.containsKey(ref.attachmentId)) return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    api?.sessionAttachment(sessionId, ref.attachmentId)
                } catch (_: Exception) {
                    null
                }
            }
            val bmp = if (bytes != null) decodeImage(bytes.base64, maxDim = 2048) else null
            if (bmp != null) {
                // Only cache if still relevant (session may have switched meanwhile).
                if (_currentSessionId.value == sessionId) {
                    _imageCache.value = _imageCache.value + (ref.attachmentId to bmp)
                }
            } else if (attempt < 6) {
                // Durable image not ready yet — back off and retry.
                val delay = longArrayOf(1500L, 2000L, 3000L, 5000L, 8000L, 12000L)[attempt]
                delay(delay)
                loadImage(sessionId, ref, attempt + 1)
            }
        }
    }

    /** Kick off image loading for every image referenced by the given chat items. */
    private fun loadImagesFor(items: List<ChatItem>, sessionId: String) {
        for (item in items) {
            val refs = when (item) {
                is ChatItem.User -> item.images
                is ChatItem.Assistant -> item.images
                else -> emptyList()
            }
            refs.forEach { loadImage(sessionId, it) }
        }
    }

    /** base64 → Bitmap decode (bounded long edge, off the main thread). */
    private fun decodeImage(base64: String, maxDim: Int = 2048): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    fun openSession(sessionId: String) {
        viewModelScope.launch {
            // Show the loading spinner immediately, before any wait.
            _loadingSession.value = true
            ensureDraftsLoaded()
            // Save the outgoing session's draft before switching.
            _currentSessionId.value?.let { old ->
                if (old != sessionId) {
                    if (_draft.value.isNotBlank()) draftBySession[old] = _draft.value
                    else draftBySession.remove(old)
                }
            }
            _currentSessionId.value = sessionId
            _finishedSessions.value = _finishedSessions.value - sessionId
            _draft.value = draftBySession[sessionId] ?: ""
            _running.value = runningBySession[sessionId] ?: false
            // Re-query the authoritative running state so a background-completed
            // turn is not left showing "运行中".
            refreshRunningStatus()
            _chatItems.value = emptyList()
            _events.value = emptyList()
            _imageCache.value = emptyMap()
            _queueItems.value = queueBySession[sessionId] ?: emptyList()
            _jobs.value = jobsBySession[sessionId] ?: emptyList()
            _streaming.value = ""
            _streamingReasoning.value = ""
            _hasMore.value = false
            _loadingOlder.value = false
            _oldestSeq = null
            _fullTimelineSpans.value = emptyList()
            fullTimelineSessionId = null
            lastSeqBySession[sessionId] = 0
            try {
                // Network + JSON decode + fold off the main thread so the spinner spins.
                // Bounded so a hung RPC (e.g. a transient 403/network stall) can never
                // leave the chat stuck on the loading spinner forever: after the cap we
                // clear loading and let the live WS stream show what it already has.
                val history = withTimeoutOrNull(20_000) {
                    withContext(Dispatchers.Default) {
                        api?.sessionHistory(sessionId, maxMessages = 15)
                    }
                }
                // The user may have switched sessions while loading — only apply if still current.
                if (history != null && _currentSessionId.value == sessionId) {
                    val events = history.events.map { it.event }
                    val views = history.events.mapNotNull { e -> e.view?.let { e.event.seq to it } }.toMap()
                    val folded = withContext(Dispatchers.Default) { foldChat(events, views) }
                    _events.value = compactEvents(events)
                    _chatItems.value = folded
                    _hasMore.value = history.hasMore
                    events.firstOrNull()?.let { _oldestSeq = it.seq }
                    events.lastOrNull()?.let { lastSeqBySession[sessionId] = it.seq }
                    loadImagesFor(folded, sessionId)
                }
                refreshModels()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loadingSession.value = false
            }
        }
    }

    /** Open a session after the connection is established (notification deep-link entry). */
    fun openSessionWhenReady(sessionId: String) {
        viewModelScope.launch {
            if (!_connected.value) {
                var guard = 0
                while (!_connected.value && guard < 150) { delay(100); guard++ }
            }
            openSession(sessionId)
        }
    }

    fun fullReasoning(seq: Long): String {
        val ev = _events.value.firstOrNull { it.seq == seq } ?: return ""
        val msg = ev.data["message"]?.jsonObject
        return reasoningOf(msg?.get("content"))
    }

    /** Lazily fetch the full (paginated) history to draw the complete trajectory timeline. */
    fun ensureFullTimeline(sessionId: String) {
        if (fullTimelineSessionId == sessionId) return
        fullTimelineSessionId = sessionId
        viewModelScope.launch {
            try {
                val spans = ArrayList<TimelineSpan>()
                var beforeSeq: Long? = null
                var guard = 0
                while (guard++ < 500) {
                    val history = withContext(Dispatchers.Default) {
                        api?.sessionHistory(sessionId, beforeSeq = beforeSeq, maxMessages = 100)
                    } ?: break
                    val events = history.events.map { it.event }
                    if (events.isEmpty()) break
                    // Newer chunks are already in `spans`; prepend this older chunk.
                    spans.addAll(0, timelineSpansOf(events))
                    if (!history.hasMore) break
                    beforeSeq = events.firstOrNull()?.seq
                }
                _fullTimelineSpans.value = spans
            } catch (_: Exception) {
            }
        }
    }

    fun loadOlder() {
        val sessionId = _currentSessionId.value ?: return
        if (_loadingOlder.value || !_hasMore.value) return
        val beforeSeq = _oldestSeq ?: return
        _loadingOlder.value = true
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.Default) {
                    api?.sessionHistory(sessionId, beforeSeq = beforeSeq, maxMessages = 15)
                }
                if (history != null && _currentSessionId.value == sessionId) {
                    val older = history.events.map { it.event }
                    if (older.isNotEmpty()) {
                        val views = history.events.mapNotNull { e -> e.view?.let { e.event.seq to it } }.toMap()
                        val folded = withContext(Dispatchers.Default) { foldChat(older, views) }
                        _events.value = compactEvents(older) + _events.value
                        _chatItems.value = folded + _chatItems.value
                        loadImagesFor(folded, sessionId)
                        older.firstOrNull()?.let { _oldestSeq = it.seq }
                    }
                    _hasMore.value = history.hasMore
                }
            } catch (_: Exception) {
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    fun send(text: String) {
        val sessionId = _currentSessionId.value ?: return
        val textTrim = text.trim()
        if (textTrim.isEmpty()) return
        sendInternal(sessionId, textTrim)
    }

    private fun sendInternal(sessionId: String, text: String) {
        _draft.value = ""
        draftBySession.remove(sessionId)
        persistDrafts()
        if (isCommandLine(text)) {
            // Slash commands execute through the host command channel, not as chat text.
            viewModelScope.launch { runCommand(sessionId, text) }
        } else {
            _running.value = true
            runningBySession[sessionId] = true
            viewModelScope.launch {
                try {
                    api?.sessionPrompt(sessionId, text)
                } catch (e: Exception) {
                    _error.value = e.message
                }
            }
        }
    }

    /** Whether a message starts with a slash-command token (e.g. "/plan"). */
    private fun isCommandLine(text: String): Boolean =
        COMMAND_LINE_REGEX.containsMatchIn(text)

    /** Execute a slash command and surface its result; fall back to a plain prompt for unknown commands. */
    private suspend fun runCommand(sessionId: String, line: String) {
        try {
            val r = api?.commandExecute(sessionId, line)
            if (r == null) {
                // Unknown/malformed command — send it to the model instead of dropping it.
                _running.value = true
                runningBySession[sessionId] = true
                api?.sessionPrompt(sessionId, line)
                return
            }
            when (r.result.kind) {
                "error" -> {
                    _error.value = r.result.text ?: Strings.str("command_failed")
                    r.result.text?.let { toast(it) }
                }
                else -> {
                    r.result.text?.takeIf { it.isNotBlank() }?.let { toast(it) }
                }
            }
            // Refresh so plan/permission/goal projections (and any steer-opened turn) reflect.
            refreshSessions()
        } catch (e: Exception) {
            _error.value = e.message
            toast(Strings.str("command_failed_fmt", e.message))
        }
    }

    /** Send an image (base64 data) as a user prompt. */
    fun sendImage(sessionId: String, mediaType: String, base64: String, name: String?) {
        _running.value = true
        runningBySession[sessionId] = true
        viewModelScope.launch {
            try {
                api?.sessionPromptImage(sessionId, mediaType, base64, name)
            } catch (e: Exception) {
                _error.value = e.message
                toast(attachmentErrorMessage(e))
            }
        }
    }

    /** Send an image + text together. */
    fun sendImageText(sessionId: String, mediaType: String, base64: String, name: String?, text: String) {
        _running.value = true
        runningBySession[sessionId] = true
        _draft.value = ""
        draftBySession.remove(sessionId)
        persistDrafts()
        viewModelScope.launch {
            try {
                api?.sessionPromptImageText(sessionId, mediaType, base64, name, text)
            } catch (e: Exception) {
                _error.value = e.message
                toast(attachmentErrorMessage(e))
            }
        }
    }

    /** Turn an attachment rejection into a short, human-readable toast message. */
    private fun attachmentErrorMessage(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("does not support image", ignoreCase = true) ||
                msg.contains("MODEL_DOES_NOT_SUPPORT_IMAGES", ignoreCase = true) ->
                Strings.str("image_unsupported")
            else -> Strings.str("attachment_failed_fmt", msg)
        }
    }

    /**
     * Queue a message by forwarding it to the host immediately. The host's own
     * inbox natively queues it while a turn runs, so it is sent in order and —
     * crucially — does not depend on the app staying foregrounded: the message is
     * already on the server the moment you tap send.
     */
    fun enqueue(text: String) {
        val sessionId = _currentSessionId.value ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        sendInternal(sessionId, t)
    }

    fun removeQueued(itemId: String) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try { api?.sessionUpdateQueue(sessionId, itemId, "remove") }
            catch (e: Exception) { _error.value = e.message }
        }
    }

    fun editQueued(itemId: String, text: String) {
        val sessionId = _currentSessionId.value ?: return
        val t = text.trim()
        viewModelScope.launch {
            try {
                if (t.isEmpty()) api?.sessionUpdateQueue(sessionId, itemId, "remove")
                else api?.sessionUpdateQueue(sessionId, itemId, "edit", t)
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun cancel() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try { api?.sessionCancel(sessionId) } catch (_: Exception) {}
        }
    }

    fun selectModel(sessionId: String, provider: String, model: String, effort: String? = null) {
        viewModelScope.launch {
            try {
                api?.sessionSelectModel(sessionId, provider, model, effort)
                refreshModels()
            } catch (_: Exception) {}
        }
    }

    fun newSession() {
        viewModelScope.launch {
            try {
                val wsId = _workspaces.value.firstOrNull()?.workspaceId
                val created = api?.sessionCreate(workspaceId = wsId)
                if (created != null) {
                    refreshSessions()
                    openSession(created.sessionId)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun toast(msg: String) {
        try {
            android.widget.Toast.makeText(getApplication<Application>(), msg, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    /** Branch the conversation into a new child session, anchored at the chosen message. */
    fun forkSession(atSeq: Long? = null) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try {
                val created = api?.sessionFork(sessionId, atSeq)
                if (created != null) {
                    refreshSessions()
                    openSession(created.sessionId)
                    toast(Strings.str("forked_session"))
                } else {
                    toast(Strings.str("fork_failed_not_conn"))
                }
            } catch (e: Exception) {
                _error.value = e.message
                toast(Strings.str("fork_failed_fmt", e.message))
            }
        }
    }

    /** Copy a single message's text to the clipboard. */
    fun copyText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) {
            toast(Strings.str("no_copy_content"))
            return
        }
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("message", t))
        toast(Strings.str("copied"))
    }

    /** Copy the visible conversation text to the clipboard. */
    fun copyConversation() {
        val text = _chatItems.value.joinToString("\n\n") { item ->
            when (item) {
                is ChatItem.User -> Strings.str("you_prefix", item.text)
                is ChatItem.Assistant -> item.text
                else -> ""
            }
        }.trim()
        if (text.isEmpty()) {
            toast(Strings.str("no_copy_content"))
            return
        }
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("conversation", text))
        toast(Strings.str("copied_clipboard"))
    }

    /** Archive (remove) the current conversation. */
    fun archiveSession() {
        val sessionId = _currentSessionId.value ?: return
        deleteSessionById(sessionId)
    }

    /** Return to the home screen without deleting the current conversation. */
    fun goHome() {
        val old = _currentSessionId.value
        if (old != null && _draft.value.isNotBlank()) {
            draftBySession[old] = _draft.value
            persistDrafts()
        }
        _currentSessionId.value = null
        _chatItems.value = emptyList()
        _events.value = emptyList()
        _imageCache.value = emptyMap()
        _streaming.value = ""
        _streamingReasoning.value = ""
        _draft.value = ""
        _fullTimelineSpans.value = emptyList()
        fullTimelineSessionId = null
    }

    fun deleteSessionById(sessionId: String) {
        viewModelScope.launch {
            try {
                api?.archiveSession(sessionId)
                if (_currentSessionId.value == sessionId) {
                    _currentSessionId.value = null
                    _chatItems.value = emptyList()
                    _events.value = emptyList()
                    _streaming.value = ""
                    _streamingReasoning.value = ""
                    _draft.value = ""
                    draftBySession.remove(sessionId)
                }
                refreshAll()
                toast(Strings.str("deleted_session"))
            } catch (e: Exception) {
                _error.value = e.message
                toast(Strings.str("delete_failed_fmt", e.message))
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            try {
                api?.sessionRename(sessionId, title)
                refreshSessions()
            } catch (_: Exception) {}
        }
    }

    fun togglePlan(sessionId: String, active: Boolean) {
        viewModelScope.launch {
            runCommand(sessionId, if (active) "/plan off" else "/plan")
        }
    }

    fun setPermission(sessionId: String, name: String) {
        viewModelScope.launch {
            runCommand(sessionId, "/permission $name")
        }
    }

    /** Execute an arbitrary slash command through the host command channel. */
    fun sendCommand(sessionId: String, command: String) {
        viewModelScope.launch {
            runCommand(sessionId, command)
        }
    }

    private fun currentGoalRef(): Pair<String, Int>? {
        val g = currentGoal.value?.goal ?: return null
        return g.id to g.revision
    }

    fun goalPause() {
        val sid = _currentSessionId.value ?: return
        val ref = currentGoalRef() ?: return
        viewModelScope.launch {
            try { api?.goalPause(sid, ref.first, ref.second); refreshSessions() } catch (_: Exception) {}
        }
    }

    fun goalResume() {
        val sid = _currentSessionId.value ?: return
        val ref = currentGoalRef() ?: return
        viewModelScope.launch {
            try { api?.goalResume(sid, ref.first, ref.second); refreshSessions() } catch (_: Exception) {}
        }
    }

    fun goalComplete() {
        val sid = _currentSessionId.value ?: return
        val ref = currentGoalRef() ?: return
        viewModelScope.launch {
            try { api?.goalComplete(sid, ref.first, ref.second); refreshSessions() } catch (_: Exception) {}
        }
    }

    fun goalCreate(objective: String) {
        val sid = _currentSessionId.value ?: return
        if (objective.isBlank()) return
        viewModelScope.launch {
            try { api?.goalCreate(sid, objective); refreshSessions() } catch (_: Exception) {}
        }
    }

    private fun refreshModels() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try { _models.value = api?.sessionModels(sessionId) } catch (_: Exception) {}
        }
    }

    private fun seedRunning(list: List<SessionSummary>) {
        for (s in list) runningBySession[s.sessionId] = s.running
    }

    /** Drop transient assistant/chunk events (the bulk of the log) before storing history. */
    private fun compactEvents(events: List<SessionEvent>): List<SessionEvent> =
        events.filter { it.type != "assistant/chunk" }

    /** Re-query the host for the current session's real running state (used on resume / open). */
    fun refreshRunningStatus() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.Default) { api?.sessionList() } ?: return@launch
                _sessions.value = list
                seedRunning(list)
                val cur = _currentSessionId.value
                if (cur != null) {
                    _running.value = runningBySession[cur] ?: false
                }
            } catch (_: Exception) {}
        }
    }

    /** Called when the app returns to the foreground: force-fresh streams + catch up. */
    fun onAppResumed() {
        // Re-establish the WebSocket streams; a backgrounded app can hold a half-open
        // socket that never errors, so retryWhen never re-subscribes on its own.
        val url = baseUrl
        val client = wsClient
        if (url != null && client != null) collectFrames(url, client)

        viewModelScope.launch {
            try {
                // Always refresh the session + workspace list on resume.
                val list = withContext(Dispatchers.Default) { api?.sessionList() }
                if (list != null) {
                    _sessions.value = list
                    seedRunning(list)
                }
                val ws = withContext(Dispatchers.Default) { api?.workspaceList() }
                if (ws != null) _workspaces.value = ws.items

                val sessionId = _currentSessionId.value ?: return@launch
                // Authoritative running state for the open session.
                _running.value = runningBySession[sessionId] ?: false
                // Recover events that arrived while the socket was dead.
                val history = withContext(Dispatchers.Default) {
                    api?.sessionHistory(sessionId, maxMessages = 30)
                }
                if (history != null && _currentSessionId.value == sessionId) {
                    val lastSeq = lastSeqBySession[sessionId] ?: 0L
                    val fresh = history.events.map { it.event }.filter { it.seq > lastSeq }
                    if (fresh.isNotEmpty()) {
                        val folded = foldChat(fresh)
                        _events.value = (_events.value + compactEvents(fresh)).takeLast(MAX_EVENTS)
                        _chatItems.value = _chatItems.value + folded
                        loadImagesFor(folded, sessionId)
                        lastSeqBySession[sessionId] = fresh.last().seq
                        _streaming.value = ""
                        _streamingReasoning.value = ""
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun refreshSessions() {
        viewModelScope.launch {
            try {
                val list = api?.sessionList() ?: emptyList()
                _sessions.value = list
                seedRunning(list)
                refreshSubagentChildren(list)
            } catch (_: Exception) {}
        }
    }

    /** Fetch each subagent parent's catalog so the sidebar can keep continuable children. */
    private fun refreshSubagentChildren(list: List<SessionSummary>) {
        val parents = list.filter { it.isSubagent && it.parentSessionId != null }
            .mapNotNull { it.parentSessionId }
            .toSet()
        if (parents.isEmpty()) {
            _subagentChildrenByParent.value = emptyMap()
            return
        }
        viewModelScope.launch {
            val map = mutableMapOf<String, List<SubagentEntry>>()
            for (p in parents) {
                try {
                    val entries = withContext(Dispatchers.Default) { api?.subagentList(p) }?.entries ?: emptyList()
                    map[p] = entries
                } catch (_: Exception) {
                    map[p] = emptyList()
                }
            }
            _subagentChildrenByParent.value = map
        }
    }

    private fun refreshAll() {
        viewModelScope.launch {
            try {
                val ws = api?.workspaceList()
                if (ws != null) {
                    _workspaces.value = ws.items
                    _archivedSessionIds.value = ws.archivedSessionIds.toSet()
                }
                val list = api?.sessionList() ?: emptyList()
                _sessions.value = list
                seedRunning(list)
                refreshSubagentChildren(list)
            } catch (_: Exception) {}
        }
    }

    fun respondApproval(p: PendingApproval, allow: Boolean) {
        viewModelScope.launch {
            try {
                api?.respondApproval(p.rpcId, p.sessionId, p.approvalId, allow)
            } catch (_: Exception) {}
            _pendingApprovals.value = _pendingApprovals.value.filterNot { it.rpcId == p.rpcId }
        }
    }

    fun respondQuestion(p: PendingQuestion, answers: List<dev.dsh.remote.data.QuestionAnswer>) {
        viewModelScope.launch {
            try {
                api?.respondQuestion(p.rpcId, p.sessionId, answers)
            } catch (_: Exception) {}
            _pendingQuestions.value = _pendingQuestions.value.filterNot { it.rpcId == p.rpcId }
        }
    }

    // --- File browser ---

    /** Open the file browser at a directory (null → the current workspace, else home). */
    fun openFileBrowser(path: String? = null) {
        val start = path ?: _workspaces.value.firstOrNull()?.path
        listDir(start)
    }

    fun listDir(path: String?) {
        _dirLoading.value = true
        _dirError.value = null
        viewModelScope.launch {
            try {
                val listing = withContext(Dispatchers.Default) { api?.listDirectory(path) } ?: return@launch
                _dirPath.value = listing.path
                _dirParent.value = listing.parent.takeIf { it.isNotBlank() && it != listing.path }
                _dirEntries.value = listing.entries
            } catch (e: Exception) {
                _dirError.value = e.message ?: "list failed"
            } finally {
                _dirLoading.value = false
            }
        }
    }

    fun navigateUp() {
        val parent = _dirParent.value ?: return
        listDir(parent)
    }

    /** Download a file's bytes (called from the browser UI). */
    suspend fun downloadFile(path: String): ByteArray {
        return withContext(Dispatchers.Default) { api?.downloadFile(path) ?: ByteArray(0) }
    }

    /** Stream a file download to a local destination file. */
    suspend fun downloadFileTo(path: String, out: java.io.File): java.io.File {
        return withContext(Dispatchers.Default) { api?.downloadFileTo(path, out) ?: out }
    }

    /** Upload a local file (base64) into the given workspace directory, then refresh. */
    fun uploadFile(dir: String, name: String, base64: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { api?.uploadFile(dir, name, base64) }
                listDir(dir)
                toast(Strings.str("uploaded_fmt", name))
                onDone(null)
            } catch (e: Exception) {
                toast(Strings.str("upload_failed_fmt", e.message))
                onDone(e.message)
            }
        }
    }

    /** Delete a file/directory in the workspace, then refresh the current listing. */
    fun deleteFile(path: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { api?.deleteFile(path) }
                listDir(_dirPath.value)
                toast(Strings.str("deleted_file"))
                onDone(null)
            } catch (e: Exception) {
                toast(Strings.str("delete_failed_fmt", e.message))
                onDone(e.message)
            }
        }
    }

    /** Rename a file/directory in the workspace, then refresh. */
    fun renameFile(path: String, name: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { api?.renameFile(path, name) }
                listDir(_dirPath.value)
                toast(Strings.str("renamed"))
                onDone(null)
            } catch (e: Exception) {
                toast(Strings.str("rename_failed_fmt", e.message))
                onDone(e.message)
            }
        }
    }

    /** Duplicate a file in the workspace, then refresh. */
    fun copyFile(path: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { api?.copyFile(path) }
                listDir(_dirPath.value)
                toast(Strings.str("copied_file"))
                onDone(null)
            } catch (e: Exception) {
                toast(Strings.str("copy_failed_fmt", e.message))
                onDone(e.message)
            }
        }
    }

    // --- Subagents ---

    fun loadSubagents() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try {
                _subagents.value = withContext(Dispatchers.Default) { api?.subagentList(sessionId) }?.entries ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun openSubagent(entry: SubagentEntry) {
        val parent = _currentSessionId.value ?: return
        val mode = entry.mode ?: "one-shot"
        _subagentId.value = entry.id
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.Default) { api?.subagentHistory(parent, entry.id, mode) }
                if (history != null) {
                    _subagentChat.value = foldChat(history.events.map { it.event })
                } else {
                    _subagentChat.value = emptyList()
                }
            } catch (_: Exception) {
                _subagentChat.value = emptyList()
            }
        }
    }

    /** Open a subagent child directly from the sidebar tree (parent + child session ids). */
    fun openSubagentSession(parentSessionId: String, childSessionId: String) {
        _currentSessionId.value = parentSessionId
        _subagentId.value = childSessionId
        viewModelScope.launch {
            try {
                val catalog = withContext(Dispatchers.Default) { api?.subagentList(parentSessionId) }
                _subagents.value = catalog?.entries ?: emptyList()
                val entry = _subagents.value.firstOrNull { it.id == childSessionId }
                val mode = entry?.mode ?: "one-shot"
                val history = withContext(Dispatchers.Default) { api?.subagentHistory(parentSessionId, childSessionId, mode) }
                _subagentChat.value = history?.let { foldChat(it.events.map { e -> e.event }) } ?: emptyList()
            } catch (_: Exception) {
                _subagentChat.value = emptyList()
            }
        }
    }

    fun interruptSubagent(entry: SubagentEntry) {
        val parent = _currentSessionId.value ?: return
        if (entry.mode != "continuable") return
        viewModelScope.launch {
            try { api?.subagentInterrupt(parent, entry.id); loadSubagents() } catch (_: Exception) {}
        }
    }

    fun subagentBack() {
        _subagentId.value = null
        _subagentChat.value = emptyList()
    }

    // --- Agent presets / reasoning effort ---

    fun loadAgentPresets() {
        viewModelScope.launch {
            try {
                _agentPresets.value = withContext(Dispatchers.Default) { api?.agentPresetList() }?.presets ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun selectAgentPreset(id: String) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try { api?.agentPresetSelect(sessionId, id) } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun selectReasoningEffort(effortId: String) {
        val sessionId = _currentSessionId.value ?: return
        val current = _models.value?.current ?: return
        viewModelScope.launch {
            try {
                api?.sessionSelectModel(sessionId, current.provider, current.model, effortId)
                refreshModels()
            } catch (_: Exception) {}
        }
    }

    // --- Full-text session search ---

    fun searchSessions(query: String, onResult: (List<Pair<String, String>>) -> Unit) {
        val q = query.trim()
        if (q.isEmpty()) { onResult(emptyList()); return }
        viewModelScope.launch {
            try {
                val v = withContext(Dispatchers.Default) { api?.sessionSearch(q) }
                onResult(v?.items?.map { it.sessionId to it.snippet } ?: emptyList())
            } catch (_: Exception) { onResult(emptyList()) }
        }
    }

    // --- Workspace management ---

    fun createWorkspace(path: String) {
        viewModelScope.launch {
            try { api?.workspaceCreate(path); refreshAll() } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun renameWorkspace(workspaceId: String, title: String) {
        viewModelScope.launch {
            try { api?.workspaceRename(workspaceId, title); refreshAll() } catch (_: Exception) {}
        }
    }

    fun deleteWorkspace(workspaceId: String) {
        viewModelScope.launch {
            try { api?.workspaceDelete(workspaceId); refreshAll() } catch (e: Exception) { _error.value = e.message }
        }
    }

    override fun onCleared() {
        // Flush any pending debounced draft write before the ViewModel is destroyed.
        val snapshot = draftBySession.toMap()
        try {
            runBlocking { settings.saveDrafts(snapshot) }
        } catch (_: Exception) {
        }
        super.onCleared()
    }
}
