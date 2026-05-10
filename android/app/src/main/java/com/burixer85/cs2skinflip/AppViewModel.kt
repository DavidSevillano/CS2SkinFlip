package com.burixer85.cs2skinflip

import androidx.lifecycle.ViewModel
import com.burixer85.cs2skinflip.core.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** App-root ViewModel — exposes preferences shared across the app. */
@HiltViewModel
class AppViewModel @Inject constructor(
    val preferences: UserPreferences,
) : ViewModel()
