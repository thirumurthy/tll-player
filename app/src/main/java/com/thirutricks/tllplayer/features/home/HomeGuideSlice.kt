package com.thirutricks.tllplayer.features.home

import android.text.format.DateFormat
import coil3.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import java.util.Date

@Composable
fun HomeLiveRow(
    title: String,
    mode: HomeLiveRowMode,
    channels: List<ChannelEntity>,
    guide: GuideSliceState,
    onChannelClick: (Long, List<ChannelEntity>) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    onContainerDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        HomeLiveRowMode.CARDS -> ChannelCardsRow(
            title = title,
            channels = channels,
            onChannelClick = { id -> onChannelClick(id, channels) },
            onFocus = onFocus,
            firstItemFocusRequester = firstItemFocusRequester,
            modifier = modifier,
        )
        HomeLiveRowMode.ON_NOW -> OnNowRow(
            title = title,
            guide = guide,
            onTuneChannel = { channel -> onChannelClick(channel.id, guide.channels) },
            onFocus = onFocus,
            firstItemFocusRequester = firstItemFocusRequester,
            onContainerDown = onContainerDown,
            modifier = modifier,
        )
    }
}

@Composable
private fun ChannelCardsRow(
    title: String,
    channels: List<ChannelEntity>,
    onChannelClick: (Long) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = OwnTVTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = Dimens.HomeRowPaddingH),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = Dimens.HomeRowPaddingH),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(100.dp),
                ) {
                    FocusableSurface(
                        onClick = { onChannelClick(channel.id) },
                        modifier = when {
                            firstItemFocusRequester != null && index == 0 -> Modifier.focusRequester(firstItemFocusRequester)
                            else -> Modifier
                        }.onFocusChanged { if (it.hasFocus) onFocus() },
                        shape = RoundedCornerShape(14.dp),
                        focusedScale = 1f,
                        focusedContainerColor = OwnTVTheme.colors.surfaceContainerHigh,
                        unfocusedContainerColor = OwnTVTheme.colors.surfaceContainerHigh,
                        selectedContainerColor = OwnTVTheme.colors.surfaceContainerHigh,
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ChannelLogo(channel, size = 44)
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (focused) OwnTVTheme.colors.primary else OwnTVTheme.colors.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnNowRow(
    title: String,
    guide: GuideSliceState,
    onTuneChannel: (ChannelEntity) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    onContainerDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val rows = guide.channels
    val sectionShape = RoundedCornerShape(10.dp)
    var activeRowIndex by remember { mutableIntStateOf(-1) }
    var visibleStartIndex by remember { mutableIntStateOf(0) }

    if (rows.isEmpty()) return

    fun selectRow(index: Int) {
        val target = index.coerceIn(0, rows.lastIndex)
        activeRowIndex = target
        visibleStartIndex = visibleStartFor(target, visibleStartIndex, rows.size)
    }

    fun handleListKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }
        if (activeRowIndex < 0) {
            return if (event.key == Key.DirectionDown && onContainerDown != null) {
                onContainerDown()
                true
            } else {
                false
            }
        }
        return when (event.key) {
            Key.DirectionUp -> {
                selectRow((activeRowIndex - 1).coerceAtLeast(0))
                true
            }
            Key.DirectionDown -> {
                selectRow((activeRowIndex + 1).coerceAtMost(rows.lastIndex))
                true
            }
            Key.DirectionLeft, Key.DirectionRight, Key.Back -> {
                activeRowIndex = -1
                true
            }
            else -> false
        }
    }

    FocusableSurface(
        onClick = {
            if (activeRowIndex < 0) {
                selectRow(visibleStartIndex.coerceAtMost(rows.lastIndex))
            } else {
                rows.getOrNull(activeRowIndex)?.let(onTuneChannel)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.HomeRowPaddingH)
            .then(
                if (firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                },
            )
            .onPreviewKeyEvent(::handleListKey)
            .onFocusChanged {
                if (!it.hasFocus) {
                    activeRowIndex = -1
                }
                if (it.hasFocus) onFocus()
            },
        shape = sectionShape,
        focusedScale = 1f,
        glowElevation = 0,
        showFocusBorder = false,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)
                .border(
                    width = 1.dp,
                    color = if (focused) {
                        colors.primary.copy(alpha = 0.42f)
                    } else {
                        Color.Transparent
                    },
                    shape = sectionShape,
                )
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${title.uppercase()} · ON NOW",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (focused && activeRowIndex >= 0) "OK to watch" else if (focused) "OK to browse" else "${rows.size} channels",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                rows.drop(visibleStartIndex)
                    .take(HOME_ON_NOW_VISIBLE_ROWS)
                    .forEachIndexed { visibleIndex, channel ->
                        val index = visibleStartIndex + visibleIndex
                        OnNowChannelItem(
                            channel = channel,
                            programmes = guide.programmes[channel.id].orEmpty(),
                            now = guide.now,
                            focused = focused && activeRowIndex == index,
                        )
                    }
            }
        }
    }
}

