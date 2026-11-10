package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calmotter.app.AllowedAppsMode
import com.calmotter.app.AllowedAppsProfile
import com.calmotter.app.R
import com.calmotter.app.ui.theme.muted

/**
 * Il bivio dei profili: l'elenco, ognuno con una riga che dice cosa fa, e
 * "Nuovo profilo" fisso in fondo. Toccando un profilo si apre la sua
 * pagina (AllowedAppsActivity: suono, regola, app). Prima scelta e
 * configurazione stavano sulla stessa pagina, con pillole, poi un menu a
 * tendina, poi schede: tutte "confusionarie" accanto alla lista delle app.
 *
 * La lista c'è anche con il solo profilo Standard: la pagina non cambia
 * forma da sola, e "Nuovo profilo" si trova subito.
 */
@Composable
fun ProfilesScreen(
    profiles: List<AllowedAppsProfile>,
    canCreateProfile: Boolean,
    onOpenProfile: (Int) -> Unit,
    onCreateProfile: (String) -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(profile = profile, onClick = { onOpenProfile(profile.id) })
            }
        }
        if (canCreateProfile) {
            Button(
                onClick = { naming = true },
                contentPadding = CtaPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 16.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.allowed_profile_new_menu))
            }
        }
    }

    if (naming) {
        ProfileNameDialog(
            initial = "",
            onDismiss = { naming = false },
            onConfirm = { naming = false; onCreateProfile(it) },
        )
    }
}

@Composable
private fun ProfileCard(profile: AllowedAppsProfile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        // Stessa tinta delle righe delle app (vedi AppRow): surface coincide
        // con background nella palette chiara.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profileSummary(profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.muted(0.7f),
                )
                Text(
                    text = stringResource(
                        R.string.profile_summary_sound,
                        stringResource(profile.sound?.labelRes ?: R.string.relax_sound_default),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.muted(0.7f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** "Blocca tutto, tranne 2 app" / "Blocca solo 4 app", con i casi a zero. */
@Composable
private fun profileSummary(profile: AllowedAppsProfile): String {
    val resources = LocalContext.current.resources
    val count = profile.packages.size
    return when {
        profile.mode == AllowedAppsMode.ALLOWLIST && count == 0 -> stringResource(R.string.profile_summary_all)
        profile.mode == AllowedAppsMode.BLOCKLIST && count == 0 -> stringResource(R.string.profile_summary_none)
        profile.mode == AllowedAppsMode.ALLOWLIST -> resources.getQuantityString(R.plurals.profile_summary_allowlist, count, count)
        else -> resources.getQuantityString(R.plurals.profile_summary_blocklist, count, count)
    }
}
