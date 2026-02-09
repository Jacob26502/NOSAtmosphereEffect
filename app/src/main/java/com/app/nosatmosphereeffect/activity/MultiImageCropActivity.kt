package com.app.nosatmosphereeffect.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
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
        setContentView(R.layout.activity_crop_multi)

        cropView = findViewById(R.id.cropView)
        val btnDone = findViewById<Button>(R.id.btnDone)
        val btnCancel = findViewById<ImageButton>(R.id.btnCancel)

        // Get the URI and any saved Matrix state
        sourceUri = intent.data
        val savedMatrixValues = intent.getFloatArrayExtra("MATRIX_VALUES")

        if (sourceUri != null) {
            loadImage(sourceUri!!, savedMatrixValues)
        }

        btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        btnDone.setOnClickListener {
            // 1. Get current zoom state (Matrix)
            val currentMatrix = Matrix()
            cropView.matrix.getValues(mapValues)
            // Note: TouchImageView might use its own internal matrix tracking,
            // but usually cropping returns the bitmap based on what is seen.

            // 2. Get cropped bitmap
            val croppedBitmap = cropView.getCroppedBitmap()

            // 3. Get the Matrix values representing current zoom/pan
            // We need to fetch it from the view. TouchImageView usually exposes it.
            // If getMatrix() returns the identity, we might need a different approach,
            // but typically this works for restoration.
            val matrixValues = FloatArray(9)
            cropView.matrix.getValues(matrixValues)

            if (croppedBitmap != null) {
                saveAndReturnResult(croppedBitmap, matrixValues)
            } else {
                Toast.makeText(this, "Error cropping image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Temp array for matrix values
    private val mapValues = FloatArray(9)

    private fun loadImage(uri: Uri, matrixValues: FloatArray?) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Handle Rotation
            val finalBitmap = handleExifRotation(uri, bitmap)

            // Set image
            cropView.setImageBitmap(finalBitmap)

            // Restore Zoom State if available
            if (matrixValues != null) {
                val matrix = Matrix()
                matrix.setValues(matrixValues)
                // We use a post block to ensure view has dimensions before applying matrix
                cropView.post {
                    cropView.imageMatrix = matrix
                    // TouchImageView specific: If it has a specific method to set zoom from matrix
                    // we should use it. Assuming standard ImageView matrix behavior here
                    // combined with TouchImageView's matrix handling.
                    cropView.invalidate()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndReturnResult(bitmap: Bitmap, matrixValues: FloatArray) {
        try {
            val filename = "cropped_playlist_${System.currentTimeMillis()}.jpg"
            val destFile = File(cacheDir, filename)

            val out = FileOutputStream(destFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            val resultIntent = Intent()
            resultIntent.putExtra("CROPPED_IMAGE_PATH", destFile.absolutePath)
            resultIntent.putExtra("MATRIX_VALUES", matrixValues) // Return state
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