package com.app.nosatmosphereeffect

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.slider.Slider
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var layoutSettings: LinearLayout
    private lateinit var sliderDimness: Slider

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val intent = Intent(this, CropActivity::class.java)
            intent.putExtra("IMAGE_URI", it.toString())
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivityIfAvailable(this)

        setContentView(R.layout.activity_main)

        layoutSettings = findViewById(R.id.layoutSettings)
        sliderDimness = findViewById(R.id.sliderDimness)

        findViewById<Button>(R.id.btnMainAction).setOnClickListener {
            pickImage.launch("image/*")
        }
        findViewById<Button>(R.id.btnUpdateDimness).setOnClickListener {
            applyDimnessUpdate()
        }
    }
    override fun onResume() {
        super.onResume()
        checkWallpaperStatus()
    }

    private fun checkWallpaperStatus() {
        if (isServiceActive()) {
            layoutSettings.visibility = View.VISIBLE
            loadCurrentDimness()
        } else {
            layoutSettings.visibility = View.GONE
        }
    }

    private fun loadCurrentDimness() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentLevel = prefs.getFloat("dim_level", 0.2f)
        sliderDimness.value = currentLevel
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

        Toast.makeText(this, "Wallpaper Updated!", Toast.LENGTH_SHORT).show()
    }

    private fun isServiceActive(): Boolean {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo
        return info != null && info.packageName == packageName
    }
}