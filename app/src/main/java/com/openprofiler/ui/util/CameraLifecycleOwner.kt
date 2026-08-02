package com.openprofiler.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner

/**
 * Returns the [ComponentActivity] hosting this composition.
 *
 * CameraX sessions must bind to this Activity-scoped [LifecycleOwner], not
 * [androidx.lifecycle.compose.LocalLifecycleOwner] (which under Navigation Compose
 * is the per-destination [androidx.navigation.NavBackStackEntry]).
 */
@Composable
fun rememberCameraLifecycleOwner(): LifecycleOwner {
    val context = LocalContext.current
    return checkNotNull(context.findComponentActivity()) {
        "Camera screens require a ComponentActivity host"
    }
}

tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

fun Context.requireComponentActivity(): Activity =
    findComponentActivity()
        ?: error("Expected ComponentActivity, got ${this::class.java.name}")
