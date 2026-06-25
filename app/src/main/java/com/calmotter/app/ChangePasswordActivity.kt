package com.calmotter.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText

class ChangePasswordActivity : BaseActivity() {
    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var passwordManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        supportActionBar?.apply {
            title = getString(R.string.change_password_title)
            setDisplayHomeAsUpEnabled(true)
        }

        passwordManager = PasswordManager(applicationContext)

        val currentField = findViewById<TextInputEditText>(R.id.currentPasswordField)
        val newField = findViewById<TextInputEditText>(R.id.newPasswordField)
        val confirmField = findViewById<TextInputEditText>(R.id.confirmPasswordField)
        val errorText = findViewById<TextView>(R.id.errorText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        saveButton.setOnClickListener {
            val current = currentField.text.toString()
            val new1 = newField.text.toString()
            val new2 = confirmField.text.toString()

            val error = when {
                !passwordManager.verify(current) -> getString(R.string.wrong_current_password)
                new1.length < 4                  -> getString(R.string.password_too_short)
                new1 != new2                     -> getString(R.string.passwords_dont_match)
                else                             -> null
            }

            if (error != null) {
                errorText.text = error
                errorText.visibility = View.VISIBLE
                // Pulisce solo il campo errato per non costringere a riscrivere tutto
                if (!passwordManager.verify(current)) currentField.text?.clear()
                else { newField.text?.clear(); confirmField.text?.clear() }
            } else {
                passwordManager.setPassword(new1)
                Toast.makeText(this, getString(R.string.password_changed), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
