package com.app.nosatmosphereeffect.service

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.animation.LinearInterpolator
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.renderer.FrostedRenderer
import java.io.File

class FrostedService : GLWallpaperService() {

    override fun onCreateEngine(): Engine {
        return FrostedEngine()
    }

    override fun getRenderer(): GLSurfaceView.Renderer {
        return FrostedRenderer(applicationContext)
    }

    inner class FrostedEngine : GLEngine() {
        private var pollInterval: Long = 50L
        private var lockDelay: Long = 0L
        private var animDuration: Long = 500L

        private var myRenderer: FrostedRenderer? = null
        private var blurAnimator: ValueAnimator? = null
        private var isLocked: Boolean = true
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

        private val resetRunnable = Runnable {
            prepareForNextUnlock()
            rotateWallpaper()
        }

        private fun rotateWallpaper() {
            val playlistDir = File(filesDir, "playlist")
            val playlistFiles = playlistDir.listFiles { _, name -> name.endsWith(".jpg") }

            if (playlistFiles == null || playlistFiles.size <= 1) {
                return
            }

            val nextFile = File(filesDir, "next_wallpaper.jpg")
            val activeFile = File(filesDir, "wallpaper.jpg")

            if (nextFile.exists()) {
                try {
                    if (activeFile.exists()) {
                        activeFile.delete()
                    }
                    val success = nextFile.renameTo(activeFile)

                    if (success) {
                        handler.post {
                            myRenderer?.reloadTexture()
                            requestRender()
                            notifyColorsChanged()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                prepareNextWallpaper()
            } else {
                prepareNextWallpaper()
            }
        }

        private fun prepareNextWallpaper() {
            Thread {
                try {
                    val playlistDir = File(filesDir, "playlist")
                    if (playlistDir.exists() && playlistDir.isDirectory) {
                        val files = playlistDir.listFiles { _, name -> name.endsWith(".jpg") }

                        if (!files.isNullOrEmpty() && files.size > 1) {
                            // 1. Get the last used image name from Prefs
                            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
                            val lastUsedName = prefs.getString("last_playlist_image", "")

                            // 2. Filter the list to EXCLUDE the last used image
                            val candidates = files.filter { it.name != lastUsedName }

                            // 3. Pick from candidates (fallback to all files if something went wrong)
                            val validFiles = candidates.ifEmpty { files.toList() }

                            val randomFile = validFiles.random()

                            // 4. Save THIS file's name as the new "last used"
                            prefs.edit().putString("last_playlist_image", randomFile.name).apply()

                            // 5. Copy to next_wallpaper.jpg
                            val nextFile = File(filesDir, "next_wallpaper.jpg")
                            randomFile.copyTo(nextFile, overwrite = true)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        override fun onComputeColors(): WallpaperColors? {
            try {
                val file = File(filesDir, "wallpaper.jpg")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        val colors = WallpaperColors.fromBitmap(bitmap)
                        bitmap.recycle() // Clean up memory immediately
                        return colors
                    }
                }
            } catch (e: Exception) {
            }
            return super.onComputeColors()
        }
        private val unlockChecker = object : Runnable {
            override fun run() {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!keyguardManager.isKeyguardLocked) {
                    isLocked = false
                    playUnlockAnimation()
                    handler.removeCallbacks(this)
                } else {
                    handler.postDelayed(this, pollInterval)
                }
            }
        }

        private val systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isLocked = true
                        handler.removeCallbacks(unlockChecker)
                        handler.post(unlockChecker)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(unlockChecker)
                        isLocked = true
                        handler.postDelayed(resetRunnable, lockDelay)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        handler.removeCallbacks(resetRunnable)
                        if (isLocked) {
                            isLocked = false
                            playUnlockAnimation()
                            handler.removeCallbacks(unlockChecker)
                        }
                    }
                    "com.app.nosatmosphereeffect.RELOAD_WALLPAPER" -> {
                        myRenderer?.reloadTexture()
                        requestRender()
                        notifyColorsChanged()
                    }
                    "com.app.nosatmosphereeffect.UPDATE_CONFIG" -> {
                        updateRendererConfig()
                        requestRender()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: android.view.SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val r = getRenderer()
            if (r is FrostedRenderer) {
                myRenderer = r
                updateRendererConfig()
                setRenderer(myRenderer!!)
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction("com.app.nosatmosphereeffect.RELOAD_WALLPAPER")
                addAction("com.app.nosatmosphereeffect.UPDATE_CONFIG")
            }
            registerReceiver(systemEventReceiver, filter, Context.RECEIVER_EXPORTED)
        }

        override fun onDestroy() {
            super.onDestroy()
            try { unregisterReceiver(systemEventReceiver) } catch (e: Exception) { }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!km.isKeyguardLocked) isLocked = false

                if (isLocked) {
                    myRenderer?.blurStrength = 0.0f // Locked = Sharp
                    requestRender()
                } else {
                    snapToHomeState()
                }
            }
        }

        private fun playUnlockAnimation() {
            val targetRenderer = myRenderer ?: return
            blurAnimator?.cancel()
            targetRenderer.blurStrength = 0.0f
            requestRender()

            blurAnimator = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                duration = animDuration
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    targetRenderer.blurStrength = value
                    requestRender()
                }
            }
            blurAnimator?.start()
        }

        private fun snapToHomeState() {
            myRenderer?.blurStrength = 1.0f // Unlocked = Blurred
            requestRender()
        }

        private fun prepareForNextUnlock() {
            myRenderer?.blurStrength = 0.0f // Reset to Sharp
            requestRender()
        }

        private fun updateRendererConfig() {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            myRenderer?.dimLevel = prefs.getFloat("dim_level", 0.2f)
            myRenderer?.enableNoise = prefs.getBoolean("enable_noise", false)
            myRenderer?.noiseScale = prefs.getFloat("noise_scale", 2000.0f)
            myRenderer?.noiseStrength = prefs.getFloat("noise_strength", 0.06f)

            // Blur Slider
            val savedRadius = prefs.getFloat("frosted_blur_radius", 200f)
            if (myRenderer?.blurRadius != savedRadius) {
                myRenderer?.blurRadius = savedRadius
                myRenderer?.reloadTexture()
            }

            val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            pollInterval = prefs.getLong("poll_interval", if (isSamsung) 30000L else 50L)
            lockDelay = prefs.getLong("lock_delay", if (isSamsung) 0L else 800L)
            animDuration = prefs.getLong("anim_duration", 500L)
        }
    }
}