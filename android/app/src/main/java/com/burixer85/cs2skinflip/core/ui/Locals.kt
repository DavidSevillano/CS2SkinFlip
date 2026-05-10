package com.burixer85.cs2skinflip.core.ui

import com.burixer85.cs2skinflip.core.preferences.Currency

/**
 * Alias to the [Currency] singleton so that existing call-sites using
 * `LocalCurrency.current.format(...)` continue to work unchanged.
 */
val LocalCurrency get() = Currency
