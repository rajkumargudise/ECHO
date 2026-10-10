package echo.player.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** In-place radix-2 FFT. The size is fixed at construction so twiddles and bit reversal are computed once. */
class Fft(val size: Int) {
    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val reversed = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        for (i in 0 until size / 2) {
            val angle = -2.0 * PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reversed[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun forward(re: FloatArray, im: FloatArray) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr
                    im[j + half] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
    }
}

/** Turns the latest mono samples into [bandCount] log-spaced bar heights in 0..1 (dB scaled). */
class SpectrumAnalyzer(
    val fftSize: Int = 2048,
    val bandCount: Int = 32,
    private val minFrequency: Float = 40f,
    private val maxFrequency: Float = 16_000f,
    private val floorDb: Float = -60f,
) {
    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))).toFloat() }
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val magnitudes = FloatArray(fftSize / 2 + 1)
    private var edgesForRate = 0
    private val lowBin = IntArray(bandCount)
    private val highBin = IntArray(bandCount)

    /** [samples] must hold at least [fftSize] values; the most recent [fftSize] are analysed. */
    fun analyze(samples: FloatArray, sampleRate: Int, out: FloatArray) {
        require(samples.size >= fftSize && out.size >= bandCount)
        prepareBands(sampleRate)
        val offset = samples.size - fftSize
        for (i in 0 until fftSize) {
            re[i] = samples[offset + i] * window[i]
            im[i] = 0f
        }
        fft.forward(re, im)
        // A full-scale sine gives a peak magnitude of about fftSize / 4 with a Hann window.
        val scale = 4f / fftSize
        for (bin in magnitudes.indices) {
            magnitudes[bin] = sqrt(re[bin] * re[bin] + im[bin] * im[bin]) * scale
        }
        for (band in 0 until bandCount) {
            var peak = 0f
            for (bin in lowBin[band]..highBin[band]) peak = max(peak, magnitudes[bin])
            out[band] = ampToUnit(peak)
        }
    }

    private fun ampToUnit(amplitude: Float): Float {
        val db = 20f * log10(max(amplitude, 1e-6f))
        return ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
    }

    private fun prepareBands(sampleRate: Int) {
        if (sampleRate == edgesForRate) return
        edgesForRate = sampleRate
        val nyquist = sampleRate / 2f
        val top = min(maxFrequency, nyquist * 0.95f)
        val ratio = (top / minFrequency).toDouble()
        val binHz = sampleRate.toFloat() / fftSize
        val maxBin = fftSize / 2
        for (band in 0 until bandCount) {
            val lo = minFrequency * Math.pow(ratio, band.toDouble() / bandCount).toFloat()
            val hi = minFrequency * Math.pow(ratio, (band + 1).toDouble() / bandCount).toFloat()
            val lowIndex = (lo / binHz).toInt().coerceIn(1, maxBin)
            val highIndex = max(lowIndex, ((hi / binHz).toInt() - 1)).coerceIn(lowIndex, maxBin)
            lowBin[band] = lowIndex
            highBin[band] = highIndex
        }
    }
}

object Ballistics {
    /** Moves [current] toward [target] with separate rise and fall time constants; allocation free. */
    fun step(current: FloatArray, target: FloatArray, dtSeconds: Float, attackTau: Float, releaseTau: Float) {
        val attack = 1f - exp(-dtSeconds / attackTau)
        val release = 1f - exp(-dtSeconds / releaseTau)
        for (i in current.indices) {
            val t = target[i]
            current[i] += (t - current[i]) * (if (t > current[i]) attack else release)
        }
    }

    fun step(current: Float, target: Float, dtSeconds: Float, attackTau: Float, releaseTau: Float): Float {
        val k = 1f - exp(-dtSeconds / (if (target > current) attackTau else releaseTau))
        return current + (target - current) * k
    }
}

object LevelMath {
    /** dBFS range shown on the VU meters. */
    private const val FLOOR_DB = -45f
    private const val RANGE_DB = 42f

    fun rms(samples: FloatArray, count: Int = samples.size): Float {
        val n = min(count, samples.size)
        if (n == 0) return 0f
        var sum = 0f
        for (i in samples.size - n until samples.size) sum += samples[i] * samples[i]
        return sqrt(sum / n)
    }

    fun peak(samples: FloatArray, count: Int = samples.size): Float {
        val n = min(count, samples.size)
        var peak = 0f
        for (i in samples.size - n until samples.size) peak = max(peak, kotlin.math.abs(samples[i]))
        return peak
    }

    /** Linear amplitude (0..1 full scale) to a 0..1 meter position. */
    fun toMeter(amplitude: Float): Float {
        val db = 20f * log10(max(amplitude, 1e-6f))
        return ((db - FLOOR_DB) / RANGE_DB).coerceIn(0f, 1f)
    }
}

object Waveform {
    /**
     * Fills [out] with a steady oscilloscope trace: starts at a rising zero crossing in the first half of
     * [samples] so a periodic signal stays put on screen, then averages equal chunks down to out.size points.
     */
    fun fill(samples: FloatArray, out: FloatArray) {
        val points = out.size
        val chunk = max(1, samples.size / (2 * points))
        val window = chunk * points
        val searchEnd = samples.size - window
        var start = 0
        for (i in 1 until max(1, searchEnd)) {
            if (samples[i - 1] < 0f && samples[i] >= 0f) {
                start = i
                break
            }
        }
        for (p in 0 until points) {
            var sum = 0f
            val base = start + p * chunk
            for (k in 0 until chunk) sum += samples[base + k]
            out[p] = sum / chunk
        }
    }
}
