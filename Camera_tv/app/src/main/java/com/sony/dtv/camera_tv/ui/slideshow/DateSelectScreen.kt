package com.sony.dtv.camera_tv.ui.slideshow

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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.domain.MonthGrid
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes
import java.time.LocalDate
import java.time.YearMonth

/** focusedRow == 0 は月送りヘッダー。1..6 がカレンダー本体。 */
private const val HEADER_ROW = 0

/**
 * 日付選択画面（カレンダー）。
 * 写真がある日だけ選択でき、月送りは最上段の左右で行う。
 */
@Composable
internal fun DateSelectScreen(
    dates: List<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val availableDates = remember(dates) { dates.toSet() }
    val initialMonth = remember(dates, selectedDate) {
        YearMonth.from(selectedDate ?: dates.firstOrNull() ?: LocalDate.now())
    }

    var currentMonth by remember { mutableStateOf(initialMonth) }
    var focusedRow by remember { mutableIntStateOf(1) }
    var focusedCol by remember { mutableIntStateOf(0) }

    val grid = remember(currentMonth) { MonthGrid.of(currentMonth) }

    // 月を切り替えたら、その月の選択日か、写真がある最初の日へフォーカスを寄せる
    LaunchedEffect(currentMonth) {
        val target = selectedDate?.takeIf { YearMonth.from(it) == currentMonth }
            ?: dates.filter { YearMonth.from(it) == currentMonth }.minOrNull()
        val position = target?.let { MonthGrid.positionOf(currentMonth, it) }
        focusedRow = (position?.first ?: 0) + 1
        focusedCol = position?.second ?: 0
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            when (key) {
                Key.DirectionLeft -> {
                    if (focusedRow == HEADER_ROW) currentMonth = currentMonth.minusMonths(1)
                    else if (focusedCol > 0) focusedCol--
                    else if (focusedRow > 1) { focusedRow--; focusedCol = MonthGrid.COLUMNS - 1 }
                    true
                }

                Key.DirectionRight -> {
                    if (focusedRow == HEADER_ROW) currentMonth = currentMonth.plusMonths(1)
                    else if (focusedCol < MonthGrid.COLUMNS - 1) focusedCol++
                    else if (focusedRow < MonthGrid.ROWS) { focusedRow++; focusedCol = 0 }
                    true
                }

                Key.DirectionUp -> {
                    if (focusedRow > HEADER_ROW) focusedRow--
                    true
                }

                Key.DirectionDown -> {
                    if (focusedRow < MonthGrid.ROWS) focusedRow++
                    true
                }

                Key.Enter, Key.DirectionCenter -> {
                    val date = grid.getOrNull(focusedRow - 1)?.getOrNull(focusedCol)
                    if (date != null && date in availableDates) onDateSelected(date)
                    true
                }

                Key.Back -> { onBack(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvDimens.ScreenPadding, vertical = TvDimens.SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        ) {
            MonthHeader(month = currentMonth, isFocused = focusedRow == HEADER_ROW)
            DayOfWeekHeader()

            grid.forEachIndexed { rowIndex, week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceXs),
                ) {
                    week.forEachIndexed { colIndex, date ->
                        DayCell(
                            date = date,
                            hasPhotos = date != null && date in availableDates,
                            isFocused = focusedRow == rowIndex + 1 && focusedCol == colIndex,
                            isSelected = date != null && date == selectedDate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(TvDimens.SpaceSm))
            CalendarLegend()
            KeyHint(
                text = stringResource(R.string.date_select_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, isFocused: Boolean) {
    Row(
        modifier = Modifier
            .clip(TvShapes.Pill)
            .background(if (isFocused) TvColors.SurfaceVariant else Color.Transparent)
            .padding(horizontal = TvDimens.SpaceLg, vertical = TvDimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\u25C0",
            color = if (isFocused) TvColors.OnSurface else TvColors.OnSurfaceDisabled,
            fontSize = TvTextSizes.Label,
        )
        Text(
            text = stringResource(R.string.date_select_month, month.year, month.monthValue),
            color = TvColors.OnSurface,
            fontSize = TvTextSizes.Headline,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "\u25B6",
            color = if (isFocused) TvColors.OnSurface else TvColors.OnSurfaceDisabled,
            fontSize = TvTextSizes.Label,
        )
    }
}

@Composable
private fun DayOfWeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceXs),
    ) {
        MonthGrid.DAY_LABELS.forEach { label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = label, color = TvColors.OnSurfaceMuted, fontSize = TvTextSizes.Body)
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    hasPhotos: Boolean,
    isFocused: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isFocused && hasPhotos -> TvColors.Focus
        isFocused -> TvColors.SurfaceVariant
        isSelected -> TvColors.AccentDim
        hasPhotos -> TvColors.AccentFaint
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused && hasPhotos -> TvColors.Background
        hasPhotos -> TvColors.OnSurface
        else -> TvColors.OnSurfaceDisabled
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(TvShapes.Small)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                fontSize = TvTextSizes.Body,
                fontWeight = if (hasPhotos) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceLg)) {
        LegendItem(color = TvColors.AccentFaint, label = stringResource(R.string.date_select_legend_has_photo))
        LegendItem(color = TvColors.SurfaceVariant, label = stringResource(R.string.date_select_legend_no_photo))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(16.dp).clip(TvShapes.Small).background(color))
        Spacer(modifier = Modifier.size(TvDimens.SpaceXs))
        Text(text = label, color = TvColors.OnSurfaceMuted, fontSize = TvTextSizes.Caption)
    }
}
