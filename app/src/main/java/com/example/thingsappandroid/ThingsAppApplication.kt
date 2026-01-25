package com.example.thingsappandroid

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.Scope

@HiltAndroidApp
class ThingsAppApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()

        SentryAndroid.init(this) { options ->
            options.isEnableAutoSessionTracking = true
            options.tracesSampleRate = 1.0
            options.isDebug = false
            // Disable session replay to avoid Compose compatibility issues
            // Session replay tries to traverse Compose tree which causes NoSuchMethodError
            options.sessionReplay.onErrorSampleRate = 0.0
            options.sessionReplay.sessionSampleRate = 0.0
            // Disable view hierarchy attachment to avoid Compose compatibility issues
            options.isAttachViewHierarchy = false
            // Disable screenshot attachment to avoid Compose traversal errors
            options.isAttachScreenshot = false
        }
        
        // Set up global exception handler that reports to Sentry
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ThingsApp", "Uncaught exception on thread ${thread.name}", throwable)
            Log.e("ThingsApp", "Exception type: ${throwable.javaClass.name}")
            Log.e("ThingsApp", "Exception message: ${throwable.message}")
            Log.e("ThingsApp", "Stack trace: ${throwable.stackTraceToString()}")
            
            // Capture exception in Sentry before re-throwing
            Sentry.withScope { scope ->
                scope.setTag("thread_name", thread.name)
                scope.setContexts("device", mapOf(
                    "manufacturer" to android.os.Build.MANUFACTURER,
                    "model" to android.os.Build.MODEL,
                    "sdk_int" to android.os.Build.VERSION.SDK_INT
                ))
                Sentry.captureException(throwable)
            }
            
            // Re-throw to let the system handle it
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        // Set device context for all Sentry events
        Sentry.configureScope { scope ->
            scope.setContexts("device", mapOf(
                "manufacturer" to android.os.Build.MANUFACTURER,
                "model" to android.os.Build.MODEL,
                "sdk_int" to android.os.Build.VERSION.SDK_INT,
                "version_release" to android.os.Build.VERSION.RELEASE
            ))
        }
        
        Log.d("ThingsApp", "Application initialized - Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.SDK_INT}")
    }
}
