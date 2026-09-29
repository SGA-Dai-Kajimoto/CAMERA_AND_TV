package com.sony.dtv.camera_tv.ui.tutorial

import com.sony.dtv.camera_tv.R

/**
 * 案内が対象にする画面。
 *
 * 案内は「その画面でしか効かないキー」を教える。メニューを開いたまま
 * 「← → で写真を送る」と出しても、左右キーはボタン移動に使われて何も起きない。
 */
enum class CoachScreen { Photo, Cull, Other }

/**
 * 案内が見ている画面の状態。
 *
 * 「どのキーを押したか」ではなく「結果が起きたか」で進める。
 * キーを見ると、押しただけで実際には何も起きていないときにも先へ進んでしまう。
 */
data class CoachSignal(
    val screen: CoachScreen = CoachScreen.Photo,
    val photoIndex: Int = 0,
    val isCulling: Boolean = false,
    val isZoomed: Boolean = false,
    val judgedCount: Int = 0,
)

/**
 * 実画面の上で案内する手順。
 *
 * 文章で先に全部説明しても覚えられないので、1 手順ずつ実際に操作してもらう。
 * 順番は「写真を送る → 選別に入る → 拡大で確かめる → 判定する」という
 * 実際の選別作業の流れそのままにしてある。
 *
 * @param requiredScreen この手順が成立する画面。null はどの画面でもよい
 * @param strandedRes 別の画面にいるときに出す戻り方
 */
enum class CoachStep(
    val titleRes: Int,
    val bodyRes: Int,
    val hintRes: Int?,
    val requiredScreen: CoachScreen?,
    val strandedRes: Int?,
) {
    SendPhoto(
        titleRes = R.string.coach_send_title,
        bodyRes = R.string.coach_send_body,
        hintRes = R.string.coach_send_hint,
        requiredScreen = CoachScreen.Photo,
        strandedRes = R.string.coach_return_photo,
    ),
    StartCulling(
        titleRes = R.string.coach_cull_title,
        bodyRes = R.string.coach_cull_body,
        hintRes = R.string.coach_cull_hint,
        // メニューを経由するので画面を限定しない
        requiredScreen = null,
        strandedRes = null,
    ),
    Zoom(
        titleRes = R.string.coach_zoom_title,
        bodyRes = R.string.coach_zoom_body,
        hintRes = R.string.coach_zoom_hint,
        requiredScreen = CoachScreen.Cull,
        strandedRes = R.string.coach_return_cull,
    ),
    Judge(
        titleRes = R.string.coach_judge_title,
        bodyRes = R.string.coach_judge_body,
        hintRes = R.string.coach_judge_hint,
        requiredScreen = CoachScreen.Cull,
        strandedRes = R.string.coach_return_cull,
    ),
    Done(
        titleRes = R.string.coach_done_title,
        bodyRes = R.string.coach_done_body,
        hintRes = null,
        requiredScreen = null,
        strandedRes = null,
    ),
    ;

    /** いま案内どおりに操作できる状態か。 */
    fun isActiveOn(signal: CoachSignal): Boolean = when (this) {
        // 拡大中の ↑↓ は見る位置の移動に使われ、採用・見送りにはならない
        Judge -> signal.screen == CoachScreen.Cull && !signal.isZoomed
        else -> requiredScreen == null || requiredScreen == signal.screen
    }

    /** 案内どおりに操作できないときに出す戻り方。 */
    fun strandedMessage(signal: CoachSignal): Int? = when {
        isActiveOn(signal) -> null
        this == Judge && signal.screen == CoachScreen.Cull -> R.string.coach_exit_zoom
        else -> strandedRes
    }

    fun next(): CoachStep? = entries.getOrNull(ordinal + 1)

    companion object {
        /** 進捗表示用。[Done] は手順ではなく締めくくりなので数えない。 */
        val TOTAL = entries.size - 1
    }
}

/**
 * 案内の進み具合。
 *
 * 手順の達成判定には「一度拡大したか」のような履歴が要るため、
 * 単体の述語ではなく状態を持つ値オブジェクトにしてある。
 */
data class CoachProgress(
    val step: CoachStep,
    private val start: CoachSignal,
    private val zoomSeen: Boolean = false,
) {
    fun advance(now: CoachSignal): CoachProgress {
        val sawZoom = zoomSeen || now.isZoomed
        if (!step.isActiveOn(now)) return copy(zoomSeen = sawZoom)

        val cleared = when (step) {
            CoachStep.SendPhoto -> now.photoIndex != start.photoIndex
            CoachStep.StartCulling -> now.isCulling
            // 拡大したまま次へ進むと、次の手順の ↑↓ が位置移動に使われて反応しない
            CoachStep.Zoom -> sawZoom && !now.isZoomed
            CoachStep.Judge -> now.judgedCount > start.judgedCount
            // 最後は達成条件を持たない。読み終えたら閉じる
            CoachStep.Done -> false
        }

        return if (cleared) {
            CoachProgress(step.next() ?: step, now)
        } else {
            copy(zoomSeen = sawZoom)
        }
    }

    companion object {
        fun initial(signal: CoachSignal) = CoachProgress(CoachStep.SendPhoto, signal)
    }
}
