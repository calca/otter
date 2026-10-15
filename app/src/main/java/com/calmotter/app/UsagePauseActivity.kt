package com.calmotter.app

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.calmotter.app.ui.screens.PasswordVerifyDialog
import com.calmotter.app.ui.screens.UsagePauseScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Pausa dopo un uso lungo (specs/usage-pause/). Si apre senza password; una
 * regola non protetta si cambia liberamente, proteggerla e poi allentarla
 * passa dalla password (le regole in [usagePasswordNeededToSave]).
 */
class UsagePauseActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var manager: UsagePauseManager
    private var rule by mutableStateOf(UsagePauseRule())
    private var accessibilityOn by mutableStateOf(true)
    /** Modifica in attesa della password; null = nessuna. */
    private var pending by mutableStateOf<UsagePauseRule?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.apply {
            title = getString(R.string.usage_pause_title)
            setDisplayHomeAsUpEnabled(true)
        }
        manager = UsagePauseManager.getInstance(applicationContext)
        rule = manager.rule()
        val passwordManager = PasswordManager.getInstance(applicationContext)
        val profiles = AllowedAppsManager.getInstance(applicationContext).profiles()

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                UsagePauseScreen(
                    rule = rule,
                    profiles = profiles,
                    accessibilityOn = accessibilityOn,
                    onChange = ::saveGuarded,
                )
                pending?.let { updated ->
                    val protecting = updated.locked && !rule.locked
                    PasswordVerifyDialog(
                        passwordManager = passwordManager,
                        title = stringResource(R.string.usage_pause_title),
                        message = stringResource(
                            if (protecting) R.string.usage_pause_lock_prompt else R.string.usage_pause_password_prompt
                        ),
                        confirmLabel = stringResource(R.string.confirm),
                        onDismiss = { pending = null },
                        onVerified = { pending = null; save(updated) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityOn = BuildConfig.DEBUG || isAccessibilityServiceEnabled(this)
    }

    private fun saveGuarded(updated: UsagePauseRule) {
        if (usagePasswordNeededToSave(rule, updated)) pending = updated else save(updated)
    }

    private fun save(updated: UsagePauseRule) {
        manager.saveRule(updated)
        rule = updated
        AppBlockerAccessibilityService.onUsageRuleChanged()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
