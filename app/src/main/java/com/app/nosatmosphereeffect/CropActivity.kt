package com.app.nosatmosphereeffect

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_crop)

        val cropView = findViewById<TouchImageView>(R.id.cropImageView)
        val btnSave = findViewById<Button>(R.id.btnSaveCrop)

        btnSave.setText(R.string.action_apply)

        val uriString = intent.getStringExtra("IMAGE_URI") ?: return
        val uri = uriString.toUri()

        // Use a background thread to load heavy images to prevent UI freeze
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
    // 1. Checks Image Size first (without loading to memory)
    // 2. Calculates Scale Factor (to prevent OutOfMemory on 200MP photos)
    // 3. Decodes & Rotates based on Exif (Supports HEIC, WebP, JPG)
    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        var inputStream: InputStream? = null
        try {
            // A. First pass: Decode dimensions only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // B. Calculate inSampleSize (Scale down factor)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            // Preferred config for high quality but lower memory than HARDWARE
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // C. Decode bitmap with inSampleSize
            inputStream = context.contentResolver.openInputStream(uri)
            val rawBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            if (rawBitmap == null) return null

            // D. Handle Rotation (HEIC/Samsung often needs this)
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

            // Use ExifInterface (Supports HEIC on API 28+)
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

            // If no rotation needed, return original
            if (rotationInDegrees == 0f) return bitmap

            // Create rotated bitmap
            val matrix = Matrix()
            matrix.postRotate(rotationInDegrees)
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            if (rotatedBitmap != bitmap) {
                bitmap.recycle() // Clean up old memory
            }
            return rotatedBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return bitmap // Return original if Exif fails
        } finally {
            inputStream?.close()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width STRICTLY UNDER the requested height and width.
        // This protects against texture limits (e.g., 4096 or 8192).
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // FIX: Loop until dimensions are smaller than or equal to requested size
            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }

            // Extra check: Ensure the final result fits within max texture size (4096 is a safe bet)
            // If the loop stopped but we are still exactly at 4096+ on one edge, bump it once more if needed.
            while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun showApplyDialog(bitmap: Bitmap) {
        val options = arrayOf("Set Static Lock Screen", "Save Copy to Gallery")
        val checkedItems = booleanArrayOf(true, true)

        MaterialAlertDialogBuilder(this)
            .setTitle("Apply Options")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Action Required")
                    .setMessage("In the next screen, please select:\n\nSet Wallpaper > Home Screen\n\n(Do not select Lock Screen, as it is already set).")
                    .setPositiveButton("I Understand") { _, _ ->
                        applyWallpaper(
                            bitmap,
                            setLockScreen = checkedItems[0],
                            saveToGallery = checkedItems[1]
                        )
                    }
                    .setCancelable(false)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyWallpaper(bitmap: Bitmap, setLockScreen: Boolean, saveToGallery: Boolean) {
        Toast.makeText(this, "Applying...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                saveFixedWallpaper(bitmap)

                if (saveToGallery) {
                    deleteOldBackups()
                    saveToPublicGallery(bitmap)
                }

                if (setLockScreen) {
                    val wm = WallpaperManager.getInstance(this)
                    wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                }

                runOnUiThread {
                    if (isServiceActive()) {
                        val intent = Intent("com.app.nosatmosphereeffect.RELOAD_WALLPAPER")
                        intent.setPackage(packageName)
                        sendBroadcast(intent)

                        val msg = if (setLockScreen) "Home & Lock Updated!" else "Home Screen Updated!"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        goHome()
                    } else {
                        Toast.makeText(this, "Setup complete! Now activate Home Screen.", Toast.LENGTH_LONG).show()
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

    private fun deleteOldBackups() {
        try {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Atmosphere_%", "%Atmosphere%")

            contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val deleteUri = ContentUris.withAppendedId(collection, id)
                    contentResolver.delete(deleteUri, null, null)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveToPublicGallery(bitmap: Bitmap) {
        try {
            val filename = "Atmosphere_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Atmosphere")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri).use { stream ->
                    if (stream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun isServiceActive(): Boolean {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo
        return info != null && info.component.className == AtmosphereService::class.java.name
    }

    private fun activateService() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, AtmosphereService::class.java))
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