package com.example.thingsappandroid.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.math.max

class CarbonService {
    // Current usage in gCO2e
    private val _currentUsage = MutableStateFlow(25.43f)
    val currentUsage: StateFlow<Float> = _currentUsage.asStateFlow()

    // Intensity in gCO2e/kWh
    private val _intensity = MutableStateFlow(96)
    val intensity: StateFlow<Int> = _intensity.asStateFlow()

    private var isSimulating = false

    suspend fun startSimulation() {
        if (isSimulating) return
        isSimulating = true
        while (true) {
            delay(2000)
            // Fluctuate usage slightly
            val change = Random.nextDouble(-0.2, 0.4).toFloat()
            val newVal = max(0f, _currentUsage.value + change)
            _currentUsage.value = newVal
            
            // Fluctuate intensity occasionally
            if (Random.nextInt(100) > 95) {
                _intensity.value = (90..110).random()
            }
        }
    }
}