package com.example.thingsappandroid.features.profile.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.AppDatabase
import com.google.gson.GsonBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val message: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

/**
 * Export item representing one charging session group summary.
 */
data class ExportGroupSummary(
    val group: String,
    val totalWattHours: Double,
    val startTime: String,
    val endTime: String,
    val startLevel: Int,
    val endLevel: Int
)

@HiltViewModel
class ExportBatteryDataViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState = _exportState.asStateFlow()

    private val database = AppDatabase.getInstance(application)
    private val readingDao = database.batteryReadingDao()
    private val consumptionDao = database.consumptionDao()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Export charging report as JSON and share. Optional [fromMillis]/[toMillis] filter by time range.
     */
    fun exportData(fromMillis: Long? = null, toMillis: Long? = null) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    generateJson(fromMillis, toMillis)
                }

                if (jsonContent.isBlank()) {
                    _exportState.value = ExportState.Error("No data found to export")
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "charging_report_$timestamp.json"
                val file = File(application.cacheDir, fileName)
                withContext(Dispatchers.IO) {
                    file.writeText(jsonContent)
                }

                val uri = FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                application.startActivity(
                    Intent.createChooser(intent, "Share Charging Report (JSON)").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )

                _exportState.value = ExportState.Success("Export ready")
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    /**
     * Exports JSON to a user-provided URI (Storage Access Framework).
     */
    fun exportToUri(uri: Uri, fromMillis: Long? = null, toMillis: Long? = null) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    generateJson(fromMillis, toMillis)
                }
                
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonContent.toByteArray())
                    } ?: throw Exception("Could not open output stream for URI")
                }
                
                _exportState.value = ExportState.Success("Data exported successfully")
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    @SuppressLint("HardwareIds")
    private suspend fun generateJson(fromMillis: Long? = null, toMillis: Long? = null): String {
        val deviceId = android.provider.Settings.Secure.getString(
            application.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date())
        val readableFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val groups = if (fromMillis != null && toMillis != null) {
            readingDao.getGroupedBatteryDataBetween(fromMillis, toMillis)
        } else {
            readingDao.getGroupedBatteryData()
        }

        // Top-level JSON array: one item per groupId (no duplicates)
        val summaries: List<ExportGroupSummary> = groups.map {
            ExportGroupSummary(
                group = it.groupId,
                totalWattHours = it.totalWattHours,
                startTime = readableFormat.format(Date(it.startTime)),
                endTime = readableFormat.format(Date(it.endTime)),
                startLevel = it.startLevel,
                endLevel = it.endLevel
            )
        }

        return gson.toJson(summaries)
    }

    fun resetState() {
        _exportState.value = ExportState.Idle
    }
}
