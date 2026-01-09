package com.example.thingsappandroid.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EnergyService(context: Context) {
    private val _consumption = MutableStateFlow(0.140f) // kWh (Simulated)
    val consumption: StateFlow<Float> = _consumption.asStateFlow()

    private val _avoidedEmissions = MutableStateFlow(0.00f) // gCO2e (Simulated)
    val avoidedEmissions: StateFlow<Float> = _avoidedEmissions.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateConnectionStatus()
        }

        override fun onLost(network: Network) {
            updateConnectionStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateConnectionStatus()
        }
    }

    private var isSimulating = false

    fun startMonitoring() {
        if (isSimulating) return
        isSimulating = true
        
        // 1. Start Network Monitoring
        try {
            updateConnectionStatus() // Initial check
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Start Simulation for Consumption (Device physics unavailable)
        // We start a coroutine loop in the scope where this suspend function is called, 
        // but since we are changing signature to non-suspend for callback setup,
        // the simulation loop needs to be separated or we launch it.
        // For simplicity in this architecture, we will just separate the simulation part.
    }
    
    suspend fun startSimulationLoop() {
        while (true) {
            delay(1000)
            // Increment consumption slowly
            _consumption.value += 0.0001f
            
            // Increment avoided emissions rarely
            if (System.currentTimeMillis() % 5000 < 1000) {
                 _avoidedEmissions.value += 0.01f
            }
        }
    }

    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun updateConnectionStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        _isWifiConnected.value = isWifi
    }
}