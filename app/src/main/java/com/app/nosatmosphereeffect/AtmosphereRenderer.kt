package com.app.nosatmosphereeffect

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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import androidx.core.graphics.createBitmap
import java.util.Random
import androidx.core.graphics.get

class AtmosphereRenderer(private val context: Context) : GLSurfaceView.Renderer {

    var blurStrength: Float = 0.0f
        set(value) {
            if (value == 0.0f && field != 0.0f) {
                reRollTargets()
            }
            field = value
        }

    var isSamsung: Boolean = false
    @Volatile var dimLevel: Float = 0.2f
    @Volatile private var needsReload: Boolean = false

    private var programId: Int = 0
    private var blurProgramId: Int = 0
    private var sharpTextureId: Int = 0
    private var blurTextureId: Int = 0
    private var tempTextureId: Int = 0
    private var fboId: Int = 0
    private var aspectRatio: Float = 1.0f

    data class BlobPhysics(
        val color: FloatArray,
        val startX: Float, val startY: Float,
        var p1x: Float, var p1y: Float,
        var endX: Float, var endY: Float,
        var startSize: Float,
        var endSize: Float,
        val massScale: Float
    )

    private val MAX_BLOBS = 16
    private var blobs = mutableListOf<BlobPhysics>()
    private val random = Random()

    private val blobColorsBuffer = FloatArray(MAX_BLOBS * 3)
    private val blobPosBuffer = FloatArray(MAX_BLOBS * 2)
    private val blobSizesBuffer = FloatArray(MAX_BLOBS)

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

        val vertexCode = loadShaderFromAssets("shaders/atmosphere.vert")
        val fragmentCode = loadShaderFromAssets("shaders/atmosphere.frag")
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
                for(float i = -uRadius; i <= uRadius; i++) {
                    vec2 offset = uDirection * i * texelSize;
                    float weight = 1.0 - abs(i) / uRadius;
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
        val blurredTextureId = gpuBlur(sharpTextureId, sharpBitmap.width, sharpBitmap.height, 200f)
        val blurredBitmap = downloadTexture(blurredTextureId, sharpBitmap.width, sharpBitmap.height)

        initBaseBlobs(blurredBitmap)

        blurTextureId = blurredTextureId
        sharpBitmap.recycle()
        blurredBitmap.recycle()
    }

    private fun initBaseBlobs(blurred: Bitmap) {
        val rawClusters = extractColorsFromBlurred(blurred, 16)
        blobs.clear()

        data class TempCluster(
            var r: Int, var g: Int, var b: Int,
            var x: Float, var y: Float,
            var count: Int
        )

        val tempClusters = rawClusters.map {
            TempCluster(Color.red(it.color), Color.green(it.color), Color.blue(it.color), it.centerX, it.centerY, 1)
        }.toMutableList()

        val mergedClusters = mutableListOf<TempCluster>()
        val processed = BooleanArray(tempClusters.size)

        for (i in tempClusters.indices) {
            if (processed[i]) continue
            val main = tempClusters[i]
            processed[i] = true

            for (j in i + 1 until tempClusters.size) {
                if (processed[j]) continue
                val other = tempClusters[j]

                val colorDist = hypot((main.r - other.r).toFloat(), (main.g - other.g).toFloat()) + abs(main.b - other.b)
                val spatialDist = hypot(main.x - other.x, main.y - other.y)

                if (colorDist < 40.0f && spatialDist < 0.25f) {
                    val totalCount = main.count + other.count
                    main.x = (main.x * main.count + other.x * other.count) / totalCount
                    main.y = (main.y * main.count + other.y * other.count) / totalCount
                    main.r = (main.r * main.count + other.r * other.count) / totalCount
                    main.g = (main.g * main.count + other.g * other.count) / totalCount
                    main.b = (main.b * main.count + other.b * other.count) / totalCount
                    main.count += other.count
                    processed[j] = true
                }
            }
            mergedClusters.add(main)
        }

        for (cluster in mergedClusters) {
            val clr = floatArrayOf(cluster.r / 255f, cluster.g / 255f, cluster.b / 255f)
            val massScale = min(1.4f, 1.0f + (cluster.count * 0.05f))

            blobs.add(BlobPhysics(
                color = clr,
                startX = cluster.x, startY = cluster.y,
                p1x = 0f, p1y = 0f, endX = 0f, endY = 0f,
                startSize = 0f, endSize = 0f,
                massScale = massScale
            ))
        }

        reRollTargets()
    }

