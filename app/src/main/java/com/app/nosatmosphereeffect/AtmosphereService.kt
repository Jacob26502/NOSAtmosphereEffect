package com.app.nosatmosphereeffect

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.GLSurfaceView
import android.view.animation.LinearInterpolator
import android.os.Build
import java.util.Locale

class AtmosphereService : GLWallpaperService() {

    override fun onCreateEngine(): Engine {
        return AtmosphereEngine()
    }

    override fun getRenderer(): GLSurfaceView.Renderer {
        return AtmosphereRenderer(applicationContext)
    }

    inner class AtmosphereEngine : GLEngine() {
        private var pollInterval: Long = 50L
        private var lockDelay: Long = 0L
        private var animDuration: Long = 2500L
        private var myRenderer: AtmosphereRenderer? = null
        private var blurAnimator: ValueAnimator? = null
        private var isLocked: Boolean = true
//        private val isSamsungDevice: Boolean
//            get() {
//                val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
//                val brand = Build.BRAND.lowercase(Locale.ROOT)
//                return manufacturer.contains("samsung") || brand.contains("samsung")
//            }

        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

        private val resetRunnable = Runnable {
            prepareForNextUnlock()
        }
        private val unlockChecker = object : Runnable {
            override fun run() {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
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

            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            isLocked = keyguardManager.isKeyguardLocked

            val r = getRenderer()
            if (r is AtmosphereRenderer) {
                myRenderer = r
//                myRenderer?.isSamsung = isSamsungDevice
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
            try {
                unregisterReceiver(systemEventReceiver)
            } catch (e: Exception) { }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
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

        private fun updateRendererConfig() {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val dim = prefs.getFloat("dim_level", 0.2f)
            myRenderer?.dimLevel = dim

            // New Advanced Settings (Default to -1 to detect "not set")
            val savedPoll = prefs.getLong("poll_interval", -1L)
            val savedDelay = prefs.getLong("lock_delay", -1L)
            val savedDuration = prefs.getLong("anim_duration", -1L)

            pollInterval = if (savedPoll != -1L) savedPoll else 50L
            lockDelay = if (savedDelay != -1L) savedDelay else 0L
            animDuration = if (savedDuration != -1L) savedDuration else 2500L
        }
    }
}