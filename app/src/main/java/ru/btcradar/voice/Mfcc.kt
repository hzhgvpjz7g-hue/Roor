package ru.btcradar.voice

import kotlin.math.*

/**
 * Лёгкая реализация MFCC на чистом Kotlin — без внешних библиотек и без сети.
 *
 * Это основа «замка по голосу»: из записи считаем компактный отпечаток
 * (эмбеддинг), а потом сравниваем косинусной близостью. Честно повторю:
 * это НЕ биометрия банковского уровня. По нескольким фразам такой замок
 * ошибается и обманывается записью голоса. Годится как удобный фильтр
 * «скорее всего это владелец», но не как защита секретов — секреты
 * стережёт пароль сейфа.
 */
object Mfcc {

    private const val SAMPLE_RATE = 16000
    private const val FRAME_MS = 25
    private const val STEP_MS = 10
    private const val N_MEL = 26
    private const val N_CEP = 13
    private const val PRE_EMPH = 0.97

    private val frameSize = SAMPLE_RATE * FRAME_MS / 1000      // 400
    private val stepSize = SAMPLE_RATE * STEP_MS / 1000        // 160
    private val fftSize = Integer.highestOneBit(frameSize - 1) * 2  // 512

    /**
     * Отпечаток записи: [mean(13 cep) ++ std(13 cep)] = 26 чисел.
     * Усреднение по кадрам убирает зависимость от длины фразы.
     */
    fun embed(pcm: ShortArray): FloatArray? {
        if (pcm.size < frameSize) return null

        // нормализация + пре-эмфазис
        val x = FloatArray(pcm.size)
        var prev = 0.0f
        for (i in pcm.indices) {
            val s = pcm[i] / 32768.0f
            x[i] = s - PRE_EMPH.toFloat() * prev
            prev = s
        }

        val mel = melFilterbank()
        val window = hamming(frameSize)

        val frames = ArrayList<DoubleArray>()
        var start = 0
        while (start + frameSize <= x.size) {
            val re = DoubleArray(fftSize)
            val im = DoubleArray(fftSize)
            for (i in 0 until frameSize) re[i] = x[start + i] * window[i]
            fft(re, im)

            // спектр мощности
            val power = DoubleArray(fftSize / 2 + 1)
            for (i in power.indices) power[i] = (re[i] * re[i] + im[i] * im[i]) / fftSize

            // мел-энергии -> log
            val logMel = DoubleArray(N_MEL)
            for (m in 0 until N_MEL) {
                var e = 0.0
                val f = mel[m]
                for (i in power.indices) e += power[i] * f[i]
                logMel[m] = ln(max(e, 1e-10))
            }

            // DCT-II -> кепстр
            val cep = DoubleArray(N_CEP)
            for (k in 0 until N_CEP) {
                var sum = 0.0
                for (m in 0 until N_MEL) sum += logMel[m] * cos(PI * k * (m + 0.5) / N_MEL)
                cep[k] = sum
            }
            frames.add(cep)
            start += stepSize
        }

        if (frames.isEmpty()) return null

        // mean и std по кадрам
        val mean = DoubleArray(N_CEP)
        for (f in frames) for (k in 0 until N_CEP) mean[k] += f[k]
        for (k in 0 until N_CEP) mean[k] /= frames.size

        val std = DoubleArray(N_CEP)
        for (f in frames) for (k in 0 until N_CEP) {
            val d = f[k] - mean[k]; std[k] += d * d
        }
        for (k in 0 until N_CEP) std[k] = sqrt(std[k] / frames.size)

        val out = FloatArray(N_CEP * 2)
        for (k in 0 until N_CEP) { out[k] = mean[k].toFloat(); out[N_CEP + k] = std[k].toFloat() }
        return out
    }

    /** Косинусная близость двух отпечатков: 1.0 — идентичны, 0 — ортогональны. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /** Усреднение нескольких отпечатков в один эталон (enrollment). */
    fun average(list: List<FloatArray>): FloatArray? {
        if (list.isEmpty()) return null
        val n = list[0].size
        val out = FloatArray(n)
        for (e in list) for (i in 0 until n) out[i] += e[i]
        for (i in 0 until n) out[i] /= list.size
        return out
    }

    // ---------- вспомогательное ----------

    private fun hamming(n: Int) = DoubleArray(n) { 0.54 - 0.46 * cos(2 * PI * it / (n - 1)) }

    private fun hzToMel(hz: Double) = 2595.0 * log10(1 + hz / 700.0)
    private fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1)

    private fun melFilterbank(): Array<DoubleArray> {
        val bins = fftSize / 2 + 1
        val lowMel = hzToMel(0.0)
        val highMel = hzToMel(SAMPLE_RATE / 2.0)
        val points = DoubleArray(N_MEL + 2) { melToHz(lowMel + (highMel - lowMel) * it / (N_MEL + 1)) }
        val binIdx = IntArray(N_MEL + 2) { floor((fftSize + 1) * points[it] / SAMPLE_RATE).toInt() }

        return Array(N_MEL) { m ->
            val f = DoubleArray(bins)
            val l = binIdx[m]; val c = binIdx[m + 1]; val r = binIdx[m + 2]
            for (k in l until c) if (k in 0 until bins && c > l) f[k] = (k - l).toDouble() / (c - l)
            for (k in c until r) if (k in 0 until bins && r > c) f[k] = (r - k).toDouble() / (r - c)
            f
        }
    }

    /** Итеративный radix-2 FFT на месте. Размеры — степень двойки. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wRe = cos(ang); val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0; var curIm = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = i + k + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe; im[b] = im[a] - tIm
                    re[a] += tRe; im[a] += tIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe; curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
