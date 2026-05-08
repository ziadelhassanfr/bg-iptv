package com.bgiptv.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*

enum class SettingsLevel { BASE, ADVANCED, EXPERT }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var level by remember { mutableStateOf(SettingsLevel.BASE) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⚙  Réglages",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                // Level tabs
                SettingsLevel.entries.forEach { tab ->
                    val selected = tab == level
                    Button(
                        onClick = { level = tab },
                        colors = ButtonDefaults.colors(
                            containerColor = if (selected) Color(0xFF4A9EFF) else Color(0x22FFFFFF)
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Text(tab.name, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF)),
                ) { Text("✕") }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Update banner
                if (uiState.updateAvailable != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A3A5C))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "🆕  Mise à jour disponible : v${uiState.updateAvailable}",
                                color = Color(0xFF4A9EFF),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { uiState.updateUrl?.let { viewModel.installUpdate(it) } }) {
                                Text("Installer", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // BASE level settings
                item { SettingsSectionHeader("LECTURE") }

                item {
                    SettingsToggle(
                        label = "HDR auto",
                        subtitle = "HDR10 activé, Dolby Vision désactivé (Samsung)",
                        value = uiState.hdrMode == "auto",
                        onToggle = { viewModel.setHdrMode(if (it) "auto" else "sdr") },
                    )
                }

                item {
                    SettingsToggle(
                        label = "Audio passthrough HDMI",
                        subtitle = "Dolby Digital / DTS sans décodage",
                        value = uiState.audioPassthrough,
                        onToggle = viewModel::setAudioPassthrough,
                    )
                }

                item {
                    SettingsToggle(
                        label = "Décodage matériel",
                        subtitle = "HEVC / H.264 via le GPU",
                        value = uiState.hardwareDecode,
                        onToggle = viewModel::setHardwareDecode,
                    )
                }

                // ADVANCED level settings
                if (level >= SettingsLevel.ADVANCED) {
                    item { Spacer(Modifier.height(8.dp)); SettingsSectionHeader("INTERFACE") }

                    item {
                        SettingsToggle(
                            label = "Pré-buffer chaîne suivante",
                            subtitle = "Zap instantané — utilise ~10 MB de RAM extra",
                            value = uiState.preBufferEnabled,
                            onToggle = viewModel::setPreBufferEnabled,
                        )
                    }

                    item {
                        SettingsToggle(
                            label = "Frame rate matching",
                            subtitle = "Bascule la TV en 50Hz pour le contenu PAL",
                            value = uiState.frameRateMatch,
                            onToggle = viewModel::setFrameRateMatch,
                        )
                    }

                    item {
                        SettingsSlider(
                            label = "Masquage auto du menu",
                            value = uiState.autoHideDelaySec,
                            valueLabel = "${uiState.autoHideDelaySec}s",
                            range = 3..30,
                            onValueChange = viewModel::setAutoHideDelay,
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)); SettingsSectionHeader("SÉCURITÉ") }

                    item {
                        SettingsToggle(
                            label = "PIN au démarrage",
                            subtitle = "Protège l'accès à l'app (désactivé par défaut)",
                            value = uiState.pinEnabled,
                            onToggle = viewModel::setPinEnabled,
                        )
                    }
                }

                // EXPERT level settings
                if (level >= SettingsLevel.EXPERT) {
                    item { Spacer(Modifier.height(8.dp)); SettingsSectionHeader("EXPERT") }

                    item {
                        SettingsToggle(
                            label = "Tunneling Android TV",
                            subtitle = "Décodage vidéo bas-latence via SurfaceView tunnel",
                            value = uiState.tunnelingEnabled,
                            onToggle = viewModel::setTunnelingEnabled,
                        )
                    }

                    item {
                        SettingsSlider(
                            label = "Cache disque",
                            value = uiState.cacheSizeMb,
                            valueLabel = "${uiState.cacheSizeMb} MB",
                            range = 100..2000,
                            onValueChange = viewModel::setCacheSizeMb,
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)); SettingsSectionHeader("CONNEXIONS SORTANTES") }

                    item {
                        OutgoingConnectionsInfo()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsSectionHeader(label: String) {
    Text(
        text = label,
        fontSize = 10.sp,
        color = Color(0x66FFFFFF),
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsToggle(
    label: String,
    subtitle: String,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color(0x15FFFFFF) else Color.Transparent)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Color.White, fontFamily = FontFamily.Monospace)
            Text(subtitle, fontSize = 11.sp, color = Color(0x66FFFFFF), fontFamily = FontFamily.Monospace)
        }
        Switch(
            checked = value,
            onCheckedChange = onToggle,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsSlider(
    label: String,
    value: Int,
    valueLabel: String,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 14.sp, color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(valueLabel, fontSize = 14.sp, color = Color(0xFF4A9EFF), fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onValueChange(value - (range.last - range.first) / 10) },
                colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF)),
                modifier = Modifier.size(36.dp),
            ) { Text("−", fontSize = 16.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(Color(0x33FFFFFF))
            ) {
                val fraction = (value - range.first).toFloat() / (range.last - range.first)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(Color(0xFF4A9EFF))
                )
            }
            Button(
                onClick = { onValueChange(value + (range.last - range.first) / 10) },
                colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF)),
                modifier = Modifier.size(36.dp),
            ) { Text("+", fontSize = 16.sp) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OutgoingConnectionsInfo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x11FFFFFF))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "Ton provider IPTV" to "Flux vidéo + EPG + VOD",
            "football-data.org" to "Scores et calendriers foot",
            "github.com/ziadelhassanfr/bg-iptv" to "Vérification de mise à jour",
        ).forEach { (host, purpose) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "▸  $host",
                    fontSize = 12.sp,
                    color = Color(0xCCFFFFFF),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    purpose,
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Aucune donnée personnelle envoyée. Aucun SDK tiers.",
            fontSize = 11.sp,
            color = Color(0x44FFFFFF),
            fontFamily = FontFamily.Monospace,
        )
    }
}
