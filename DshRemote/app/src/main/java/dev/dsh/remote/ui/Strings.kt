package dev.dsh.remote.ui

import androidx.compose.runtime.mutableStateOf

/**
 * Minimal in-app i18n. The active language is a Compose state so every screen
 * recomposes when the user switches language. UI code calls [Strings.str]; the
 * ViewModel and foreground service read it too (non-composable contexts are
 * fine because `lang` is a plain MutableState).
 */
object Strings {
    const val ZH = "zh"
    const val EN = "en"

    val lang = mutableStateOf(ZH)

    fun setLang(v: String) {
        lang.value = if (v == EN) EN else ZH
    }

    /** Localized text for a key. Falls back to the key itself when missing. */
    fun str(key: String): String {
        val pair = table[key] ?: return key
        return if (lang.value == EN) pair.second else pair.first
    }

    /** Localized text with `{0}`, `{1}`, … placeholders replaced (null args → empty). */
    fun str(key: String, vararg args: Any?): String {
        var s = str(key)
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a?.toString() ?: "") }
        return s
    }

    /** Pair(zh, en). */
    private val table = mapOf(
        // ---- common ----
        "back" to ("返回" to "Back"),
        "settings" to ("设置" to "Settings"),
        "cancel" to ("取消" to "Cancel"),
        "submit" to ("提交" to "Submit"),
        "confirm" to ("确认" to "Confirm"),
        "delete" to ("删除" to "Delete"),
        "rename" to ("重命名" to "Rename"),
        "copy" to ("复制" to "Copy"),
        "save" to ("保存" to "Save"),
        "loading" to ("加载中…" to "Loading…"),
        "connecting" to ("连接中…" to "Connecting…"),
        "retry" to ("重试" to "Retry"),
        "ok" to ("确定" to "OK"),
        "done" to ("完成" to "Done"),
        "send" to ("发送" to "Send"),
        "stop" to ("停止" to "Stop"),
        "language" to ("语言" to "Language"),
        "appearance" to ("外观" to "Appearance"),
        "notifications" to ("通知" to "Notifications"),
        "about" to ("关于" to "About"),
        "connected" to ("● 已连接" to "● Connected"),
        "disconnected" to ("● 未连接" to "● Not connected"),

        // ---- settings ----
        "server_url" to ("服务器地址" to "Server URL"),
        "server_url_hint" to ("https://host:port" to "https://host:port"),
        "server_url_example" to ("例如 https://desktop-e0lt97r.tailcf2bf3.ts.net:8443" to "e.g. https://your-machine.tailxxxx.ts.net:8443"),
        "save_and_connect" to ("保存并连接" to "Save & Connect"),
        "light" to ("浅色" to "Light"),
        "dark" to ("深色" to "Dark"),
        "follow_system" to ("跟随系统" to "System"),
        "notify_done" to ("任务完成提醒" to "Task done alerts"),
        "notify_done_sub" to ("回合完成时发送通知并播放提示音" to "Notify with sound when a turn completes"),
        "notify_prompt" to ("提问 / 批准提醒" to "Question / approval alerts"),
        "notify_prompt_sub" to ("AI 提问或请求批准时发送通知" to "Notify on AI questions or approval requests"),
        "deepseek_platform" to ("DeepSeek 平台" to "DeepSeek Platform"),
        "check_balance" to ("查询余额 / 用量" to "Check balance / usage"),

        // ---- home / sidebar ----
        "decisions" to ("待决策" to "Needs decision"),
        "running" to ("正在运行" to "Running"),
        "finished_recent" to ("刚完成" to "Just finished"),
        "conversation" to ("对话" to "Chat"),
        "trajectory" to ("轨迹" to "Trajectory"),
        "sessions" to ("会话" to "Sessions"),
        "workspace" to ("工作区" to "Workspace"),
        "sidebar" to ("侧边栏" to "Sidebar"),
        "running_dot_desc" to ("运行中" to "Running"),
        "click_to_view" to ("点击查看" to "Tap to view"),
        "click_to_answer" to ("点击查看并作答" to "Tap to view and answer"),
        "click_to_handle" to ("点击处理" to "Tap to handle"),
        "ai_asks" to ("AI 向你提问" to "AI asks you"),
        "plan_review" to ("计划待审" to "Plan review"),
        "needs_approval" to ("需要批准" to "Needs approval"),

        // ---- chat / composer ----
        "input_hint" to ("输入消息…" to "Type a message…"),
        "commands" to ("命令" to "Commands"),
        "permissions" to ("权限" to "Permissions"),
        "choose_model" to ("选择模型" to "Choose model"),
        "enqueue" to ("加入队列" to "Queue"),
        "remove" to ("移除" to "Remove"),
        "edit_queue_msg" to ("修改队列消息" to "Edit queued message"),
        "fork" to ("分支" to "Fork"),
        "copy_conversation" to ("复制对话" to "Copy conversation"),
        "load_earlier" to ("加载更早的对话…" to "Load earlier…"),
        "jump_bottom" to ("回到底部" to "Jump to bottom"),
        "deliverables" to ("产物" to "Deliverables"),

        // ---- decision cards ----
        "approve_once" to ("允许一次" to "Allow once"),
        "reject" to ("拒绝" to "Reject"),
        "approve_execute" to ("确认执行" to "Approve"),
        "keep_planning" to ("继续规划" to "Keep planning"),
        "custom_answer_hint" to ("或输入自定义答案…" to "Or type a custom answer…"),
        "tool_label" to ("工具" to "Tool"),
        "question_title" to ("问题" to "Question"),

        // ---- file browser ----
        "upload" to ("上传" to "Upload"),
        "files" to ("文件" to "Files"),
        "file_browser" to ("文件" to "Files"),
        "empty_dir" to ("空目录" to "Empty directory"),

        // ---- toasts / misc ----
        "copied" to ("已复制" to "Copied"),
        "copied_clipboard" to ("已复制到剪贴板" to "Copied to clipboard"),
        "no_copy_content" to ("没有可复制的内容" to "Nothing to copy"),
        "forked_session" to ("已创建分支会话" to "Forked session created"),
        "fork_failed" to ("分支失败" to "Fork failed"),
        "not_connected" to ("未连接" to "Not connected"),
        "deleted_session" to ("已删除对话" to "Conversation deleted"),
        "delete_failed" to ("删除失败" to "Delete failed"),
        "renamed" to ("已重命名" to "Renamed"),
        "rename_failed" to ("重命名失败" to "Rename failed"),
        "uploaded_file" to ("已上传" to "Uploaded"),
        "upload_failed" to ("上传失败" to "Upload failed"),
        "deleted_file" to ("已删除" to "Deleted"),
        "copied_file" to ("已复制" to "Copied"),
        "attachment_failed" to ("附件发送失败" to "Attachment failed"),
        "image_unsupported" to ("当前模型不支持图片附件" to "Current model does not support images"),
        "image_loading" to ("图片加载中…" to "Loading image…"),
        "image_preview" to ("图片" to "Image"),
        "command_failed" to ("命令执行失败" to "Command failed"),
        "command_error" to ("命令失败" to "Command failed"),

        // ---- plan/goal commands ----
        "cmd_plan" to ("进入或退出计划模式" to "Enter or leave plan mode"),
        "cmd_goal" to ("查看或设置长期任务目标" to "View or set the long-running goal"),
        "cmd_compact" to ("压缩较早的对话历史" to "Compact older conversation history"),
        "plan_active" to ("（当前：计划中）" to (" (planning)")),

        // ---- notifications (foreground service) ----
        "notif_task_done" to ("DSH 任务已完成" to "DSH task completed"),
        "notif_task_done_sub" to ("一个对话回合已完成" to "A conversation turn finished"),
        "notif_task_error" to ("DSH 任务出错" to "DSH task failed"),
        "notif_task_error_sub" to ("一个对话回合出错了" to "A conversation turn errored"),
        "notif_question" to ("DSH: AI 向你提问" to "DSH: AI asks you"),
        "notif_question_sub" to ("点击回到应用作答" to "Tap to open and answer"),
        "notif_approval" to ("DSH: 需要批准" to "DSH: approval needed"),
        "notif_approval_sub" to ("点击回到应用处理" to "Tap to open and handle"),
        "notif_fg_title" to ("DSH Remote" to "DSH Remote"),
        "notif_fg_sub" to ("已连接,后台监控中" to "Connected, monitoring in background"),
        "channel_conn" to ("连接状态" to "Connection status"),
        "channel_events" to ("任务提醒" to "Task alerts"),
        "channel_done" to ("任务完成" to "Task done"),

        // ---- tool view info ----
        "tool_cmd" to ("执行命令" to "Run command"),
        "paths_count" to ("{0} 个路径" to "{0} paths"),
        "matches_files" to ("{0} 处匹配 · {1} 个文件" to "{0} matches · {1} files"),
        "matches_count" to ("{0} 处匹配" to "{0} matches"),
        "search" to ("搜索" to "Search"),
        "sources_count" to ("{0} 个来源" to "{0} sources"),
        "fetched" to ("抓取 {0}" to "Fetched {0}"),
        "web" to ("网页" to "Web"),

        // ---- toasts / errors ----
        "fill_api_key" to ("请先填写 DeepSeek API Key" to "Please fill in the DeepSeek API Key first"),
        "query_failed" to ("查询失败" to "Query failed"),
        "command_failed_fmt" to ("命令失败: {0}" to "Command failed: {0}"),
        "attachment_failed_fmt" to ("附件发送失败: {0}" to "Attachment failed: {0}"),
        "fork_failed_not_conn" to ("分支失败: 未连接" to "Fork failed: not connected"),
        "fork_failed_fmt" to ("分支失败: {0}" to "Fork failed: {0}"),
        "you_prefix" to ("你: {0}" to "You: {0}"),
        "delete_failed_fmt" to ("删除失败: {0}" to "Delete failed: {0}"),
        "uploaded_fmt" to ("已上传 {0}" to "Uploaded {0}"),
        "upload_failed_fmt" to ("上传失败: {0}" to "Upload failed: {0}"),
        "rename_failed_fmt" to ("重命名失败: {0}" to "Rename failed: {0}"),
        "copy_failed_fmt" to ("复制失败: {0}" to "Copy failed: {0}"),
        "download_failed" to ("下载失败" to "Download failed"),
        "cannot_open_file" to ("无法打开文件" to "Cannot open file"),

        // ---- balance ----
        "balance_title" to ("DeepSeek 余额" to "DeepSeek Balance"),
        "querying" to ("查询中…" to "Querying…"),
        "save_and_query" to ("保存并查询余额" to "Save & query balance"),
        "no_balance_info" to ("暂无余额信息" to "No balance info"),
        "fill_key_then_query" to ("填写 API Key 后点击查询" to "Fill in the API Key then query"),
        "total_balance" to ("总余额 {0}" to "Total balance {0}"),
        "available" to ("可用 {0}" to "Available {0}"),
        "yes" to ("是" to "Yes"),
        "no" to ("否" to "No"),
        "granted_balance" to ("赠送余额" to "Granted balance"),
        "topped_up_balance" to ("充值余额" to "Topped-up balance"),

        // ---- file browser ----
        "cannot_read_file" to ("无法读取文件" to "Cannot read file"),
        "file_too_large" to ("文件过大（上限约 30MB）" to "File too large (max ~30MB)"),
        "parent_dir" to ("上级目录" to "Parent directory"),
        "choose_this_dir" to ("选择此目录" to "Choose this directory"),
        "uploading" to ("上传中…" to "Uploading…"),
        "close" to ("关闭" to "Close"),
        "downloaded" to ("已下载" to "Downloaded"),
        "saved_to" to ("已保存到本地:\n{0}" to "Saved to:\n{0}"),
        "open" to ("打开" to "Open"),
        "delete_confirm_fmt" to ("确定删除「{0}」吗?此操作不可撤销。" to "Delete \"{0}\"? This cannot be undone."),
        "op_failed" to ("操作失败" to "Operation failed"),

        // ---- markdown ----
        "code_copied" to ("已复制代码" to "Code copied"),
        "copy_code" to ("复制代码" to "Copy code"),
        "image" to ("图片" to "Image"),

        // ---- right panel / jobs ----
        "no_goal_todo_jobs" to ("无目标 / 待办 / 后台任务" to "No goal / todos / background jobs"),
        "todos_count" to ("待办 ({0})" to "Todos ({0})"),
        "goal_phase" to ("目标 · {0}" to "Goal · {0}"),
        "pause" to ("暂停" to "Pause"),
        "complete" to ("完成" to "Complete"),
        "resume" to ("恢复" to "Resume"),
        "background_jobs" to ("后台任务" to "Background jobs"),
        "task" to ("任务" to "Task"),
        "status_completed" to ("已完成" to "Completed"),
        "status_killed" to ("已终止" to "Killed"),
        "status_failed" to ("失败" to "Failed"),
        "status_stopping" to ("停止中" to "Stopping"),
        "status_running" to ("进行中" to "Running"),
        "secs_fmt" to ("{0}秒" to "{0}s"),
        "min_sec_fmt" to ("{0}分{1}秒" to "{0}m{1}s"),
        "secs_dec_fmt" to ("{0}.{1}秒" to "{0}.{1}s"),

        // ---- subagent ----
        "subagents" to ("子代理" to "Subagents"),
        "subagent_chat" to ("子代理对话" to "Subagent chat"),
        "no_subagents" to ("暂无子代理" to "No subagents"),
        "interrupt" to ("中断" to "Interrupt"),

        // ---- trajectory ----
        "params" to ("参数" to "Arguments"),
        "result" to ("结果" to "Result"),
        "exec_error" to ("执行出错" to "Execution failed"),
        "no_output" to ("(无输出)" to "(no output)"),
        "mode_sequence" to ("顺序" to "Sequence"),
        "mode_time" to ("时间" to "Time"),
        "mode_duration" to ("时长" to "Duration"),
        "mode_actual" to ("真实" to "Actual"),
        "lane_input" to ("输入" to "Input"),
        "lane_model" to ("模型" to "Model"),
        "lane_tool" to ("工具" to "Tool"),
        "stat_turns" to ("轮次" to "Turns"),
        "stat_steps" to ("步" to "Steps"),
        "stat_model" to ("模型" to "Model"),
        "stat_tool" to ("工具" to "Tool"),
        "stat_ttft" to ("首token" to "First token"),
        "stat_input_tokens" to ("输入token" to "Input tokens"),
        "stat_cache_hits" to ("缓存命中" to "Cache hits"),
        "stat_output_tokens" to ("输出token" to "Output tokens"),
        "ctx_used" to ("上下文已用 {0}%" to "Context used {0}%"),
        "ctx_system" to ("系统提示词" to "System prompt"),
        "ctx_tools" to ("工具" to "Tools"),
        "ctx_messages" to ("对话消息" to "Messages"),
        "turn_fmt" to ("回合 {0}" to "Turn {0}"),
        "turn_end_fmt" to ("—— 回合结束 ({0}) ——" to "—— Turn ended ({0}) ——"),
        "tool_result" to ("工具结果" to "Tool result"),
        "todo_update" to ("待办更新" to "Todo update"),

        // ---- reasoning / expand ----
        "reasoning" to ("思考过程" to "Reasoning"),
        "collapse" to ("收起" to "Collapse"),
        "expand" to ("展开" to "Expand"),
        "more_files" to ("+ {0} 文件" to "+ {0} files"),
        "tool_exec_error" to ("工具执行出错" to "Tool execution failed"),
        "preset" to ("预设" to "Preset"),
        "reasoning_effort" to ("推理" to "Reasoning"),
        "msg_queue" to ("消息队列 ({0})" to "Message queue ({0})"),
        "attachment" to ("附件" to "Attachment"),
        "image_attachment" to ("图片附件" to "Image attachment"),

        // ---- main screen ----
        "menu" to ("菜单" to "Menu"),
        "back_home" to ("回到主界面" to "Back to home"),
        "connection_failed" to ("连接失败" to "Connection failed"),
        "cannot_connect_server" to ("无法连接到服务器" to "Cannot connect to server"),
        "refresh" to ("刷新" to "Refresh"),
        "api_key_not_set" to ("未设置 API Key（在设置中填写）" to "API Key not set (set it in Settings)"),
        "tap_refresh" to ("点击刷新查询余额" to "Tap refresh to query balance"),
        "unread" to ("未读" to "Unread"),
        "new_session" to ("新建会话" to "New session"),
        "batch_delete" to ("批量删除" to "Batch delete"),
        "search_conversations" to ("搜索对话…" to "Search conversations…"),
        "selected_items" to ("已选 {0} 项" to "{0} selected"),
        "workspace_files" to ("工作区文件" to "Workspace files"),
        "no_results" to ("无搜索结果" to "No results"),
        "other" to ("其他" to "Other"),
        "choose_action" to ("选择操作" to "Choose action"),
        "workspace_actions" to ("工作区操作" to "Workspace actions"),
        "confirm_batch_delete" to ("确定删除选中的 {0} 个对话吗?此操作会将其归档(从列表移除)。" to "Archive the {0} selected conversations? This removes them from the list."),
        "rename_session" to ("重命名会话" to "Rename session"),
        "turn_cost" to ("本次约 ¥{0} · 输入 {1} · 输出 {2}" to "This turn ≈ ¥{0} · in {1} · out {2}"),
        "balance_only" to ("剩余 ¥{0}" to "Remaining ¥{0}"),
        "balance_and_cost" to ("剩余 ¥{0} · 本次 ¥{1}" to "Balance ¥{0} · This turn ¥{1}"),
    )
}
