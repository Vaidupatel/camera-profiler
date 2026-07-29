package com.openprofiler.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Main ViewModel for application-level state management.
 * Phase 0: Placeholder with empty state.
 */
@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())

    /** Observable UI state for the main screen. */
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
}

/**
 * Immutable UI state for the main screen.
 *
 * @property isReady Whether the application is initialized and ready.
 */
data class MainUiState(
    val isReady: Boolean = false,
)
