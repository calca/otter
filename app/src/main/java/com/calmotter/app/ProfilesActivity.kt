package com.calmotter.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.calmotter.app.ui.screens.ProfilesScreen
import com.calmotter.app.ui.theme.CalmOtterTheme

/**
 * Il bivio dei profili: l'elenco, da cui si apre la pagina di un profilo
 * ([AllowedAppsActivity]). Raggiungibile solo dopo la password in
 * Impostazioni, come prima la pagina delle app.
 */
class ProfilesActivity : BaseActivity() {

    override val themeVariant = ThemeVariant.WITH_ACTION_BAR

    private lateinit var allowedAppsManager: AllowedAppsManager
    private var profiles by mutableStateOf<List<AllowedAppsProfile>>(emptyList())
    private var canCreate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        allowedAppsManager = AllowedAppsManager.getInstance(applicationContext)

        setContent {
            CalmOtterTheme(appTheme = ThemeManager.getTheme(this)) {
                ProfilesScreen(
                    profiles = profiles,
                    canCreateProfile = canCreate,
                    onOpenProfile = ::openProfile,
                    onCreateProfile = { name ->
                        allowedAppsManager.createProfile(name)?.let { openProfile(it.id) }
                    },
                )
            }
        }
    }

    // Al ritorno dalla pagina di un profilo: nome, regola, app o suono
    // possono essere cambiati, o il profilo eliminato.
    override fun onResume() {
        super.onResume()
        profiles = allowedAppsManager.profiles()
        canCreate = allowedAppsManager.canCreateProfile()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun openProfile(id: Int) {
        startActivity(
            Intent(this, AllowedAppsActivity::class.java)
                .putExtra(AllowedAppsActivity.EXTRA_PROFILE_ID, id)
        )
    }
}
