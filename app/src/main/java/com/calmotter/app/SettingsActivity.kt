package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import com.calmotter.app.ui.screens.SettingsScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Configurazione: personalizzazione (palette) e le due azioni protette da
 * password (cambio password, elenco app consentite) più le preferenze non
 * critiche — spostate qui dalla Home (vedi MainActivity/MainScreen) per
 * tenerla ridotta al solo avvio di una pausa.
 *
 * Nessun resumeSignal: come HistoryActivity/AllowedAppsActivity, niente
 * qui dipende da stato del sistema operativo che può cambiare mentre
 * l'Activity è in background.
 */
class SettingsActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var passwordManager: PasswordManager
    private lateinit var phraseManager: PhraseManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        passwordManager = PasswordManager.getInstance(applicationContext)
        phraseManager = PhraseManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                SettingsScreen(
                    currentTheme = ThemeManager.getTheme(this),
                    phraseManager = phraseManager,
                    onPickTheme = { theme -> pickTheme(theme) },
                    onManageApps = { promptPasswordThenOpenAllowedApps() },
                    onChangePassword = { startActivity(Intent(this, ChangePasswordActivity::class.java)) },
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun pickTheme(theme: AppTheme) {
        if (ThemeManager.getTheme(this) == theme) return
        ThemeManager.setTheme(this, theme)
        // Ricrea l'activity per applicare il nuovo tema immediatamente
        recreate()
    }

    /**
     * L'elenco delle app extra consentite durante una pausa può essere
     * modificato solo da chi conosce la password: la verifica avviene qui,
     * prima di aprire AllowedAppsActivity.
     */
    private fun promptPasswordThenOpenAllowedApps() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.hint_unlock_password)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.manage_allowed_apps)
            .setMessage(R.string.manage_allowed_apps_password_prompt)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (passwordManager.isLockedOut()) {
                    // Dialog "usa e getta": niente countdown live, mostriamo
                    // solo quanto manca al termine del lockout in questo momento.
                    Toast.makeText(
                        this,
                        getString(R.string.password_locked_out, passwordManager.lockoutRemainingSeconds()),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (passwordManager.verify(input.text.toString())) {
                    startActivity(Intent(this, AllowedAppsActivity::class.java))
                } else {
                    Toast.makeText(this, getString(R.string.wrong_password), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
