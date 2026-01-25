package com.example.thingsappandroid.data.local

import android.content.Context
import androidx.core.content.edit

class TokenManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        sharedPreferences.edit { putString("auth_token", token) }
    }

    fun getToken(): String? {
        return sharedPreferences.getString("auth_token", null)
    }

    fun clearToken() {
        sharedPreferences.edit { remove("auth_token") }
    }

    fun hasToken(): Boolean {
        return sharedPreferences.contains("auth_token")
    }

    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit { putString("device_id", deviceId) }
    }

    fun getDeviceId(): String? {
        return sharedPreferences.getString("device_id", null)
    }
}