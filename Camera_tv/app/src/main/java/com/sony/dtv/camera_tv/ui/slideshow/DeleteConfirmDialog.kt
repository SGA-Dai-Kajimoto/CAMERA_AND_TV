package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

/**
 * 確認ダイアログ。
 * 誤操作を避けるため、初期フォーカスは「キャンセル」側に置く。
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmFocused by remember { mutableStateOf(false) }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.ScrimStrong),
        onKey = { key ->
            when (key) {
                Key.DirectionLeft -> { confirmFocused = true; true }
                Key.DirectionRight -> { confirmFocused = false; true }
                Key.Enter, Key.DirectionCenter -> {
                    if (confirmFocused) onConfirm() else onCancel()
                    true
                }

                Key.Back -> { onCancel(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        ) {
            Text(
                text = title,
                color = TvColors.OnSurface,
                fontSize = TvTextSizes.Title,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                color = TvColors.OnSurfaceMuted,
                fontSize = TvTextSizes.Body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceLg)) {
                DialogButton(
                    label = confirmLabel,
                    isFocused = confirmFocused,
                    focusedColor = TvColors.Danger,
                )
                DialogButton(
                    label = stringResource(R.string.common_cancel),
                    isFocused = !confirmFocused,
                    focusedColor = TvColors.Focus,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    isFocused: Boolean,
    focusedColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .height(TvDimens.MenuButtonHeight)
            .clip(TvShapes.Pill)
            .background(if (isFocused) focusedColor else TvColors.SurfaceVariant)
            .padding(horizontal = TvDimens.SpaceXl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !isFocused -> TvColors.OnSurface
                focusedColor == TvColors.Focus -> TvColors.Background
                else -> TvColors.OnSurface
            },
            fontSize = TvTextSizes.Label,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
