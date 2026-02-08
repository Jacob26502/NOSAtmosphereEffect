package com.app.nosatmosphereeffect.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.app.nosatmosphereeffect.helper.EffectItem
import com.app.nosatmosphereeffect.helper.EffectsAdapter
import com.app.nosatmosphereeffect.R

class EffectSelectionActivity : AppCompatActivity() {

    private var selectedEffectId: String = "ORIGINAL"

    private val effectsList = listOf(
        EffectItem(
            id = "ORIGINAL",
            title = "Original Atmosphere",
            description = "Wake up: Sharp ➔ Blur\nSignature style. Drifting ambient atmospheric clouds."
        ),
        EffectItem(
            id = "REVERSE",
            title = "Reverse Atmosphere",
            description = "Wake up: Blur ➔ Sharp\nMysterious reveal. Ambient clouds fade to a clear view."
        ),
        EffectItem(
            id = "FROSTED",
            title = "Simple Frosted",
            description = "Wake up: Sharp ➔ Blur\nModern minimalism. A clean, uniform frosted glass layer."
        ),
        EffectItem(
            id = "FROSTED_REVERSE",
            title = "Simple Frosted (Reverse)",
            description = "Wake up: Blur ➔ Sharp\nElegant clarity. Heavy frost dissolves to crystal clear."
        )
    )

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val intent = if (selectedEffectId.contains("REVERSE")) {
                Intent(this, BlurToSharpCropActivity::class.java)
            } else {
                Intent(this, CropActivity::class.java)
            }
            intent.data = uri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra("EFFECT_ID", selectedEffectId)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_effect_selection)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerEffects)

        val adapter = EffectsAdapter(effectsList) { item ->
            selectedEffectId = item.id
            pickImage.launch("image/*")
        }

        recyclerView.adapter = adapter
    }
}