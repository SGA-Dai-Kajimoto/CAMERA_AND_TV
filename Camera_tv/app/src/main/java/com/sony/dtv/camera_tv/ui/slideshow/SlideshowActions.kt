package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.runtime.Immutable
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.domain.CullFilter
import com.sony.dtv.camera_tv.domain.SortMode
import java.time.LocalDate

/**
 * スライドショー画面から ViewModel へのユーザー操作をまとめたもの。
 *
 * 各 Composable が ViewModel を直接参照すると依存が広がりプレビューも書けないため、
 * 必要な操作だけをこの束にして渡す。
 */
@Immutable
class SlideshowActions(
    val reload: () -> Unit,
    val next: () -> Unit,
    val prev: () -> Unit,
    val selectIndex: (Int) -> Unit,
    val selectDate: (LocalDate) -> Unit,
    val setSortMode: (SortMode) -> Unit,
    val togglePlay: () -> Unit,
    val setRating: (Int) -> Unit,
    val delete: () -> Unit,
    val requestShare: () -> Unit,
    val closeShare: () -> Unit,
    val loadThumbnails: (List<Content>) -> Unit,
    val startCulling: (LocalDate?) -> Unit,
    val exitCulling: () -> Unit,
    val toggleCullScope: () -> Unit,
    val cullPick: () -> Unit,
    val cullSkip: () -> Unit,
    val cullNext: () -> Unit,
    val cullPrev: () -> Unit,
    val cycleZoom: () -> Unit,
    val zoomOff: () -> Unit,
    val panZoom: (Int, Int) -> Unit,
    val setCullReviewFilter: (CullFilter) -> Unit,
    val flipCullJudgement: (String) -> Unit,
    val revertCullJudgement: (String) -> Unit,
    val startCompare: () -> Unit,
    val exitCompare: () -> Unit,
    val compareMoveBy: (Int) -> Unit,
    val toggleCompareSelection: () -> Unit,
    val toggleCompareLayout: () -> Unit,
    val comparePick: () -> Unit,
    val compareSkip: () -> Unit,
    val clearError: () -> Unit,
    val clearNotice: () -> Unit,
) {
    companion object {
        /** プレビュー・テスト用の何もしない実装。 */
        val Noop = SlideshowActions(
            reload = {},
            next = {},
            prev = {},
            selectIndex = {},
            selectDate = {},
            setSortMode = {},
            togglePlay = {},
            setRating = {},
            delete = {},
            requestShare = {},
            closeShare = {},
            loadThumbnails = {},
            startCulling = {},
            exitCulling = {},
            toggleCullScope = {},
            cullPick = {},
            cullSkip = {},
            cullNext = {},
            cullPrev = {},
            cycleZoom = {},
            zoomOff = {},
            panZoom = { _, _ -> },
            setCullReviewFilter = {},
            flipCullJudgement = {},
            revertCullJudgement = {},
            startCompare = {},
            exitCompare = {},
            compareMoveBy = {},
            toggleCompareSelection = {},
            toggleCompareLayout = {},
            comparePick = {},
            compareSkip = {},
            clearError = {},
            clearNotice = {},
        )
    }
}

/** スライドショー内の表示状態。 */
internal enum class ScreenMode { Photo, Menu, DateSelect, Gallery, Cull, CullReview, Compare }
