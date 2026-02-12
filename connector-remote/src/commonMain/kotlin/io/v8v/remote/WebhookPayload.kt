package io.v8v.remote

import kotlinx.serialization.Serializable

/**
 * JSON payload sent to the remote webhook.
 */
@Serializable
data class WebhookPayload(
    val intent: String,
    val extractedText: String,
    val rawText: String,
    val language: String,
    val timestamp: String,
)

/**
 * Expected JSON response from the remote webhook.
 */
@Serializable
data class WebhookResponse(
    val success: Boolean = true,
    val message: String = "",
    val data: Map<String, String> = emptyMap(),
)
