package com.example.thingsappandroid.features.home

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.BatteryService
import com.example.thingsappandroid.services.CarbonService
import com.example.thingsappandroid.services.ClimateService
import com.example.thingsappandroid.services.ClimateData
import com.example.thingsappandroid.services.EnergyService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    // Dependencies
    private val repository = ThingsRepository()
    private val tokenManager = TokenManager(application)
    private val batteryService = BatteryService(application)
    private val energyService = EnergyService(application)
    private val climateService = ClimateService()
    private val carbonService = CarbonService() // Simulation for now

    // MVI State
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // MVI Effect
    private val _effect = Channel<HomeEffect>()
    val effect = _effect.receiveAsFlow()
    
    // Cached Device ID
    private var cachedDeviceId: String? = null

    init {
        dispatch(HomeIntent.Initialize)
    }

    fun dispatch(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Initialize -> initialize()
            HomeIntent.RefreshData -> refreshServerData()
            HomeIntent.Logout -> performLogout()
        }
    }

    private fun performLogout() {
        viewModelScope.launch {
            tokenManager.clearToken()
            _effect.send(HomeEffect.NavigateToLogin)
        }
    }
    
    @SuppressLint("HardwareIds")
    private suspend fun getDeviceId(): String {
        if (cachedDeviceId == null) {
            cachedDeviceId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Settings.Secure.getString(getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID) 
                    ?: UUID.randomUUID().toString()
            }
        }
        return cachedDeviceId!!
    }

    private fun initialize() {
        startServices()
        
        // Set Device Name
        _state.update { it.copy(deviceName = Build.MODEL) }
        
        // Combine local flows into State
        viewModelScope.launch {
            combine(
                batteryService.batteryLevel,
                batteryService.isCharging,
                energyService.isWifiConnected,
                energyService.consumption,
                carbonService.intensity,
                climateService.climateData
            ) { args: Array<Any?> ->
                args
            }.collect { args ->
                val battery = args[0] as Float
                val charging = args[1] as Boolean
                val wifi = args[2] as Boolean
                val consumption = args[3] as Float
                val intensity = args[4] as Int
                val climate = args[5] as? ClimateData

                _state.update {
                    it.copy(
                        batteryLevel = battery,
                        isCharging = charging,
                        isWifiConnected = wifi,
                        currentUsageKwh = consumption,
                        carbonIntensity = intensity,
                        climateData = climate
                    )
                }
            }
        }
        
        // Start Sync Loop
        viewModelScope.launch {
            val id = getDeviceId()
            Log.d("HomeVM", "Initializing Sync for Device: $id")
            
            // Try Authentication
            val authToken = repository.authenticate(id)

            if (authToken != null) {
                _effect.send(HomeEffect.ShowToast("Connected to Cloud"))
                while(true) {
                    dispatch(HomeIntent.RefreshData)
                    delay(60_000) // Sync every minute
                }
            } else {
                // Specific error for user
                _state.update { it.copy(error = "Authentication Failed. Check Internet.") }
                _effect.send(HomeEffect.ShowToast("Server Connection Failed"))
                // Retry logic could go here, but for now we stop to avoid spamming logs
            }
        }
    }

    private fun refreshServerData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val id = getDeviceId()
            
            // 1. Sync Info (Background thread via Repo)
            val deviceInfo = repository.syncDeviceInfo(id)
            
            // 2. Upload Consumption (Snapshot)
            val currentState = _state.value
            if (currentState.currentUsageKwh > 0) {
                // Check API level for uploadConsumption as it requires API 26
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    repository.uploadConsumption(
                        deviceId = id,
                        watts = currentState.currentUsageKwh * 1000 / 1.0, 
                        kwh = currentState.currentUsageKwh.toDouble(),
                        batteryLevel = (currentState.batteryLevel * 100).toInt(),
                        isCharging = currentState.isCharging
                    )
                }
            }

            if (deviceInfo != null) {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                    ) 
                }
            } else {
                _state.update { it.copy(isLoading = false) }
                // Don't toast on every sync failure, just log or silent fail
            }
        }
    }

    private fun startServices() {
        batteryService.startMonitoring()
        energyService.startMonitoring()
        viewModelScope.launch { climateService.startSimulation() }
        viewModelScope.launch { carbonService.startSimulation() }
        viewModelScope.launch { energyService.startSimulationLoop() }
    }

    override fun onCleared() {
        super.onCleared()
        batteryService.stopMonitoring()
        energyService.stopMonitoring()
    }
}