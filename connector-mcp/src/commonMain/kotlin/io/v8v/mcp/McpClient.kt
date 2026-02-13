package io.v8v.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.v8v.mcp.model.JsonRpcRequest
import io.v8v.mcp.model.JsonRpcResponse
import io.v8v.mcp.model.McpInitializeParams
import io.v8v.mcp.model.McpInitializeResult
import io.v8v.mcp.model.McpToolCallParams
import io.v8v.mcp.model.McpToolInfo
import io.v8v.mcp.model.McpToolListResult
import io.v8v.mcp.model.McpToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * MCP client that communicates with a local MCP server via JSON-RPC 2.0 over HTTP.
 *
 * Usage:
 * ```kotlin
 * val client = McpClient(McpServerConfig(name = "demo", port = 3001))
 * client.initialize()
 * val tools = client.listTools()
 * val result = client.callTool("create_task", buildJsonObject { put("title", JsonPrimitive("buy milk")) })
 * client.close()
 * ```
 */
class McpClient(
    private val config: McpServerConfig,
    httpClient: HttpClient? = null,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val httpClient: HttpClient =
        httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(this@McpClient.json)
            }
        }

    private var nextRequestId = 0

    /**
     * Initialize the MCP session (protocol handshake).
     *
     * @return Server capabilities and info.
     */
    suspend fun initialize(): McpInitializeResult {
        val params = json.encodeToJsonElement(McpInitializeParams()).jsonObject
        val response = rpc("initialize", params)
        return json.decodeFromJsonElement(McpInitializeResult.serializer(), response.result!!)
    }

    /**
     * Discover available tools on the server.
     */
    suspend fun listTools(): List<McpToolInfo> {
        val response = rpc("tools/list")
        val result = json.decodeFromJsonElement(McpToolListResult.serializer(), response.result!!)
        return result.tools
    }

    /**
     * Invoke a tool on the MCP server.
     *
     * @param name Tool name (e.g. "create_task").
     * @param arguments Tool arguments as a JSON object.
     * @return The tool result.
     */
    suspend fun callTool(
        name: String,
        arguments: JsonObject? = null
    ): McpToolResult {
        val params = json.encodeToJsonElement(McpToolCallParams(name, arguments)).jsonObject
        val response = rpc("tools/call", params)
        if (response.error != null) {
            return McpToolResult(
                content =
                    listOf(
                        io.v8v.mcp.model
                            .McpContent(text = response.error.message),
                    ),
                isError = true,
            )
        }
        return json.decodeFromJsonElement(McpToolResult.serializer(), response.result!!)
    }

    /** Close the HTTP client and release resources. */
    fun close() {
        httpClient.close()
    }

    // ---- internal ----

    private suspend fun rpc(
        method: String,
        params: JsonObject? = null
    ): JsonRpcResponse {
        val request =
            JsonRpcRequest(
                id = ++nextRequestId,
                method = method,
                params = params,
            )
        return httpClient
            .post(config.url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
    }
}
