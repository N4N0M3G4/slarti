package com.georecon.app.scan

import android.app.Activity
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

/** Live state for the HUD, posted on every processed frame. */
data class ScanState(
    val coach: Coach, val kept: Int, val side: Int, val cellsDone: Int, val cellsTotal: Int,
    val filled: BooleanArray, val azimuthDeg: Double, val elevationDeg: Double, val progress: Double,
    val totalFrames: Int, val tracking: Boolean, val resolution: String,
)

/**
 * ARCore motion tracking (no cloud/Geospatial) + camera preview + auto-capture.
 * JPEGs are encoded and written on one background thread as they come, so memory
 * stays bounded: if the writer is busy, the frame is skipped.
 */
class ArScanEngine(
    private val activity: Activity,
    private val store: ScanStore,
    private val onState: (ScanState) -> Unit,
    private val onCapture: () -> Unit,
) : GLSurfaceView.Renderer {
    val view: GLSurfaceView = GLSurfaceView(activity).apply {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setRenderer(this@ArScanEngine)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }
    private var session: Session? = null
    val brain = ScanBrain()
    var side = 1; private set
    var frameNo = 0; private set
    val sides = mutableListOf<SideRange>()
    private var sideFirst = 1
    private var center: DoubleArray? = null
    private var lastPose: Pose? = null
    private var lastPoseNs = 0L
    private var lastProcessNs = 0L
    private val writer = Executors.newSingleThreadExecutor()
    private val writing = AtomicBoolean(false)
    @Volatile var running = false
    private var resolution = ""

    /** Returns an error message, or null when ARCore is ready. */
    fun resume(): String? {
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(activity, true)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> return "Installing Google Play Services for AR…"
                    else -> {}
                }
                val s = Session(activity)
                // Highest CPU image resolution the device offers for ARCore.
                val configs = s.getSupportedCameraConfigs(CameraConfigFilter(s))
                configs.maxByOrNull { it.imageSize.width * it.imageSize.height }?.let { s.cameraConfig = it }
                resolution = s.cameraConfig.imageSize.let { "${it.width}x${it.height}" }
                s.configure(Config(s).apply {
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.DISABLED
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                })
                session = s
            } catch (e: Exception) {
                return "ARCore unavailable on this device: ${e.javaClass.simpleName}"
            }
        }
        session?.resume()
        view.onResume()
        return null
    }

    fun pause() { view.onPause(); session?.pause() }
    fun close() { running = false; session?.close(); session = null; writer.shutdown() }

    /** Start (or restart) orbiting: object centre = 30 cm in front of the camera now. */
    fun start() { center = null; running = true }

    fun flip() {
        if (frameNo >= sideFirst) sides += SideRange(side, sideFirst, frameNo)
        side++; sideFirst = frameNo + 1; brain.newSide(); center = null
    }

    fun finishSides(): List<SideRange> {
        if (frameNo >= sideFirst) sides += SideRange(side, sideFirst, frameNo)
        sideFirst = frameNo + 1
        return sides.toList()
    }

    // --- GL ---
    private var tex = 0; private var program = 0
    private val quad: FloatBuffer = floatBuf(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uv: FloatBuffer = floatBuf(FloatArray(8))
    private var rotation = Surface.ROTATION_0
    private var uvReady = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        program = link(
            "attribute vec4 p; attribute vec2 t; varying vec2 v; void main(){gl_Position=p; v=t;}",
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES s; varying vec2 v; void main(){gl_FragColor=texture2D(s,v);}",
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        @Suppress("DEPRECATION")
        rotation = activity.windowManager.defaultDisplay.rotation
        session?.setDisplayGeometry(rotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val s = session ?: return
        s.setCameraTextureName(tex)
        val frame = try { s.update() } catch (e: Exception) { return }
        if (frame.hasDisplayGeometryChanged() || !uvReady) {
            uvReady = true
            val out = FloatArray(8)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
                Coordinates2d.TEXTURE_NORMALIZED, out)
            uv.position(0); uv.put(out); uv.position(0)
        }
        drawBackground()
        if (!running) return
        val now = frame.timestamp
        if (now - lastProcessNs < 120_000_000L) return // ~8 checks per second
        lastProcessNs = now
        val cam = frame.camera
        val tracking = cam.trackingState == TrackingState.TRACKING
        val pose = cam.pose
        val rate = lastPose?.let { angleDeg(it, pose) / ((now - lastPoseNs) / 1e9).coerceAtLeast(1e-3) } ?: 0.0
        lastPose = pose; lastPoseNs = now
        if (tracking && center == null) {
            center = pose.compose(Pose.makeTranslation(0f, 0f, -0.30f)).translation.map { it.toDouble() }.toDoubleArray()
        }
        val c = center ?: run {
            val cov = brain.coverage
            onState(ScanState(Coach.LOST, brain.kept, side, cov.doneCount, cov.total, BooleanArray(cov.total) { cov.done(it) },
                0.0, 0.0, brain.progress(), frameNo, tracking, resolution))
            return
        }
        val camPos = pose.translation.map { it.toDouble() }.toDoubleArray()
        val v = viewOf(camPos, c)
        val fwd = FloatArray(3).also { pose.getTransformedAxis(2, -1f, it, 0) } // camera looks down -Z
        val toC = doubleArrayOf(c[0] - camPos[0], c[1] - camPos[1], c[2] - camPos[2])
        val off = angleBetween(fwd.map { it.toDouble() }.toDoubleArray(), toC)
        var sharp = 0.0; var luma = 0.0; var nv21: ByteArray? = null; var w = 0; var h = 0
        try {
            frame.acquireCameraImage().use { img ->
                w = img.width; h = img.height
                val yPlane = img.planes[0]
                val ybytes = ByteArray(yPlane.buffer.remaining()).also { yPlane.buffer.get(it) }
                val step = (w / 640).coerceAtLeast(1)
                sharp = laplacianVariance(ybytes, w, h, yPlane.rowStride, step)
                luma = meanLuma(ybytes, w, h, yPlane.rowStride)
                val check = FrameCheck(tracking, v, rate, sharp, luma, off)
                if (!writing.get() && brain.offer(check)) nv21 = toNv21(img, ybytes)
            }
        } catch (e: Exception) { /* image not ready yet */ }
        val check = FrameCheck(tracking, v, rate, sharp, luma, off)
        val data = nv21
        if (data != null) {
            frameNo++
            val intr = cam.imageIntrinsics
            val f = intr.focalLength; val pp = intr.principalPoint; val dim = intr.imageDimensions
            val meta = FrameMeta(frameNo, side, now, w, h, f[0], f[1], pp[0], pp[1], dim[0], dim[1],
                pose.translation, pose.rotationQuaternion, cam.trackingState.name, sharp)
            writing.set(true)
            writer.execute {
                try {
                    val out = ByteArrayOutputStream()
                    YuvImage(data, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 92, out)
                    store.writeFrame(meta, out.toByteArray())
                } finally { writing.set(false) }
            }
            onCapture()
        }
        val cov = brain.coverage
        onState(ScanState(brain.coach(check), brain.kept, side, cov.doneCount, cov.total,
            BooleanArray(cov.total) { cov.done(it) }, v.azimuthDeg, v.elevationDeg, brain.progress(), frameNo, tracking, resolution))
    }

    private fun drawBackground() {
        GLES20.glUseProgram(program)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        val p = GLES20.glGetAttribLocation(program, "p"); val t = GLES20.glGetAttribLocation(program, "t")
        quad.position(0); uv.position(0)
        GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 0, quad); GLES20.glEnableVertexAttribArray(p)
        GLES20.glVertexAttribPointer(t, 2, GLES20.GL_FLOAT, false, 0, uv); GLES20.glEnableVertexAttribArray(t)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    companion object {
        private fun floatBuf(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

        private fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        private fun link(vs: String, fs: String) = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(it)
        }

        fun angleDeg(a: Pose, b: Pose): Double {
            val qa = a.rotationQuaternion; val qb = b.rotationQuaternion
            val dot = abs(qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3]).toDouble().coerceAtMost(1.0)
            return Math.toDegrees(2 * acos(dot))
        }

        fun angleBetween(a: DoubleArray, b: DoubleArray): Double {
            val na = sqrt(a.sumOf { it * it }); val nb = sqrt(b.sumOf { it * it })
            if (na == 0.0 || nb == 0.0) return 180.0
            return Math.toDegrees(acos(((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1.0, 1.0)))
        }

        /** YUV_420_888 -> NV21 (handles row and pixel strides). */
        fun toNv21(img: Image, ybytes: ByteArray): ByteArray {
            val w = img.width; val h = img.height
            val out = ByteArray(w * h * 3 / 2)
            val yp = img.planes[0]
            for (row in 0 until h) System.arraycopy(ybytes, row * yp.rowStride, out, row * w, w)
            val u = img.planes[1]; val v = img.planes[2]
            val ub = u.buffer; val vb = v.buffer
            var o = w * h
            for (row in 0 until h / 2) for (col in 0 until w / 2) {
                val vi = row * v.rowStride + col * v.pixelStride; val ui = row * u.rowStride + col * u.pixelStride
                out[o++] = vb.get(vi); out[o++] = ub.get(ui)
            }
            return out
        }
    }
}
