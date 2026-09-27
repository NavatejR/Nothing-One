package com.nothing.one.ui.orb

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Kotlin port of orbkit-core (https://github.com/zzzzshawn/orbkit), MIT.
 * The volume synthesis, critically damped spring and integrated-clock model
 * match the TypeScript engine line for line so the orb behaves exactly as it
 * does on the web. Two states are added for Journal's voice flow: LISTENING
 * (the user is dictating) and SLEEPING (a dimmed, near-still idle).
 */

/** Agent states the orb renders. */
enum class OrbState(val label: String) {
    IDLE("IDLE"),
    LISTENING("LISTENING"),
    THINKING("THINKING"),
    SPEAKING("SPEAKING"),
    SLEEPING("SLEEPING"),
}

fun clamp01(n: Float): Float = n.coerceIn(0f, 1f)

/**
 * Per-state [input, output] volume synthesis. Input is user speech energy,
 * output is agent speech energy.
 */
fun targetVolumes(state: OrbState, t: Double): Pair<Float, Float> = when (state) {
    OrbState.IDLE -> 0f to 0.3f
    OrbState.LISTENING ->
        // The user is talking: restless input, quiet output. The shader's own
        // uInput coupling deepens the waves on top of this.
        clamp01((0.45 + 0.25 * sin(t * 3.1)).toFloat()) to 0.12f
    OrbState.THINKING -> {
        val base = 0.38 + 0.07 * sin(t * 0.7)
        val wander = 0.05 * sin(t * 2.1) * sin(t * 0.37 + 1.2)
        clamp01((base + wander).toFloat()) to clamp01((0.48 + 0.12 * sin(t * 1.05 + 0.6)).toFloat())
    }
    OrbState.SPEAKING ->
        clamp01((0.65 + 0.22 * sin(t * 4.8)).toFloat()) to
            clamp01((0.75 + 0.22 * sin(t * 3.6)).toFloat())
    OrbState.SLEEPING -> 0f to 0.08f
}

/**
 * Transition rate shared by params, colours and the flow-speed multiplier.
 * Drives a critically damped spring: a state change starts at rest and
 * accelerates, so the look cross-fades instead of lurching.
 */
const val PARAM_EASE = 4f

/** One implicit step of a critically damped spring (never explodes on long frames). */
internal class Spring(var x: Float, var v: Float) {
    fun step(target: Float, dt: Float, omega: Float) {
        val f = 1f + 2f * dt * omega
        val oo = omega * omega
        val hoo = dt * oo
        val hhoo = dt * hoo
        val detInv = 1f / (f + hhoo)
        val nx = (f * x + dt * v + hhoo * target) * detInv
        val nv = (v + hoo * (target - x)) * detInv
        x = nx
        v = nv
    }
}

class OrbParamDef(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val default: Float,
    /**
     * Rate params are integrated into a clock (`clock += dt * value * speed`)
     * and the clock is uploaded, so changing the rate never jumps the phase.
     */
    val integrate: Boolean = false,
)

class OrbColorDef(val key: String, val label: String, val defaultHex: String)

/** One frame of uniform values produced by [OrbEngine.step]. */
class OrbFrame(
    val time: Float,
    val anim: Float,
    val input: Float,
    val output: Float,
    val params: Map<String, Float>,
    val colors: Map<String, FloatArray>,
)

/**
 * The orbkit driver state machine. Framework-free: [step] is called once per
 * rendered frame and returns the uniforms to upload.
 */
class OrbEngine(
    private val params: List<OrbParamDef>,
    private val colors: List<OrbColorDef>,
    private val statePresets: Map<OrbState, Map<String, Float>>,
    private val stateColors: Map<OrbState, Map<String, String>>,
) {
    /** Active state; safe to set from any thread (read on the GL thread). */
    @Volatile
    var state: OrbState = OrbState.IDLE

    /** Pin the synthesized input volume (e.g. live mic level) when non-null. */
    @Volatile
    var pinnedInput: Float? = null

    /** Pin the synthesized output volume (e.g. TTS energy) when non-null. */
    @Volatile
    var pinnedOutput: Float? = null

    @Volatile
    var paused: Boolean = false

    private var tSec = 0f
    private var anim = Random.nextFloat() * 100f
    private var speed = Spring(0.1f, 0f)
    private val volumeIn = Spring(0f, 0f)
    private val volumeOut = Spring(0.3f, 0f)
    private val paramCur = HashMap<String, Float>()
    private val paramVel = HashMap<String, Spring>()
    private val paramClocks = HashMap<String, Float>()
    private val colorCur = HashMap<String, FloatArray>()
    private val colorVel = HashMap<String, FloatArray>()
    private var initialized = false

    private fun hexToRgb(hex: String): FloatArray {
        val h = hex.removePrefix("#").trim()
        val n = h.toLongOrNull(16) ?: return floatArrayOf(1f, 1f, 1f)
        if (h.length != 6) return floatArrayOf(1f, 1f, 1f)
        return floatArrayOf(
            ((n shr 16) and 0xFF).toInt() / 255f,
            ((n shr 8) and 0xFF).toInt() / 255f,
            (n and 0xFF).toInt() / 255f,
        )
    }

    private fun init() {
        if (initialized) return
        val (i, o) = targetVolumes(state, 0.0)
        volumeIn.x = i
        volumeOut.x = o
        paramCur.clear()
        params.forEach { paramCur[it.key] = it.default }
        initialized = true
    }

    /**
     * Advance the engine by [dt] seconds and produce the uniforms to draw.
     * When [snap] is true every value lands exactly on its target (used for
     * the reduced-motion single frame), otherwise values glide on springs.
     */
    fun step(dt: Float, snap: Boolean = false): OrbFrame {
        init()
        if (snap) tSec = 1f else tSec += dt

        val (tin, tout) = targetVolumes(state, tSec.toDouble())
        val targetIn = pinnedInput ?: tin
        val targetOut = pinnedOutput ?: tout
        if (snap) {
            volumeIn.x = targetIn; volumeIn.v = 0f
            volumeOut.x = targetOut; volumeOut.v = 0f
        } else {
            val kVol = 1f - kotlin.math.exp(-dt * 12f)
            volumeIn.x += (targetIn - volumeIn.x) * kVol
            volumeOut.x += (targetOut - volumeOut.x) * kVol
        }

        // Flow speed follows the output volume, eased on the same spring as
        // the params so a state change ramps its motion over the same window
        // its look cross-fades.
        val targetSpeed = 0.1f + (1f - (volumeOut.x - 1f).pow(2)) * 0.9f
        if (snap) {
            speed.x = targetSpeed; speed.v = 0f
        } else {
            speed.step(targetSpeed, dt, PARAM_EASE)
        }
        anim += dt * speed.x

        val frameParams = HashMap<String, Float>(params.size)
        val statePreset = statePresets[state]
        for (def in params) {
            val target = statePreset?.get(def.key) ?: def.default
            val spring = paramVel.getOrPut(def.key) {
                Spring(paramCur[def.key] ?: target, 0f)
            }
            val curVal = paramCur[def.key] ?: target
            val next: Float
            if (snap) {
                spring.x = target; spring.v = 0f
                next = target
            } else {
                spring.x = curVal
                spring.step(target, dt, PARAM_EASE)
                next = spring.x
            }
            paramCur[def.key] = next

            if (def.integrate) {
                val clock = (paramClocks[def.key] ?: Random.nextFloat() * 100f) + dt * speed.x * next
                paramClocks[def.key] = clock
                frameParams[def.key] = clock
            } else {
                frameParams[def.key] = next
            }
        }

        val frameColors = HashMap<String, FloatArray>(colors.size)
        val stateColor = stateColors[state]
        for (def in colors) {
            val target = hexToRgb(stateColor?.get(def.key) ?: def.defaultHex)
            val cur = colorCur.getOrPut(def.key) { target.copyOf() }
            val vel = colorVel.getOrPut(def.key) { FloatArray(3) }
            if (snap) {
                target.copyInto(cur)
                vel.fill(0f)
            } else {
                for (i in 0..2) {
                    val s = Spring(cur[i], vel[i])
                    s.step(target[i], dt, PARAM_EASE)
                    cur[i] = s.x
                    vel[i] = s.v
                }
            }
            frameColors[def.key] = cur
        }

        return OrbFrame(
            time = tSec * 0.5f,
            anim = anim,
            input = volumeIn.x,
            output = volumeOut.x,
            params = frameParams,
            colors = frameColors,
        )
    }
}

/*
 * SHDR-14 — demoscene sine-plasma on a rolling dome, quantized to chunky
 * two-tone pixels. Ported from orbkit shdr-14 (MIT): the luminance is the
 * classic demo plasma — three interfering sine waves — evaluated on the
 * sphere's rotating dome point, plus a ripple source that orbits the dome.
 * An 8x8 ordered Bayer dither snaps it onto a short tone ladder between two
 * colours, ink and paper. At 2 tone steps it is the classic 1-bit look.
 */
object Shdr14 {

    val params = listOf(
        OrbParamDef("speed", "Wave speed", 0.015f, 10f, 0.05f, 0.5f, integrate = true),
        OrbParamDef("spin", "Roll", 0f, 5f, 0.03f, 0.15f, integrate = true),
        OrbParamDef("radius", "Radius", 0.15f, 3f, 0.015f, 0.9f),
        OrbParamDef("cells", "Grid cells", 32f, 320f, 2f, 140f),
        OrbParamDef("levels", "Tone steps", 2f, 8f, 1f, 3f),
        OrbParamDef("scale", "Wave scale", 0.3f, 12f, 0.1f, 1.5f),
        OrbParamDef("plasma", "Wave amount", 0f, 3f, 0.015f, 0.9f),
        OrbParamDef("light", "Key light", 0f, 3f, 0.015f, 0.9f),
        OrbParamDef("rim", "Rim light", 0f, 3f, 0.015f, 0.35f),
        OrbParamDef("gain", "Brightness", 0.05f, 5f, 0.05f, 1f),
        OrbParamDef("contrast", "Contrast", 0.15f, 10f, 0.05f, 1.1f),
    )

    val colors = listOf(
        OrbColorDef("ink", "Ink", "#101426"),
        OrbColorDef("paper", "Paper", "#cfe6ff"),
    )

    /*
     * Staged in the family language: thinking churns the plasma in place while
     * the light freezes, speaking sweeps the light fast and brightens the
     * ladder. `cells` and `levels` never move between states — both quantize,
     * and a gliding quantizer pops instead of fading.
     */
    val statePresets: Map<OrbState, Map<String, Float>> = mapOf(
        OrbState.IDLE to mapOf(
            "speed" to 0.5f, "spin" to 0.15f, "plasma" to 0.9f,
            "gain" to 1f, "contrast" to 1.1f,
        ),
        OrbState.LISTENING to mapOf(
            "speed" to 0.7f, "spin" to 0.15f, "plasma" to 1.05f,
            "gain" to 1.05f, "contrast" to 1.1f,
        ),
        OrbState.THINKING to mapOf(
            "speed" to 1.6f, "spin" to 0.05f, "plasma" to 1.15f,
            "gain" to 0.95f, "contrast" to 1.15f,
        ),
        OrbState.SPEAKING to mapOf(
            "speed" to 1.3f, "spin" to 0.8f, "plasma" to 1f,
            "gain" to 1.3f, "contrast" to 1.05f,
        ),
        OrbState.SLEEPING to mapOf(
            "speed" to 0.25f, "spin" to 0.06f, "plasma" to 0.7f,
            "gain" to 0.6f, "contrast" to 1.2f,
        ),
    )

    // Ink/paper carry the at-a-glance read: cool print at rest, teal while
    // listening, violet-blue while computing, warm amber while answering,
    // near-dead grey while sleeping.
    val stateColors: Map<OrbState, Map<String, String>> = mapOf(
        OrbState.IDLE to mapOf("ink" to "#101426", "paper" to "#cfe6ff"),
        OrbState.LISTENING to mapOf("ink" to "#0f2026", "paper" to "#b8e6e0"),
        OrbState.THINKING to mapOf("ink" to "#140f38", "paper" to "#a9b9ff"),
        OrbState.SPEAKING to mapOf("ink" to "#2a1410", "paper" to "#ffd9a4"),
        OrbState.SLEEPING to mapOf("ink" to "#0a0c10", "paper" to "#3a4048"),
    )

    val engine: OrbEngine
        get() = newEngine()

    /** A fresh, independent engine per orb instance on screen. */
    fun newEngine(): OrbEngine = OrbEngine(params, colors, statePresets, stateColors)

    /** Fragment shader. GLSL ES 1.0 — no arrays, no bitwise. */
    const val FRAG: String = """
// 2x2 Bayer base: floor/fract only. (0,0)=0, (1,0)=.5, (0,1)=.75, (1,1)=.25
// — the 0,2,3,1 ordering over 4.
float bayer2(vec2 a) {
  a = floor(a);
  return fract(a.x / 2.0 + a.y * a.y * 0.75);
}

// 8x8 by recursion: M8 = M2(a/4)/16 + M2(a/2)/4 + M2(a). No arrays, no
// bitwise — neither exists in GLSL ES 1.0.
float bayer8(vec2 a) {
  return bayer2(a * 0.25) * 0.0625 + bayer2(a * 0.5) * 0.25 + bayer2(a);
}

void main() {
  // Volume coupling: user input deepens the waves, agent output brightens
  // the whole tone ladder — the dot field visibly blooms while it speaks.
  float plasmaAmt = uP_plasma * (1.0 + 0.4 * uInput);
  float gainNow = uP_gain * (0.85 + 0.5 * uOutput);

  /*
    Chunky pixel grid, RESOLUTION-RELATIVE: uP_cells is how many cells span
    the canvas, so a 64px bubble and a 320px hero orb show the same
    composition — the same wave resolved by the same number of dots. All
    content below samples at the cell centre so every dot is one flat square.
  */
  float cellPx = max(min(uRes.x, uRes.y) / max(uP_cells, 8.0), 1.0);
  vec2 pix = floor(gl_FragCoord.xy / cellPx);
  vec2 cellCentre = (pix + 0.5) * cellPx;

  vec2 suv = (2.0 * cellCentre - uRes) / min(uRes.x, uRes.y);
  vec2 uv = suv / uP_radius;
  float r2 = dot(uv, uv);

  // blocky silhouette — cut on the cell grid, deliberately not smoothed
  float mask = 1.0 - step(1.0, r2);

  float z = sqrt(max(1.0 - r2, 0.0));
  vec3 n = vec3(uv, z);

  /*
    The plasma is evaluated in a ROTATING frame: the dome point spins about
    Y on its own integrated clock, so the wavefronts roll around the ball
    instead of sliding across a flat disc. The light stays screen-fixed —
    the form shading holds still while the pattern travels over it.
  */
  float rot = uP_spin; // integrated clock
  float cr = cos(rot);
  float sr = sin(rot);
  vec3 sp = vec3(n.x * cr - n.z * sr, n.y, n.x * sr + n.z * cr);

  float t = uP_speed; // integrated clock

  // the classic demoscene plasma: three interfering sine waves, each on its
  // own direction and rate
  float f = uP_scale;
  float v = sin(sp.x * f * 3.1 + t)
    + sin((sp.y * 0.85 + sp.z * 0.4) * f * 3.6 - t * 1.3)
    + sin((sp.x + sp.y + sp.z) * f * 2.2 + t * 0.7);

  // a ripple source orbiting the dome — expanding rings pushed through the
  // interference; the clock enters only as additive phase
  vec2 src = 0.55 * vec2(cos(t * 0.5), sin(t * 0.5));
  v += sin(length(uv - src) * f * 5.0 - t * 2.2);
  v *= 0.25; // four unit waves back to -1..1

  float lambert = clamp(dot(n, normalize(vec3(-0.45, 0.55, 0.7))), 0.0, 1.0);
  float fres = pow(1.0 - z, 2.0);

  // waves modulated by the dome shading, so the ball stays a ball under
  // the rolling pattern; everything collapses into one luminance
  float lum = (0.5 + 0.5 * v * plasmaAmt) * (0.3 + uP_light * lambert)
    + uP_rim * fres;
  lum = pow(clamp(lum * gainNow, 0.0, 1.0), uP_contrast);

  // ordered dither onto the tone ladder — levels 2 is the classic 1-bit
  // look, higher values keep the grain but add mid-tones
  float steps = max(uP_levels - 1.0, 1.0);
  float q = clamp(floor(lum * steps + bayer8(pix)) / steps, 0.0, 1.0);

  vec3 col = mix(uC_ink, uC_paper, q);

  // Surface-lit orb bounded by a mask: alpha IS coverage, so premultiply.
  float a = mask;
  gl_FragColor = vec4(col * a, a);
}
"""
}
