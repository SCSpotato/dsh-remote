package dev.dsh.remote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.dsh.remote.MainActivity
import dev.dsh.remote.R
import dev.dsh.remote.data.SettingsStore
import dev.dsh.remote.net.TrustAll
import dev.dsh.remote.net.WsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DshForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var muxJob: Job? = null
    private val settingsStore by lazy { SettingsStore(this) }
    // Dedupe keys so a re-broadcast of the same decision/turn does not spam
    // a new high-importance notification (which previously crashed the app).
    private val notified = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FG_ID, buildForegroundNotification("DSH Remote", "已连接,后台监控中"))
        // Cancel any previous WebSocket collection before opening a new one,
        // otherwise repeated onStartCommand calls leak sockets.
        muxJob?.cancel()
        muxJob = scope.launch {
            val url = settingsStore.serverUrl.first()
            val ws = WsClient(url, TrustAll.client())
            ws.frames("/api/events.mux").collect { frame ->
                val type = frame.payload["type"]?.jsonPrimitive?.content ?: return@collect
                when (type) {
                    "session/event" -> {
                        val ev = frame.payload["event"]?.jsonObject ?: return@collect
                        if (ev["type"]?.jsonPrimitive?.content == "turn/end") {
                            val reason = ev["data"]?.jsonObject
                                ?.get("reason")?.jsonObject
                                ?.get("kind")?.jsonPrimitive?.content
                            val sid = frame.payload["sessionId"]?.jsonPrimitive?.content ?: ""
                            val seq = ev["seq"]?.jsonPrimitive?.content ?: ""
                            val key = "turn:$sid:$seq"
                            if (!notified.add(key)) return@collect
                            when (reason) {
                                "completed" -> if (settingsStore.notifyDone.first()) notifyEvent("DSH 任务已完成", "一个对话回合已完成", sid, CHANNEL_DONE)
                                "error" -> if (settingsStore.notifyDone.first()) notifyEvent("DSH 任务出错", "一个对话回合出错了", sid)
                            }
                        }
                    }
                    "question/requested" -> {
                        if (!notified.add("q:${frame.rpcId}")) return@collect
                        val sid = frame.payload["sessionId"]?.jsonPrimitive?.content ?: ""
                        if (settingsStore.notifyPrompt.first()) notifyEvent("DSH: AI 向你提问", "点击回到应用作答", sid)
                    }
                    "approval/requested" -> {
                        if (!notified.add("a:${frame.rpcId}")) return@collect
                        val sid = frame.payload["sessionId"]?.jsonPrimitive?.content ?: ""
                        if (settingsStore.notifyPrompt.first()) notifyEvent("DSH: 需要批准", "点击回到应用处理", sid)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun notifyEvent(title: String, text: String, sessionId: String, channelId: String = CHANNEL_EVENTS) {
        val nm = getSystemService(NotificationManager::class.java)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, sessionId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FOREGROUND, "连接状态", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, "任务提醒", NotificationManager.IMPORTANCE_HIGH),
        )
        // Completion channel plays the bundled chime (res/raw/task_done.wav).
        // On Android 8+ the sound belongs to the channel, not the notification.
        val done = NotificationChannel(CHANNEL_DONE, "任务完成", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                Uri.parse("android.resource://${packageName}/${R.raw.task_done}"),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
        }
        nm.createNotificationChannel(done)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_FOREGROUND = "dsh_foreground"
        const val CHANNEL_EVENTS = "dsh_events"
        const val CHANNEL_DONE = "dsh_done"
        const val FG_ID = 1
        const val EXTRA_SESSION_ID = "dsh.sessionId"
    }
}
