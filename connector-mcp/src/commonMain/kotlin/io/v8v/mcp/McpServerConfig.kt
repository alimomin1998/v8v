package io.v8v.mcp

/**
 * Configuration for connecting to a local MCP server.
 *
 * @property name Human-readable server name (e.g. "efforti").
 * @property host Host address. Defaults to localhost.
 * @property port Port the MCP server listens on.
 * @property path HTTP path for the JSON-RPC endpoint.
 */
data class McpServerConfig(
    val name: String,
    val host: String = "127.0.0.1",
    val port: Int,
    val path: String = "/mcp",
) {
    /** Full base URL for the MCP endpoint. */
    val url: String get() = "http://$host:$port$path"
}
