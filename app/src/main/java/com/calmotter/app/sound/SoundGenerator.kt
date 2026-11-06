package com.calmotter.app.sound

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Genera i campioni PCM (16 bit, mono) di un [RelaxSound]. Puro Kotlin, senza
 * Android: si prova su JVM. Tiene solo lo stato dei filtri fra un blocco e
 * l'altro, quindi il flusso non si ripete mai in modo udibile.
 */
class SoundGenerator(private val sound: RelaxSound, seed: Int = Random.nextInt()) {

    private val random = Random(seed)
    private var t = 0L // campioni generati finora

    private var brown = 0.0
    private var rainLow = 0.0
    private var rainHigh = 0.0
    private var rainLevel = 0.8
    private var dropEnv = 0.0
    private var foam = 0.0

    /** Riempie [buffer] con i prossimi campioni. */
    fun fill(buffer: ShortArray) {
        for (i in buffer.indices) {
            val v = when (sound) {
                RelaxSound.OFF -> 0.0
                RelaxSound.BROWN -> brownSample() * 0.9
                RelaxSound.RAIN -> rainSample()
                RelaxSound.WAVES -> waveSample()
                RelaxSound.DRONE -> droneSample()
            }
            buffer[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * HEADROOM).toInt().toShort()
            t++
        }
    }

    private fun white() = random.nextDouble() * 2 - 1

    // Rumore bruno: rumore bianco integrato con perdita, poi riportato a livello.
    private fun brownSample(): Double {
        brown = (brown + 0.02 * white()) / 1.02
        return brown * 3.5
    }

    // Pioggia: rumore morbido, un livello che cambia piano, più gocce rade.
    private fun rainSample(): Double {
        val w = white()
        rainLow += 0.35 * (w - rainLow)
        rainHigh += 0.002 * (rainLow - rainHigh)
        val body = rainLow - rainHigh
        if (t % 512L == 0L) rainLevel = (rainLevel + (random.nextDouble() - 0.5) * 0.08).coerceIn(0.6, 1.0)
        if (random.nextInt(1800) == 0) dropEnv = 0.5 + random.nextDouble() * 0.4
        val drop = dropEnv * white()
        dropEnv *= 0.992
        return body * 0.45 * rainLevel + drop * 0.35
    }

    // Onde: rumore bruno sotto un'onda lenta, con un velo di schiuma sulla cresta.
    private fun waveSample(): Double {
        val phase = 2 * PI * t / (SAMPLE_RATE * WAVE_PERIOD_SECONDS)
        val swell = (1 + sin(phase)) / 2
        val env = 0.15 + 0.85 * swell * swell
        foam += 0.4 * (white() - foam)
        val crest = env * env * env * env
        return brownSample() * env * 0.9 + foam * crest * 0.18
    }

    // Drone: tre note basse appena scordate, con il volume che respira piano.
    private fun droneSample(): Double {
        val sec = t.toDouble() / SAMPLE_RATE
        val a = sin(2 * PI * 55.0 * sec) * (0.6 + 0.4 * sin(2 * PI * sec / 13.0))
        val b = sin(2 * PI * 82.6 * sec) * (0.6 + 0.4 * sin(2 * PI * sec / 11.0 + 1.0))
        val c = sin(2 * PI * 110.4 * sec) * (0.5 + 0.5 * sin(2 * PI * sec / 7.0 + 2.0))
        return (a * 0.5 + b * 0.3 + c * 0.2) * 0.8
    }

    companion object {
        const val SAMPLE_RATE = 22_050
        private const val WAVE_PERIOD_SECONDS = 10.0
        // Un po' sotto il massimo: nessuna saturazione, volume al sistema.
        private const val HEADROOM = 0.7
    }
}
