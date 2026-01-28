package com.app.nosatmosphereeffect

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.DynamicColors
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class BlurToSharpCropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_crop)

        val cropView = findViewById<TouchImageView>(R.id.cropImageView)
        val btnSave = findViewById<Button>(R.id.btnSaveCrop)

        btnSave.setText(R.string.action_apply)

        val uriString = intent.getStringExtra("IMAGE_URI") ?: return
        val uri = uriString.toUri()

        Thread {
            try {
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
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()

        btnSave.setOnClickListener {
            val cropped = cropView.getCroppedBitmap()
            showApplyDialog(cropped)
        }
    }

    // --- ROBUST IMAGE LOADER ---
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

            if (rawBitmap == null) return null
            return handleExifRotation(context, uri, rawBitmap)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    private fun handleExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
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
            e.printStackTrace()
            return bitmap
        } finally {
            inputStream?.close()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    // --- END LOADER ---

    private fun showApplyDialog(bitmap: Bitmap) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Apply Wallpaper")
            .setMessage("In the next screen, please select:\n\nSet Wallpaper > Home Screen and Lock Screen.\n\n(This ensures the lock screen effect works correctly).")
            .setPositiveButton("Set Wallpaper") { _, _ ->
                applyWallpaper(bitmap)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyWallpaper(bitmap: Bitmap) {
        Toast.makeText(this, "Applying...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                saveFixedWallpaper(bitmap)

                runOnUiThread {
                    if (isServiceActive()) {
                        val intent = Intent("com.app.nosatmosphereeffect.RELOAD_WALLPAPER")
                        intent.setPackage(packageName)
                        sendBroadcast(intent)
                        Toast.makeText(this, "Wallpaper Updated!", Toast.LENGTH_SHORT).show()
                        goHome()
                    } else {
                        Toast.makeText(this, "Image saved! Please activate the wallpaper.", Toast.LENGTH_LONG).show()
                        activateService()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveFixedWallpaper(bitmap: Bitmap) {
        val file = File(filesDir, "wallpaper.jpg")
        if (file.exists()) file.delete()
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        out.flush()
        out.close()
    }

    private fun isServiceActive(): Boolean {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo
        return info != null && info.packageName == packageName
    }

    private fun activateService() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, BlurToSharpService::class.java))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
            startActivity(intent)
        } finally {
            finish()
        }
    }

    private fun goHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}