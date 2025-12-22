package com.example.dgtheme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var accessibilityButton: Button
    private lateinit var overlayButton: Button
    private lateinit var startServiceButton: Button
    private lateinit var wakeupWordEditText: EditText
    private lateinit var exitWordEditText: EditText
    private lateinit var saveWordsButton: Button
    private lateinit var selectWallpaperButton: Button

    private lateinit var sharedPreferences: SharedPreferences

    // A single launcher to handle all permission requests
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check which permission was granted and act accordingly
        val readImagesGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        val readStorageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

        if (readImagesGranted || readStorageGranted) {
            // Storage permission was just granted, launch the image picker
            selectWallpaperLauncher.launch("image/*")
        } else {
             Toast.makeText(this, "Storage permission is required to select a wallpaper.", Toast.LENGTH_LONG).show()
        }

        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startVoiceService()
        } else if (permissions.containsKey(Manifest.permission.RECORD_AUDIO)) {
             Toast.makeText(this, "Microphone permission is required for voice commands.", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateButtonStates() }

    private val selectWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val filePath = copyImageToInternalStorage(it, "selected_wallpaper.jpg")
            if (filePath != null) {
                sharedPreferences.edit().putString("wallpaper_path", filePath).apply()
                Toast.makeText(this, "Wallpaper selected!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save wallpaper.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("DGThemePrefs", Context.MODE_PRIVATE)

        accessibilityButton = findViewById(R.id.button_accessibility)
        overlayButton = findViewById(R.id.button_overlay)
        startServiceButton = findViewById(R.id.button_start_service)
        wakeupWordEditText = findViewById(R.id.edit_text_wakeup_word)
        exitWordEditText = findViewById(R.id.edit_text_exit_word)
        saveWordsButton = findViewById(R.id.button_save_words)
        selectWallpaperButton = findViewById(R.id.button_select_wallpaper)

        wakeupWordEditText.setText(sharedPreferences.getString("wakeup_word", "set wallpaper"))
        exitWordEditText.setText(sharedPreferences.getString("exit_word", "restore wallpaper"))

        setupClickListeners()
        updateButtonStates()
    }

    private fun copyImageToInternalStorage(uri: Uri, fileName: String): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun setupClickListeners() {
        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        overlayButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            }
        }

        startServiceButton.setOnClickListener {
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startVoiceService()
            } else {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }

        saveWordsButton.setOnClickListener {
            val wakeupWord = wakeupWordEditText.text.toString().trim()
            val exitWord = exitWordEditText.text.toString().trim()
            if (wakeupWord.isNotEmpty() && exitWord.isNotEmpty()) {
                sharedPreferences.edit().apply {
                    putString("wakeup_word", wakeupWord.lowercase())
                    putString("exit_word", exitWord.lowercase())
                    apply()
                }
                Toast.makeText(this, "Words saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Words cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

        selectWallpaperButton.setOnClickListener {
            val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED) {
                selectWallpaperLauncher.launch("image/*")
            } else {
                requestPermissionsLauncher.launch(arrayOf(storagePermission))
            }
        }
    }
    
    private fun startVoiceService() {
        val svc = Intent(this, VoiceForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
        updateButtonStates() 
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)

        accessibilityButton.isEnabled = !accessibilityEnabled
        overlayButton.isEnabled = !overlayEnabled

        startServiceButton.isEnabled = accessibilityEnabled && overlayEnabled && !VoiceForegroundService.isRunning
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${MyAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }
}
