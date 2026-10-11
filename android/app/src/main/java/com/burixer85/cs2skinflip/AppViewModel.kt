package com.burixer85.cs2skinflip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.preferences.DefaultMarketplace
import com.burixer85.cs2skinflip.core.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-root ViewModel — exposes preferences shared across the app. */
@HiltViewModel
class AppViewModel @Inject constructor(
    val preferences: UserPreferences,
) : ViewModel() {
    val marketplace: StateFlow<DefaultMarketplace> =
        preferences.marketplace.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultMarketplace.LOWEST)
}
