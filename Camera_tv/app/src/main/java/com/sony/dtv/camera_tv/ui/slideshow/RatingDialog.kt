package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.domain.ContentRating
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

/**
 * 5段階評価ダイアログ。
 * ← / → で 0（評価なし）〜5 を選び、決定で確定する。
 */
@Composable
internal fun RatingDialog(
    currentRating: Int,
    isLoading: Boolean,
    onRatingSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(ContentRating.clamp(currentRating)) }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.ScrimStrong),
        onKey = { key ->
            when (key) {
                Key.DirectionLeft -> { selected = (selected - 1).coerceAtLeast(ContentRating.NONE); true }
                Key.DirectionRight -> { selected = (selected + 1).coerceAtMost(ContentRating.MAX); true }
                Key.Enter, Key.DirectionCenter -> {
                    if (!isLoading) onRatingSelected(selected)
                    true
                }

                Key.Back -> { onDismiss(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceLg),
        ) {
            Text(
                text = stringResource(R.string.rating_title),
                color = TvColors.OnSurface,
                fontSize = TvTextSizes.Headline,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (selected == ContentRating.NONE) {
                    stringResource(R.string.rating_none)
                } else {
                    stringResource(R.string.rating_value, selected)
                },
                color = TvColors.Star,
                fontSize = TvTextSizes.Title,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RatingChip(
                    glyph = "\u2715",
                    isFocused = selected == ContentRating.NONE,
                    isFilled = false,
                )
                for (rating in ContentRating.MIN..ContentRating.MAX) {
                    RatingChip(
                        glyph = "\u2605",
                        isFocused = rating == selected,
                        isFilled = rating <= selected,
                    )
                }
            }

            KeyHint(
                text = stringResource(R.string.rating_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            if (isLoading) {
                CircularProgressIndicator(color = TvColors.OnSurface, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun RatingChip(glyph: String, isFocused: Boolean, isFilled: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "ratingChipScale",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(72.dp)
            .background(
                color = if (isFilled) TvColors.Star else TvColors.SurfaceVariant,
                shape = CircleShape,
            )
            .border(
                width = if (isFocused) TvDimens.FocusBorder else 1.dp,
                color = if (isFocused) TvColors.Focus else TvColors.OnSurfaceDisabled,
                shape = CircleShape,
            )
            .padding(TvDimens.SpaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (isFilled) TvColors.Background else TvColors.OnSurfaceMuted,
            fontSize = TvTextSizes.Headline,
        )
    }
}
