package com.nothing.one.ui.screen.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.data.EntryRepository
import com.nothing.one.data.model.Mood
import com.nothing.one.ui.components.DotGridDivider
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.components.SectionLabel
import com.nothing.one.ui.theme.NothingRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TREND_DATE = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun InsightsScreen(
    onNavigate: (String) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val moodSeries by viewModel.moodSeries.collectAsState()
    val moodCounts by viewModel.moodCounts.collectAsState()
    val journalDays by viewModel.journalDays.collectAsState()
    val topTags by viewModel.topTags.collectAsState()

    val streak = remember(journalDays) { computeStreak(journalDays) }

    // Outer column pins the nav bar; only the content area scrolls.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RedDot(size = 8.dp)
            Spacer(Modifier.width(10.dp))
            DotMatrixText(text = "INSIGHTS", style = MaterialTheme.typography.headlineMedium)
        }
        DotGridDivider()

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Streak
            SectionLabel("STREAK")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                DotMatrixText(
                    text = streak.toString(),
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "DAYS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("MOOD TREND")
            Spacer(Modifier.height(12.dp))
            MoodTrendChart(series = moodSeries.takeLast(30))
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (moodSeries.isEmpty()) "WRITE A FEW DAYS FIRST"
                else "LAST ${moodSeries.takeLast(30).size} ENTRIES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("MOOD MIX")
            Spacer(Modifier.height(12.dp))
            MoodDistribution(counts = moodCounts)

            Spacer(Modifier.height(24.dp))
            SectionLabel("TOP TAGS")
            Spacer(Modifier.height(12.dp))
            if (topTags.isEmpty()) {
                Text(
                    "NO TAGS YET",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                topTags.forEach { (tag, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DotMatrixText(
                            text = tag,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "×$count",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingRed,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

    }
}

/** Consecutive journal days ending at the most recent entry or today. */
internal fun computeStreak(days: List<Long>): Int {
    if (days.isEmpty()) return 0
    val today = EntryRepository.todayIndex()
    val set = days.toSet()
    var start = if (today in set) today else set.last()
    if (start !in set) return 0
    var streak = 0
    var cursor = start
    while (cursor in set) {
        streak++
        cursor--
    }
    return streak
}

/** Bars from oldest to newest; a gap renders as a dot on the baseline. */
@Composable
private fun MoodTrendChart(series: List<Pair<Long, Int>>) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        if (series.isEmpty()) return@Canvas
        val maxMood = 5f
        val slot = size.width / series.size
        val barW = (slot * 0.55f).coerceAtMost(18f)
        val chartH = size.height - 14f
        series.forEachIndexed { i, (_, moodValue) ->
            val x = i * slot + (slot - barW) / 2f
            if (moodValue <= 0) {
                drawCircle(
                    color = outline,
                    radius = 2.dp.toPx(),
                    center = Offset(x + barW / 2f, size.height - 6f),
                )
            } else {
                val h = (moodValue / maxMood) * chartH
                drawRoundRect(
                    color = Mood.fromValue(moodValue)?.color ?: outline,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        // baseline
        drawLine(
            color = outline,
            start = Offset(0f, size.height - 6f),
            end = Offset(size.width, size.height - 6f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
private fun MoodDistribution(counts: Map<Int, Int>) {
    val total = counts.values.sum()
    if (total == 0) {
        Text(
            "NO MOODS YET",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mood.entries.forEach { mood ->
            val count = counts[mood.value] ?: 0
            val frac = count.toFloat() / total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mood.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(52.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(10.dp)
                            .background(mood.color)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
