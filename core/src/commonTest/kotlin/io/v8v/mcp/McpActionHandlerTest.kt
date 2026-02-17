package io.v8v.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.v8v.core.ActionResult
import io.v8v.core.ActionScope
import io.v8v.core.model.ResolvedIntent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class McpActionHandlerTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val testConfig = McpServerConfig(name = "test", port = 9999)

    private val testIntent =
        ResolvedIntent(
            intent = "task.create",
            extractedText = "buy milk",
            rawText = "create task buy milk",
            language = "en",
        )

    private fun mockClient(responseBody: String): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel(responseBody),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(this@McpActionHandlerTest.json)
            }
        }

    private fun failingClient(): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondError(HttpStatusCode.InternalServerError)
                }
            }
            install(ContentNegotiation) {
                json(this@McpActionHandlerTest.json)
            }
        }

    @Test
    fun execute_success_returns_action_success() =
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
            val handler = McpActionHandler(client, "create_task")

            val result = handler.execute(testIntent)

            assertIs<ActionResult.Success>(result)
            assertEquals(ActionScope.MCP, result.scope)
            assertEquals("task.create", result.intent)
            assertEquals("Task created: buy milk", result.message)
            client.close()
        }

    @Test
    fun execute_tool_error_returns_action_error() =
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
            val handler = McpActionHandler(client, "create_task")

            val result = handler.execute(testIntent)

            assertIs<ActionResult.Error>(result)
            assertEquals(ActionScope.MCP, result.scope)
            assertEquals("Invalid arguments", result.message)
            client.close()
        }

    @Test
    fun execute_network_error_returns_action_error() =
        runTest {
            val client = McpClient(testConfig, failingClient())
            val handler = McpActionHandler(client, "create_task")

            val result = handler.execute(testIntent)

            assertIs<ActionResult.Error>(result)
            assertEquals(ActionScope.MCP, result.scope)
            assertTrue(result.message.contains("MCP call failed"))
            client.close()
        }

    @Test
    fun scope_is_mcp() {
        val noOpClient =
            HttpClient(MockEngine) {
                engine { addHandler { respondError(HttpStatusCode.OK) } }
            }
        val client = McpClient(testConfig, noOpClient)
        val handler = McpActionHandler(client, "create_task")

        assertEquals(ActionScope.MCP, handler.scope)
        client.close()
    }
}
