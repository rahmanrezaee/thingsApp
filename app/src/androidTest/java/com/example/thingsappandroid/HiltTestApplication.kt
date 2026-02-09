package com.example.thingsappandroid

import android.app.Application
import dagger.hilt.android.testing.CustomTestApplication

/**
 * Hilt test application for instrumented tests.
 * Uses Application (not @HiltAndroidApp) as base – @CustomTestApplication cannot reference @HiltAndroidApp classes.
 * Hilt generates HiltTestApplication_Application from this.
 */
@CustomTestApplication(Application::class)
interface HiltTestApplication
