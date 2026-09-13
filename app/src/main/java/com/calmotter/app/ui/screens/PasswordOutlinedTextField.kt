package com.calmotter.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * L'icona è disegnata a mano via `Canvas`, non `Icons.Filled.Visibility`/
 * `VisibilityOff`: quei due glifi vivono solo in `material-icons-extended`
 * (un artefatto molto più pesante — migliaia di icone — di
 * `material-icons-core`, l'unico incluso in `app/build.gradle.kts`),
 * aggiungerlo per due soli glifi non ne varrebbe il costo. Stesso stile
 * "disegnato a mano via Canvas" già usato per le mascotte in
 * `ui/mascot/OtterMarks.kt`.
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

/**
 * Occhio stilizzato (mandorla + pupilla), con una linea diagonale quando
 * [visible] è falso — la stessa coppia di stati di Visibility/VisibilityOff,
 * disegnata a mano invece di importarle (vedi doc di
 * [PasswordOutlinedTextField]).
 */
@Composable
private fun EyeIcon(visible: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()

        val eyeShape = Path().apply {
            moveTo(w * 0.04f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.1f, w * 0.96f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.9f, w * 0.04f, h * 0.5f)
            close()
        }
        drawPath(eyeShape, color = tint, style = Stroke(width = strokeWidth))
        drawCircle(color = tint, radius = h * 0.14f, center = Offset(w * 0.5f, h * 0.5f))

        if (!visible) {
            drawLine(
                color = tint,
                start = Offset(w * 0.06f, h * 0.88f),
                end = Offset(w * 0.94f, h * 0.12f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
