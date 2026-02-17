package io.v8v.poc

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

class MockMcpServer(private val port: Int = 3001) {
    private var server: EmbeddedServer<*, *>? = null
    private val tasks = mutableListOf<String>()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json(this@MockMcpServer.json) }
            routing {
                post("/mcp") {
                    val body = call.receiveText()
                    Log.d(TAG, "MCP request: $body")
                    val req = json.parseToJsonElement(body).jsonObject
                    val id = req["id"]?.jsonPrimitive?.int ?: 0
                    val method = req["method"]?.jsonPrimitive?.content ?: ""
                    val resp = handle(id, method, req["params"]?.jsonObject)
                    call.respondText(resp, ContentType.Application.Json, HttpStatusCode.OK)
                }
            }
        }.start(wait = false)
        Log.d(TAG, "Mock MCP server started on port $port")
    }

    fun stop() {
        server?.stop(500, 1000)
        server = null
    }

    @Suppress("ktlint:standard:max-line-length")
    private fun handle(id: Int, method: String, params: JsonObject?): String =
        when (method) {
            "initialize" -> ok(
                id,
                buildJsonObject {
                    put("protocolVersion", JsonPrimitive("2024-11-05"))
                    put("capabilities", buildJsonObject {
                        put("tools", buildJsonObject { put("listChanged", JsonPrimitive(false)) })
                    })
                    put("serverInfo", buildJsonObject {
                        put("name", JsonPrimitive("poc-task-app"))
                        put("version", JsonPrimitive("1.0.0"))
                    })
                },
            )
            "tools/list" -> ok(
                id,
                buildJsonObject {
                    put("tools", JsonArray(listOf(buildJsonObject {
                        put("name", JsonPrimitive("create_task"))
                        put("description", JsonPrimitive("Create a new task"))
                        put("inputSchema", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                put("text", buildJsonObject { put("type", JsonPrimitive("string")) })
                            })
                        })
                    })))
                },
            )
            "tools/call" -> {
                val tool = params?.get("name")?.jsonPrimitive?.content
                val text = params?.get("arguments")?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content ?: "unknown"
                if (tool == "create_task") {
                    tasks.add(text)
                    ok(id, buildJsonObject {
                        put("content", JsonArray(listOf(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive("Task created: $text (total: ${tasks.size})"))
                        })))
                        put("isError", JsonPrimitive(false))
                    })
                } else {
                    err(id, -32601, "Unknown tool: $tool")
                }
            }
            else -> err(id, -32601, "Method not found: $method")
        }

    private fun ok(id: Int, result: JsonObject): String =
        buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("result", result)
        }.toString()

    private fun err(id: Int, code: Int, msg: String): String =
        buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("error", buildJsonObject {
                put("code", JsonPrimitive(code))
                put("message", JsonPrimitive(msg))
            })
        }.toString()

    private companion object {
        const val TAG = "MockMcpServer"
    }
}
