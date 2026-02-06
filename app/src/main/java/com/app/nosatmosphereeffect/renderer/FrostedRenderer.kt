package com.app.nosatmosphereeffect.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.core.graphics.createBitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

class FrostedRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // Animation State
    @Volatile var blurStrength: Float = 0.0f

    // User Settings
    @Volatile var dimLevel: Float = 0.2f
    @Volatile var blurRadius: Float = 200f // Controlled by Slider
    @Volatile var enableNoise: Boolean = false
    @Volatile var noiseScale: Float = 2000.0f
    @Volatile var noiseStrength: Float = 0.06f

    @Volatile private var needsReload: Boolean = false

    private var programId: Int = 0
    private var blurProgramId: Int = 0
    private var sharpTextureId: Int = 0
    private var blurTextureId: Int = 0
    private var tempTextureId: Int = 0
    private var fboId: Int = 0
    private var aspectRatio: Float = 1.0f

    private val vertices = floatArrayOf(
        -1f, -1f,  0f, 1f,
        1f, -1f,  1f, 1f,
        -1f,  1f,  0f, 0f,
        1f,  1f,  1f, 0f
    )
    private lateinit var vertexBuffer: FloatBuffer

    fun reloadTexture() {
        needsReload = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        // Reuse existing vertex shader, use new frosted fragment shader
        val vertexCode = loadShaderFromAssets("shaders/frostedBlur/frosted.vert")
        val fragmentCode = loadShaderFromAssets("shaders/frostedBlur/frosted.frag")
        programId = createProgram(vertexCode, fragmentCode)

        val blurFragCode = """
            #version 300 es
            precision highp float;
            in vec2 vTexCoord;
            out vec4 fragColor;
            uniform sampler2D uTexture;
            uniform vec2 uDirection;
            uniform float uRadius;
            void main() {
                vec2 texelSize = 1.0 / vec2(textureSize(uTexture, 0));
                vec3 result = vec3(0.0);
                float totalWeight = 0.0;
                // Limit samples for performance, scale offset by radius
                float samples = clamp(uRadius / 2.0, 5.0, 40.0); 
                for(float i = -samples; i <= samples; i++) {
                    float t = i / samples;
                    vec2 offset = uDirection * t * (uRadius * 0.01); 
                    float weight = 1.0 - abs(t);
                    result += texture(uTexture, vTexCoord + offset).rgb * weight;
                    totalWeight += weight;
                }
                fragColor = vec4(result / totalWeight, 1.0);
            }
        """.trimIndent()
        blurProgramId = createProgram(vertexCode, blurFragCode)

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        fboId = fbo[0]

        loadAndApplyTextures()
    }

    private fun loadAndApplyTextures() {
        if (sharpTextureId != 0) {
            val ids = intArrayOf(sharpTextureId, blurTextureId, tempTextureId)
            GLES30.glDeleteTextures(3, ids, 0)
        }

        val sharpBitmap = loadFixedWallpaper()
        sharpTextureId = uploadTexture(sharpBitmap)
        tempTextureId = createEmptyTexture(sharpBitmap.width, sharpBitmap.height)

        // Use the configurable blurRadius here
        // If radius is 0, we can just use the sharp texture, but simpler to just process a minimal blur
        val safeRadius = if (blurRadius < 1f) 1f else blurRadius

        val blurredTextureId = gpuBlur(sharpTextureId, sharpBitmap.width, sharpBitmap.height, safeRadius)

        blurTextureId = blurredTextureId
        sharpBitmap.recycle()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        if (needsReload) {
            needsReload = false
            loadAndApplyTextures()
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(programId)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uBlurStrength"), blurStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uDimLevel"), dimLevel)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uEnableNoise"), if (enableNoise) 1.0f else 0.0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uNoiseScale"), noiseScale)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uNoiseStrength"), noiseStrength)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sharpTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uTextureSharp"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uTextureBlur"), 1)

        val aPosLoc = GLES30.glGetAttribLocation(programId, "aPosition")
        val aTexLoc = GLES30.glGetAttribLocation(programId, "aTexCoord")
        drawQuad(aPosLoc, aTexLoc)
    }

    // --- Helper functions (same as AtmosphereRenderer) ---
    private fun createEmptyTexture(width: Int, height: Int): Int {
        val t = IntArray(1); GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    private fun gpuBlur(inputTexture: Int, width: Int, height: Int, radius: Float): Int {
        val outputTexture = createEmptyTexture(width, height)
        GLES30.glUseProgram(blurProgramId)
        val aPosLoc = GLES30.glGetAttribLocation(blurProgramId, "aPosition")
        val aTexLoc = GLES30.glGetAttribLocation(blurProgramId, "aTexCoord")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)

        // Pass 1: Horizontal
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tempTextureId, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blurProgramId, "uTexture"), 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(blurProgramId, "uDirection"), 1f, 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(blurProgramId, "uRadius"), radius)
        drawQuad(aPosLoc, aTexLoc)

        // Pass 2: Vertical
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, outputTexture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tempTextureId)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(blurProgramId, "uDirection"), 0f, 1f)
        drawQuad(aPosLoc, aTexLoc)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return outputTexture
    }

    private fun drawQuad(aPosLoc: Int, aTexLoc: Int) {
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, vertexBuffer)
        GLES30.glEnableVertexAttribArray(aPosLoc)
        vertexBuffer.position(2)
        GLES30.glVertexAttribPointer(aTexLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, vertexBuffer)
        GLES30.glEnableVertexAttribArray(aTexLoc)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(aPosLoc)
        GLES30.glDisableVertexAttribArray(aTexLoc)
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES30.glGenTextures(1, textureHandle, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        return textureHandle[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }

    private fun loadShaderFromAssets(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun loadFixedWallpaper(): Bitmap {
        val file = File(context.filesDir, "wallpaper.jpg")
        var rawBitmap: Bitmap? = null
        if (file.exists()) {
            try { rawBitmap = BitmapFactory.decodeFile(file.absolutePath) } catch (e: Exception) { e.printStackTrace() }
        }
        if (rawBitmap == null) {
            val color = Color.BLUE
            rawBitmap = createBitmap(1080, 1920)
            rawBitmap.eraseColor(color)
        }
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val width = rawBitmap.width
        val height = rawBitmap.height
        val targetW = screenWidth.coerceAtMost(1440)
        val targetH = (targetW.toFloat() / screenWidth * screenHeight).toInt()
        val finalBitmap = createBitmap(targetW, targetH)
        val canvas = Canvas(finalBitmap)
        val matrix = Matrix()
        val safeScale = max(targetW.toFloat() / width, targetH.toFloat() / height)
        matrix.postScale(safeScale, safeScale)
        matrix.postTranslate((targetW - width * safeScale) / 2f, (targetH - height * safeScale) / 2f)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(rawBitmap, matrix, paint)
        if (rawBitmap != finalBitmap) rawBitmap.recycle()
        return finalBitmap
    }
}