package com.focus.moment.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.focus.moment.data.model.WhiteNoiseType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 白噪音引擎：全部音效由程序实时合成，无需联网、不占体积。
 * 雨声/海浪/风声 = 滤波噪音 + 幅度调制；森林/篝火/虫鸣 = 噪音底 + 随机事件（鸟鸣/爆裂/蟋蟀）。
 */
object WhiteNoiseEngine {

    private const val SR = 44100
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var type: WhiteNoiseType = WhiteNoiseType.RAIN
    @Volatile private var volume: Float = 0.7f

    // 合成器状态
    private var pinkB0 = 0f; private var pinkB1 = 0f; private var pinkB2 = 0f
    private var pinkB3 = 0f; private var pinkB4 = 0f; private var pinkB5 = 0f; private var pinkB6 = 0f
    private var brown = 0f
    private var phase = 0.0
    private var nextEventAt = 0.0    // 下一个随机事件（鸟鸣/爆裂/蟋蟀脉冲）样本位置
    private var eventRemaining = 0.0 // 当前事件剩余样本数
    private var eventFreq = 0.0
    private var eventSweep = 0.0
    private var eventKind = 0
    private var eventTotal = 1.0
    private var pulseOn = false
    private var pulseCount = 0
    private var rumbleLP = 0f      // 雷声低通状态

    private val rnd = Random(System.nanoTime())

