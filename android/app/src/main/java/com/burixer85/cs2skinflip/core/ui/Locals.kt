package com.burixer85.cs2skinflip.core.ui

import androidx.compose.runtime.compositionLocalOf
import com.burixer85.cs2skinflip.core.preferences.Currency
import com.burixer85.cs2skinflip.core.preferences.DefaultMarketplace

/**
 * Alias to the [Currency] singleton so that existing call-sites using
 * `LocalCurrency.current.format(...)` continue to work unchanged.
 */
val LocalCurrency get() = Currency

/** The user's "default marketplace" setting, provided at the app root by AppNavigation. */
val LocalDefaultMarketplace = compositionLocalOf { DefaultMarketplace.LOWEST }
