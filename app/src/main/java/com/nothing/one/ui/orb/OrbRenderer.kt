package com.nothing.one.ui.orb

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.TextureView

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig as KhronosEGLConfig

private const val TAG = "Shdr14Orb"

/** Full-screen triangle — the orb is a fragment shader, the geometry is free. */
private const val VERT_SRC = "attribute vec2 aPos;\nvoid main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n"

/**
 * Prelude prepended to every orb fragment shader: uniforms and the value
 * noise / fbm helpers, mirroring ORB_GLSL_HELPERS in orbkit-core.
 */
private const val PRELUDE = """
precision highp float;
uniform vec2 uRes;
uniform float uTime;
uniform float uAnim;
uniform float uInput;
uniform float uOutput;
"""

private const val HELPERS = """
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }
float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
             mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.y),
             f.y);
}
float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 5; i++) {
    v += a * noise(p);
    p = p * 2.03 + vec2(11.7, 7.3);
    a *= 0.5;
  }
  return v;
}
vec2 orbUV() { return (2.0 * gl_FragCoord.xy - uRes) / min(uRes.x, uRes.y); }
"""

private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
        Log.e(TAG, "shader compile error: " + GLES20.glGetShaderInfoLog(shader))
        GLES20.glDeleteShader(shader)
        return 0
    }
    return shader
}

/**
 * Draws the shdr-14 orb by stepping [engine] once per frame and uploading the
 * resulting uniforms. Faithful to the orbkit render loop: dt is clamped at
 * 50ms so a stalled frame cannot jump the clocks, and uRes is uploaded
 * unconditionally — a rebuilt program starts with uRes = 0, and a gated
 * upload would leave a healthy context drawing a permanently blank orb.
 *
 * Framework-agnostic: driven by [OrbTextureView] (TextureView + EGL14), not
 * GLSurfaceView. TextureView composites through the normal view hierarchy, so
 * the orb fades with Compose transitions instead of lingering ~1s after the
 * screen changes the way a Z-order-on-top SurfaceView does.
 */
class OrbRenderer(private val engine: OrbEngine) {

    /** Reduced motion: draw one representative frame, snapped to targets, then stop. */
    @Volatile
    var reduceMotion: Boolean = false

    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var uRes = 0
    private var uTime = 0
    private var uAnim = 0
    private var uInput = 0
    private var uOutput = 0
    private val paramLocs = HashMap<String, Int>()
    private val colorLocs = HashMap<String, Int>()
    private var width = 1
    private var height = 1
    private var lastFrameNanos = 0L

    fun surfaceCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        buildProgram()
    }

    private fun buildProgram() {
        val uniformDecls = StringBuilder()
        Shdr14.params.forEach { uniformDecls.append("uniform float uP_").append(it.key).append(";\n") }
        Shdr14.colors.forEach { uniformDecls.append("uniform vec3 uC_").append(it.key).append(";\n") }
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERT_SRC)
        val fs = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            PRELUDE + uniformDecls + HELPERS + Shdr14.FRAG,
        )
        if (vs == 0 || fs == 0) return

        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "program link error: " + GLES20.glGetProgramInfoLog(prog))
            GLES20.glDeleteProgram(prog)
            return
        }
        program = prog
        GLES20.glUseProgram(prog)

        val vbb = ByteBuffer.allocateDirect(3 * 2 * 4).order(ByteOrder.nativeOrder())
        val vb = vbb.asFloatBuffer()
        vb.put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
        vb.position(0)
        vertexBuffer = vb

        val aPos = GLES20.glGetAttribLocation(prog, "aPos")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vb)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        uRes = GLES20.glGetUniformLocation(prog, "uRes")
        uTime = GLES20.glGetUniformLocation(prog, "uTime")
        uAnim = GLES20.glGetUniformLocation(prog, "uAnim")
        uInput = GLES20.glGetUniformLocation(prog, "uInput")
        uOutput = GLES20.glGetUniformLocation(prog, "uOutput")
        Shdr14.params.forEach { paramLocs[it.key] = GLES20.glGetUniformLocation(prog, "uP_" + it.key) }
        Shdr14.colors.forEach { colorLocs[it.key] = GLES20.glGetUniformLocation(prog, "uC_" + it.key) }

        // See class doc: a rebuilt program must learn the viewport size even
        // if surfaceChanged already ran for the old one.
        GLES20.glUniform2f(uRes, width.toFloat(), height.toFloat())
    }

    fun surfaceChanged(w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
        if (program != 0) GLES20.glUniform2f(uRes, w.toFloat(), h.toFloat())
    }

    fun drawFrame() {
        if (program == 0) return
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
        }
        lastFrameNanos = now
        val reduced = reduceMotion
        val frame = engine.step(if (reduced) 1f else dt, snap = reduced)

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUniform2f(uRes, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(uTime, frame.time)
        GLES20.glUniform1f(uAnim, frame.anim)
        GLES20.glUniform1f(uInput, frame.input)
        GLES20.glUniform1f(uOutput, frame.output)
        frame.params.forEach { (key, value) ->
            paramLocs[key]?.let { GLES20.glUniform1f(it, value) }
        }
        frame.colors.forEach { (key, c) ->
            colorLocs[key]?.let { GLES20.glUniform3f(it, c[0], c[1], c[2]) }
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    /** Releases GL objects; must run with the owning context current. */
    fun teardown() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        vertexBuffer = null
    }
}

