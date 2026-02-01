package com.example.thingsappandroid.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.thingsappandroid.data.local.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    application: Application,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val _hasCompletedOnboarding = MutableStateFlow(preferenceManager.isOnboardingCompleted())
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    fun completeOnboarding() {
        preferenceManager.setOnboardingCompleted(true)
        _hasCompletedOnboarding.value = true
    }
}