    private fun reRollTargets() {
        for (blob in blobs) {
            blob.endX = 0.05f + random.nextFloat() * 0.9f
            blob.endY = 0.05f + random.nextFloat() * 0.9f

            val midX = (blob.startX + blob.endX) / 2f
            val midY = (blob.startY + blob.endY) / 2f
            blob.p1x = midX + (random.nextFloat() - 0.5f) * 0.5f
            blob.p1y = midY + (random.nextFloat() - 0.5f) * 0.5f

            val baseSize = 0.12f + random.nextFloat() * 0.08f
            val finalTargetSize = baseSize * blob.massScale

            blob.startSize = 0.01f
            blob.endSize = finalTargetSize
        }
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

        val t = blurStrength.coerceIn(0f, 1f)

        val physicsRaw = (t - 0.1f) / 0.9f
        val physicsT = physicsRaw.coerceIn(0f, 1f)

        // MOVEMENT CURVE: Fast Start, Slow Settle
        val progress = 1.0f - (1.0f - physicsT).pow(3)

        var idx = 0

        for (b in blobs) {
            if (idx >= MAX_BLOBS) break

            // Cubic Bezier Movement
            val u = 1.0f - progress
            val tt = progress * progress
            val uu = u * u
            val ut2 = 2 * u * progress

            val bx = (uu * b.startX) + (ut2 * b.p1x) + (tt * b.endX)
            val by = (uu * b.startY) + (ut2 * b.p1y) + (tt * b.endY)

            // Clean movement, no wobble
            val bSize = b.startSize + (b.endSize - b.startSize) * progress

            blobPosBuffer[idx * 2] = bx
            blobPosBuffer[idx * 2 + 1] = by
            blobSizesBuffer[idx] = bSize

            blobColorsBuffer[idx * 3] = b.color[0]
            blobColorsBuffer[idx * 3 + 1] = b.color[1]
            blobColorsBuffer[idx * 3 + 2] = b.color[2]

            idx++
        }

        if (idx > 0) {
            GLES30.glUniform3fv(GLES30.glGetUniformLocation(programId, "uBlobColors"), idx, blobColorsBuffer, 0)
            GLES30.glUniform2fv(GLES30.glGetUniformLocation(programId, "uBlobPositions"), idx, blobPosBuffer, 0)
            GLES30.glUniform1fv(GLES30.glGetUniformLocation(programId, "uBlobSizes"), idx, blobSizesBuffer, 0)
        }

        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uBlobCount"), idx)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uAspectRatio"), aspectRatio)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uBlurStrength"), blurStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uDimLevel"), dimLevel)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uIsSamsung"), if (isSamsung) 1.0f else 0.0f)

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
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tempTextureId, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blurProgramId, "uTexture"), 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(blurProgramId, "uDirection"), 1f, 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(blurProgramId, "uRadius"), radius)
        drawQuad(aPosLoc, aTexLoc)
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

    private fun downloadTexture(textureId: Int, width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, textureId, 0)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        val bitmap = createBitmap(width, height)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
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

    data class ColorCluster(val color: Int, val centerX: Float, val centerY: Float)
    data class ColorPoint(val color: Int, val x: Int, val y: Int)

    private fun extractColorsFromBlurred(blurred: Bitmap, targetColors: Int = 12): List<ColorCluster> {
        val w = blurred.width; val h = blurred.height
        val samples = mutableListOf<ColorPoint>()
        val step = 10
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                samples.add(ColorPoint(blurred.getPixel(x, y), x, y))
            }
        }
        val colorBuckets = medianCut(samples, targetColors)
        val colorClusters = mutableListOf<ColorCluster>()
        for (bucket in colorBuckets) {
            if (bucket.isEmpty()) continue
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var sumX = 0f; var sumY = 0f
            for (point in bucket) {
                sumR += Color.red(point.color); sumG += Color.green(point.color); sumB += Color.blue(point.color)
                sumX += point.x; sumY += point.y
            }
            val count = bucket.size
            val avgColor = Color.rgb((sumR/count).toInt(), (sumG/count).toInt(), (sumB/count).toInt())
            colorClusters.add(ColorCluster(avgColor, sumX/count/w, sumY/count/h))
        }
        return colorClusters
    }

    private fun medianCut(pixels: List<ColorPoint>, targetBuckets: Int): List<List<ColorPoint>> {
        val buckets = mutableListOf<MutableList<ColorPoint>>()
        buckets.add(pixels.toMutableList())
        while (buckets.size < targetBuckets) {
            var largestBucket: MutableList<ColorPoint>? = null; var largestRange = 0; var splitChannel = 0
            for (bucket in buckets) {
                if (bucket.size <= 1) continue
                val reds = bucket.map { Color.red(it.color) }; val greens = bucket.map { Color.green(it.color) }; val blues = bucket.map { Color.blue(it.color) }
                val rRange = (reds.maxOrNull()?:0) - (reds.minOrNull()?:0)
                val gRange = (greens.maxOrNull()?:0) - (greens.minOrNull()?:0)
                val bRange = (blues.maxOrNull()?:0) - (blues.minOrNull()?:0)
                val maxRange = maxOf(rRange, gRange, bRange)
                if (maxRange > largestRange) { largestRange = maxRange; largestBucket = bucket; splitChannel = if(maxRange==rRange) 0 else if(maxRange==gRange) 1 else 2 }
            }
            if (largestBucket == null) break
            val sorted = when(splitChannel) { 0 -> largestBucket.sortedBy { Color.red(it.color) }; 1 -> largestBucket.sortedBy { Color.green(it.color) }; else -> largestBucket.sortedBy { Color.blue(it.color) } }
            val median = sorted.size / 2
            buckets.remove(largestBucket)
            buckets.add(sorted.subList(0, median).toMutableList())
            buckets.add(sorted.subList(median, sorted.size).toMutableList())
        }
        return buckets
    }
}