package com.example.thingsappandroid.util

import android.view.Gravity
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable

/**
 * Helper function to show Toast messages at the bottom of the screen
 */
fun showToastBottom(context: android.content.Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
    val toast = Toast.makeText(context, message, duration)
    toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 100)
    toast.show()
}

/**
 * Composable helper to show Toast messages at the bottom
 */
@Composable
fun showToastBottomComposable(message: String, duration: Int = Toast.LENGTH_SHORT) {
    val context = LocalContext.current
    showToastBottom(context, message, duration)
}
