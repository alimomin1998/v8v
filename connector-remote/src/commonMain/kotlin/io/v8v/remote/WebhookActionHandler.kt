package io.v8v.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.v8v.core.ActionHandler
import io.v8v.core.ActionResult
import io.v8v.core.ActionScope
import io.v8v.core.model.ResolvedIntent
import kotlinx.serialization.json.Json

/**
 * An [ActionHandler] that dispatches intents to a remote webhook (e.g. n8n).
 *
 * Sends a POST request with a [WebhookPayload] JSON body and expects a
 * [WebhookResponse] back. Works with n8n, Zapier, Make, or any HTTP webhook.
 *
 * @param config Webhook URL, headers, and timeout settings.
 */
class WebhookActionHandler(
    private val config: WebhookConfig,
    httpClient: HttpClient? = null,
) : ActionHandler {

    override val scope: ActionScope = ActionScope.REMOTE

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient: HttpClient = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(this@WebhookActionHandler.json)
        }
    }

    override suspend fun execute(intent: ResolvedIntent): ActionResult {
        return try {
            val payload = WebhookPayload(
                intent = intent.intent,
                extractedText = intent.extractedText,
                rawText = intent.rawText,
                language = intent.language,
                timestamp = currentTimestamp(),
            )

            val response: WebhookResponse = httpClient.post(config.url) {
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = config.timeoutMs }
                headers {
                    config.headers.forEach { (key, value) -> append(key, value) }
                }
                setBody(payload)
            }.body()

            if (response.success) {
                ActionResult.Success(
                    scope = ActionScope.REMOTE,
                    intent = intent.intent,
                    message = response.message,
                    data = response.data,
                )
            } else {
                ActionResult.Error(
                    scope = ActionScope.REMOTE,
                    intent = intent.intent,
                    message = response.message.ifEmpty { "Webhook returned failure" },
                )
            }
        } catch (e: Exception) {
            ActionResult.Error(
                scope = ActionScope.REMOTE,
                intent = intent.intent,
                message = "Webhook call failed: ${e.message}",
            )
        }
    }

    /** Release HTTP client resources. */
    fun close() {
        httpClient.close()
    }
}

/** Simple ISO-8601 timestamp. */
internal expect fun currentTimestamp(): String
