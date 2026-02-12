package io.v8v.core

import android.content.Context

/**
 * Android implementation of the platform engine factory.
 *
 * @param context Must be an Android [Context] (Application or Activity).
 * @throws IllegalArgumentException if [context] is not a [Context].
 */
actual fun createPlatformEngine(context: Any?): SpeechRecognitionEngine {
    require(context is Context) {
        "Android requires a Context to create the speech engine. " +
            "Pass applicationContext or an Activity."
    }
    return AndroidSpeechEngine(context)
}