/**
 * TextureView host for the orb: owns a private EGL context on a dedicated GL
 * thread and drives [OrbRenderer] through Choreographer. Unlike the old
 * GLSurfaceView (Z-order on top), this composites through the normal view
 * hierarchy — Compose transitions fade it out, scrims cover it, and teardown
 * happens the moment the surface is destroyed, so the orb never lingers on
 * screen after navigation.
 */
private class OrbTextureView(
    context: Context,
    private val engine: OrbEngine,
) : TextureView(context), TextureView.SurfaceTextureListener {

    private val glThread = HandlerThread("orb-gl").apply { start() }
    private val glHandler = Handler(glThread.looper)
    private val halted = AtomicBoolean(false)

    private var rendererRef: OrbRenderer? = null
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var choreographer: Choreographer? = null

    @Volatile
    private var reduceMotion = false

    private var looping = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!looping) return
            val renderer = rendererRef ?: return
            renderer.drawFrame()
            if (!swapBuffers()) {
                looping = false
                return
            }
            if (renderer.reduceMotion) {
                looping = false
            } else {
                choreographer?.postFrameCallback(this)
            }
        }
    }

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    /** Called from composition; safe from any thread, idempotent. */
    fun halt() {
        if (!halted.compareAndSet(false, true)) return
        glHandler.post {
            stopRender()
            glThread.quitSafely()
        }
    }

    fun setReduceMotion(value: Boolean) {
        if (reduceMotion == value) return
        reduceMotion = value
        glHandler.post {
            rendererRef?.reduceMotion = value
            if (value) {
                looping = false
                drawAndSwap()
            } else {
                ensureLooping()
            }
        }
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        glHandler.post { startRender(st, width, height) }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        glHandler.post { rendererRef?.surfaceChanged(width, height) }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        // Keep the texture alive until the GL thread has torn the surface
        // down; then release it ourselves (returning false promises this).
        glHandler.post {
            stopRender()
            st.release()
        }
        return false
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    private fun startRender(st: SurfaceTexture, w: Int, h: Int) {
        if (halted.get()) return
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) stopRender()

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY ||
            !EGL14.eglInitialize(display, IntArray(1), 0, IntArray(1), 0)
        ) {
            Log.e(TAG, "eglInitialize failed")
            return
        }
        eglDisplay = display

        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) ||
            numConfigs[0] == 0
        ) {
            Log.e(TAG, "eglChooseConfig failed")
            stopRender()
            return
        }
        eglContext = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed")
            stopRender()
            return
        }
        eglSurface = EGL14.eglCreateWindowSurface(
            display, configs[0], st, intArrayOf(EGL14.EGL_NONE), 0,
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE ||
            !EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)
        ) {
            Log.e(TAG, "egl surface/makeCurrent failed")
            stopRender()
            return
        }

        val renderer = OrbRenderer(engine).apply { reduceMotion = this@OrbTextureView.reduceMotion }
        rendererRef = renderer
        renderer.surfaceCreated()
        renderer.surfaceChanged(w, h)

        choreographer = Choreographer.getInstance()
        if (renderer.reduceMotion) {
            looping = false
            drawAndSwap()
        } else {
            ensureLooping()
        }
    }

    private fun ensureLooping() {
        if (!looping) {
            looping = true
            choreographer?.postFrameCallback(frameCallback)
        }
    }

    private fun drawAndSwap() {
        rendererRef?.drawFrame()
        swapBuffers()
    }

    private fun swapBuffers(): Boolean =
        eglDisplay != EGL14.EGL_NO_DISPLAY &&
            eglSurface != EGL14.EGL_NO_SURFACE &&
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

    private fun stopRender() {
        looping = false
        val display = eglDisplay
        if (display != EGL14.EGL_NO_DISPLAY) {
            rendererRef?.teardown()
            rendererRef = null
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            // No eglTerminate: the default display is process-global and other
            // renderers in this process share it.
        }
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        eglDisplay = EGL14.EGL_NO_DISPLAY
        choreographer = null
    }
}

/**
 * The orb as a Composable. Sized by [sizeDp]; the WebGL canvas is replaced by
 * a translucent GLES surface on a TextureView, so the square corners the
 * shader leaves empty show the parent background through.
 */
@Composable
fun ShaderOrb(
    state: OrbState,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    pinnedInput: Float? = null,
    pinnedOutput: Float? = null,
    reduceMotion: Boolean = false,
    contentDescription: String? = null,
) {
    val engine = remember { Shdr14.newEngine() }
    val orbLabel = contentDescription ?: state.label

    // State and volume changes never remount the surface — they just move
    // engine fields the render loop reads next frame, exactly like the
    // live-ref model in orbkit-core.
    LaunchedEffect(state) { engine.state = state }
    LaunchedEffect(pinnedInput) { engine.pinnedInput = pinnedInput }
    LaunchedEffect(pinnedOutput) { engine.pinnedOutput = pinnedOutput }

    Box(
        modifier = modifier
            .size(sizeDp)
            .semantics { this.contentDescription = orbLabel },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                OrbTextureView(ctx, engine).apply { setReduceMotion(reduceMotion) }
            },
            modifier = Modifier.size(sizeDp),
            onRelease = { it.halt() },
            update = { it.setReduceMotion(reduceMotion) },
        )
    }
}
