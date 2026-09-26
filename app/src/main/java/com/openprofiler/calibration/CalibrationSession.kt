package com.openprofiler.calibration

import com.openprofiler.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the accumulation of calibration point correspondences.
 * Stores complete production-grade observations.
 */
@Singleton
class CalibrationSession @Inject constructor() {
    private val lock = Any()

    private val _observations = MutableStateFlow<List<AcceptedObservation>>(emptyList())
    val observations: StateFlow<List<AcceptedObservation>> = _observations

    fun addObservation(observation: FrameObservation) {
        synchronized(lock) {
            val id = "frame_${observation.timestamp}_${_observations.value.size}"
            val accepted = AcceptedObservation(CalibrationFrame(id, observation))
            _observations.value = _observations.value + accepted
        }
    }

    fun reset() {
        synchronized(lock) {
            _observations.value = emptyList()
        }
    }

    fun getAcceptedFrameCount(): Int = synchronized(lock) {
        _observations.value.size
    }

    fun getObservations(): List<AcceptedObservation> = synchronized(lock) {
        _observations.value
    }

    /**
     * Legacy support for native engine.
     */
    fun getObjectPoints(): Array<FloatArray> = synchronized(lock) {
        _observations.value.map { it.frame.observation.objectPoints.flatMap { p -> listOf(p.x, p.y, p.z) }.toFloatArray() }.toTypedArray()
    }

    fun getImagePoints(): Array<FloatArray> = synchronized(lock) {
        _observations.value.map { it.frame.observation.imagePoints.flatMap { p -> listOf(p.x, p.y) }.toFloatArray() }.toTypedArray()
    }
}
