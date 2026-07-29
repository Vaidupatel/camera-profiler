package com.openprofiler.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Sealed interface defining all navigation routes in the application.
 * Phase 1: All 9 screens defined for type-safe Compose Navigation.
 */
sealed interface Route {

    /** Splash screen shown at application launch. */
    @Serializable
    data object Splash : Route

    /** Instructions screen explaining calibration process and target selection. */
    @Serializable
    data object Instructions : Route

    /** Camera permission request screen. */
    @Serializable
    data object Permissions : Route

    /** Live camera preview and control screen. */
    @Serializable
    data object Camera : Route

    /** Live calibration progress and quality feedback screen. */
    @Serializable
    data object Calibration : Route

    /** Calibration results display and export screen. */
    @Serializable
    data object Results : Route

    /** Application settings screen. */
    @Serializable
    data object Settings : Route

    /** About screen with application and open-source license information. */
    @Serializable
    data object About : Route

    /** Developer diagnostics and log screen. */
    @Serializable
    data object Developer : Route
}
