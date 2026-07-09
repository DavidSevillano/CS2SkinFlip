package com.burixer85.cs2skinflip.core.util

import androidx.annotation.StringRes
import com.burixer85.cs2skinflip.R
import java.io.IOException

/**
 * Maps a caught exception to a localized message resource safe to show a user — never the
 * raw exception text (e.g. "Unable to resolve host ...: No address associated with hostname").
 * Returns a resource ID rather than a String since this runs in ViewModels, outside any
 * Composable/Context — resolve it with `stringResource()` at the call site.
 */
@StringRes
fun Throwable.toUserMessageRes(): Int = when (this) {
    is IOException -> R.string.error_no_internet
    else -> R.string.error_generic_retry
}
