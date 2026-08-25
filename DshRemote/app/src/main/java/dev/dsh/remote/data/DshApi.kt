package dev.dsh.remote.data

import dev.dsh.remote.net.DshJson
import dev.dsh.remote.net.RpcClient
import java.net.URLEncoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class DshApi(private val rpc: RpcClient) {
    private val json = DshJson.json

    suspend fun workspaceList(): WorkspaceListValue {
        val v = rpc.invoke("workspace.list", JsonObject(emptyMap()))
        return json.decodeFromJsonElement(WorkspaceListValue.serializer(), v)
    }

    suspend fun sessionList(): List<SessionSummary> {
        val v = rpc.invoke("session.list", JsonObject(emptyMap()))
        return json.decodeFromJsonElement(SessionListValue.serializer(), v).items
    }

    suspend fun sessionHistory(
        sessionId: String,
        beforeSeq: Long? = null,
        maxMessages: Int? = null,
    ): SessionHistoryValue {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            if (beforeSeq != null) put("beforeSeq", beforeSeq)
            if (maxMessages != null) put("maxMessages", maxMessages)
        }
        return json.decodeFromJsonElement(SessionHistoryValue.serializer(), rpc.invoke("session.history", payload))
    }

    /**
     * Read one durable image attachment's bytes for a session (base64 in the
     * RPC value). The host only serves attachments this session's log references,
     * so the correct sessionId must be passed. Returns null when data is absent.
     */
    suspend fun sessionAttachment(sessionId: String, attachmentId: String): AttachmentImage? {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("attachmentId", attachmentId)
        }
        val v = rpc.invoke("session.attachment", payload) as? JsonObject ?: return null
        val data = v["data"]?.jsonPrimitive?.content ?: return null
        val att = v["attachment"]?.jsonObject
        return AttachmentImage(
            attachmentId = attachmentId,
            mediaType = att?.get("mediaType")?.jsonPrimitive?.content ?: "image/png",
            width = att?.get("width")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            height = att?.get("height")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            base64 = data,
        )
    }

    suspend fun sessionCreate(
        workspaceId: String? = null,
        agentPreset: String? = null,
        sessionId: String? = null,
    ): SessionCreateValue {
        val payload = buildJsonObject {
            if (workspaceId != null) put("workspaceId", workspaceId)
            if (agentPreset != null) put("agentPreset", agentPreset)
            if (sessionId != null) put("sessionId", sessionId)
        }
        return json.decodeFromJsonElement(SessionCreateValue.serializer(), rpc.invoke("session.create", payload))
    }

    suspend fun sessionPrompt(sessionId: String, text: String) {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("mode", "queue")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            }
        }
        rpc.invoke("session.prompt", payload)
    }

    /**
     * Edit/remove an item already sitting in the host's native inbox queue.
     * actionKind: "edit" (with text content) | "remove" | "steer".
     */
    suspend fun sessionUpdateQueue(sessionId: String, itemId: String, actionKind: String, text: String? = null) {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("itemId", itemId)
            putJsonObject("action") {
                put("kind", actionKind)
                if (actionKind == "edit") {
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", text ?: "") }
                    }
                }
            }
        }
        rpc.invoke("session.updateQueue", payload)
    }

    /** Fork the conversation into a new child session, anchored at the given event seq. */
    suspend fun sessionFork(sessionId: String, atSeq: Long? = null): SessionCreateValue {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            if (atSeq != null) put("atSeq", atSeq)
        }
        return json.decodeFromJsonElement(SessionCreateValue.serializer(), rpc.invoke("session.fork", payload))
    }

    /** Send an image as a user prompt (base64 data). */
    suspend fun sessionPromptImage(sessionId: String, mediaType: String, base64: String, name: String?) {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("mode", "queue")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "image")
                    put("mediaType", mediaType)
                    put("data", base64)
                    name?.let { put("name", it) }
                }
            }
        }
        rpc.invoke("session.prompt", payload)
    }

    /** Send an image + optional text as one user prompt. */
    suspend fun sessionPromptImageText(sessionId: String, mediaType: String, base64: String, name: String?, text: String) {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("mode", "queue")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "image")
                    put("mediaType", mediaType)
                    put("data", base64)
                    name?.let { put("name", it) }
                }
                if (text.isNotBlank()) {
                    addJsonObject { put("type", "text"); put("text", text) }
                }
            }
        }
        rpc.invoke("session.prompt", payload)
    }

    /** Archive (remove) a session from the active list. */
    suspend fun archiveSession(sessionId: String) {
        val payload = buildJsonObject { put("sessionId", sessionId) }
        rpc.invoke("workspace.archiveSession", payload)
    }

    suspend fun subagentList(parentSessionId: String): SubagentListValue {
        val payload = buildJsonObject { put("parentSessionId", parentSessionId) }
        return json.decodeFromJsonElement(SubagentListValue.serializer(), rpc.invoke("subagent.list", payload))
    }

    suspend fun subagentHistory(parentSessionId: String, childSessionId: String, mode: String): SessionHistoryValue {
        val payload = buildJsonObject {
            put("parentSessionId", parentSessionId)
            put("childSessionId", childSessionId)
            put("mode", mode)
            put("maxMessages", 30)
        }
        return json.decodeFromJsonElement(SessionHistoryValue.serializer(), rpc.invoke("subagent.history", payload))
    }

    suspend fun subagentInterrupt(parentSessionId: String, childSessionId: String) {
        val payload = buildJsonObject {
            put("parentSessionId", parentSessionId)
            put("childSessionId", childSessionId)
            put("mode", "continuable")
        }
        rpc.invoke("subagent.interrupt", payload)
    }

    /**
     * Execute a slash command through the host command channel (`commands.execute`),
     * NOT as a chat message. Returns `null` when the line is not a known command
     * (the host logs nothing in that case), so callers can fall back to a plain prompt.
     */
    suspend fun commandExecute(sessionId: String, line: String): CommandExecuteValue? {
        val args = buildJsonObject {
            put("agentId", sessionId)
            put("line", line)
            putJsonArray("images") { /* no image attachments for slash commands */ }
        }
        val payload = buildJsonObject { put("args", args) }
        val v = rpc.invoke("commands/execute", payload)
        if (v is JsonNull) return null
        return json.decodeFromJsonElement(CommandExecuteValue.serializer(), v)
    }

    /** List the host's slash-command directory for one session (`commands.list`). */
    suspend fun commandList(sessionId: String): List<CommandDescriptor> {
        val args = buildJsonObject { put("agentId", sessionId) }
        val payload = buildJsonObject { put("args", args) }
        val v = rpc.invoke("commands/list", payload)
        return json.decodeFromJsonElement(ListSerializer(CommandDescriptor.serializer()), v)
    }

    suspend fun agentPresetList(): AgentPresetListValue {
        val v = rpc.invoke("agentPreset.list", JsonObject(emptyMap()))
        return json.decodeFromJsonElement(AgentPresetListValue.serializer(), v)
    }

    suspend fun agentPresetSelect(sessionId: String, agentPreset: String) {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("agentPreset", agentPreset)
        }
        rpc.invoke("agentPreset.select", payload)
    }

    suspend fun sessionSearch(query: String): SessionSearchValue {
        val payload = buildJsonObject { put("query", query) }
        return json.decodeFromJsonElement(SessionSearchValue.serializer(), rpc.invoke("session.search", payload))
    }

    suspend fun workspaceCreate(path: String) {
        rpc.invoke("workspace.create", buildJsonObject { put("path", path) })
    }

    suspend fun workspaceRename(workspaceId: String, title: String) {
        rpc.invoke("workspace.rename", buildJsonObject { put("workspaceId", workspaceId); put("title", title) })
    }

    suspend fun workspaceDelete(workspaceId: String) {
        rpc.invoke("workspace.delete", buildJsonObject { put("workspaceId", workspaceId) })
    }

    suspend fun sessionModels(sessionId: String): SessionModelsValue {
        val payload = buildJsonObject { put("sessionId", sessionId) }
        return json.decodeFromJsonElement(SessionModelsValue.serializer(), rpc.invoke("session.models", payload))
    }

    suspend fun sessionSelectModel(
        sessionId: String,
        provider: String,
        model: String,
        reasoningEffort: String? = null,
    ) {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("provider", provider)
            put("model", model)
            if (reasoningEffort != null) put("reasoningEffort", reasoningEffort)
        }
        rpc.invoke("session.selectModel", payload)
    }

    suspend fun sessionCancel(sessionId: String) {
        rpc.invoke("session.cancel", buildJsonObject { put("sessionId", sessionId) })
    }

    suspend fun sessionRename(sessionId: String, title: String) {
        rpc.invoke("session.rename", buildJsonObject { put("sessionId", sessionId); put("title", title) })
    }

    suspend fun goalCreate(sessionId: String, objective: String) {
        rpc.invoke("goal.create", buildJsonObject { put("sessionId", sessionId); put("objective", objective) })
    }

    suspend fun goalPause(sessionId: String, refId: String, revision: Int) {
        rpc.invoke("goal.pause", goalPayload(sessionId, refId, revision))
    }

    suspend fun goalResume(sessionId: String, refId: String, revision: Int) {
        rpc.invoke("goal.resume", goalPayload(sessionId, refId, revision))
    }

    suspend fun goalComplete(sessionId: String, refId: String, revision: Int) {
        rpc.invoke("goal.complete", goalPayload(sessionId, refId, revision))
    }

    suspend fun goalClear(sessionId: String, refId: String, revision: Int) {
        rpc.invoke("goal.clear", goalPayload(sessionId, refId, revision))
    }

    suspend fun respondApproval(rpcId: String, sessionId: String, approvalId: String, allow: Boolean) {
        val value = buildJsonObject {
            put("approvalId", approvalId)
            put("sessionId", sessionId)
            put("outcome", if (allow) "allowed-once" else "rejected")
        }
        rpc.respond(rpcId, value)
    }

    suspend fun respondQuestion(
        rpcId: String,
        sessionId: String,
        answers: List<QuestionAnswer>,
    ) {
        val value = buildJsonObject {
            put("sessionId", sessionId)
            putJsonObject("answer") {
                putJsonArray("answers") {
                    for (a in answers) {
                        addJsonObject {
                            put("id", a.id)
                            putJsonArray("selected") { for (s in a.selected) add(s) }
                            a.custom?.let { put("custom", it) }
                        }
                    }
                }
            }
        }
        rpc.respond(rpcId, value)
    }

    /** List one directory (files + folders) via the remote-control browse route. */
    suspend fun listDirectory(path: String?): DirectoryListingValue {
        val q = if (path.isNullOrBlank()) "" else "?path=" + URLEncoder.encode(path, "UTF-8")
        val text = rpc.getText("/remote/list$q")
        return json.decodeFromString(DirectoryListingValue.serializer(), text)
    }

    /** Download a file's bytes via the remote-control file route. */
    suspend fun downloadFile(path: String): ByteArray {
        val q = "?path=" + URLEncoder.encode(path, "UTF-8")
        return rpc.getBytes("/remote/file$q")
    }

    /** Stream a file download to a local destination file. */
    suspend fun downloadFileTo(path: String, out: java.io.File): java.io.File {
        val q = "?path=" + URLEncoder.encode(path, "UTF-8")
        return rpc.downloadToFile("/remote/file$q", out)
    }

    /** Upload a file (base64) into a workspace directory via the remote-control route. */
    suspend fun uploadFile(dir: String, name: String, base64: String) {
        val payload = buildJsonObject {
            put("dir", dir)
            put("name", name)
            put("data", base64)
        }
        rpc.postJson("/remote/upload", payload.toString())
    }

    /** Delete a file or directory in the workspace via the remote-control route. */
    suspend fun deleteFile(path: String) {
        val payload = buildJsonObject { put("path", path) }
        rpc.postJson("/remote/delete", payload.toString())
    }

    /** Rename a file/directory (new name, same directory). */
    suspend fun renameFile(path: String, name: String) {
        val payload = buildJsonObject { put("path", path); put("name", name) }
        rpc.postJson("/remote/rename", payload.toString())
    }

    /** Duplicate a file in place ("-copy" suffix). */
    suspend fun copyFile(path: String) {
        val payload = buildJsonObject { put("path", path) }
        rpc.postJson("/remote/copy", payload.toString())
    }
}

data class QuestionAnswer(
    val id: String,
    val selected: List<String> = emptyList(),
    val custom: String? = null,
)

/** A durable image attachment fetched from the host (bytes as base64). */
data class AttachmentImage(
    val attachmentId: String,
    val mediaType: String = "image/png",
    val width: Int = 0,
    val height: Int = 0,
    val base64: String,
)

private fun goalPayload(sessionId: String, refId: String, revision: Int): JsonObject =
    buildJsonObject {
        put("sessionId", sessionId)
        putJsonObject("ref") {
            put("id", refId)
            put("revision", revision)
        }
    }
