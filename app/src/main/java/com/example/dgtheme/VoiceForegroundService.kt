package com.example.dgtheme

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap

class VoiceForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_service_channel"
        const val ACTION_TRIGGER_LISTEN = "ACTION_TRIGGER_LISTEN"
        const val DEBUG_TAG = "VoiceServiceDebug"
        var isRunning = false
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var savedWallpaper: Bitmap? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(DEBUG_TAG, "Service created and isRunning set to true.")
        sharedPreferences = getSharedPreferences("DGThemePrefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        startForeground(1, buildNotification("Service running"))
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_LISTEN) {
            Log.d(DEBUG_TAG, "ACTION_TRIGGER_LISTEN received.")
            showOverlay()
            startListening()
        }
        return START_STICKY
    }

    private fun handleRecognizedText(text: String) {
        Log.d(DEBUG_TAG, "Recognized text: $text")
        val wakeupWord = sharedPreferences.getString("wakeup_word", "set wallpaper")
        val exitWord = sharedPreferences.getString("exit_word", "restore wallpaper")
        Log.d(DEBUG_TAG, "Comparing with wakeup: '$wakeupWord' and exit: '$exitWord'")

        if (text.contains(wakeupWord!!)) {
            Log.d(DEBUG_TAG, "Wake-up word detected!")
            setNewWallpaper()
        } else if (text.contains(exitWord!!)) {
            Log.d(DEBUG_TAG, "Exit word detected!")
            restoreWallpaper()
        } else {
            Log.d(DEBUG_TAG, "No keywords detected in recognized text.")
        }
    }

    // Force rewrite to fix deprecated drawable call
    private fun setNewWallpaper() {
        try {
            Log.d(DEBUG_TAG, "Attempting to set new wallpaper.")
            val wallpaperManager = WallpaperManager.getInstance(this)

            if (savedWallpaper == null) {
                wallpaperManager.peekDrawable()?.let {
                    savedWallpaper = it.toBitmap()
                    Log.d(DEBUG_TAG, "Original wallpaper saved.")
                }
            }

            // Get the user-selected wallpaper PATH
            val wallpaperPath = sharedPreferences.getString("wallpaper_path", null)
            if (wallpaperPath != null) {
                Log.d(DEBUG_TAG, "Found wallpaper path: $wallpaperPath")
                val bmp = BitmapFactory.decodeFile(wallpaperPath)
                if (bmp != null) {
                    wallpaperManager.setBitmap(bmp)
                    Log.d(DEBUG_TAG, "New wallpaper successfully set from file.")
                } else {
                    Log.e(DEBUG_TAG, "Failed to decode bitmap from file path.")
                }
            } else {
                Log.e(DEBUG_TAG, "No wallpaper path found in SharedPreferences.")
            }
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error setting new wallpaper: ${e.message}", e)
        }
    }

    private fun restoreWallpaper() {
        try {
            Log.d(DEBUG_TAG, "Attempting to restore wallpaper.")
            savedWallpaper?.let {
                WallpaperManager.getInstance(this).setBitmap(it)
                Log.d(DEBUG_TAG, "Original wallpaper restored.")
            } ?: run {
                Log.w(DEBUG_TAG, "No saved wallpaper to restore.")
            }
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error restoring wallpaper: ${e.message}", e)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Wallpaper Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Voice Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_listening, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "overlay add failed: ${e.message}")
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
    }

    private fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { hideOverlay() }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        for (word in matches) {
                            handleRecognizedText(word.lowercase())
                        }
                    }
                    hideOverlay()
                }
            })
            speechRecognizer?.startListening(intent)
        } else {
            hideOverlay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        speechRecognizer?.destroy()
        hideOverlay()
        Log.d(DEBUG_TAG, "Service destroyed and isRunning set to false.")
    }

    override fun onBind(intent: Intent?) = null
}