@Composable
private fun OnNowChannelItem(
    channel: ChannelEntity,
    programmes: List<EpgProgrammeEntity>,
    now: Long,
    focused: Boolean,
) {
    val colors = OwnTVTheme.colors
    val context = LocalContext.current
    val rowShape = RoundedCornerShape(8.dp)
    val info = remember(programmes, now, context) {
        val formatTime: (Long) -> String = { ms ->
            DateFormat.getTimeFormat(context).format(Date(ms))
        }
        val programme = currentProgramme(programmes, now) ?: programmes.firstOrNull { it.stopMs > now } ?: programmes.firstOrNull()
        OnNowProgrammeInfo(
            title = programme?.title ?: "No programme details",
            timeLabel = programme?.let { programmeTimeLabel(it, now, formatTime) } ?: "Guide data unavailable",
            progress = programmeProgress(programme, now),
            upcoming = upcomingProgrammes(programmes, programme, HOME_ON_NOW_UPCOMING_COUNT)
                .map { programmeUpcomingLabel(it, formatTime) },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(rowShape)
            .background(
                if (focused) {
                    colors.surfaceContainerHighest
                } else {
                    colors.surfaceContainerHigh.copy(alpha = 0.58f)
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChannelLogoBadge(channel = channel, focused = focused)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = channel.number?.let { "$it  ${channel.name}" } ?: channel.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (focused) 0.88f else 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = info.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (focused) {
                        colors.primary.copy(alpha = 0.82f)
                    } else {
                        colors.onSurfaceVariant.copy(alpha = 0.62f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                ProgrammeProgressBar(progress = info.progress)
            }

            if (info.upcoming.isNotEmpty()) {
                Column(
                    modifier = Modifier.width(300.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Next",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.52f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = info.upcoming.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.44f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class OnNowProgrammeInfo(
    val title: String,
    val timeLabel: String,
    val progress: Float,
    val upcoming: List<String>,
)

private const val HOME_ON_NOW_VISIBLE_ROWS = 5
private const val HOME_ON_NOW_UPCOMING_COUNT = 4

private fun visibleStartFor(
    activeIndex: Int,
    currentStart: Int,
    rowCount: Int,
): Int {
    val maxStart = (rowCount - HOME_ON_NOW_VISIBLE_ROWS).coerceAtLeast(0)
    return when {
        activeIndex < currentStart -> activeIndex
        activeIndex >= currentStart + HOME_ON_NOW_VISIBLE_ROWS -> activeIndex - HOME_ON_NOW_VISIBLE_ROWS + 1
        else -> currentStart
    }.coerceIn(0, maxStart)
}

@Composable
private fun ChannelTextBadge(
    channel: ChannelEntity,
    focused: Boolean,
) {
    val colors = OwnTVTheme.colors
    val text = channel.number?.toString() ?: channel.name.firstOrNull()?.uppercase().orEmpty()
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) colors.primaryContainer else colors.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) colors.onPrimaryContainer else colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelLogoBadge(
    channel: ChannelEntity,
    focused: Boolean,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) colors.primaryContainer else colors.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            ChannelTextBadge(channel = channel, focused = focused)
        }
    }
}

@Composable
private fun ChannelLogo(
    channel: ChannelEntity,
    size: Int,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OwnTVTheme.colors.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            OwnTVIcon(
                OwnTVIcon.LIVE_TV,
                tint = OwnTVTheme.colors.onSurfaceVariant,
                modifier = Modifier.size((size / 2).dp),
            )
        }
    }
}

@Composable
private fun ProgrammeProgressBar(
    progress: Float,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerLowest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.primary),
        )
    }
}

private fun currentProgramme(programmes: List<EpgProgrammeEntity>, now: Long): EpgProgrammeEntity? {
    return programmes.firstOrNull { now in it.startMs until it.stopMs }
}

private fun upcomingProgrammes(
    programmes: List<EpgProgrammeEntity>,
    current: EpgProgrammeEntity?,
    limit: Int,
): List<EpgProgrammeEntity> {
    if (current == null) return programmes.take(limit)
    val currentIndex = programmes.indexOfFirst { it.id == current.id }
    if (currentIndex < 0) return programmes.take(limit)
    return programmes.drop(currentIndex + 1).take(limit)
}

private fun programmeProgress(programme: EpgProgrammeEntity?, now: Long): Float {
    if (programme == null || now !in programme.startMs until programme.stopMs) return 0f
    val duration = (programme.stopMs - programme.startMs).coerceAtLeast(1L)
    return (now - programme.startMs).toFloat() / duration.toFloat()
}

private fun programmeTimeLabel(
    programme: EpgProgrammeEntity,
    now: Long,
    formatTime: (Long) -> String,
): String {
    val time = "${formatTime(programme.startMs)}-${formatTime(programme.stopMs)}"
    return if (now in programme.startMs until programme.stopMs) {
        "NOW · $time"
    } else {
        "UP NEXT · $time"
    }
}

private fun programmeUpcomingLabel(
    programme: EpgProgrammeEntity,
    formatTime: (Long) -> String,
): String {
    return "${formatTime(programme.startMs)} ${programme.title}"
}
