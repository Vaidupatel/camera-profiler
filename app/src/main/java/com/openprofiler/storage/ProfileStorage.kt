package com.openprofiler.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.openprofiler.common.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProfileStorage"
private const val PROFILE_FILENAME = "camera-profile.json"

/**
 * Local storage handler for camera profiles and calibration data.
 */
@Singleton
class ProfileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Saves the profile JSON to app-private storage.
     */
    fun saveProfile(json: String): Boolean {
        return try {
            val file = File(context.filesDir, PROFILE_FILENAME)
            file.writeText(json)
            Logger.i(TAG, "Profile saved to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save profile", e)
            false
        }
    }

    /**
     * Returns the URI for sharing the profile file.
     */
    fun getProfileUri(): Uri? {
        val file = File(context.filesDir, PROFILE_FILENAME)
        if (!file.exists()) return null
        
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get FileProvider URI", e)
            null
        }
    }
}
