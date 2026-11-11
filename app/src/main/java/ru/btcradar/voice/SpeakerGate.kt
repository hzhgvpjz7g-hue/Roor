package ru.btcradar.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * «Замок по голосу» — мягкий. Enrollment: пользователь несколько раз
 * произносит фразу, усредняем MFCC-отпечаток в эталон. Verify: пишем фразу
 * и сравниваем косинусной близостью с эталоном.
 *
 * Ещё раз честно: это удобный фильтр, а не защита. Порог настраиваемый;
 * по умолчанию умеренный, чтобы владельца не отшивало постоянно.
 */
class SpeakerGate(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("btcradar_voice", Context.MODE_PRIVATE)

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val KEY_TEMPLATE = "voice_template"
        private const val KEY_THRESHOLD = "voice_threshold"
        const val DEFAULT_THRESHOLD = 0.80f
        /** Сколько секунд пишем на одну фразу. */
        const val CAPTURE_SEC = 2.5
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun isEnrolled(): Boolean = prefs.contains(KEY_TEMPLATE)

    fun threshold(): Float = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
    fun setThreshold(v: Float) = prefs.edit().putFloat(KEY_THRESHOLD, v.coerceIn(0.5f, 0.98f)).apply()

    fun clearEnrollment() = prefs.edit().remove(KEY_TEMPLATE).apply()

    /** Записать одну фразу и вернуть её отпечаток (для набора при обучении). */
    suspend fun captureEmbedding(): FloatArray? = withContext(Dispatchers.IO) {
        val pcm = record(CAPTURE_SEC) ?: return@withContext null
        Mfcc.embed(pcm)
    }

    /** Сохранить эталон из нескольких отпечатков. */
    fun enroll(samples: List<FloatArray>): Boolean {
        val tpl = Mfcc.average(samples) ?: return false
        val arr = JSONArray()
        tpl.forEach { arr.put(it.toDouble()) }
        prefs.edit().putString(KEY_TEMPLATE, arr.toString()).apply()
        return true
    }

    private fun loadTemplate(): FloatArray? {
        val raw = prefs.getString(KEY_TEMPLATE, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        }.getOrNull()
    }

    /** Результат проверки: похоже/нет + сама близость, чтобы показать пользователю. */
    data class Verdict(val accepted: Boolean, val similarity: Float, val enrolled: Boolean)

    suspend fun verify(): Verdict = withContext(Dispatchers.IO) {
        val tpl = loadTemplate() ?: return@withContext Verdict(false, 0f, enrolled = false)
        val pcm = record(CAPTURE_SEC) ?: return@withContext Verdict(false, 0f, enrolled = true)
        val emb = Mfcc.embed(pcm) ?: return@withContext Verdict(false, 0f, enrolled = true)
        val sim = Mfcc.cosine(emb, tpl)
        Verdict(sim >= threshold(), sim, enrolled = true)
    }

    private fun record(seconds: Double): ShortArray? {
        if (!hasPermission()) return null
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null

        val total = (SAMPLE_RATE * seconds).toInt()
        val out = ShortArray(total)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, total * 2)
            )
        }.getOrNull() ?: return null

        return try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) return null
            rec.startRecording()
            var read = 0
            while (read < total) {
                val n = rec.read(out, read, total - read)
                if (n <= 0) break
                read += n
            }
            if (read < SAMPLE_RATE / 2) null else out.copyOf(read)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
    }
}
