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

data class ExportData(
    val deviceId: String,
    val exportedAt: String,
    val rawReadings: List<com.example.thingsappandroid.data.local.entity.BatteryReadingEntity>,
    val pendingConsumptions: List<com.example.thingsappandroid.data.local.entity.ConsumptionEntity>
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
     * Legacy method used by AboutScreen to trigger a JSON export and share it.
     */
    fun exportData() {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    generateJson()
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
    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    generateJson()
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
    private suspend fun generateJson(): String {
        val deviceId = android.provider.Settings.Secure.getString(
            application.contentResolver, 
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date())
        
        val allReadings = mutableListOf<com.example.thingsappandroid.data.local.entity.BatteryReadingEntity>()
        val groups = readingDao.getAllGroupIds()
        groups.forEach { groupId ->
            allReadings.addAll(readingDao.getByGroup(groupId))
        }

        val pendings = consumptionDao.getAllPendingOnce()
        
        val exportData = ExportData(
            deviceId = deviceId,
            exportedAt = timestamp,
            rawReadings = allReadings,
            pendingConsumptions = pendings
        )

        return gson.toJson(exportData)
    }

    fun resetState() {
        _exportState.value = ExportState.Idle
    }
}
