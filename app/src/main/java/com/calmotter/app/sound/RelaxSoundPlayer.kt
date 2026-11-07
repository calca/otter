package com.calmotter.app.sound

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Suona un [RelaxSound] per tutta la durata della pausa. Appartiene al
 * servizio in primo piano, che lo crea, lo ferma e lo mette in pausa; nessuna
 * rete, nessun file: i campioni li produce [SoundGenerator].
 *
 * Cede il passo a ciò che conta (chiamate, sveglie, altre app: focus audio) e
 * si ferma se le cuffie vengono staccate. Un suono messo in pausa da fuori
 * (focus perso, cuffie) torna da solo solo se è il sistema a ridare il focus.
 */
class RelaxSoundPlayer(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var focus: AudioFocusRequest? = null

    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var ducked = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) setPaused(true)
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> { ducked = false; setPaused(false) }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> ducked = true
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> setPaused(true)
        }
    }

    val isPlaying: Boolean get() = running && !paused

    /** Parte (o cambia suono). [sound] OFF ferma. */
    fun play(sound: RelaxSound) {
        stop()
        if (sound == RelaxSound.OFF) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = request

        val minBuffer = AudioTrack.getMinBufferSize(
            SoundGenerator.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SoundGenerator.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, BLOCK * 2 * 4))
            .build()
        track = newTrack
        paused = false
        ducked = false
        running = true
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        newTrack.play()

        val generator = SoundGenerator(sound)
        worker = Thread({
            val block = ShortArray(BLOCK)
            var fade = 0f // dissolvenza in entrata, circa 3 secondi
            while (running) {
                if (paused) {
                    try { Thread.sleep(150) } catch (_: InterruptedException) { break }
                    continue
                }
                generator.fill(block)
                if (fade < 1f || ducked) {
                    val gain = (if (fade < 1f) fade else 1f) * (if (ducked) DUCK_GAIN else 1f)
                    for (i in block.indices) block[i] = (block[i] * gain).toInt().toShort()
                    fade = (fade + BLOCK.toFloat() / (SoundGenerator.SAMPLE_RATE * FADE_IN_SECONDS)).coerceAtMost(1f)
                }
                if (newTrack.write(block, 0, block.size) < 0) break
            }
        }, "relax-sound").also { it.isDaemon = true; it.start() }
    }

    /** Mette in pausa o riprende senza distruggere la traccia. */
    fun setPaused(value: Boolean) {
        if (!running || paused == value) return
        paused = value
        runCatching { if (value) track?.pause() else track?.play() }
    }

    fun stop() {
        if (!running && track == null) return
        running = false
        runCatching { context.unregisterReceiver(noisyReceiver) }
        worker?.interrupt()
        worker?.join(500)
        worker = null
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        paused = false
    }

    private companion object {
        const val BLOCK = 2048
        const val FADE_IN_SECONDS = 3f
        const val DUCK_GAIN = 0.3f
    }
}
