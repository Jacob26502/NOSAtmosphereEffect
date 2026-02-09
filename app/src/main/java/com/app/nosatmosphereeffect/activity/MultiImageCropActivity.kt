package com.app.nosatmosphereeffect.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.TouchImageView
import java.io.File
import java.io.FileOutputStream

class MultiImageCropActivity : AppCompatActivity() {

    private lateinit var cropView: TouchImageView
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_multi) // Ensure this layout exists

        cropView = findViewById(R.id.cropImageView)
        val btnDone = findViewById<TextView>(R.id.btnSaveCrop)

        sourceUri = intent.data

        if (sourceUri != null) {
            loadImage(sourceUri!!)
        } else {
            Toast.makeText(this, "No Image Data Found", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnDone.setOnClickListener {
            // 1. Get the cropped bitmap
            val croppedBitmap = cropView.getCroppedBitmap()
            if (croppedBitmap != null) {
                // 2. Save and return result
                saveAndReturnResult(croppedBitmap)
            } else {
                Toast.makeText(this, "Error cropping image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Handle Rotation
            val rotatedBitmap = handleExifRotation(uri, bitmap)
            cropView.setImageBitmap(rotatedBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndReturnResult(bitmap: Bitmap) {
        try {
            // Create a unique temp file for the cropped image
            val filename = "cropped_playlist_${System.currentTimeMillis()}.jpg"
            val destFile = File(cacheDir, filename)

            val out = FileOutputStream(destFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            // Send path back to PlaylistEditorActivity
            val resultIntent = Intent()
            resultIntent.putExtra("CROPPED_IMAGE_PATH", destFile.absolutePath)
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val input = contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(input)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            input.close()

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(rotation) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            return bitmap
        }
    }
}