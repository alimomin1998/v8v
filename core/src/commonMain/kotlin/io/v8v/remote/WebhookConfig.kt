package io.v8v.remote

/**
 * Configuration for a remote webhook (e.g. n8n workflow).
 *
 * @property url The full webhook URL (e.g. "https://n8n.example.com/webhook/voice-agent").
 * @property headers Optional HTTP headers (e.g. for authentication).
 * @property timeoutMs HTTP request timeout in milliseconds.
 */
data class WebhookConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 10_000,
)
