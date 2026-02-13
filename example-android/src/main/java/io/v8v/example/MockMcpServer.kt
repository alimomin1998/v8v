package io.v8v.example

import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Embedded mock MCP server for demo purposes.
 *
 * Simulates an external app exposing MCP tools. Runs on localhost:3001.
 * Supports:
 * - `initialize` — returns server capabilities
 * - `tools/list` — returns a "create_task" tool
 * - `tools/call` — simulates creating a task and returns success
 */
class MockMcpServer(
    private val port: Int = 3001
) {
    private var server: EmbeddedServer<*, *>? = null
    private val tasks = mutableListOf<String>()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun start() {
        if (server != null) return
        server =
            embeddedServer(CIO, port = port) {
                install(ContentNegotiation) { json(this@MockMcpServer.json) }
                routing {
                    post("/mcp") {
                        val body = call.receiveText()
                        Log.d(TAG, "MCP request: $body")
                        val request = json.parseToJsonElement(body).jsonObject
                        val id = request["id"]?.jsonPrimitive?.int ?: 0
                        val method = request["method"]?.jsonPrimitive?.content ?: ""
                        val response = handleMethod(id, method, request["params"]?.jsonObject)
                        Log.d(TAG, "MCP response: $response")
                        call.respondText(response, ContentType.Application.Json, HttpStatusCode.OK)
                    }
                }
            }.start(wait = false)
        Log.d(TAG, "Mock MCP server started on port $port")
    }

    fun stop() {
        server?.stop(500, 1000)
        server = null
        Log.d(TAG, "Mock MCP server stopped")
    }

    fun getTaskCount(): Int = tasks.size

    private fun handleMethod(
        id: Int,
        method: String,
        params: JsonObject?
    ): String =
        when (method) {
            "initialize" ->
                jsonRpcSuccess(
                    id,
                    buildJsonObject {
                        put("protocolVersion", JsonPrimitive("2024-11-05"))
                        put(
                            "capabilities",
                            buildJsonObject {
                                put(
                                    "tools",
                                    buildJsonObject {
                                        put("listChanged", JsonPrimitive(false))
                                    }
                                )
                            }
                        )
                        put(
                            "serverInfo",
                            buildJsonObject {
                                put("name", JsonPrimitive("mock-task-app"))
                                put("version", JsonPrimitive("1.0.0"))
                            }
                        )
                    }
                )

            "tools/list" ->
                jsonRpcSuccess(
                    id,
                    buildJsonObject {
                        put(
                            "tools",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("name", JsonPrimitive("create_task"))
                                        put("description", JsonPrimitive("Create a new task"))
                                        put(
                                            "inputSchema",
                                            buildJsonObject {
                                                put("type", JsonPrimitive("object"))
                                                put(
                                                    "properties",
                                                    buildJsonObject {
                                                        put(
                                                            "text",
                                                            buildJsonObject {
                                                                put("type", JsonPrimitive("string"))
                                                                put("description", JsonPrimitive("Task description"))
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            )
                        )
                    }
                )

            "tools/call" -> {
                val toolName = params?.get("name")?.jsonPrimitive?.content
                val args = params?.get("arguments")?.jsonObject
                val text = args?.get("text")?.jsonPrimitive?.content ?: "unknown"

                if (toolName == "create_task") {
                    tasks.add(text)
                    jsonRpcSuccess(
                        id,
                        buildJsonObject {
                            put(
                                "content",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("text"))
                                            put("text", JsonPrimitive("Task created: $text (total: ${tasks.size})"))
                                        }
                                    )
                                )
                            )
                            put("isError", JsonPrimitive(false))
                        }
                    )
                } else {
                    jsonRpcError(id, -32601, "Unknown tool: $toolName")
                }
            }

            else -> jsonRpcError(id, -32601, "Method not found: $method")
        }

    private fun jsonRpcSuccess(
        id: Int,
        result: JsonObject
    ): String {
        val response =
            buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(id))
                put("result", result)
            }
        return response.toString()
    }

    private fun jsonRpcError(
        id: Int,
        code: Int,
        message: String
    ): String {
        val response =
            buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(id))
                put(
                    "error",
                    buildJsonObject {
                        put("code", JsonPrimitive(code))
                        put("message", JsonPrimitive(message))
                    }
                )
            }
        return response.toString()
    }

    private companion object {
        const val TAG = "MockMcpServer"
    }
}
