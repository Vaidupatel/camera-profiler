package com.openprofiler.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openprofiler.ui.viewmodel.ResultsViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Results screen presenting calibration parameters and profile export actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel = hiltViewModel(),
    onDone: () -> Unit = {},
    onDeveloper: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.shareUri.collectLatest { uri ->
            if (uri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Camera Profile"))
            } else if (uiState.exportSuccess == null) {
                snackbarHostState.showSnackbar("Save profile first before sharing.")
            }
        }
    }

    LaunchedEffect(uiState.exportSuccess) {
        uiState.exportSuccess?.let { success ->
            if (success) {
                snackbarHostState.showSnackbar("Profile saved successfully!")
            } else {
                snackbarHostState.showSnackbar("Failed to save profile.")
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibration Results") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val res = uiState.result
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Profile Summary",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val success = res?.success == true
                            val statusText = if (success) "Calibration Successful" else "Calibration Failed"
                            val statusColor = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            
                            Text(
                                text = "Status: $statusText",
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (res != null && res.success) {
                                Text("RMS Reprojection Error: ${"%.4f".format(res.rms)} px")
                                Text("Accepted Frames: ${res.imageCount}")
                                Text("Coverage Score: ${"%.1f".format(uiState.coverageScore)}%")
                                Text("Profile ID: camera-profile.json")
                            } else {
                                Text("Insufficient data or solve error.")
                                Text("Accepted Frames: ${res?.imageCount ?: 0}")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Export Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.exportProfile() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = res?.success == true && !uiState.isExporting
                            ) {
                                if (uiState.isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Save camera-profile.json")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { viewModel.shareProfile() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = res?.success == true
                            ) {
                                Text("Share Profile")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = onDeveloper) {
                        Text("Diagnostics")
                    }
                    Button(onClick = onDone) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
