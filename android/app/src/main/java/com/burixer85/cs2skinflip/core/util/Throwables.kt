package com.burixer85.cs2skinflip.core.util

import java.io.IOException

/**
 * Maps a caught exception to a message safe to show a user — never the raw
 * exception text (e.g. "Unable to resolve host ...: No address associated with hostname").
 */
fun Throwable.toUserMessage(): String = when (this) {
    is IOException -> "No internet connection. Check your connection and try again."
    else -> "Something went wrong. Please try again."
}
