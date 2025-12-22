package com.example.dgtheme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.core.app.NotificationCompat
import java.util.Locale

class VoiceService : Service(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "VoiceServiceChannel"

    companion object {
        // A simple way to hold the selected voice. For a more robust app, consider SharedPreferences.
        var selectedVoice: Voice? = null
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val textToSpeak = intent?.getStringExtra("TEXT_TO_SPEAK") ?: "Service started"

        createNotificationChannel()
        val notification = createNotification("Running in background...")
        startForeground(NOTIFICATION_ID, notification)

        if (isTtsInitialized) {
            speakText(textToSpeak)
        } else {
            // Queue the text to be spoken once TTS is initialized.
            // This is a simple way to handle it. A more robust solution might use a list of pending texts.
            tts.setOnUtteranceCompletedListener {
                speakText(textToSpeak)
            }
        }

        // Use START_STICKY if you want the service to be restarted if it's killed.
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts.language = Locale.getDefault()

            // If a custom voice has been selected, apply it
            selectedVoice?.let {
                tts.voice = it
            }

        } else {
            // Handle TTS initialization failure
        }
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need binding
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Voice Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DG Theme Voice")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
