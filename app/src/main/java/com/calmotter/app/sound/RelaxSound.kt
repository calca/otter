package com.calmotter.app.sound

import androidx.annotation.StringRes
import com.calmotter.app.R

/** I suoni di relax che l'app genera da sola durante una pausa (specs/relax-sound/). */
enum class RelaxSound(@StringRes val labelRes: Int) {
    OFF(R.string.relax_sound_off),
    RAIN(R.string.relax_sound_rain),
    WAVES(R.string.relax_sound_waves),
    BROWN(R.string.relax_sound_brown),
    DRONE(R.string.relax_sound_drone);

    companion object {
        /** Nome salvato → suono; assente o sconosciuto = null. */
        fun fromName(name: String?): RelaxSound? =
            runCatching { valueOf(name ?: "") }.getOrNull()
    }
}
