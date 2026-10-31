package cn.anlele.checkin.ui.games

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin

/**
 * 轻量合成音效（F13）—— 不进重资源文件：PCM 直接合成 + AudioTrack 单次播放。
 * 四类：eat 吃鱼 / hurt 受伤 / dash 冲刺 / end 结束（与网页 fish.js WebAudio 同参数）。
 * 全部 runCatching 包裹 + 后台线程播放，音效永远不允许把游戏弄崩（P0 闪退教训）。
 * melon 等后续批次可直接复用本对象。
 */
internal object GameSfx {
    private const val SAMPLE_RATE = 22050
    private const val WAVE_SINE = 0
    private const val WAVE_SQUARE = 1
    private const val WAVE_TRIANGLE = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 音效开关（DisplaySettings.Sound）：设置面板「音效」项，关了就全程静音 */
    @Volatile
    var enabled: Boolean = true

    /** 播放一类音效；未知名字静默忽略。fire-and-forget，绝不抛出。 */
    fun play(name: String) {
        if (!enabled) return
        scope.launch {
            runCatching {
                val pcm = render(name) ?: return@runCatching
                playPcm(pcm)
            }
        }
    }

    /** 合成四类音效波形（参数与网页 fish.js sfx 对齐：频率/时长一致）。 */
    private fun render(name: String): ShortArray? = when (name) {
        "eat" -> synth(520f, 820f, 0.09f, 0.32f, WAVE_SINE)
        "hurt" -> synth(200f, 90f, 0.22f, 0.30f, WAVE_SQUARE)
        "dash" -> synth(320f, 960f, 0.11f, 0.26f, WAVE_TRIANGLE)
        "merge" -> synth(420f, 720f, 0.10f, 0.30f, WAVE_SINE)      // melon 合成 pop（M13）
        "explode" -> concat(                                        // melon 爆瓜（M13）
            synth(220f, 640f, 0.16f, 0.34f, WAVE_TRIANGLE),
            synth(640f, 180f, 0.20f, 0.26f, WAVE_SINE),
        )
        "end" -> concat(
            synth(660f, 660f, 0.14f, 0.30f, WAVE_SINE),
            synth(440f, 440f, 0.14f, 0.30f, WAVE_SINE),
            synth(294f, 294f, 0.30f, 0.30f, WAVE_SINE),
        )
        else -> null
    }

    /** 生成一段 PCM：线性扫频 + 平方衰减包络。 */
    private fun synth(f0: Float, f1: Float, durSec: Float, volume: Float, wave: Int): ShortArray {
        val n = (SAMPLE_RATE * durSec).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val freq = f0 + (f1 - f0) * t
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = (1.0 - t) * (1.0 - t)
            val s = when (wave) {
                WAVE_SQUARE -> if (sin(phase) >= 0.0) 1.0 else -1.0
                WAVE_TRIANGLE -> 2.0 / PI * asin(sin(phase))
                else -> sin(phase)
            }
            out[i] = (s * env * volume * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun concat(vararg parts: ShortArray): ShortArray {
        var total = 0
        for (p in parts) total += p.size
        val out = ShortArray(total)
        var off = 0
        for (p in parts) {
            p.copyInto(out, off)
            off += p.size
        }
        return out
    }

    /** 单次播放一段 PCM（静态缓冲，播完释放）。 */
    private fun playPcm(data: ShortArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bytes = maxOf(data.size * 2, minBuf)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes)
            .build()
        try {
            track.write(data, 0, data.size)
            track.play()
            Thread.sleep(data.size * 1000L / SAMPLE_RATE + 80)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
