package com.lionstone.monkeyrapids

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural sound effects for MonkeySwim. Each effect is synthesized once at
 * init() into a 16-bit PCM ShortArray and uploaded into an AudioTrack in
 * MODE_STATIC, then re-triggered on demand via play(). No audio assets — every
 * sound is generated from math (sine + sawtooth + filtered noise + envelopes),
 * matching the project's procedural-canvas-everything aesthetic.
 *
 * One AudioTrack per effect. Re-triggering an effect that's still playing
 * restarts it from the beginning; for short overlapping sounds (e.g. rapid
 * pellet clicks) this clip-and-restart is the desired arcade feel. Different
 * effects can play simultaneously because each has its own AudioTrack.
 *
 * `enabled` is a persisted flag (mirrors Brick Basher's SoundManager) — when
 * false, play() short-circuits so the toggle is effectively a global mute.
 */
class SoundEngine {

    enum class Sfx {
        PELLET, FRUIT, LIGHTNING, BLACK_HOLE, SHARK, SLOW_PIRANHAS, EXTRA_LIFE,
        DEATH, PIRANHA_EATEN, PORTAL, LEVEL_COMPLETE, BAIT,
    }

    private val tracks = mutableMapOf<Sfx, AudioTrack>()
    private var appContext: Context? = null

    private var _enabled = true
    /** Global mute toggle. Persisted to SharedPreferences so the choice survives
     *  process restarts (matching Brick Basher's behaviour). */
    var enabled: Boolean
        get() = _enabled
        set(value) {
            _enabled = value
            appContext?.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_SOUND, value)?.apply()
        }

    /** Build + pre-load all sound buffers, and load the persisted mute state. */
    fun init(context: Context) {
        if (tracks.isNotEmpty()) return
        appContext = context.applicationContext
        _enabled = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND, true)
        tracks[Sfx.PELLET] = loadTrack(generatePellet())
        tracks[Sfx.FRUIT] = loadTrack(generateFruit())
        tracks[Sfx.LIGHTNING] = loadTrack(generateLightning())
        tracks[Sfx.BLACK_HOLE] = loadTrack(generateBlackHole())
        tracks[Sfx.SHARK] = loadTrack(generateShark())
        tracks[Sfx.SLOW_PIRANHAS] = loadTrack(generateSlowPiranhas())
        tracks[Sfx.EXTRA_LIFE] = loadTrack(generateExtraLife())
        tracks[Sfx.DEATH] = loadTrack(generateDeath())
        tracks[Sfx.PIRANHA_EATEN] = loadTrack(generatePiranhaEaten())
        tracks[Sfx.PORTAL] = loadTrack(generatePortal())
        tracks[Sfx.LEVEL_COMPLETE] = loadTrack(generateLevelComplete())
        tracks[Sfx.BAIT] = loadTrack(generateBait())
    }

    /** Trigger (or re-trigger) an effect. No-op if muted or not yet inited. */
    fun play(sfx: Sfx) {
        if (!_enabled) return
        val track = tracks[sfx] ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
            }
            track.reloadStaticData()
            track.play()
        } catch (_: IllegalStateException) {
            // Audio system can throw mid-state-change; swallow so a single bad
            // play() doesn't crash gameplay.
        }
    }

    fun release() {
        for (t in tracks.values) {
            try { t.release() } catch (_: Exception) { /* already released */ }
        }
        tracks.clear()
    }

    // ---------- AudioTrack plumbing ----------

    private fun loadTrack(pcm: ShortArray): AudioTrack {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    // ---------- PCM generators ----------

    /**
     * Crisp dry click — pure high-pass-filtered white noise with a very fast
     * decay envelope. No tonal content (the previous version's 2.2 kHz sine
     * read as "high piano key"); this should sound like a stick tap instead.
     */
    private fun generatePellet(): ShortArray {
        val ms = 22
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val rng = Lcg(0x12345678L)
        // First-order high-pass: y[n] = α · (y[n-1] + x[n] − x[n-1]).
        // α ≈ 0.92 puts the cutoff up around 2 kHz at 44.1 kHz, scrubbing
        // most low-frequency body so the result reads as a transient click.
        val alpha = 0.92f
        var prevIn = 0f
        var prevOut = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 320f)
            val noiseIn = rng.next() * 2f - 1f
            val hpOut = alpha * (prevOut + noiseIn - prevIn)
            prevIn = noiseIn
            prevOut = hpOut
            buf[i] = (hpOut * env * 0.7f).toClippedShort()
        }
        return buf
    }

    /** Three ascending sine tones (C5-E5-G5), ~85ms each, brief ADSR per note. */
    private fun generateFruit(): ShortArray =
        concatSineNotes(listOf(523.25f, 659.25f, 783.99f), noteMs = 85, amp = 0.45f)

    /** Zap: descending tone (4kHz→500Hz) with noise overlay, 280ms. */
    private fun generateLightning(): ShortArray {
        val ms = 280
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val rng = Lcg(0xABCD1234L)
        val total = ms / 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / total
            val freq = 4000f - 3500f * p
            val env = (1f - p) * (1f - exp(-t * 200f))
            val noise = rng.next() * 0.4f
            val tone = sin(t * freq * TAU)
            buf[i] = ((tone * 0.5f + noise) * env * 0.5f).toClippedShort()
        }
        return buf
    }

    /** Ominous sub-bass slide: 200Hz → ~45Hz exponential, with sub-octave, 650ms. */
    private fun generateBlackHole(): ShortArray {
        val ms = 650
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val total = ms / 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / total
            val freq = 200f * exp(-p * 1.5f)
            val env = (1f - exp(-t * 6f)) * (1f - p).coerceAtLeast(0f)
            val tone = sin(t * freq * TAU) * 0.7f
            val sub = sin(t * freq * 0.5f * TAU) * 0.3f
            buf[i] = ((tone + sub) * env * 0.55f).toClippedShort()
        }
        return buf
    }

    /** Low growl: sawtooth ~110Hz with mild pitch wobble + noise, 500ms. */
    private fun generateShark(): ShortArray {
        val ms = 500
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val rng = Lcg(0xDEADBEEFL)
        val total = ms / 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / total
            val freq = 110f + sin(t * 4f * TAU) * 8f
            val phase = (t * freq) - (t * freq).toInt()
            val saw = phase * 2f - 1f
            val noise = rng.next() * 0.2f
            val env = (1f - exp(-t * 8f)) * (1f - p).coerceAtLeast(0f)
            buf[i] = ((saw * 0.7f + noise) * env * 0.45f).toClippedShort()
        }
        return buf
    }

    /** Wobbly mid tone: 440Hz with 7Hz vibrato (±18Hz), 650ms — matches turtle visual. */
    private fun generateSlowPiranhas(): ShortArray {
        val ms = 650
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val total = ms / 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / total
            val freq = 440f + sin(t * 7f * TAU) * 18f
            val env = (1f - exp(-t * 12f)) * (1f - p * 0.7f)
            buf[i] = (sin(t * freq * TAU) * env * 0.4f).toClippedShort()
        }
        return buf
    }

    /** Cheerful ascending major arpeggio (C5-E5-G5-C6), 90ms each. */
    private fun generateExtraLife(): ShortArray =
        concatSineNotes(listOf(523.25f, 659.25f, 783.99f, 1046.5f), noteMs = 90, amp = 0.42f)

    /**
     * Sad "wah-wah-waaah" trombone — three descending detuned-sine notes with
     * the final one sustained, evoking a classic deflation finale. Two slightly
     * detuned sines per note give the woozy chorus the sound that makes it
     * read "sad" rather than just descending.
     */
    private fun generateDeath(): ShortArray {
        val freqs = floatArrayOf(587.33f, 466.16f, 349.23f)  // D5, A#4, F4 — minor descent
        val durations = intArrayOf(180, 180, 380)  // sustain the last note
        val total = durations.sumOf { SAMPLE_RATE * it / 1000 }
        val buf = ShortArray(total)
        var offset = 0
        for ((ni, f) in freqs.withIndex()) {
            val noteN = SAMPLE_RATE * durations[ni] / 1000
            val releaseN = SAMPLE_RATE * 40 / 1000
            for (i in 0 until noteN) {
                val t = i.toFloat() / SAMPLE_RATE
                val attack = 1f - exp(-t * 200f)
                val release = if (i > noteN - releaseN) {
                    (noteN - i).toFloat() / releaseN
                } else 1f
                val env = attack * release
                // Slight detune (1.008x) for the "wah" character.
                val a = sin(t * f * TAU) * 0.5f
                val b = sin(t * f * 1.008f * TAU) * 0.4f
                buf[offset + i] = ((a + b) * env * 0.4f).toClippedShort()
            }
            offset += noteN
        }
        return buf
    }

    /** Quick rising bleep — pitch sweeps 600Hz → 1200Hz over 180ms. Classic coin-pop. */
    private fun generatePiranhaEaten(): ShortArray {
        val ms = 180
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val total = ms / 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / total
            val freq = 600f + 600f * p
            val env = (1f - exp(-t * 120f)) * exp(-p * 3f)
            buf[i] = (sin(t * freq * TAU) * env * 0.5f).toClippedShort()
        }
        return buf
    }

    /**
     * Portal whoosh — bell-shaped envelope on an ascending tone (400 → 1200Hz)
     * plus a layer of HP-filtered noise. Reads as a quick teleport/warp.
     */
    private fun generatePortal(): ShortArray {
        val ms = 420
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val rng = Lcg(0xC0FFEEL)
        val total = ms / 1000f
        var prevIn = 0f
        var prevOut = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / total
            val freq = 400f + 800f * p
            val tone = sin(t * freq * TAU) * 0.55f
            val noiseIn = rng.next() * 2f - 1f
            val hpOut = 0.9f * (prevOut + noiseIn - prevIn)
            prevIn = noiseIn
            prevOut = hpOut
            // Bell-shaped envelope: rise then fall over the full duration.
            val env = sin(p * PI.toFloat())
            buf[i] = ((tone + hpOut * 0.25f) * env * 0.5f).toClippedShort()
        }
        return buf
    }

    /**
     * Wet "plop" — descending tone 800Hz → 300Hz with splash noise that fades
     * over the duration. Fires when the player drops a bait into the water.
     */
    private fun generateBait(): ShortArray {
        val ms = 180
        val n = SAMPLE_RATE * ms / 1000
        val buf = ShortArray(n)
        val rng = Lcg(0xBA17F15L)
        val total = ms / 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val p = t / total
            val freq = 800f - 500f * p
            val tone = sin(t * freq * TAU) * 0.6f
            val noise = (rng.next() * 2f - 1f) * 0.3f * (1f - p)
            val env = (1f - exp(-t * 80f)) * exp(-p * 2.5f)
            buf[i] = ((tone + noise) * env * 0.5f).toClippedShort()
        }
        return buf
    }

    /**
     * Triumphant ascending arpeggio (C5-E5-G5-C6-E6), 100ms each = 500ms total.
     * Bigger than the fruit jingle since this is the level-clear celebration.
     */
    private fun generateLevelComplete(): ShortArray =
        concatSineNotes(
            listOf(523.25f, 659.25f, 783.99f, 1046.5f, 1318.51f),
            noteMs = 100,
            amp = 0.45f,
        )

    // ---------- Helpers ----------

    /** Concatenate sine-tone notes with a quick attack and release-at-tail ADSR. */
    private fun concatSineNotes(freqs: List<Float>, noteMs: Int, amp: Float): ShortArray {
        val noteN = SAMPLE_RATE * noteMs / 1000
        val releaseN = SAMPLE_RATE * 30 / 1000  // last 30ms of each note fades out
        val buf = ShortArray(noteN * freqs.size)
        for ((ni, f) in freqs.withIndex()) {
            for (i in 0 until noteN) {
                val t = i.toFloat() / SAMPLE_RATE
                val attack = 1f - exp(-t * 300f)
                val release = if (i > noteN - releaseN) {
                    (noteN - i).toFloat() / releaseN
                } else 1f
                val env = attack * release
                buf[ni * noteN + i] = (sin(t * f * TAU) * env * amp).toClippedShort()
            }
        }
        return buf
    }

    private fun Float.toClippedShort(): Short =
        (this.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    /** Tiny linear-congruential PRNG — deterministic per generator, fast in a tight loop. */
    private class Lcg(seed: Long) {
        private var state = seed and 0x7FFFFFFFL
        fun next(): Float {
            state = (state * 1103515245L + 12345L) and 0x7FFFFFFFL
            return state.toInt().toFloat() / Int.MAX_VALUE
        }
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TAU = 6.283185307f  // 2 * PI
        private const val PREFS_FILE = "monkeyswim_settings"
        private const val KEY_SOUND = "soundEnabled"
    }
}
