package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.domain.SortMode
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

private val DIALOG_WIDTH = 640.dp
private val OPTION_ICON_SIZE = 28.dp

/**
 * 並び順の選択ダイアログ。
 *
 * ボタンのラベルが「日付順」「評価順」と入れ替わる方式は、
 * それが現在の状態なのか押した結果なのか分からないため、選択式にしている。
 */
@Composable
internal fun SortDialog(
    current: SortMode,
    onSelected: (SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = SortMode.entries
    var index by remember { mutableIntStateOf(options.indexOf(current).coerceAtLeast(0)) }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.ScrimStrong),
        onKey = { key ->
            when (key) {
                Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); true }
                Key.DirectionDown -> { index = (index + 1).coerceAtMost(options.lastIndex); true }
                Key.Enter, Key.DirectionCenter -> { onSelected(options[index]); true }
                Key.Back -> { onDismiss(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(DIALOG_WIDTH)
                .clip(TvShapes.Large)
                .background(TvColors.Surface)
                .padding(TvDimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        ) {
            Text(
                text = stringResource(R.string.sort_title),
                color = TvColors.OnSurface,
                fontSize = TvTextSizes.Title,
                fontWeight = FontWeight.Bold,
            )
            options.forEachIndexed { i, mode ->
                SortOption(
                    icon = mode.icon(),
                    label = stringResource(mode.labelRes()),
                    detail = stringResource(mode.detailRes()),
                    isSelected = mode == current,
                    isFocused = i == index,
                )
            }
            KeyHint(
                text = stringResource(R.string.sort_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SortOption(
    icon: ImageVector,
    label: String,
    detail: String,
    isSelected: Boolean,
    isFocused: Boolean,
) {
    val contentColor = if (isFocused) TvColors.Background else TvColors.OnSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvShapes.Medium)
            .background(if (isFocused) TvColors.Focus else TvColors.SurfaceVariant)
            .border(
                width = if (isFocused) TvDimens.FocusBorder else 1.dp,
                color = if (isFocused) TvColors.Focus else TvColors.SurfaceVariant,
                shape = TvShapes.Medium,
            )
            .padding(TvDimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(OPTION_ICON_SIZE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = contentColor,
                fontSize = TvTextSizes.Label,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                color = if (isFocused) TvColors.Background else TvColors.OnSurfaceMuted,
                fontSize = TvTextSizes.Caption,
            )
        }
        // いま選ばれている方をチェックで示す。フォーカスとは別の情報なので混ぜない
        Box(modifier = Modifier.size(OPTION_ICON_SIZE), contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (isFocused) TvColors.Background else TvColors.Pick,
                )
            }
        }
    }
}

private fun SortMode.icon(): ImageVector = when (this) {
    SortMode.DateDesc -> Icons.Filled.DateRange
    SortMode.RatingDesc -> Icons.Filled.Star
}

private fun SortMode.labelRes(): Int = when (this) {
    SortMode.DateDesc -> R.string.sort_date_label
    SortMode.RatingDesc -> R.string.sort_rating_label
}

private fun SortMode.detailRes(): Int = when (this) {
    SortMode.DateDesc -> R.string.sort_date_detail
    SortMode.RatingDesc -> R.string.sort_rating_detail
}
