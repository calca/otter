package com.calmotter.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.calmotter.app.R

/**
 * `OutlinedTextField` per password condiviso da tutti i punti dove l'utente
 * ne digita una (`PasswordVerifyDialog`, `ChangePasswordScreen`,
 * `OnboardingScreen`'s step password) — mascherata di default, con
 * un'icona "occhio" per mostrarla in chiaro temporaneamente, su richiesta.
 *
 * Non usa `Icons.Filled.Visibility`/`VisibilityOff`: quei due glifi vivono
 * solo in `material-icons-extended` (un artefatto molto più pesante —
 * migliaia di icone — di `material-icons-core`, l'unico incluso in
 * `app/build.gradle.kts`), aggiungerlo per due soli glifi non ne varrebbe
 * il costo. Invece [EyeIcon] disegna gli stessi identici path vettoriali
 * ufficiali Material (licenza Apache-2.0, gli stessi dati usati da
 * `Icons.Filled.Visibility`/`VisibilityOff`), parsati con
 * `PathParser`/`toPath()` invece di riprodurli a occhio — un primo
 * tentativo disegnato interamente a mano (mandorla + pupilla via
 * `quadraticTo`) è stato scartato perché visibilmente non allineato al
 * linguaggio visivo Material.
 */
@Composable
fun PasswordOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // **Niente compilazione automatica su queste password.**
    //
    // Due motivi, e il secondo conta più del primo.
    //
    // 1. Bug reale su Galaxy S22 (One UI): toccando il campo password
    //    durante una pausa, il servizio di compilazione automatica apre una
    //    propria finestra sopra al dialog. Quella finestra genera un
    //    TYPE_WINDOW_STATE_CHANGED con il *suo* package
    //    (`com.google.android.ext.services` nel caso osservato), che
    //    [AppBlockerAccessibilityService] leggeva come "app non consentita"
    //    e copriva col blocco proprio mentre si digitava.
    //
    // 2. Soprattutto: una password salvata nel gestore del telefono
    //    contraddice il senso della password stessa. La conosce la persona
    //    di fiducia, non chi è in pausa — se il telefono la ricompila da
    //    solo, chi si è messo in pausa se la sblocca da sé e il patto non
    //    vale più nulla. Questo vale anche dove il bug non si presenta.
    //
    // `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` sulla View che ospita
    // questa composizione vale per l'intera finestra: per un dialog è la sua
    // finestra, per una schermata quella dell'Activity — in entrambi i casi
    // esattamente ciò che contiene i campi di questa app.
    val hostView = LocalView.current
    LaunchedEffect(hostView) {
        hostView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            val description = stringResource(
                if (passwordVisible) R.string.hide_password else R.string.show_password
            )
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                EyeIcon(
                    visible = passwordVisible,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        modifier = modifier,
    )
}

// Path data dei drawable ufficiali Material Design "ic_visibility_24" /
// "ic_visibility_off_24" (Apache-2.0), viewport 24x24 — gli stessi dati
// grezzi da cui material-icons-extended costruisce Icons.Filled.Visibility
// / VisibilityOff.
private const val VISIBILITY_PATH =
    "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5" +
        "c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 " +
        "5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 " +
        "-1.34,-3 -3,-3z"

private const val VISIBILITY_OFF_PATH =
    "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92" +
        "c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 " +
        "-1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
        "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10 1,12c1.73,4.39 6,7.5 11,7.5 " +
        "1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
        "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 " +
        "0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 " +
        "-2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z" +
        "M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.02z"

/**
 * Renderizza [VISIBILITY_PATH] o [VISIBILITY_OFF_PATH] scalati al canvas —
 * stesso identico glifo Material di Icons.Filled.Visibility/VisibilityOff,
 * senza dipendere da material-icons-extended (vedi doc di
 * [PasswordOutlinedTextField]).
 */
@Composable
private fun EyeIcon(visible: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val path = remember(visible) {
        PathParser().parsePathString(if (visible) VISIBILITY_PATH else VISIBILITY_OFF_PATH).toPath()
    }
    Canvas(modifier = modifier.size(22.dp)) {
        val viewportSize = 24f
        val scaleFactor = size.minDimension / viewportSize
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
            drawPath(path, color = tint)
        }
    }
}
