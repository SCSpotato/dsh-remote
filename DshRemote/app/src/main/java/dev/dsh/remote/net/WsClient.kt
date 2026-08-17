package dev.dsh.remote.net

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WsClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
) {
    private val json = DshJson.json

    fun connect(path: String): Flow<ServerRequest> = callbackFlow {
        val wsUrl = baseUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + path

        val socket = client.newWebSocket(
            Request.Builder()
                .url(wsUrl)
                .header("Origin", baseUrl.trimEnd('/'))
                .build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        trySend(json.decodeFromString(ServerRequest.serializer(), text))
                    } catch (_: Exception) {
                        // drop malformed frames
                    }
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }
            },
        )

        awaitClose { socket.cancel() }
    }

    /** One stream with automatic reconnect (2s backoff). */
    fun frames(path: String): Flow<ServerRequest> =
        connect(path).retryWhen { _, _ ->
            delay(2000)
            true
        }
}
