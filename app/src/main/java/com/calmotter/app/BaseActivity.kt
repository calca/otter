package com.calmotter.app

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Activity base da cui ereditano tutte le schermate dell'app.
 * Applica il tema scelto dall'utente prima che il sistema gonfi
 * il layout, così i colori sono corretti fin dal primo frame.
 *
 * Le sottoclassi possono sovrascrivere [themeVariant] se usano
 * un tema con ActionBar o il tema Block.
 */
abstract class BaseActivity : AppCompatActivity() {

    open val themeVariant: ThemeVariant = ThemeVariant.BASE

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this, themeVariant)
        super.onCreate(savedInstanceState)

        // Solo da Android 15 (API 35) l'edge-to-edge imposto da targetSdk 37
        // rende la status bar trasparente: le sue icone finiscono disegnate
        // sopra lo sfondo reale della pagina (chiaro in modalità giorno), non
        // più su una fascia colorata a parte, quindi devono diventare scure
        // per restare leggibili. Sotto API 35 la status bar resta una fascia
        // opaca (colorPrimary, impostato altrove) pensata per icone chiare —
        // comportamento Material classico, invariato — quindi qui non si
        // applica nulla.
        if (Build.VERSION.SDK_INT >= 35) {
            val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode
        }

        // La ActionBar nativa (Settings/History/ChangePassword/AllowedApps/
        // Onboarding) di base non è a schermo piatto: Theme.Material3.DayNight
        // le applica di suo un colore di sfondo diverso da quello reale della
        // pagina (android:colorBackground, che questo tema non imposta mai —
        // resta al bianco/viola di base di Material3 — invece del vero
        // android:windowBackground di ogni palette) più un'elevazione, che la
        // fanno leggere come una fascia separata. Impostare `actionBarStyle`/
        // `elevation` da XML risulta ignorato dal decoratore Material3
        // dell'ActionBar nativa (verificato), quindi si forza qui via API,
        // risolvendo il vero colore di sfondo della finestra (non
        // colorBackground, che è un attr Material3 distinto e mai impostato
        // da questo tema).
        supportActionBar?.let { actionBar ->
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
            val backgroundColor = if (typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                getColor(typedValue.resourceId)
            }
            actionBar.setBackgroundDrawable(ColorDrawable(backgroundColor))
            actionBar.elevation = 0f
        }
    }
}
