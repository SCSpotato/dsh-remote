package dev.dsh.remote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dsh.remote.data.WorkspaceView
import dev.dsh.remote.data.SessionSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by vm.sessions.collectAsState()
    val currentId by vm.currentSessionId.collectAsState()
    val connected by vm.connected.collectAsState()
    val connecting by vm.connecting.collectAsState()
    val error by vm.error.collectAsState()
    val running by vm.running.collectAsState()
    val title = sessions.firstOrNull { it.sessionId == currentId }?.title ?: "DSH Remote"
    var showPanel by rememberSaveable { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showSubagents by remember { mutableStateOf(false) }
    var pickWorkspaceDir by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                SidebarContent(
                    vm = vm,
                    onSelectSession = { id ->
                        vm.openSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onSelectSubagent = { parentId, childId ->
                        vm.openSubagentSession(parentId, childId)
                        showSubagents = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenSettings = onOpenSettings,
                    onOpenFiles = {
                        vm.openFileBrowser()
                        showFileBrowser = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenSubagents = {
                        showSubagents = true
                        scope.launch { drawerState.close() }
                    },
                    onCreateWorkspace = {
                        vm.openFileBrowser()
                        pickWorkspaceDir = true
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (running) {
                                StateDot("ongoing", size = 10.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                title,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            DshIcon(DshIcons.PanelLeft, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp, contentDescription = Strings.str("menu"))
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.goHome() }) {
                            DshIcon(DshIcons.Fish, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp, contentDescription = Strings.str("back_home"))
                        }
                        IconButton(onClick = { showPanel = !showPanel }) {
                            DshIcon(DshIcons.Data, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp, contentDescription = Strings.str("sidebar"))
                        }
                        IconButton(onClick = onOpenSettings) {
                            DshIcon(DshIcons.Settings, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp, contentDescription = Strings.str("settings"))
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    showFileBrowser -> FileBrowserScreen(vm, onBack = { showFileBrowser = false })
                    pickWorkspaceDir -> FileBrowserScreen(
                        vm,
                        onBack = { pickWorkspaceDir = false },
                        pickMode = true,
                        onPickDirectory = { dir ->
                            vm.createWorkspace(dir)
                            pickWorkspaceDir = false
                        },
                    )
                    showSubagents -> SubagentScreen(vm, onBack = { showSubagents = false })
                    else -> {
                        when {
                            currentId == null && connecting -> LoadingState(Strings.str("connecting"))
                            currentId == null -> HomeScreen(vm, onSelect = { vm.openSession(it) })
                            else -> SessionView(vm, showPanel, onClosePanel = { showPanel = false })
                        }
                    }
                }
            }
        }
    }

    if (!connected && error != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(Strings.str("connection_failed")) },
            text = { Text(error ?: Strings.str("cannot_connect_server")) },
            confirmButton = {
                TextButton(onClick = { vm.connect() }) { Text(Strings.str("retry")) }
            },
            dismissButton = {
                TextButton(onClick = onOpenSettings) { Text(Strings.str("settings")) }
            },
        )
    }
}

