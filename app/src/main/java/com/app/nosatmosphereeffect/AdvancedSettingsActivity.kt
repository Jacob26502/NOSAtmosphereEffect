package com.app.nosatmosphereeffect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.textfield.TextInputEditText
import android.view.View
import android.widget.LinearLayout

class AdvancedSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        val inputPoll = findViewById<TextInputEditText>(R.id.inputPollInterval)
        val inputDelay = findViewById<TextInputEditText>(R.id.inputLockDelay)
        val inputDuration = findViewById<TextInputEditText>(R.id.inputAnimDuration)
        val btnApply = findViewById<Button>(R.id.btnApplyAdvanced)
        val btnReset = findViewById<Button>(R.id.btnResetDefaults)
        val switchNoise = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchNoise)
        val layoutNoise = findViewById<LinearLayout>(R.id.layoutNoiseSettings)
        val inputNoiseScale = findViewById<TextInputEditText>(R.id.inputNoiseScale)
        val inputNoiseStrength = findViewById<TextInputEditText>(R.id.inputNoiseStrength)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Load existing values or show defaults in placeholder
        val savedPoll = prefs.getLong("poll_interval", -1L)
        val savedDelay = prefs.getLong("lock_delay", -1L)
        val savedDuration = prefs.getLong("anim_duration", -1L)

        inputPoll.setText(if (savedPoll != -1L) savedPoll.toString() else "50")
        inputDelay.setText(if (savedDelay != -1L) savedDelay.toString() else "0")

        // For duration, show what is currently saved, or leave empty/generic if using defaults
        if (savedDuration != -1L) {
            inputDuration.setText(savedDuration.toString())
        } else {
            // Leave empty or set a hint, usually easier to just show 2000 as a placeholder
            inputDuration.setText("2500")
        }

        switchNoise.isChecked = prefs.getBoolean("enable_noise", false)
        val savedNoiseScale = prefs.getFloat("noise_scale", -1f)
        val savedNoiseStrength = prefs.getFloat("noise_strength", -1f)

        inputNoiseScale.setText(if (savedNoiseScale != -1f) savedNoiseScale.toString() else "2000.0")
        inputNoiseStrength.setText(if (savedNoiseStrength != -1f) savedNoiseStrength.toString() else "0.06")

        val isNoiseEnabled = prefs.getBoolean("enable_noise", false)
        switchNoise.isChecked = isNoiseEnabled
        layoutNoise.visibility = if (isNoiseEnabled) View.VISIBLE else View.GONE

        switchNoise.setOnCheckedChangeListener { _, isChecked ->
            layoutNoise.visibility = if (isChecked) View.VISIBLE else View.GONE
        }


        btnApply.setOnClickListener {
            val poll = inputPoll.text.toString().toLongOrNull() ?: 50L
            val delay = inputDelay.text.toString().toLongOrNull() ?: 0L
            val duration = inputDuration.text.toString().toLongOrNull() ?: 2500L
            val enableNoise = switchNoise.isChecked
            val noiseScale = inputNoiseScale.text.toString().toFloatOrNull() ?: 2000.0f
            val noiseStrength = inputNoiseStrength.text.toString().toFloatOrNull() ?: 0.06f

            prefs.edit {
                putLong("poll_interval", poll)
                putLong("lock_delay", delay)
                putLong("anim_duration", duration)
                putBoolean("enable_noise", enableNoise)
                putFloat("noise_scale", noiseScale)
                putFloat("noise_strength", noiseStrength)
            }
            sendUpdateBroadcast()
        }

        btnReset.setOnClickListener {
            // Remove keys to revert to Service-specific hardcoded defaults
            prefs.edit {
                remove("poll_interval")
                remove("lock_delay")
                remove("anim_duration")
                remove("enable_noise")
                remove("noise_scale")
                remove("noise_strength")
            }

            // Visual reset
            inputPoll.setText("50")
            inputDelay.setText("0")
            inputDuration.setText("2500")
            switchNoise.isChecked = false
            layoutNoise.visibility = View.GONE
            inputNoiseScale.setText("2000.0")
            inputNoiseStrength.setText("0.06")

            sendUpdateBroadcast()
        }
    }

    private fun sendUpdateBroadcast() {
        val intent = Intent("com.app.nosatmosphereeffect.UPDATE_CONFIG")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        Toast.makeText(this, "Settings Applied!", Toast.LENGTH_SHORT).show()
        finish()
    }
}