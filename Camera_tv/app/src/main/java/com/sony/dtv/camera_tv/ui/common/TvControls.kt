package com.sony.dtv.camera_tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

/**
 * リモコンのキー入力を受け取るフルスクリーンの入力レイヤー。
 *
 * 各画面で「FocusRequester を作る → focusable() → onKeyEvent で KeyDown を判定」という
 * 定型コードが重複していたため、ここへ集約する。
 *
 * @param onKey キーを処理したら true を返す。false ならシステムへ伝播する
 */
@Composable
fun KeyInputSurface(
    modifier: Modifier = Modifier,
    onKey: (Key) -> Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyDown) onKey(event.key) else false
            },
    ) {
        content()
    }
}

/** 画面上に重ねる情報チップ（枚数・日付など）。 */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TvColors.OnSurface,
) {
    Text(
        text = text,
        color = color,
        fontSize = TvTextSizes.Caption,
        modifier = modifier
            .clip(TvShapes.Pill)
            .background(TvColors.Scrim)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * [KeyHint] の書式。`キー|説明` を `;` で並べる。
 *
 * 区切りに空白を使わないのは、strings.xml の連続した空白が
 * 1 つにまとめられて項目分割に失敗するため。
 */
private const val KEY_ACTION_SEPARATOR = '|'
private const val HINT_ITEM_SEPARATOR = ';'

/**
 * リモコンのキー説明。
 *
 * 全体に下地を敷いて写真の明るさに左右されないようにし、
 * キー名だけさらに濃い面を重ねて、どこまでがキーか分かるようにする。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .clip(TvShapes.Small)
            .background(TvColors.HintSurface)
            .padding(horizontal = TvDimens.SpaceSm, vertical = TvDimens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceXs),
    ) {
        text.split(HINT_ITEM_SEPARATOR).forEach { item ->
            val key = item.substringBefore(KEY_ACTION_SEPARATOR).trim()
            val action = item.substringAfter(KEY_ACTION_SEPARATOR, "").trim()
            if (key.isEmpty()) return@forEach
            Row(
                horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = key,
                    color = TvColors.OnSurface,
                    fontSize = TvTextSizes.Caption,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(TvShapes.Small)
                        .background(TvColors.HintKeySurface)
                        .border(1.dp, TvColors.SurfaceVariant, TvShapes.Small)
                        .padding(horizontal = TvDimens.SpaceSm, vertical = 3.dp),
                )
                if (action.isNotEmpty()) {
                    Text(
                        text = action,
                        color = TvColors.OnSurface,
                        fontSize = TvTextSizes.Caption,
                    )
                }
            }
        }
    }
}

/**
 * 押せる操作を表すボタン。
 *
 * [InfoChip] は「情報」を出すためのもので、押せそうに見えない。
 * 実行できる操作は必ずこちらを使い、背景と枠でボタンだと分かるようにする。
 */
@Composable
fun TvActionButton(
    label: String,
    modifier: Modifier = Modifier,
    isFocused: Boolean = true,
    accent: Color = TvColors.Focus,
) {
    val contentColor = if (isFocused) TvColors.Background else TvColors.OnSurface
    Text(
        text = label,
        color = contentColor,
        fontSize = TvTextSizes.Label,
        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier
            .clip(TvShapes.Pill)
            // 非フォーカスでも Surface のカード上で形が見える色にする
            .background(if (isFocused) accent else TvColors.SurfaceVariant)
            .border(
                width = if (isFocused) TvDimens.FocusBorder else 1.dp,
                color = if (isFocused) accent else TvColors.OnSurfaceDisabled,
                shape = TvShapes.Pill,
            )
            .padding(horizontal = TvDimens.SpaceLg, vertical = TvDimens.SpaceSm),
    )
}