@Composable
private fun SessionView(vm: AppViewModel, showPanel: Boolean, onClosePanel: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(Strings.str("conversation")) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(Strings.str("trajectory")) })
        }
        when (tab) {
            0 -> Box(Modifier.fillMaxSize()) {
                ChatScreen(vm, Modifier.fillMaxSize())
                if (showPanel) {
                    // Scrim: dim the conversation so the panel visibly floats on
                    // top (the same overlay behaviour as the left drawer), and
                    // tapping outside the panel dismisses it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(onClick = onClosePanel),
                    )
                    RightPanel(vm, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
            else -> TrajectoryScreen(vm)
        }
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel, onSelect: (String) -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val finishedAt by vm.finishedSessions.collectAsState()
    val pendingQuestions by vm.pendingQuestions.collectAsState()
    val pendingApprovals by vm.pendingApprovals.collectAsState()

    val runningSessions = remember(sessions) { sessions.filter { it.running && !it.isSubagent } }
    // Recently finished (≤5 min) and not yet opened.
    val finishedSessions = remember(sessions, finishedAt) {
        val now = System.currentTimeMillis()
        sessions.filter { s ->
            val t = finishedAt[s.sessionId] ?: return@filter false
            t >= now - 5 * 60 * 1000L
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item(key = "balance") { BalanceSummaryCard(vm) }

        if (pendingQuestions.isNotEmpty() || pendingApprovals.isNotEmpty()) {
            item(key = "h-decision") { SectionHeader(Strings.str("decisions")) }
            items(pendingQuestions, key = { "q-${it.rpcId}" }) { q ->
                val isPlan = q.questions.firstOrNull()?.intent?.kind == "plan-review"
                NotificationCard(
                    if (isPlan) DshIcons.Goal else DshIcons.Question,
                    if (isPlan) Strings.str("plan_review") else Strings.str("ai_asks"),
                    Strings.str("click_to_answer"),
                    unread = true,
                ) { onSelect(q.sessionId) }
            }
            items(pendingApprovals, key = { "a-${it.rpcId}" }) { a ->
                NotificationCard(DshIcons.Warning, "${Strings.str("needs_approval")} · ${a.toolName}", Strings.str("click_to_handle"), unread = true) { onSelect(a.sessionId) }
            }
        }

        if (runningSessions.isNotEmpty()) {
            item(key = "h-running") { SectionHeader(Strings.str("running")) }
            items(runningSessions, key = { "r-${it.sessionId}" }) { s ->
                NotificationCard(DshIcons.Play, s.title, Strings.str("running_dot_desc") + " · " + Strings.str("click_to_view")) { onSelect(s.sessionId) }
            }
        }

        if (finishedSessions.isNotEmpty()) {
            item(key = "h-finished") { SectionHeader(Strings.str("finished_recent")) }
            items(finishedSessions, key = { "f-${it.sessionId}" }) { s ->
                NotificationCard(DshIcons.Check, s.title, Strings.str("click_to_view"), unread = true) { onSelect(s.sessionId) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun BalanceSummaryCard(vm: AppViewModel) {
    val balance by vm.deepseekBalance.collectAsState()
    val apiKey by vm.deepseekApiKey.collectAsState()
    val info = balance?.balance_infos?.firstOrNull()

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DshIcon(DshIcons.Data, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(Strings.str("balance_title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.queryDeepseekBalance() }) { Text(Strings.str("refresh"), style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.height(6.dp))
        when {
            info != null -> {
                Text(
                    "${info.total_balance} ${info.currency}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    Strings.str("available", if ((info.total_balance.toDoubleOrNull() ?: 0.0) > 0.0) Strings.str("yes") else Strings.str("no")),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            apiKey.isBlank() -> Text(
                Strings.str("api_key_not_set"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text(
                Strings.str("tap_refresh"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NotificationCard(icon: Int, title: String, subtitle: String, unread: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DshIcon(icon, tint = MaterialTheme.colorScheme.primary, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (unread) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(Strings.str("unread"), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LoadingState(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SidebarContent(
    vm: AppViewModel,
    onSelectSession: (String) -> Unit,
    onSelectSubagent: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSubagents: () -> Unit,
    onCreateWorkspace: () -> Unit,
) {
    val workspaces by vm.workspaces.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val archivedIds by vm.archivedSessionIds.collectAsState()
    val activeSessions = sessions.filter { it.sessionId !in archivedIds }
    val subagentCatalogs by vm.subagentChildrenByParent.collectAsState()
    val currentId by vm.currentSessionId.collectAsState()
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sessionMenuTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // sessionId to title
    var searchQuery by remember { mutableStateOf("") }
    var workspaceTarget by remember { mutableStateOf<WorkspaceView?>(null) }
    var workspaceRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf(setOf<String>()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var expandedParents by remember { mutableStateOf(setOf<String>()) }

    // Main conversations (exclude subagent children).
    val mainSessions = remember(activeSessions) { activeSessions.filter { !it.isSubagent } }
    // Subagent children per parent: keep running one-shots and every continuable
    // (resumable) child; drop finished one-shot children. Catalog is primary,
    // session.list running children are a fallback while the catalog is loading.
    val childrenByParent = remember(activeSessions, subagentCatalogs) {
        val result = mutableMapOf<String, MutableList<SubagentChildRowView>>()
        fun add(parentId: String, view: SubagentChildRowView) {
            val list = result.getOrPut(parentId) { mutableListOf() }
            if (list.none { it.id == view.id }) list.add(view)
        }
        for ((parentId, entries) in subagentCatalogs) {
            for (e in entries) {
                if (e.activity == "running" || e.mode == "continuable") {
                    val title = e.label?.takeIf { it.isNotBlank() }
                        ?: activeSessions.firstOrNull { it.sessionId == e.id }?.title
                        ?: e.id
                    add(parentId, SubagentChildRowView(e.id, title, e.activity == "running"))
                }
            }
        }
        for (s in activeSessions) {
            if (s.isSubagent && s.running && s.parentSessionId != null) {
                add(s.parentSessionId!!, SubagentChildRowView(s.sessionId, s.title, true))
            }
        }
        result
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DSH Remote", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row {
                IconButton(onClick = { vm.newSession() }) {
                    DshIcon(DshIcons.NewChat, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp, contentDescription = Strings.str("new_session"))
                }
                IconButton(onClick = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedForDelete = emptySet()
                }) {
                    DshIcon(
                        DshIcons.Trash,
                        tint = if (selectionMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 20.dp,
                        contentDescription = Strings.str("batch_delete"),
                    )
                }
                TextButton(onClick = onCreateWorkspace) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DshIcon(DshIcons.ProjectAdd, tint = MaterialTheme.colorScheme.primary, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(Strings.str("workspace"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(Strings.str("search_conversations")) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))

        if (selectionMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Strings.str("selected_items", selectedForDelete.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = selectedForDelete.isNotEmpty(),
                    onClick = { confirmBatchDelete = true },
                ) { Text(Strings.str("delete"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = {
                    selectionMode = false
                    selectedForDelete = emptySet()
                }) { Text(Strings.str("cancel"), style = MaterialTheme.typography.labelSmall) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFiles)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshIcon(DshIcons.Folder, tint = MaterialTheme.colorScheme.tertiary, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(Strings.str("workspace_files"), fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSubagents)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshIcon(DshIcons.Fish, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(Strings.str("subagents"), fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))

        var searchResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) { searchResults = emptyList(); return@LaunchedEffect }
            delay(300)
            vm.searchSessions(searchQuery) { searchResults = it }
        }

        if (searchQuery.isNotBlank()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(searchResults, key = { it.first }) { (id, snippet) ->
                    val s = sessions.firstOrNull { it.sessionId == id }
                    SessionRow(
                        title = s?.title ?: id,
                        running = s?.running ?: false,
                        selected = id == currentId,
                        onClick = { onSelectSession(id) },
                        onLongClick = { sessionMenuTarget = id to (s?.title ?: id) },
                    )
                    if (snippet.isNotBlank()) {
                        Text(
                            snippet,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                    item(key = "no-results") {
                        Text(Strings.str("no_results"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        } else {
            fun addBranch(out: MutableList<SideRow>, s: dev.dsh.remote.data.SessionSummary) {
                val children = childrenByParent[s.sessionId].orEmpty()
                val hasChildren = children.isNotEmpty()
                val expanded = s.sessionId in expandedParents
                out.add(SideRow.Parent(s, hasChildren, expanded))
                if (expanded) for (c in children) out.add(SideRow.Child(s.sessionId, c))
            }
            val rows = remember(mainSessions, childrenByParent, workspaces, expandedParents) {
                buildList {
                    for (ws in workspaces) {
                        val wsSessions = mainSessions.filter { it.sessionId in ws.sessionIds }
                        if (wsSessions.isEmpty()) continue
                        add(SideRow.Header("ws-${ws.workspaceId}", ws))
                        wsSessions.forEach { s -> addBranch(this, s) }
                    }
                    val orphans = mainSessions.filter { s -> workspaces.none { ws -> s.sessionId in ws.sessionIds } }
                    if (orphans.isNotEmpty()) {
                        add(SideRow.Header("orphans", null))
                        orphans.forEach { s -> addBranch(this, s) }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is SideRow.Header -> {
                            if (row.workspace != null) WorkspaceHeader(row.workspace, onLongClick = { workspaceTarget = row.workspace })
                            else Text(Strings.str("other"), style = MaterialTheme.typography.labelMedium)
                        }
                        is SideRow.Parent -> {
                            SessionRow(
                                title = row.session.title,
                                running = row.session.running,
                                selected = row.session.sessionId == currentId,
                                onClick = { onSelectSession(row.session.sessionId) },
                                onLongClick = { sessionMenuTarget = row.session.sessionId to row.session.title },
                                selectionMode = selectionMode,
                                checked = row.session.sessionId in selectedForDelete,
                                onToggleSelect = {
                                    selectedForDelete = if (row.session.sessionId in selectedForDelete) selectedForDelete - row.session.sessionId else selectedForDelete + row.session.sessionId
                                },
                                hasChildren = row.hasChildren,
                                expanded = row.expanded,
                                onToggleExpand = if (row.hasChildren) {
                                    {
                                        expandedParents = if (row.expanded) expandedParents - row.session.sessionId else expandedParents + row.session.sessionId
                                    }
                                } else null,
                                pendingInteraction = row.session.pendingInteraction,
                            )
                        }
                        is SideRow.Child -> {
                            SubagentChildRow(
                                title = row.child.title,
                                running = row.child.running,
                                selected = row.child.id == currentId,
                                onClick = { onSelectSubagent(row.parentId, row.child.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    sessionMenuTarget?.let { (id, title) ->
        AlertDialog(
            onDismissRequest = { sessionMenuTarget = null },
            title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            text = { Text(Strings.str("choose_action")) },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget = id to title
                    sessionMenuTarget = null
                }) { Text(Strings.str("rename")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteSessionById(id)
                    sessionMenuTarget = null
                }) { Text(Strings.str("delete"), color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    renameTarget?.let { (id, oldTitle) ->
        RenameDialog(
            initial = oldTitle,
            onConfirm = { newTitle ->
                vm.renameSession(id, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    workspaceTarget?.let { ws ->
        AlertDialog(
            onDismissRequest = { workspaceTarget = null },
            title = { Text(ws.title) },
            text = { Text(Strings.str("workspace_actions")) },
            confirmButton = {
                TextButton(onClick = {
                    workspaceRename = ws.workspaceId to ws.title
                    workspaceTarget = null
                }) { Text(Strings.str("rename")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteWorkspace(ws.workspaceId)
                    workspaceTarget = null
                }) { Text(Strings.str("delete"), color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    workspaceRename?.let { (id, oldTitle) ->
        RenameDialog(
            initial = oldTitle,
            onConfirm = { newTitle ->
                vm.renameWorkspace(id, newTitle)
                workspaceRename = null
            },
            onDismiss = { workspaceRename = null },
        )
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text(Strings.str("batch_delete")) },
            text = { Text(Strings.str("confirm_batch_delete", selectedForDelete.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBatchDelete = false
                    selectedForDelete.forEach { vm.deleteSessionById(it) }
                    selectedForDelete = emptySet()
                    selectionMode = false
                }) { Text(Strings.str("delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text(Strings.str("cancel")) }
            },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.str("rename_session")) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(Strings.str("ok")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.str("cancel")) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceHeader(ws: WorkspaceView, onLongClick: () -> Unit) {
    Text(
        text = ws.title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(top = 10.dp, bottom = 4.dp),
    )
}

/** One nested subagent child row (id + resolved title + running flag). */
private data class SubagentChildRowView(val id: String, val title: String, val running: Boolean)

/** Flat row descriptor for the sidebar session tree (headers + parents + nested subagent children). */
private sealed interface SideRow {
    val key: String
    data class Header(override val key: String, val workspace: WorkspaceView?) : SideRow
    data class Parent(val session: SessionSummary, val hasChildren: Boolean, val expanded: Boolean) : SideRow {
        override val key: String get() = session.sessionId
    }
    data class Child(val parentId: String, val child: SubagentChildRowView) : SideRow {
        override val key: String get() = "sub-${child.id}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    title: String,
    running: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selectionMode: Boolean = false,
    checked: Boolean = false,
    onToggleSelect: () -> Unit = {},
    hasChildren: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    pendingInteraction: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick,
            )
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            )
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = checked, onCheckedChange = { onToggleSelect() })
            Spacer(Modifier.width(4.dp))
        }
        if (onToggleExpand != null) {
            DshIcon(
                if (expanded) DshIcons.ChevronDown else DshIcons.ChevronRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 14.dp,
                modifier = Modifier.clickable(onClick = onToggleExpand),
            )
            Spacer(Modifier.width(2.dp))
        }
        StateDot(
            when {
                running -> "ongoing"
                pendingInteraction != null -> "warning"
                else -> "idle"
            },
            size = 8.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SubagentChildRow(
    title: String,
    running: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            )
            .padding(start = 30.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DshIcon(DshIcons.Sparkle, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 12.dp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
