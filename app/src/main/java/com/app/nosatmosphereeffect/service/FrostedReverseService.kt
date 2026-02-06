package com.app.nosatmosphereeffect.service

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.animation.LinearInterpolator
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.renderer.FrostedRenderer

class FrostedReverseService : GLWallpaperService() {

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

        private val resetRunnable = Runnable { prepareForNextUnlock() }
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
                myRenderer?.blurStrength = 1.0f // Default Locked state is Blurred
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
                    myRenderer?.blurStrength = 1.0f // Locked = Blurred
                    requestRender()
                } else {
                    snapToHomeState()
                }
            }
        }

        private fun playUnlockAnimation() {
            val targetRenderer = myRenderer ?: return
            blurAnimator?.cancel()
            targetRenderer.blurStrength = 1.0f
            requestRender()

            // Reverse: 1.0 (Blur) -> 0.0 (Sharp)
            blurAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
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
            myRenderer?.blurStrength = 0.0f // Unlocked = Sharp
            requestRender()
        }

        private fun prepareForNextUnlock() {
            myRenderer?.blurStrength = 1.0f // Reset to Blurred
            requestRender()
        }

        private fun updateRendererConfig() {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            myRenderer?.dimLevel = prefs.getFloat("dim_level", 0.2f)
            myRenderer?.enableNoise = prefs.getBoolean("enable_noise", false)
            myRenderer?.noiseScale = prefs.getFloat("noise_scale", 2000.0f)
            myRenderer?.noiseStrength = prefs.getFloat("noise_strength", 0.06f)

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