    fun start(t: WhiteNoiseType, vol: Float) {
        stop()
        type = t; volume = vol
        running = true
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SR)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, SR * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        thread = Thread {
            track?.play()
            val buf = ShortArray(4096)
            var pos = 0.0
            while (running) {
                genChunk(buf, pos)
                pos += buf.size
                val tr = track ?: break
                tr.write(buf, 0, buf.size)
            }
        }.apply { priority = Thread.NORM_PRIORITY - 1; start() }
    }

    fun setType(t: WhiteNoiseType) {
        type = t
        resetState()
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        track?.setVolume(volume)
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
        thread = null
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        track = null
        resetState()
    }

    fun isPlaying(): Boolean = running

    /** 试听：播放约 2.5 秒后自动停止（再次点击其他音效会切换） */
    private val previewSeq = java.util.concurrent.atomic.AtomicInteger(0)

    fun preview(t: WhiteNoiseType, vol: Float = 0.7f) {
        val my = previewSeq.incrementAndGet()
        start(t, vol)
        Thread {
            try { Thread.sleep(2500) } catch (_: Exception) {}
            if (previewSeq.get() == my) stop()
        }.apply { priority = Thread.MIN_PRIORITY; start() }
    }

    private fun resetState() {
        pinkB0 = 0f; pinkB1 = 0f; pinkB2 = 0f; pinkB3 = 0f; pinkB4 = 0f; pinkB5 = 0f; pinkB6 = 0f
        brown = 0f; phase = 0.0
        nextEventAt = 0.0; eventRemaining = 0.0; pulseCount = 0; pulseOn = false
        rumbleLP = 0f
    }

    private fun pinkSample(): Float {
        val w = rnd.nextFloat() * 2f - 1f
        pinkB0 = 0.99886f * pinkB0 + w * 0.0555179f
        pinkB1 = 0.99332f * pinkB1 + w * 0.0750759f
        pinkB2 = 0.96900f * pinkB2 + w * 0.1538520f
        pinkB3 = 0.86650f * pinkB3 + w * 0.3104856f
        pinkB4 = 0.55000f * pinkB4 + w * 0.5329522f
        pinkB5 = -0.7616f * pinkB5 - w * 0.0168980f
        val out = (pinkB0 + pinkB1 + pinkB2 + pinkB3 + pinkB4 + pinkB5 + pinkB6 + w * 0.5362f) * 0.11f
        pinkB6 = w * 0.115926f
        return out
    }

    private fun brownSample(): Float {
        val w = rnd.nextFloat() * 2f - 1f
        brown = (brown + 0.02f * w) / 1.02f
        return brown * 3.5f
    }

    private fun genChunk(buf: ShortArray, globalPos: Double) {
        val t = type
        for (i in buf.indices) {
            var v = when (t) {
                WhiteNoiseType.WHITE -> (rnd.nextFloat() * 2f - 1f) * 0.30f

                WhiteNoiseType.RAIN -> {
                    // 粉噪音打底 + 高频细雨沙沙
                    val p = pinkSample() * 0.9f
                    val hiss = (rnd.nextFloat() * 2f - 1f) * 0.12f
                    (p + hiss) * 1.1f
                }

                WhiteNoiseType.WAVE -> {
                    // 棕噪音 + 慢速潮汐起伏
                    val lfo = 0.5f + 0.5f * sin(2.0 * PI * globalPos / (SR * 7.0)).toFloat()
                    brownSample() * (0.25f + 0.75f * lfo) * 0.9f
                }

                WhiteNoiseType.WIND -> {
                    // 粉噪音 + 更慢的阵风起伏 + 低通感
                    val lfo = 0.55f + 0.45f * sin(2.0 * PI * globalPos / (SR * 13.0)).toFloat()
                    pinkSample() * lfo * 1.2f
                }

                WhiteNoiseType.FOREST -> {
                    // 轻微叶子沙沙 + 随机鸟鸣（下滑/上滑音）
                    var v = pinkSample() * 0.35f
                    v += event(globalPos, SR)
                    v
                }

                WhiteNoiseType.FIRE -> {
                    // 低频篝火隆隆 + 随机噼啪爆裂
                    var v = brownSample() * 0.5f + pinkSample() * 0.15f
                    v += event(globalPos, SR) * 1.4f
                    v
                }

                WhiteNoiseType.CRICKET -> {
                    // 蟋蟀：4.5kHz 正弦，脉冲串（4 声一组）+ 微弱夜底噪
                    var v = pinkSample() * 0.06f
                    val interval = (SR * 0.9).toInt()
                    val inCycle = (globalPos.toInt() % interval)
                    val group = if (inCycle < SR * 0.28) {
                        val pulseLen = (SR * 0.045).toInt()
                        val idx = inCycle % (pulseLen * 2)
                        idx < pulseLen
                    } else false
                    if (group) {
                        phase += 2.0 * PI * 4500.0 / SR
                        v += (sin(phase).toFloat() * 0.16f)
                    }
                    v
                }

                WhiteNoiseType.STREAM -> {
                    // 溪流：轻粉噪音 + 高频水泡随机事件
                    var v = pinkSample() * 0.5f + (rnd.nextFloat() * 2f - 1f) * 0.08f
                    v += event(globalPos, SR) * 1.3f
                    v
                }

                WhiteNoiseType.THUNDER -> {
                    // 雷雨：雨声打底 + 随机低频雷鸣（长衰减）
                    var v = pinkSample() * 0.8f + (rnd.nextFloat() * 2f - 1f) * 0.1f
                    v += event(globalPos, SR) * 2.6f
                    v
                }

                WhiteNoiseType.BIRDS -> {
                    // 清晨鸟鸣：极轻风底 + 更频繁明亮的鸟鸣
                    var v = pinkSample() * 0.18f
                    v += event(globalPos, SR)
                    v
                }

                WhiteNoiseType.CAFE -> {
                    // 咖啡馆：人声嗡嗡底 + 杯碟碰撞声
                    var v = brownSample() * 0.55f + pinkSample() * 0.12f
                    v += event(globalPos, SR) * 1.2f
                    v
                }

                WhiteNoiseType.SNOWSTORM -> {
                    // 风雪夜：呼啸风 + 高频雪粒沙沙
                    val lfo = 0.6f + 0.4f * sin(2.0 * PI * globalPos / (SR * 9.0)).toFloat()
                    pinkSample() * lfo * 1.1f + (rnd.nextFloat() * 2f - 1f) * 0.07f
                }

                WhiteNoiseType.LEAVES -> {
                    // 雨打树叶：柔和雨底 + 叶片滴答事件
                    var v = pinkSample() * 0.55f
                    v += event(globalPos, SR) * 1.5f
                    v
                }
            }
            v = v.coerceIn(-0.95f, 0.95f)
            buf[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** 随机事件：0鸟鸣 / 1篝火爆裂 / 2水泡·杯碟碰撞 / 3雷鸣 / 4叶片滴答。返回当前样本值（-1~1） */
    private fun event(globalPos: Double, sr: Int): Float {
        if (eventRemaining <= 0.0) {
            if (globalPos >= nextEventAt) {
                eventKind = when (type) {
                    WhiteNoiseType.FIRE -> 1
                    WhiteNoiseType.STREAM, WhiteNoiseType.CAFE -> 2
                    WhiteNoiseType.THUNDER -> 3
                    WhiteNoiseType.LEAVES -> 4
                    else -> 0
                }
                when (eventKind) {
                    0 -> { // 鸟鸣 80~200ms，2~4kHz 扫频
                        eventTotal = sr * (0.08 + rnd.nextDouble() * 0.12)
                        eventRemaining = eventTotal
                        eventFreq = 2000.0 + rnd.nextDouble() * 2000.0
                        eventSweep = (rnd.nextDouble() - 0.3) * 3000.0
                    }
                    1 -> { // 爆裂 15~50ms 白噪衰减
                        eventTotal = sr * (0.015 + rnd.nextDouble() * 0.035)
                        eventRemaining = eventTotal
                        eventFreq = 0.0
                        eventSweep = 0.0
                    }
                    2 -> { // 水泡/杯碟 30~100ms，800~3000Hz 下滑音
                        eventTotal = sr * (0.03 + rnd.nextDouble() * 0.07)
                        eventRemaining = eventTotal
                        eventFreq = 800.0 + rnd.nextDouble() * 2200.0
                        eventSweep = -(300.0 + rnd.nextDouble() * 900.0)
                    }
                    3 -> { // 雷鸣 1.2~3s 低频隆隆
                        eventTotal = sr * (1.2 + rnd.nextDouble() * 1.8)
                        eventRemaining = eventTotal
                        rumbleLP = 0f
                    }
                    else -> { // 4 滴答 8~25ms
                        eventTotal = sr * (0.008 + rnd.nextDouble() * 0.017)
                        eventRemaining = eventTotal
                    }
                }
                val gap = when (eventKind) {
                    0 -> if (type == WhiteNoiseType.BIRDS) 0.35 + rnd.nextDouble() * 1.3 else 1.2 + rnd.nextDouble() * 2.5
                    1 -> 0.05 + rnd.nextDouble() * 0.5
                    2 -> if (type == WhiteNoiseType.CAFE) 0.4 + rnd.nextDouble() * 1.8 else 0.08 + rnd.nextDouble() * 0.4
                    3 -> 6.0 + rnd.nextDouble() * 14.0
                    else -> 0.06 + rnd.nextDouble() * 0.4
                }
                nextEventAt = globalPos + eventRemaining + sr * gap
            }
            return 0f
        }
        eventRemaining -= 1.0
        return when (eventKind) {
            0 -> {
                phase += 2.0 * PI * (eventFreq + eventSweep * (1.0 - eventRemaining / eventTotal)) / sr
                val env = min(1.0, eventRemaining / (sr * 0.05)).toFloat()
                sin(phase).toFloat() * env * 0.22f
            }
            1 -> {
                val env = (eventRemaining / (sr * 0.03)).toFloat().coerceIn(0f, 1f)
                (rnd.nextFloat() * 2f - 1f) * env * 0.5f
            }
            2 -> {
                phase += 2.0 * PI * (eventFreq + eventSweep * (1.0 - eventRemaining / eventTotal)) / sr
                val env = (eventRemaining / (sr * 0.02)).toFloat().coerceIn(0f, 1f)
                sin(phase).toFloat() * env * 0.3f
            }
            3 -> {
                val env = (eventRemaining / (sr * 1.2)).toFloat().coerceIn(0f, 1f)
                val target = (rnd.nextFloat() * 2f - 1f) * 0.5f
                rumbleLP += 0.015f * (target - rumbleLP)
                rumbleLP * env * 2.4f
            }
            else -> {
                val env = (eventRemaining / (sr * 0.015)).toFloat().coerceIn(0f, 1f)
                (rnd.nextFloat() * 2f - 1f) * env * 0.4f
            }
        }
    }
}
