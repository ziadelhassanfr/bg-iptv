package com.bgiptv.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*

sealed class SearchResult {
    data class LiveEvent(val eventId: String, val title: String, val competition: String, val channelName: String, val minute: Int?) : SearchResult()
    data class UpcomingEvent(val eventId: String, val title: String, val competition: String, val channelName: String, val startTime: String) : SearchResult()
    data class Channel(val streamId: Int, val name: String, val currentProgram: String?) : SearchResult()
    data class EpgProgram(val channelId: Int, val title: String, val channelName: String, val startTime: String) : SearchResult()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onSelectChannel: (Int) -> Unit,
    onSelectEvent: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0000000))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Rechercher chaîne, match, film...", color = Color(0x66FFFFFF)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A9EFF),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF4A9EFF),
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF))
                ) { Text("✕") }
            }

            // Results
            if (uiState.query.isBlank()) {
                SearchHints()
            } else if (uiState.results.isEmpty() && !uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun résultat pour \"${uiState.query}\"", color = Color(0x66FFFFFF))
                }
            } else {
                SearchResults(
                    results = uiState.results,
                    onSelectChannel = onSelectChannel,
                    onSelectEvent = onSelectEvent,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchResults(
    results: List<SearchResult>,
    onSelectChannel: (Int) -> Unit,
    onSelectEvent: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Live events first
        val liveEvents = results.filterIsInstance<SearchResult.LiveEvent>()
        if (liveEvents.isNotEmpty()) {
            item {
                SectionHeader("🔴 EN DIRECT")
            }
            items(liveEvents) { event ->
                SearchRow(
                    icon = "⚽",
                    title = event.title,
                    subtitle = "${event.competition}  •  ${event.channelName}  •  ${event.minute ?: 0}'",
                    accentColor = Color(0xFFFF4444),
                    onClick = { onSelectEvent(event.eventId) },
                )
            }
        }

        // Upcoming events
        val upcoming = results.filterIsInstance<SearchResult.UpcomingEvent>()
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("⏰ BIENTÔT") }
            items(upcoming) { event ->
                SearchRow(
                    icon = "⚽",
                    title = event.title,
                    subtitle = "${event.competition}  •  ${event.startTime}  •  ${event.channelName}",
                    onClick = { onSelectEvent(event.eventId) },
                )
            }
        }

        // Channels
        val channels = results.filterIsInstance<SearchResult.Channel>()
        if (channels.isNotEmpty()) {
            item { SectionHeader("📺 CHAÎNES") }
            items(channels) { ch ->
                SearchRow(
                    icon = "📺",
                    title = ch.name,
                    subtitle = ch.currentProgram ?: "",
                    onClick = { onSelectChannel(ch.streamId) },
                )
            }
        }

        // EPG programs
        val programs = results.filterIsInstance<SearchResult.EpgProgram>()
        if (programs.isNotEmpty()) {
            item { SectionHeader("📅 PROGRAMMES À VENIR") }
            items(programs) { prog ->
                SearchRow(
                    icon = "📅",
                    title = prog.title,
                    subtitle = "${prog.channelName}  •  ${prog.startTime}",
                    onClick = { onSelectChannel(prog.channelId) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchRow(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: Color = Color(0xAAFFFFFF),
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color(0x22FFFFFF) else Color.Transparent)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 16.sp, modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White, fontFamily = FontFamily.Monospace)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 11.sp, color = Color(0x66FFFFFF), fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SectionHeader(label: String) {
    Text(
        text = label,
        fontSize = 11.sp,
        color = Color(0x66FFFFFF),
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchHints() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Cherche par :", color = Color(0x66FFFFFF), fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        listOf("⚽  Nom d'équipe ou compétition", "📺  Nom de chaîne", "🎬  Titre d'un film (VOD)", "📅  Programme EPG").forEach {
            Text(it, color = Color(0x44FFFFFF), fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}
