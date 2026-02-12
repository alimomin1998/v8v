package io.v8v.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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

class WebhookActionHandlerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val testIntent = ResolvedIntent(
        intent = "notify.team",
        extractedText = "build is ready",
        rawText = "notify team build is ready",
        language = "en",
    )

    private fun mockClient(
        responseBody: String,
        validate: ((io.ktor.client.request.HttpRequestData) -> Unit)? = null,
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    validate?.invoke(request)
                    respond(
                        content = ByteReadChannel(responseBody),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(this@WebhookActionHandlerTest.json)
            }
        }
    }

    private fun failingClient(): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondError(HttpStatusCode.InternalServerError)
                }
            }
            install(ContentNegotiation) {
                json(this@WebhookActionHandlerTest.json)
            }
        }
    }

    @Test
    fun execute_success_returns_action_success() = runTest {
        val responseJson = """
            {
                "success": true,
                "message": "Notification sent",
                "data": { "channel": "#general" }
            }
        """.trimIndent()

        val config = WebhookConfig(url = "https://example.com/webhook")
        val handler = WebhookActionHandler(config, mockClient(responseJson))

        val result = handler.execute(testIntent)

        assertIs<ActionResult.Success>(result)
        assertEquals(ActionScope.REMOTE, result.scope)
        assertEquals("notify.team", result.intent)
        assertEquals("Notification sent", result.message)
        assertEquals("#general", result.data["channel"])
        handler.close()
    }

    @Test
    fun execute_failure_returns_action_error() = runTest {
        val responseJson = """
            {
                "success": false,
                "message": "Rate limit exceeded"
            }
        """.trimIndent()

        val config = WebhookConfig(url = "https://example.com/webhook")
        val handler = WebhookActionHandler(config, mockClient(responseJson))

        val result = handler.execute(testIntent)

        assertIs<ActionResult.Error>(result)
        assertEquals(ActionScope.REMOTE, result.scope)
        assertEquals("Rate limit exceeded", result.message)
        handler.close()
    }

    @Test
    fun execute_network_error_returns_action_error() = runTest {
        val config = WebhookConfig(url = "https://example.com/webhook")
        val handler = WebhookActionHandler(config, failingClient())

        val result = handler.execute(testIntent)

        assertIs<ActionResult.Error>(result)
        assertEquals(ActionScope.REMOTE, result.scope)
        assertTrue(result.message.contains("Webhook call failed"))
        handler.close()
    }

    @Test
    fun sends_correct_http_method_and_url() = runTest {
        val responseJson = """{ "success": true, "message": "OK" }"""

        val config = WebhookConfig(url = "https://n8n.example.com/webhook/voice")
        val handler = WebhookActionHandler(
            config,
            mockClient(responseJson) { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("https://n8n.example.com/webhook/voice", request.url.toString())
            },
        )

        handler.execute(testIntent)
        handler.close()
    }

    @Test
    fun scope_is_remote() {
        val noOpClient = HttpClient(MockEngine) {
            engine { addHandler { respondError(HttpStatusCode.OK) } }
        }
        val config = WebhookConfig(url = "https://example.com/webhook")
        val handler = WebhookActionHandler(config, noOpClient)

        assertEquals(ActionScope.REMOTE, handler.scope)
        handler.close()
    }

    @Test
    fun empty_failure_message_uses_default() = runTest {
        val responseJson = """{ "success": false, "message": "" }"""

        val config = WebhookConfig(url = "https://example.com/webhook")
        val handler = WebhookActionHandler(config, mockClient(responseJson))

        val result = handler.execute(testIntent)

        assertIs<ActionResult.Error>(result)
        assertEquals("Webhook returned failure", result.message)
        handler.close()
    }
}
