package com.app.nosatmosphereeffect.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.app.nosatmosphereeffect.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AdvancedSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        val layoutPoll = findViewById<TextInputLayout>(R.id.layoutPollInterval)
        val layoutDelay = findViewById<TextInputLayout>(R.id.layoutLockDelay)
        val inputPoll = findViewById<TextInputEditText>(R.id.inputPollInterval)
        val inputDelay = findViewById<TextInputEditText>(R.id.inputLockDelay)
        val inputDuration = findViewById<TextInputEditText>(R.id.inputAnimDuration)
        val btnApply = findViewById<Button>(R.id.btnApplyAdvanced)
        val btnReset = findViewById<Button>(R.id.btnResetDefaults)
        val switchNoise = findViewById<MaterialSwitch>(R.id.switchNoise)
        val layoutNoise = findViewById<LinearLayout>(R.id.layoutNoiseSettings)
        val inputNoiseScale = findViewById<TextInputEditText>(R.id.inputNoiseScale)
        val inputNoiseStrength = findViewById<TextInputEditText>(R.id.inputNoiseStrength)
        val activeEffect = intent.getStringExtra("ACTIVE_EFFECT_TYPE") ?: "ORIGINAL"
        val isSamsung = intent.getBooleanExtra("IS_SAMSUNG", false)
        val defaultDuration = if (activeEffect == "REVERSE") 1500L else if (activeEffect == "ORIGINAL") 2500L else 500L
        val defaultPoll = if (isSamsung) 30000L else 50L
        val defaultDelay = if (isSamsung) 0L else 800L

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Load existing values or show defaults in placeholder
        val savedPoll = prefs.getLong("poll_interval", -1L)
        val savedDelay = prefs.getLong("lock_delay", -1L)
        val savedDuration = prefs.getLong("anim_duration", -1L)

        inputPoll.setText(if (savedPoll != -1L) savedPoll.toString() else defaultPoll.toString())
        inputDelay.setText(if (savedDelay != -1L) savedDelay.toString() else defaultDelay.toString())

        // For duration, show what is currently saved, or leave empty/generic if using defaults
        if (savedDuration != -1L) {
            inputDuration.setText(savedDuration.toString())
        } else {
            // Leave empty or set a hint, usually easier to just show 2000 as a placeholder
            inputDuration.setText(defaultDuration.toString())
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

        layoutPoll.setEndIconOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Unlock Check Interval")
                .setMessage(
                    "Controls how frequently the app checks if the device has been unlocked.\n\n" +
                            "• What it solves:\n" +
                            "If you unlock your phone and the animation starts after a delay, lower this value.\n\n" +
                            "• Recommended:\n" +
                            "30000ms for Samsung and most devices (Saves Battery).\n" +
                            "50ms if you experience delayed animation start."
                )
                .setPositiveButton("Got it", null)
                .show()
        }

        layoutDelay.setEndIconOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Lock Delay")
                .setMessage(
                    "Adds a pause before the wallpaper resets when you lock the phone.\n\n" +
                            "• What it solves:\n" +
                            "If you see a glimpse of the wallpaper resetting/snapping back before the screen turns fully black, increase this value.\n\n" +
                            "• Recommended:\n" +
                            "0ms for Samsung/Most devices.\n" +
                            "500ms - 800ms if you experience the glitch.\n\n" +
                            "⚠️ Note: If this value is too high, unlocking immediately after locking might show the wallpaper in its previous state."
                )
                .setPositiveButton("Got it", null)
                .show()
        }


        btnApply.setOnClickListener {
            val poll = inputPoll.text.toString().toLongOrNull() ?: defaultPoll
            val delay = inputDelay.text.toString().toLongOrNull() ?: defaultDelay
            val duration = inputDuration.text.toString().toLongOrNull() ?: defaultDuration
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
            inputPoll.setText(defaultPoll.toString())
            inputDelay.setText(defaultDelay.toString())
            inputDuration.setText(defaultDuration.toString())
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