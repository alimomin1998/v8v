package io.v8v.core

/**
 * Defines where an action is executed.
 */
enum class ActionScope {
    /** In-app action — runs a local lambda. Default, offline, safe. */
    LOCAL,

    /** Cross-app action — calls a local MCP server on-device via HTTP. */
    MCP,

    /** Remote action — calls a cloud webhook (e.g. n8n). */
    REMOTE,
}
