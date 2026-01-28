package com.app.nosatmosphereeffect

import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.slider.Slider
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var layoutSettings: LinearLayout
    private lateinit var sliderDimness: Slider

    private lateinit var btnUpdateDimness: Button
    private lateinit var btnMainAction: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivityIfAvailable(this)

        setContentView(R.layout.activity_main)

        layoutSettings = findViewById(R.id.layoutSettings)
        sliderDimness = findViewById(R.id.sliderDimness)
        btnUpdateDimness = findViewById(R.id.btnUpdateDimness)
        btnMainAction = findViewById(R.id.btnMainAction)

        btnMainAction.setOnClickListener {
            startActivity(Intent(this, EffectSelectionActivity::class.java))
        }

        sliderDimness.addOnChangeListener { _, value, _ ->
            updateButtonState(value)
        }
        btnUpdateDimness.setOnClickListener {
            applyDimnessUpdate()
        }
    }

    override fun onResume() {
        super.onResume()
        checkWallpaperStatus()
    }

    private fun checkWallpaperStatus() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasEffectSelected = prefs.contains("current_effect_type")

        // Update Button Text Logic
        if (hasEffectSelected || isServiceActive()) {
            btnMainAction.setText(R.string.action_change_effect)
        } else {
            btnMainAction.setText(R.string.action_select_effect)
        }
        if (isServiceActive()) {
            layoutSettings.visibility = View.VISIBLE
            loadCurrentDimness()
        } else {
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

    private fun isServiceActive(): Boolean {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo
        return info != null && info.packageName == packageName
    }
}