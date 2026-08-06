package com.openprofiler.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openprofiler.domain.model.CalibrationResult
import com.openprofiler.domain.repository.CalibrationRepository
import com.openprofiler.domain.repository.CoverageRepository
import com.openprofiler.domain.usecase.ExportProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val result: CalibrationResult? = null,
    val coverageScore: Double = 0.0,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean? = null,
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val calibrationRepository: CalibrationRepository,
    private val coverageRepository: CoverageRepository,
    private val exportProfileUseCase: ExportProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private val _shareUri = MutableSharedFlow<Uri?>()
    val shareUri: SharedFlow<Uri?> = _shareUri.asSharedFlow()

    init {
        solve()
    }

    private fun solve() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = calibrationRepository.solve()
            val coverage = coverageRepository.getCurrentCoverage().overallPercentage
            _uiState.update {
                it.copy(
                    result = result,
                    coverageScore = coverage,
                    isLoading = false
                )
            }
        }
    }

    fun exportProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportSuccess = null) }
            val success = exportProfileUseCase.export()
            _uiState.update { it.copy(isExporting = false, exportSuccess = success) }
        }
    }

    fun shareProfile() {
        viewModelScope.launch {
            val uri = exportProfileUseCase.getShareUri()
            _shareUri.emit(uri)
        }
    }
}
