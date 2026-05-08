package com.bgiptv.app.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onSetupComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState.step) {
            SetupStep.WELCOME -> WelcomeStep(
                onPhoneSetup = viewModel::startQrSetup,
                onManualSetup = viewModel::startManualSetup,
            )
            SetupStep.QR_PAIRING -> QrPairingStep(
                qrData = uiState.qrData,
                localUrl = uiState.localUrl,
                onCancel = viewModel::cancelSetup,
            )
            SetupStep.MANUAL_ENTRY -> ManualEntryStep(
                serverUrl = uiState.serverUrl,
                username = uiState.username,
                password = uiState.password,
                error = uiState.error,
                onServerUrlChange = viewModel::onServerUrlChange,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onPasteFull = viewModel::onPasteFullUrl,
                onConfirm = viewModel::confirmManualEntry,
            )
            SetupStep.IMPORTING -> ImportingStep(
                channelCount = uiState.importedChannels,
                epgCount = uiState.importedEpg,
                vodCount = uiState.importedVod,
                onWatchNow = onSetupComplete,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WelcomeStep(
    onPhoneSetup: () -> Unit,
    onManualSetup: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "BG IPTV",
            fontSize = 42.sp,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "Configurons ta playlist.",
            fontSize = 18.sp,
            color = Color(0xAAFFFFFF),
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = onPhoneSetup) {
            Text("📱  Avec mon téléphone  (recommandé)")
        }
        Button(
            onClick = onManualSetup,
            colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF))
        ) {
            Text("⌨   Saisir avec la télécommande")
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "🔒 Tes identifiants restent sur ta TV — aucun serveur tiers",
            fontSize = 12.sp,
            color = Color(0x55FFFFFF),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QrPairingStep(
    qrData: String?,
    localUrl: String?,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Scanne ce QR avec ton téléphone", fontSize = 20.sp, color = Color.White)
        Text("pour configurer ta playlist sans rien taper.", fontSize = 14.sp, color = Color(0x88FFFFFF))

        Spacer(Modifier.height(8.dp))

        // QR placeholder — real QR rendered via a Bitmap in production
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("QR CODE", color = Color.Black, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(8.dp))

        if (localUrl != null) {
            Text("Ou ouvre sur ton téléphone :", fontSize = 13.sp, color = Color(0x88FFFFFF))
            Text(
                text = localUrl,
                fontSize = 16.sp,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(
            text = "⏳  En attente de connexion...",
            fontSize = 14.sp,
            color = Color(0x88FFFFFF),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF))
        ) {
            Text("Annuler")
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManualEntryStep(
    serverUrl: String,
    username: String,
    password: String,
    error: String?,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasteFull: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.width(480.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connexion Xtream Codes", fontSize = 22.sp, color = Color.White)

        if (error != null) {
            Text(error, fontSize = 13.sp, color = Color.Red)
        }

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("URL serveur  (ex: http://provider.com:8080)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Identifiant") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text("Connexion")
            }
        }

        Text(
            text = "💡 Tu peux coller directement le lien m3u complet fourni par ton provider",
            fontSize = 11.sp,
            color = Color(0x55FFFFFF),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImportingStep(
    channelCount: Int,
    epgCount: Int,
    vodCount: Int,
    onWatchNow: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import en cours...", fontSize = 22.sp, color = Color.White)

        Spacer(Modifier.height(8.dp))

        ImportLine(done = channelCount > 0, label = "Chaînes live", value = "$channelCount trouvées")
        ImportLine(done = epgCount > 0, label = "Programmes EPG", value = "$epgCount trouvés")
        ImportLine(done = false, label = "Films VOD", value = "$vodCount / ...")
        ImportLine(done = false, label = "Dédoublonnage", value = "en cours...")

        Spacer(Modifier.height(24.dp))

        if (channelCount > 0) {
            Button(onClick = onWatchNow) {
                Text("▶  Commencer à regarder maintenant")
            }
            Text(
                "(l'import continue en arrière-plan)",
                fontSize = 12.sp,
                color = Color(0x55FFFFFF),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImportLine(done: Boolean, label: String, value: String) {
    Row(
        modifier = Modifier.width(400.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${if (done) "✓" else "⏳"}  $label",
            fontSize = 14.sp,
            color = if (done) Color(0xFF44CC77) else Color(0xAAFFFFFF),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color(0xAAFFFFFF),
            fontFamily = FontFamily.Monospace,
        )
    }
}
