package com.app.nosatmosphereeffect.activity

import android.app.ProgressDialog
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.nosatmosphereeffect.MainActivity
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.PlaylistAdapter
import com.app.nosatmosphereeffect.helper.PlaylistItem
import com.app.nosatmosphereeffect.service.AtmosphereService
import com.app.nosatmosphereeffect.service.BlurToSharpService
import com.app.nosatmosphereeffect.service.FrostedReverseService
import com.app.nosatmosphereeffect.service.FrostedService
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class PlaylistEditorActivity : AppCompatActivity() {

    private lateinit var adapter: PlaylistAdapter
    private val playlistItems = mutableListOf<PlaylistItem>()
    private var effectId: String = "ORIGINAL"
    private var editingPosition = -1

    // Handle return from Crop Activity
    private val editImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultUriString = result.data?.getStringExtra("CROPPED_IMAGE_PATH")
            val matrixValues = result.data?.getFloatArrayExtra("MATRIX_VALUES")

            if (resultUriString != null && editingPosition != -1 && editingPosition < playlistItems.size) {
                val item = playlistItems[editingPosition]

                // Update Path AND State
                item.isEdited = true
                item.editedFilePath = resultUriString
                item.matrixValues = matrixValues

                adapter.notifyItemChanged(editingPosition)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        setContentView(R.layout.activity_playlist_editor)

        effectId = intent.getStringExtra("EFFECT_ID") ?: "ORIGINAL"
        val uris = intent.getParcelableArrayListExtra<Uri>("IMAGE_URIS")

        if (uris != null) {
            uris.forEach { playlistItems.add(PlaylistItem(it)) }
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerPlaylist)
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        adapter = PlaylistAdapter(this, playlistItems,
            onItemClick = { pos ->
                editingPosition = pos
                launchEditActivity(playlistItems[pos])
            },
            onDeleteClick = { pos ->
                playlistItems.removeAt(pos)
                adapter.notifyItemRemoved(pos)
                adapter.notifyItemRangeChanged(pos, playlistItems.size)
            }
        )
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnApplyAll).setOnClickListener {
            if (playlistItems.isEmpty()) {
                Toast.makeText(this, "Playlist is empty", Toast.LENGTH_SHORT).show()
            } else {
                applyPlaylist()
            }
        }

        if (savedInstanceState != null) {
            editingPosition = savedInstanceState.getInt("EDITING_POS", -1)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("EDITING_POS", editingPosition)
    }

    private fun launchEditActivity(item: PlaylistItem) {
        val intent = Intent(this, MultiImageCropActivity::class.java)

        // ALWAYS pass Original URI to allow zooming out / re-cropping from scratch
        intent.data = item.originalUri

        // Pass saved state if it exists
        if (item.matrixValues != null) {
            intent.putExtra("MATRIX_VALUES", item.matrixValues)
        }

        editImageLauncher.launch(intent)
    }

    private fun applyPlaylist() {
        val progress = ProgressDialog(this)
        progress.setMessage("Processing playlist...")
        progress.setCancelable(false)
        progress.show()

        Thread {
            try {
                val playlistDir = File(filesDir, "playlist")
                if (playlistDir.exists()) playlistDir.deleteRecursively()
                playlistDir.mkdirs()

                // Process each item
                playlistItems.forEachIndexed { index, item ->
                    val destFile = File(playlistDir, "wallpaper_$index.jpg")

                    if (item.isEdited && item.editedFilePath != null) {
                        // Copy manually cropped image
                        File(item.editedFilePath!!).copyTo(destFile, overwrite = true)
                    } else {
                        // Auto-crop (Center Crop)
                        val bitmap = decodeCenterCropBitmap(item.originalUri)
                        if (bitmap != null) {
                            FileOutputStream(destFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                        }
                    }
                }

                // Set the first image as the main active wallpaper
                val firstFile = File(playlistDir, "wallpaper_0.jpg")
                val activeWallpaper = File(filesDir, "wallpaper.jpg")
                if (firstFile.exists()) {
                    firstFile.copyTo(activeWallpaper, overwrite = true)
                }

                // Set lock screen prefs
                getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit().clear().apply()

                runOnUiThread {
                    progress.dismiss()
                    if (isServiceActive()) {
                        sendBroadcast(Intent("com.app.nosatmosphereeffect.RELOAD_WALLPAPER"))
                        Toast.makeText(this, "Playlist Updated!", Toast.LENGTH_SHORT).show()
                        goHome()
                    } else {
                        activateService()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // --- Helper to Auto-Crop unedited images to screen size ---
    private fun decodeCenterCropBitmap(uri: Uri): Bitmap? {
        val metrics = windowManager.currentWindowMetrics.bounds
        val reqW = metrics.width()
        val reqH = metrics.height()

        // 1. Load scaled down
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        options.inSampleSize = calculateInSampleSize(options, reqW, reqH)
        options.inJustDecodeBounds = false

        var bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        // 2. Rotate
        bitmap = handleExifRotation(this, uri, bitmap)

        // 3. Center Crop Logic
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val screenRatio = reqW.toFloat() / reqH.toFloat()

        val matrix = Matrix()
        val scale: Float

        if (bitmapRatio > screenRatio) {
            scale = reqH.toFloat() / bitmap.height.toFloat()
        } else {
            scale = reqW.toFloat() / bitmap.width.toFloat()
        }

        matrix.setScale(scale, scale)
        val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Crop center
        val x = max(0, (scaledBitmap.width - reqW) / 2)
        val y = max(0, (scaledBitmap.height - reqH) / 2)
        val finalW = min(reqW, scaledBitmap.width - x)
        val finalH = min(reqH, scaledBitmap.height - y)

        return Bitmap.createBitmap(scaledBitmap, x, y, finalW, finalH)
    }

    // Reuse rotation logic from other activities...
    private fun handleExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        // (Use same logic as in CropActivity)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return bitmap
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
        } catch(e: Exception) { return bitmap }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun isServiceActive(): Boolean {
        // Reuse checking logic
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo ?: return false
        val activeClass = info.component.className
        val targetClass = when(effectId) {
            "ORIGINAL" -> AtmosphereService::class.java.name
            "REVERSE" -> BlurToSharpService::class.java.name
            "FROSTED" -> FrostedService::class.java.name
            "FROSTED_REVERSE" -> FrostedReverseService::class.java.name
            else -> AtmosphereService::class.java.name
        }
        return activeClass == targetClass
    }

    private fun activateService() {
        // Reuse activation logic
        try {
            val serviceClass = when(effectId) {
                "ORIGINAL" -> AtmosphereService::class.java
                "REVERSE" -> BlurToSharpService::class.java
                "FROSTED" -> FrostedService::class.java
                "FROSTED_REVERSE" -> FrostedReverseService::class.java
                else -> AtmosphereService::class.java
            }
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, serviceClass))
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
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