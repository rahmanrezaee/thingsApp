package com.example.thingsappandroid.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.services.BatteryService
import com.example.thingsappandroid.services.CarbonService
import com.example.thingsappandroid.services.ClimateService
import com.example.thingsappandroid.services.EnergyService
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    // Services
    val batteryService = BatteryService(application)
    val energyService = EnergyService(application)
    val climateService = ClimateService()
    val carbonService = CarbonService()

    init {
        // Start monitoring real hardware data
        batteryService.startMonitoring()
        energyService.startMonitoring()

        // Start simulations for other data
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