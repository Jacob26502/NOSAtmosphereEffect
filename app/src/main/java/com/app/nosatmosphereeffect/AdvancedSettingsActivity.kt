package com.app.nosatmosphereeffect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.textfield.TextInputEditText

class AdvancedSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        val inputPoll = findViewById<TextInputEditText>(R.id.inputPollInterval)
        val inputDelay = findViewById<TextInputEditText>(R.id.inputLockDelay)
        val inputDuration = findViewById<TextInputEditText>(R.id.inputAnimDuration)
        val btnApply = findViewById<Button>(R.id.btnApplyAdvanced)
        val btnReset = findViewById<Button>(R.id.btnResetDefaults)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Load existing values or show defaults in placeholder
        val savedPoll = prefs.getLong("poll_interval", -1L)
        val savedDelay = prefs.getLong("lock_delay", -1L)
        val savedDuration = prefs.getLong("anim_duration", -1L)

        inputPoll.setText(if (savedPoll != -1L) savedPoll.toString() else "50")
        inputDelay.setText(if (savedDelay != -1L) savedDelay.toString() else "800")

        // For duration, show what is currently saved, or leave empty/generic if using defaults
        if (savedDuration != -1L) {
            inputDuration.setText(savedDuration.toString())
        } else {
            // Leave empty or set a hint, usually easier to just show 2000 as a placeholder
            inputDuration.setText("2000")
        }

        btnApply.setOnClickListener {
            val poll = inputPoll.text.toString().toLongOrNull() ?: 50L
            val delay = inputDelay.text.toString().toLongOrNull() ?: 800L
            val duration = inputDuration.text.toString().toLongOrNull() ?: 2000L

            prefs.edit {
                putLong("poll_interval", poll)
                putLong("lock_delay", delay)
                putLong("anim_duration", duration)
            }
            sendUpdateBroadcast()
        }

        btnReset.setOnClickListener {
            // Remove keys to revert to Service-specific hardcoded defaults
            prefs.edit {
                remove("poll_interval")
                remove("lock_delay")
                remove("anim_duration")
            }

            // Visual reset
            inputPoll.setText("50")
            inputDelay.setText("0")
            inputDuration.setText("2000")

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