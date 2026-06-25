package com.calmotter.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

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
    }
}
