package com.example.thingsappandroid

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Custom test runner that loads HiltTestApplication_Application instead of ThingsAppApplication.
 * Required for @HiltAndroidTest instrumented tests.
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication_Application::class.java.name, context)
    }
}
