package com.example.thingsappandroid.features.profile.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    application: Application,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val _themeMode = MutableStateFlow(themeModeFromString(preferenceManager.getThemeMode()))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            preferenceManager.setThemeMode(mode.name.lowercase())
        }
    }

    private fun themeModeFromString(s: String): ThemeMode = when (s.lowercase()) {
        "light" -> ThemeMode.Light
        "dark" -> ThemeMode.Dark
        else -> ThemeMode.System
    }
}
