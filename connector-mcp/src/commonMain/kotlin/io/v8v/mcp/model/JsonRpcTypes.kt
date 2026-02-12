package io.v8v.mcp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ---- JSON-RPC 2.0 envelope ----

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonObject? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

// ---- MCP-specific types ----

@Serializable
data class McpInitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: McpClientCapabilities = McpClientCapabilities(),
    val clientInfo: McpClientInfo = McpClientInfo(),
)

@Serializable
data class McpClientCapabilities(
    val roots: McpRootsCapability? = null,
)

@Serializable
data class McpRootsCapability(
    val listChanged: Boolean = false,
)

@Serializable
data class McpClientInfo(
    val name: String = "voice-agent",
    val version: String = "1.0.0",
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: McpServerCapabilities = McpServerCapabilities(),
    val serverInfo: McpServerInfo? = null,
)

@Serializable
data class McpServerCapabilities(
    val tools: McpToolsCapability? = null,
)

@Serializable
data class McpToolsCapability(
    val listChanged: Boolean = false,
)

@Serializable
data class McpServerInfo(
    val name: String,
    val version: String? = null,
)

// ---- Tools ----

@Serializable
data class McpToolListResult(
    val tools: List<McpToolInfo> = emptyList(),
)

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject? = null,
)

@Serializable
data class McpToolCallParams(
    val name: String,
    val arguments: JsonObject? = null,
)

@Serializable
data class McpToolResult(
    val content: List<McpContent> = emptyList(),
    @SerialName("isError")
    val isError: Boolean = false,
)

@Serializable
data class McpContent(
    val type: String = "text",
    val text: String = "",
)
