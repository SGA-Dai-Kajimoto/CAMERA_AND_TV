package com.sony.dtv.camera_tv.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.common.InfoChip
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes
import kotlinx.coroutines.delay

private val COACH_WIDTH = 340.dp

/** 最後の締めくくりを読んでもらう時間。 */
private const val DONE_VISIBLE_MS = 5000L

/**
 * 実画面の上に重ねる案内パネル。
 *
 * **キー入力を一切受け取らない。** ここに [com.sony.dtv.camera_tv.ui.common.KeyInputSurface] を
 * 置くと背後の画面とフォーカスを奪い合い、案内どおりに操作できなくなる。
 * 進行は [CoachStep.isCleared] で状態の変化を見て判定する。
 */
@Composable
fun TutorialCoachOverlay(
    signal: CoachSignal,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableStateOf(CoachProgress.initial(signal)) }
    val step = progress.step
    val isActive = step.isActiveOn(signal)

    LaunchedEffect(signal) { progress = progress.advance(signal) }

    LaunchedEffect(step) {
        if (step == CoachStep.Done) {
            delay(DONE_VISIBLE_MS)
            onFinish()
        }
    }

    Column(
        modifier = modifier
            .width(COACH_WIDTH)
            .clip(TvShapes.Medium)
            .background(TvColors.ScrimStrong)
            .border(1.dp, if (isActive) TvColors.Accent else TvColors.Star, TvShapes.Medium)
            .padding(TvDimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
    ) {
        InfoChip(
            text = if (step == CoachStep.Done) {
                stringResource(R.string.coach_finished)
            } else {
                stringResource(R.string.coach_progress, step.ordinal + 1, CoachStep.TOTAL)
            },
            color = if (isActive) TvColors.Accent else TvColors.Star,
        )

        Text(
            text = stringResource(step.titleRes),
            color = TvColors.OnSurface,
            fontSize = TvTextSizes.Label,
            fontWeight = FontWeight.Bold,
        )

        // 案内どおりに操作できないときは、手順ではなく戻り方を出す。
        // その状態で効かないキーを案内すると「押しても反応しない」ことになる。
        val stranded = step.strandedMessage(signal)
        Text(
            text = stringResource(stranded ?: step.bodyRes),
            color = if (stranded != null) TvColors.Star else TvColors.OnSurfaceMuted,
            fontSize = TvTextSizes.Caption,
        )

        if (isActive) {
            step.hintRes?.let {
                KeyHint(
                    text = stringResource(it),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
