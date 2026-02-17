package io.v8v.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpClientTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val testConfig = McpServerConfig(name = "test", port = 9999)

    private fun mockClient(responseBody: String): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(testConfig.url, request.url.toString())
                    respond(
                        content = ByteReadChannel(responseBody),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(this@McpClientTest.json)
            }
        }

    @Test
    fun initialize_sends_request_and_parses_result() =
        runTest {
            val responseJson =
                """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": { "tools": { "listChanged": false } },
                        "serverInfo": { "name": "test-server", "version": "1.0" }
                    }
                }
                """.trimIndent()

            val client = McpClient(testConfig, mockClient(responseJson))
            val result = client.initialize()

            assertEquals("2024-11-05", result.protocolVersion)
            assertNotNull(result.serverInfo)
            assertEquals("test-server", result.serverInfo?.name)
            client.close()
        }

    @Test
    fun listTools_returns_deserialized_tool_list() =
        runTest {
            val responseJson =
                """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                        "tools": [
                            { "name": "create_task", "description": "Creates a task" },
                            { "name": "delete_task", "description": "Deletes a task" }
                        ]
                    }
                }
                """.trimIndent()

            val client = McpClient(testConfig, mockClient(responseJson))
            val tools = client.listTools()

            assertEquals(2, tools.size)
            assertEquals("create_task", tools[0].name)
            assertEquals("Deletes a task", tools[1].description)
            client.close()
        }

    @Test
    fun callTool_success_returns_tool_result() =
        runTest {
            val responseJson =
                """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                        "content": [{ "type": "text", "text": "Task created: buy milk" }],
                        "isError": false
                    }
                }
                """.trimIndent()

            val client = McpClient(testConfig, mockClient(responseJson))
            val result = client.callTool("create_task")

            assertEquals(false, result.isError)
            assertEquals("Task created: buy milk", result.content.first().text)
            client.close()
        }

    @Test
    fun callTool_jsonrpc_error_returns_error_result() =
        runTest {
            val responseJson =
                """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "error": { "code": -32601, "message": "Method not found" }
                }
                """.trimIndent()

            val client = McpClient(testConfig, mockClient(responseJson))
            val result = client.callTool("unknown_tool")

            assertTrue(result.isError)
            assertEquals("Method not found", result.content.first().text)
            client.close()
        }

    @Test
    fun callTool_tool_error_returns_error_result() =
        runTest {
            val responseJson =
                """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                        "content": [{ "type": "text", "text": "Invalid arguments" }],
                        "isError": true
                    }
                }
                """.trimIndent()

            val client = McpClient(testConfig, mockClient(responseJson))
            val result = client.callTool("create_task")

            assertTrue(result.isError)
            assertEquals("Invalid arguments", result.content.first().text)
            client.close()
        }
}
