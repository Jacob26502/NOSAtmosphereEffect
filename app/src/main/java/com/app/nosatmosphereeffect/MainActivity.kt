package com.app.nosatmosphereeffect

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import androidx.core.content.edit
import com.app.nosatmosphereeffect.activity.AdvancedSettingsActivity
import com.app.nosatmosphereeffect.activity.EffectSelectionActivity
import com.app.nosatmosphereeffect.service.AtmosphereService
import com.app.nosatmosphereeffect.service.BlurToSharpService
import com.app.nosatmosphereeffect.service.FrostedReverseService
import com.app.nosatmosphereeffect.service.FrostedService
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var layoutSettings: LinearLayout
    private lateinit var sliderDimness: Slider

    private lateinit var btnUpdateDimness: Button
    private lateinit var btnMainAction: Button
    private lateinit var btnAdvanceSettings: Button
    private lateinit var cardBlurSettings: View
    private lateinit var sliderBlurStrength: Slider
    private lateinit var btnUpdateBlur: Button
    private lateinit var btnRotationInterval: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initializeSmartDefaults()

        layoutSettings = findViewById(R.id.layoutSettings)
        sliderDimness = findViewById(R.id.sliderDimness)
        btnUpdateDimness = findViewById(R.id.btnUpdateDimness)
        btnMainAction = findViewById(R.id.btnMainAction)
        btnAdvanceSettings = findViewById(R.id.btnAdvanceSettings)
        cardBlurSettings = findViewById(R.id.cardBlurSettings)
        sliderBlurStrength = findViewById(R.id.sliderBlurStrength)
        btnUpdateBlur = findViewById(R.id.btnUpdateBlur)
        btnRotationInterval = findViewById(R.id.btnRotationInterval)

        btnMainAction.setOnClickListener {
            startActivity(Intent(this, EffectSelectionActivity::class.java))
        }

        btnAdvanceSettings.setOnClickListener {
            val intent = Intent(this, AdvancedSettingsActivity::class.java)
            val activeEffect = getActiveEffectType() ?: "ORIGINAL"
            intent.putExtra("ACTIVE_EFFECT_TYPE", activeEffect)
            intent.putExtra("IS_SAMSUNG", isSamsungDevice())
            startActivity(intent)
        }


        sliderDimness.addOnChangeListener { _, value, _ ->
            updateButtonState(value)
        }
        btnUpdateDimness.setOnClickListener {
            applyDimnessUpdate()
        }

        sliderBlurStrength.addOnChangeListener { _, value, _ ->
            updateBlurButtonState(value)
        }
        btnUpdateBlur.setOnClickListener {
            applyBlurUpdate()
        }

        val playlistDir = File(filesDir, "playlist")
        if (playlistDir.exists() && playlistDir.isDirectory) {
            val files = playlistDir.listFiles { _, name -> name.endsWith(".jpg") }
            if (!files.isNullOrEmpty() && files.size > 1) {
                btnRotationInterval.visibility = View.VISIBLE
                btnRotationInterval.setOnClickListener {
                    showRotationIntervalDialog()
                }
            }else{
                btnRotationInterval.visibility = View.GONE
            }

        }else{
            btnRotationInterval.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        checkWallpaperStatus()
    }

    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    private fun initializeSmartDefaults() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Check if we have already set up defaults (check if poll_interval exists)
        if (!prefs.contains("poll_interval")) {
            val isSamsung = isSamsungDevice()

            // Set optimal defaults based on device
            val defaultPoll = if (isSamsung) 30000L else 50L
            val defaultDelay = if (isSamsung) 0L else 800L

            // Save immediately so Settings screen reads this next time
            prefs.edit {
                putLong("poll_interval", defaultPoll)
                putLong("lock_delay", defaultDelay)
            }
        }
    }

    private fun checkWallpaperStatus() {
        val activeEffect = getActiveEffectType()
        if (activeEffect != null) {
            btnMainAction.setText(R.string.action_change_effect)
            layoutSettings.visibility = View.VISIBLE
            loadCurrentDimness()
            if (activeEffect.contains("FROSTED")) {
                cardBlurSettings.visibility = View.VISIBLE
                loadCurrentBlur()
            } else {
                cardBlurSettings.visibility = View.GONE
            }
        } else {
            btnMainAction.setText(R.string.action_select_effect)
            layoutSettings.visibility = View.GONE
        }
    }
    private fun updateButtonState(sliderValue: Float) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentSavedLevel = prefs.getFloat("dim_level", 0.2f)

        // Enable button only if the slider value differs from the saved value
        btnUpdateDimness.isEnabled = sliderValue != currentSavedLevel
    }
    private fun loadCurrentDimness() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentLevel = prefs.getFloat("dim_level", 0.2f)
        sliderDimness.value = currentLevel
        updateButtonState(currentLevel)
    }

    private fun applyDimnessUpdate() {
        val newValue = sliderDimness.value

        // 1. Save to Preferences
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit {
                putFloat("dim_level", newValue)
            }

        // 2. Broadcast update to Service
        val intent = Intent("com.app.nosatmosphereeffect.UPDATE_CONFIG")
        intent.setPackage(packageName)
        sendBroadcast(intent)

        btnUpdateDimness.isEnabled = false

        Toast.makeText(this, "Wallpaper Updated!", Toast.LENGTH_SHORT).show()
    }
    private fun getActiveEffectType(): String? {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo ?: return null
        if (info.packageName == packageName) {
            val componentName = info.component.className
            return when (componentName) {
                AtmosphereService::class.java.name -> "ORIGINAL"
                BlurToSharpService::class.java.name -> "REVERSE"
                FrostedService::class.java.name -> "FROSTED"
                FrostedReverseService::class.java.name -> "FROSTED_REVERSE"
                else -> null
            }
        }
        return null
    }
    private fun loadCurrentBlur() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentRadius = prefs.getFloat("frosted_blur_radius", 200f)
        sliderBlurStrength.value = currentRadius
        updateBlurButtonState(currentRadius)
    }
    private fun updateBlurButtonState(sliderValue: Float) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val saved = prefs.getFloat("frosted_blur_radius", 200f)
        btnUpdateBlur.isEnabled = sliderValue != saved
    }
    private fun applyBlurUpdate() {
        val newValue = sliderBlurStrength.value
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit {
            putFloat("frosted_blur_radius", newValue)
        }

        val intent = Intent("com.app.nosatmosphereeffect.UPDATE_CONFIG")
        intent.setPackage(packageName)
        sendBroadcast(intent)

        btnUpdateBlur.isEnabled = false
        Toast.makeText(this, "Blur Strength Updated!", Toast.LENGTH_SHORT).show()
    }

    private fun showRotationIntervalDialog() {
        val intervals = arrayOf("Every Lock (Instant)", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours", "6 Hours", "12 Hours", "24 Hours")
        val values = longArrayOf(0, 15, 30, 60, 180, 360, 720, 1440)

        // Get current setting to show selection (Optional)
        val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val currentVal = prefs.getLong("rotation_interval_minutes", 0)
        var checkedItem = values.indexOfFirst { it == currentVal }
        if (checkedItem == -1) checkedItem = 0

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Rotation Interval")
            .setSingleChoiceItems(intervals, checkedItem) { dialog, which ->
                val selectedMinutes = values[which]
                prefs.edit().putLong("rotation_interval_minutes", selectedMinutes).apply()

                android.widget.Toast.makeText(this, "Set to: ${intervals[which]}", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }
}