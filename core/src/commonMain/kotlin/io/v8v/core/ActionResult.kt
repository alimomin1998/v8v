package io.v8v.core

/**
 * Result returned by an [ActionHandler] after executing an intent.
 */
sealed class ActionResult {

    /** The intent's [ActionScope] that produced this result. */
    abstract val scope: ActionScope

    /** The intent identifier that was dispatched. */
    abstract val intent: String

    /** Action executed successfully. */
    data class Success(
        override val scope: ActionScope,
        override val intent: String,
        val message: String = "",
        val data: Map<String, String> = emptyMap(),
    ) : ActionResult()

    /** Action failed. */
    data class Error(
        override val scope: ActionScope,
        override val intent: String,
        val message: String,
        val code: Int = -1,
    ) : ActionResult()
}
