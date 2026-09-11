package com.calmotter.app

import android.os.Bundle
import androidx.activity.compose.setContent
import com.calmotter.app.ui.screens.ChangePasswordScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

class ChangePasswordActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            title = getString(R.string.change_password_title)
            setDisplayHomeAsUpEnabled(true)
        }

        val passwordManager = PasswordManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                ChangePasswordScreen(
                    passwordManager = passwordManager,
                    onDone = { finish() }
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
