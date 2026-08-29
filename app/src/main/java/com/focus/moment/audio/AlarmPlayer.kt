package com.focus.moment.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.focus.moment.data.model.AlarmSound
import com.focus.moment.data.model.RemindMode
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 专注结束提醒：铃声（内置合成音色或系统铃声）+ 震动，循环播放直到用户手动停止。
 */
object AlarmPlayer {

    private const val SR = 44100
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var ringtone: android.media.Ringtone? = null

    @Volatile private var playing = false
    private var vibrator: Vibrator? = null

    fun start(context: Context, sound: AlarmSound, remind: RemindMode) {
        stop()
        // 震动
        if (remind != RemindMode.SOUND) {
            val vib = getVibrator(context)
            val pattern = longArrayOf(0, 700, 400, 700, 400, 1200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
            vibrator = vib
        }
        // 铃声
        if (remind != RemindMode.VIBRATE) {
            if (sound == AlarmSound.SYSTEM) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, uri)
                r.isLooping = true
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                r.play()
                ringtone = r
            } else {
                playSynthLoop(sound)
            }
        }
    }

    fun stop() {
        playing = false
        try { thread?.join(400) } catch (_: Exception) {}
        thread = null
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        track = null
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    fun isPlaying(): Boolean = playing

    /** 试听 1.5 秒 */
    fun preview(context: Context, sound: AlarmSound) {
        if (sound == AlarmSound.SYSTEM) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
            return
        }
        val buf = synthBuffer(sound, 1.5)
        playBufferOnce(buf)
    }

    private fun getVibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun playSynthLoop(sound: AlarmSound) {
        playing = true
        val buf = synthBuffer(sound, 3.0)
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SR)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, SR))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        thread = Thread {
            t.play()
            while (playing) {
                t.write(buf, 0, buf.size)
            }
        }.apply { start() }
    }

    private fun playBufferOnce(buf: ShortArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SR)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, SR))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        Thread {
            t.play()
            t.write(buf, 0, buf.size)
            Thread.sleep(200)
            t.stop(); t.release()
        }.apply { priority = Thread.NORM_PRIORITY - 1; start() }
    }

    // ---------------- 音色合成 ----------------

    private fun synthBuffer(sound: AlarmSound, seconds: Double): ShortArray {
        val n = (SR * seconds).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var v = 0.0
            when (sound) {
                AlarmSound.BELL -> {
                    // 每隔 1.2s 敲一记钟声：基音 + 泛音指数衰减
                    val lt = t % 1.2
                    if (lt < 1.1) {
                        val env = exp(-lt * 4.5)
                        v = (sin(2 * PI * 660 * lt) + 0.5 * sin(2 * PI * 1320 * lt) + 0.25 * sin(2 * PI * 1980 * lt)) * env * 0.5
                    }
                }
                AlarmSound.DROP -> {
                    // 每 0.9s 一滴：频率下滑 + 快速衰减
                    val lt = t % 0.9
                    if (lt < 0.25) {
                        val f = 1400 - 3600 * lt
                        val env = exp(-lt * 14)
                        v = sin(2 * PI * maxOf(220.0, f) * lt) * env * 0.7
                    }
                }
                AlarmSound.CHIME -> {
                    // 编钟琶音 C6-E6-G6-C7，每音 0.5s 衰减，循环 2s
                    val seq = doubleArrayOf(1046.5, 1318.5, 1568.0, 2093.0)
                    val lt = t % 2.0
                    val idx = (lt / 0.5).toInt().coerceIn(0, 3)
                    val nt = lt - idx * 0.5
                    if (nt < 0.5) {
                        val env = exp(-nt * 5)
                        v = sin(2 * PI * seq[idx] * nt) * env * 0.55 + sin(2 * PI * seq[idx] * 2 * nt) * env * 0.15
                    }
                }
                AlarmSound.ELECTRONIC -> {
                    // 电子音：方波感旋律 E5-G5-A5-C6
                    val seq = doubleArrayOf(659.3, 784.0, 880.0, 1046.5)
                    val lt = t % 1.6
                    val idx = (lt / 0.4).toInt().coerceIn(0, 3)
                    val nt = lt - idx * 0.4
                    if (nt < 0.3) {
                        val env = min(1.0, exp(-nt * 2.0) * 1.4)
                        val sq = if (sin(2 * PI * seq[idx] * nt) >= 0) 1.0 else -1.0
                        v = (0.6 * sq + 0.4 * sin(2 * PI * seq[idx] * nt)) * env * 0.4
                    }
                }
                AlarmSound.WINDCHIME -> {
                    // 风铃：五声音阶随机高音，快速衰减
                    val scale = doubleArrayOf(1568.0, 1760.0, 2093.0, 2349.2, 2637.0)
                    val seg = 0.5
                    val seed = (t / seg).toInt()
                    val f = scale[(seed * 7 + 3) % scale.size]
                    val nt = t % seg
                    val jitter = ((seed * 13) % 10) / 100.0
                    if (nt < seg - jitter) {
                        val env = exp(-nt * 8)
                        v = sin(2 * PI * f * nt) * env * 0.5 + sin(2 * PI * f * 2.01 * nt) * env * 0.2
                    }
                }
                else -> {}
            }
            out[i] = (v.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
