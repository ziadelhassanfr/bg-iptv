package com.bgiptv.app.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.bgiptv.app.core.data.entity.ChannelEntity
import com.bgiptv.app.core.data.entity.SportEventEntity
import com.bgiptv.app.ui.theme.BgOverlay
import com.bgiptv.app.ui.theme.BgSurfaceVariant

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowserScreen(
    onPlayChannel: (Int) -> Unit = {},
    onSearch: () -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // TV player surface would be here (behind the browser)

        BrowserOverlay(
            uiState = uiState,
            onGroupSelected = viewModel::selectGroup,
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayChannel = onPlayChannel,
            onSearch = onSearch,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowserOverlay(
    uiState: BrowserUiState,
    onGroupSelected: (Int) -> Unit,
    onToggleFavorite: (Int, Boolean) -> Unit,
    onPlayChannel: (Int) -> Unit = {},
    onSearch: () -> Unit = {},
) {
    val overlayBackground = if (uiState.isOverlay) BgOverlay else Color.Black

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayBackground)
    ) {
        // Left column — Groups
        GroupsColumn(
            groups = uiState.groups,
            selectedIndex = uiState.selectedGroupIndex,
            onGroupSelected = onGroupSelected,
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0x99000000))
        )

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0x33FFFFFF))
        )

        // Right column — Content
        ContentColumn(
            content = uiState.rightContent,
            groupLabel = uiState.groups.getOrNull(uiState.selectedGroupIndex)?.label ?: "",
            onToggleFavorite = onToggleFavorite,
            onPlayChannel = onPlayChannel,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }

    // Footer bar
    BrowserFooter(
        channelName = uiState.currentProgramTitle ?: "BG IPTV",
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GroupsColumn(
    groups: List<BrowserGroup>,
    selectedIndex: Int,
    onGroupSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        itemsIndexed(groups) { index, group ->
            val isSelected = index == selectedIndex
            val focusRequester = remember { FocusRequester() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onGroupSelected(index) }
                    .focusable()
                    .background(
                        if (isSelected) Color(0x33FFFFFF) else Color.Transparent
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.emoji,
                    fontSize = 16.sp,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    text = group.label,
                    fontSize = 13.sp,
                    color = if (isSelected) Color.White else Color(0xAAFFFFFF),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (group.channelCount > 0) {
                    Text(
                        text = group.channelCount.toString(),
                        fontSize = 11.sp,
                        color = Color(0x66FFFFFF),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (group.hasLiveEvent) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }

            // Separators between sections
            if (index == 2 || index == groups.indexOfLast { it.tag.contains("SPORT") || it.tag in listOf("FOOT","F1","NBA") }) {
                Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContentColumn(
    content: BrowserContent,
    groupLabel: String,
    onToggleFavorite: (Int, Boolean) -> Unit,
    onPlayChannel: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Header
        Text(
            text = "CHAÎNES — $groupLabel",
            fontSize = 11.sp,
            color = Color(0x99FFFFFF),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Divider(color = Color(0x22FFFFFF))

        when (content) {
            is BrowserContent.Channels -> ChannelList(
                channels = content.channels,
                onToggleFavorite = onToggleFavorite,
                onPlayChannel = onPlayChannel,
            )
            is BrowserContent.Events -> EventList(events = content.events)
            BrowserContent.Empty -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Chargement...", color = Color(0x66FFFFFF), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelList(
    channels: List<ChannelEntity>,
    onToggleFavorite: (Int, Boolean) -> Unit,
    onPlayChannel: (Int) -> Unit = {},
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
        itemsIndexed(channels) { _, channel ->
            ChannelRow(channel = channel, onToggleFavorite = onToggleFavorite, onPlay = { onPlayChannel(channel.streamId) })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelRow(
    channel: ChannelEntity,
    onToggleFavorite: (Int, Boolean) -> Unit,
    onPlay: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (isFocused) Color(0x22FFFFFF) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp)
            .then(Modifier.clickable { onPlay() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.isFavorite) {
            Text("⭐", fontSize = 12.sp, modifier = Modifier.width(20.dp))
        } else {
            Spacer(Modifier.width(20.dp))
        }

        Text(
            text = channel.canonicalName,
            fontSize = 13.sp,
            color = if (isFocused) Color.White else Color(0xCCFFFFFF),
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )

        // Quality badge
        Text(
            text = channel.quality,
            fontSize = 10.sp,
            color = Color(0x55FFFFFF),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EventList(events: List<SportEventEntity>) {
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
        itemsIndexed(events) { _, event ->
            EventRow(event = event)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EventRow(event: SportEventEntity) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFocused) Color(0x22FFFFFF) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Score or time
            val scoreText = if (event.homeScore != null && event.awayScore != null) {
                "${event.homeTeam ?: ""} ${event.homeScore} - ${event.awayScore} ${event.awayTeam ?: ""}"
            } else {
                "${event.homeTeam ?: ""} vs ${event.awayTeam ?: ""}"
            }

            Text(
                text = scoreText,
                fontSize = 14.sp,
                color = if (isFocused) Color.White else Color(0xEEFFFFFF),
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Excitement + minute
            val minuteText = event.minute?.let { "${it}'" } ?: ""
            val excitement = "🔥".repeat((event.excitementScore * 3).toInt().coerceIn(0, 3))
            Text(
                text = "$excitement $minuteText",
                fontSize = 12.sp,
                color = Color(0xAAFFFFFF),
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(
            text = event.competition,
            fontSize = 11.sp,
            color = Color(0x66FFFFFF),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun BrowserFooter(
    channelName: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "▶ $channelName",
                fontSize = 12.sp,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                FooterHint("🟡", "Recherche")
                FooterHint("🔵", "Radar")
                FooterHint("⭐", "Favori")
                FooterHint("≡", "Menu")
            }
        }
    }
}

@Composable
fun RowScope.FooterHint(icon: String, label: String) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 10.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace)
    }
}
