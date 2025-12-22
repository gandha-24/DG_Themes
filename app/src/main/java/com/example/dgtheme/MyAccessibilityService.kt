package com.example.dgtheme

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    private var lastVolumeDownPress: Long = 0
    private val DOUBLE_PRESS_INTERVAL: Long = 500 // 500ms

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for key events
    }

    override fun onInterrupt() {
        // Not needed
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeDownPress < DOUBLE_PRESS_INTERVAL) {
                // Double press detected!
                Log.d("AccessibilityService", "Volume down double press detected!")
                triggerVoiceListening()
                lastVolumeDownPress = 0 // Reset timer
            } else {
                lastVolumeDownPress = currentTime
            }
        }
        return super.onKeyEvent(event)
    }

    private fun triggerVoiceListening() {
        val intent = Intent(this, VoiceForegroundService::class.java).apply {
            action = VoiceForegroundService.ACTION_TRIGGER_LISTEN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AccessibilityService", "Service connected!")
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_FOCUSED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
        // We need to request the capability to receive key events.
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }
}
