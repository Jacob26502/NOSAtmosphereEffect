package com.app.nosatmosphereeffect.activity

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import com.app.nosatmosphereeffect.MainActivity
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.TouchImageView
import com.app.nosatmosphereeffect.service.AtmosphereService
import com.app.nosatmosphereeffect.service.BlurToSharpService
import com.app.nosatmosphereeffect.service.FrostedReverseService
import com.app.nosatmosphereeffect.service.FrostedService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MultiImageCropActivity : AppCompatActivity() {

    private var imageUris: ArrayList<Uri> = ArrayList()
    private var currentIndex = 0

    private lateinit var cropView: TouchImageView
    private lateinit var btnDone: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val windowController = WindowCompat.getInsetsController(window, window.decorView)
        windowController.hide(WindowInsetsCompat.Type.systemBars())
        windowController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_crop_multi)

        // Receive the list of URIs
        val uri = intent.data ?: run {
            Toast.makeText(this, "No Image Data Found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Thread {
            try {
                // Load safely with Downsampling + Rotation
                val correctedBitmap = decodeSampledBitmapFromUri(this, uri, 4096, 4096)

                runOnUiThread {
                    if (correctedBitmap != null) {
                        cropView.setInitialImage(correctedBitmap)
                    } else {
                        Toast.makeText(this, "Could not load image format.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()

        cropView = findViewById(R.id.cropImageView)
        btnDone = findViewById(R.id.btnSaveCrop)

        clearPlaylist()

        btnDone.setOnClickListener {
            val croppedBitmap = cropView.getCroppedBitmap()
            saveToPlaylist(croppedBitmap)
            finish()
        }
    }

    private fun saveToPlaylist(bitmap: Bitmap) {
        val playlistDir = File(filesDir, "playlist")
        if (!playlistDir.exists()) playlistDir.mkdirs()

        val filename = "wallpaper_${System.currentTimeMillis()}_$currentIndex.jpg"
        val file = File(playlistDir, filename)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearPlaylist() {
        val playlistDir = File(filesDir, "playlist")
        if (playlistDir.exists()) {
            playlistDir.deleteRecursively()
            playlistDir.mkdirs()
        }
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        var inputStream: InputStream? = null
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            inputStream = context.contentResolver.openInputStream(uri)
            val rawBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            return if (rawBitmap != null) handleExifRotation(context, uri, rawBitmap) else null
        } catch (e: Exception) { return null }
    }

    private fun handleExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        // ... (Copy exact logic from CropActivity or BlurToSharpCropActivity) ...
        // For brevity, assuming standard rotation logic here
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return bitmap

            val exifInterface = ExifInterface(inputStream)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationInDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationInDegrees == 0f) return bitmap

            val matrix = Matrix()
            matrix.postRotate(rotationInDegrees)
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            return rotatedBitmap

        } catch (e: Exception) {
            return bitmap
        } finally {
            inputStream?.close()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        val maxImageDimension = kotlin.math.max(height, width)
        val maxTextureSize = kotlin.math.min(reqWidth, reqHeight)
        if (maxImageDimension > maxTextureSize) {
            val factor = maxImageDimension.toFloat() / maxTextureSize.toFloat()
            while (inSampleSize < factor) inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun goHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}