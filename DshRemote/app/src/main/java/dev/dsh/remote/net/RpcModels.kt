package dev.dsh.remote.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object DshJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
}

@Serializable
data class ClientRequest(
    val type: String = "client-request",
    val rpcId: String,
    val method: String,
    val payload: JsonObject,
)

@Serializable
data class RpcError(
    val code: String,
    val message: String,
)

@Serializable
data class RpcResult(
    val ok: Boolean,
    val value: JsonElement? = null,
    val error: RpcError? = null,
)

@Serializable
data class ServerResponse(
    val type: String,
    val rpcId: String,
    val result: RpcResult,
)

@Serializable
data class ServerRequest(
    val type: String,
    val rpcId: String,
    val method: String,
    val payload: JsonObject,
)

@Serializable
data class ClientResponse(
    val type: String = "client-response",
    val rpcId: String,
    val result: RpcResult,
)

class RpcException(val code: String, message: String) : Exception("$code: $message")
