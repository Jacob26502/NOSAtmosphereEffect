package com.app.nosatmosphereeffect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class EffectSelectionActivity : AppCompatActivity() {

    private var selectedEffect: String = "ORIGINAL"

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val intent = if (selectedEffect == "REVERSE") {
                Intent(this, BlurToSharpCropActivity::class.java)
            } else {
                Intent(this, CropActivity::class.java)
            }
            intent.putExtra("IMAGE_URI", it.toString())
            startActivity(intent)

            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_effect_selection)

        val cardOriginal = findViewById<MaterialCardView>(R.id.cardEffectOriginal)
        val cardReverse = findViewById<MaterialCardView>(R.id.cardEffectReverse)

        cardOriginal.setOnClickListener {
            selectedEffect = "ORIGINAL"
            pickImage.launch("image/*")
        }

        cardReverse.setOnClickListener {
            selectedEffect = "REVERSE"
            pickImage.launch("image/*")
        }
    }
}