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
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.animation.LinearInterpolator
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.renderer.AtmosphereRenderer
import java.io.File

class AtmosphereService : GLWallpaperService() {

    override fun onCreateEngine(): Engine {
        return AtmosphereEngine()
    }

    override fun getRenderer(): GLSurfaceView.Renderer {
        return AtmosphereRenderer(applicationContext)
    }

    inner class AtmosphereEngine : GLEngine() {
        private var pollInterval: Long = if (isSamsungDevice()) 30000L else 50L
        private var lockDelay: Long = if (isSamsungDevice()) 0L else 800L
        private var animDuration: Long = 2500L
        private var myRenderer: AtmosphereRenderer? = null
        private var blurAnimator: ValueAnimator? = null
        private var isLocked: Boolean = true

        private val handler = Handler(Looper.getMainLooper())

        private val resetRunnable = Runnable {
            prepareForNextUnlock()
            rotateWallpaper()
        }

        private fun rotateWallpaper() {
            Thread {
                try {
                    val playlistDir = File(filesDir, "playlist")
                    if (playlistDir.exists() && playlistDir.isDirectory) {
                        val files = playlistDir.listFiles { _, name -> name.endsWith(".jpg") }

                        if (!files.isNullOrEmpty() && files.size > 1) {
                            // 1. Pick a random file
                            val randomFile = files.random()

                            // 2. Copy it to the active "wallpaper.jpg"
                            val activeFile = File(filesDir, "wallpaper.jpg")
                            randomFile.copyTo(activeFile, overwrite = true)

                            // 3. Reload texture on Main Thread
                            handler.post {
                                myRenderer?.reloadTexture()
                                requestRender()
                                notifyColorsChanged()
                            }
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
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                if (!keyguardManager.isKeyguardLocked) {
                    // BOOM! Device is unlocked. Trigger animation immediately.
                    isLocked = false
                    playUnlockAnimation()
                    // Stop checking
                    handler.removeCallbacks(this)
                } else {
                    // Still locked, check again in 50ms
                    handler.postDelayed(this, pollInterval)
                }
            }
        }

        private val systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        // Screen turned on. Start watching for unlock immediately.
                        isLocked = true
                        handler.removeCallbacks(unlockChecker)
                        handler.post(unlockChecker)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        // Screen off. Stop watching (save battery) and reset state.
                        handler.removeCallbacks(unlockChecker)
                        isLocked = true
                        handler.postDelayed(resetRunnable, lockDelay)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // Backup: Keep this as a failsafe in case polling misses (rare)
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

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            isLocked = keyguardManager.isKeyguardLocked

            val r = getRenderer()
            if (r is AtmosphereRenderer) {
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

            registerReceiver(systemEventReceiver, filter, RECEIVER_EXPORTED)
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                unregisterReceiver(systemEventReceiver)
            } catch (e: Exception) { }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                if (!keyguardManager.isKeyguardLocked) {
                    isLocked = false
                }
                if (isLocked) {
                    myRenderer?.blurStrength = 0.0f
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
            val targetRenderer = myRenderer ?: return
            blurAnimator?.cancel()
            targetRenderer.blurStrength = 1.0f
            requestRender()
        }

        private fun prepareForNextUnlock() {
            val targetRenderer = myRenderer ?: return
            blurAnimator?.cancel()
            targetRenderer.blurStrength = 0.0f
            requestRender()
        }

        private fun isSamsungDevice(): Boolean {
            return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        }

        private fun updateRendererConfig() {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val dim = prefs.getFloat("dim_level", 0.2f)
            myRenderer?.dimLevel = dim

            // New Advanced Settings (Default to -1 to detect "not set")
            val savedPoll = prefs.getLong("poll_interval", -1L)
            val savedDelay = prefs.getLong("lock_delay", -1L)
            val savedDuration = prefs.getLong("anim_duration", -1L)
            val noise = prefs.getBoolean("enable_noise", false)
            val scale = prefs.getFloat("noise_scale", 2000.0f)
            val strength = prefs.getFloat("noise_strength", 0.06f)

            myRenderer?.enableNoise = noise
            myRenderer?.noiseScale = scale
            myRenderer?.noiseStrength = strength
            pollInterval = if (savedPoll != -1L) savedPoll else if (isSamsungDevice()) 30000L else 50L
            lockDelay = if (savedDelay != -1L) savedDelay else if (isSamsungDevice()) 0L else 800L
            animDuration = if (savedDuration != -1L) savedDuration else 2500L
        }
    }
}