package com.sony.dtv.camera_tv.ui.tutorial

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.common.CLOUD_UPLOAD_VIDEO_ID
import com.sony.dtv.camera_tv.ui.common.CloudUploadGuide
import com.sony.dtv.camera_tv.ui.common.HelpVideoOverlay
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.TvActionButton
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

private val CARD_WIDTH = 780.dp

/** 導入カードの選択肢。並び順と左右キーの順を一致させるために列挙で持つ。 */
private enum class Choice(@StringRes val labelRes: Int) {
    Start(R.string.tutorial_start),
    Watch(R.string.cloud_upload_watch),
    Skip(R.string.tutorial_skip),
}

/**
 * 案内を受けるかどうかを最初に選んでもらうカード。
 *
 * 以前は文章 5 ページを読ませていたが、読んだだけでは操作を覚えられない。
 * 実際の画面で 1 手順ずつ案内する [TutorialCoachOverlay] へ渡す前の入口だけをここで持つ。
 */
@Composable
fun TutorialIntro(
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    var selected by remember { mutableStateOf(0) }
    var showVideo by remember { mutableStateOf(false) }

    // KeyInputSurface を 2 つ同時に出すとフォーカスを奪い合うので、再生中はカードを描かない
    if (showVideo) {
        HelpVideoOverlay(
            videoId = CLOUD_UPLOAD_VIDEO_ID,
            onClose = { showVideo = false },
        )
        return
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.ScrimStrong),
        onKey = { key ->
            when (key) {
                Key.DirectionLeft -> { selected = (selected - 1).coerceAtLeast(0); true }
                Key.DirectionRight -> {
                    selected = (selected + 1).coerceAtMost(Choice.entries.lastIndex); true
                }

                Key.Enter, Key.DirectionCenter -> {
                    when (Choice.entries[selected]) {
                        Choice.Start -> onStart()
                        // 動画を閉じたらカードへ戻る。案内はまだ始めていない
                        Choice.Watch -> showVideo = true
                        Choice.Skip -> onSkip()
                    }
                    true
                }

                Key.Back -> { onSkip(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(CARD_WIDTH)
                .clip(TvShapes.Large)
                .background(TvColors.Surface)
                .padding(TvDimens.SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceLg),
        ) {
            Text(
                text = stringResource(R.string.tutorial_intro_title),
                color = TvColors.OnSurface,
                fontSize = TvTextSizes.Headline,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.tutorial_intro_body),
                color = TvColors.OnSurfaceMuted,
                fontSize = TvTextSizes.Body,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            CloudUploadGuide(
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd)) {
                Choice.entries.forEachIndexed { index, choice ->
                    TvActionButton(
                        label = stringResource(choice.labelRes),
                        isFocused = index == selected,
                    )
                }
            }

            KeyHint(
                text = stringResource(R.string.tutorial_intro_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
