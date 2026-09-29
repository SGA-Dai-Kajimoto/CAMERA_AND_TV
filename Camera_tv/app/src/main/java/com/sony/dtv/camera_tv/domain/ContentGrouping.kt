package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** コンテンツの並び順。 */
enum class SortMode {
    /** 撮影日グループ表示（既定）。 */
    DateDesc,

    /** 全コンテンツを評価の高い順にフラット表示。 */
    RatingDesc,
    ;

    fun toggled(): SortMode = if (this == DateDesc) RatingDesc else DateDesc
}

/** 日付ヘッダーとコンテンツを混在させて一覧表示するための表示モデル。 */
sealed interface ContentListItem {
    data class DateHeader(val date: LocalDate, val label: String) : ContentListItem
    data class ContentItem(val content: Content) : ContentListItem
}

/**
 * コンテンツ一覧の並び替え・グルーピングを担う純粋関数群。
 * ViewModel から状態を持たない形で切り出しているため、単体テストしやすい。
 */
object ContentGrouping {

    private val HEADER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

    const val UNKNOWN_DATE_LABEL = "日付不明"

    /** 撮影日の新しい順に並べ、日付ヘッダーを挟んだ表示用リストを作る。 */
    fun groupByDate(contents: List<Content>): List<ContentListItem> =
        contents
            .sortedByDescending { it.dateSortKey() }
            .groupBy { it.recordedLocalDate() }
            .flatMap { (date, items) ->
                val header = ContentListItem.DateHeader(
                    date = date ?: LocalDate.MIN,
                    label = date?.format(HEADER_FORMATTER) ?: UNKNOWN_DATE_LABEL,
                )
                listOf(header) + items.map(ContentListItem::ContentItem)
            }

    /** コンテンツが存在する撮影日を新しい順に返す。 */
    fun availableDates(contents: List<Content>): List<LocalDate> =
        contents.mapNotNull { it.recordedLocalDate() }
            .distinct()
            .sortedDescending()

    /**
     * 現在の並び順・選択日に応じて、実際に画面へ表示するコンテンツ列を決める。
     *  - [SortMode.RatingDesc]: 全件を評価の高い順（同評価は新しい日付順）
     *  - [SortMode.DateDesc]  : 選択日のコンテンツのみ（未選択なら全件）
     */
    fun visibleContents(
        contents: List<Content>,
        sortMode: SortMode,
        selectedDate: LocalDate?,
    ): List<Content> = when (sortMode) {
        SortMode.RatingDesc -> contents.sortedWith(
            compareByDescending<Content> { it.ratingValue() }.thenByDescending { it.dateSortKey() },
        )

        SortMode.DateDesc ->
            if (selectedDate == null) contents
            else contents.filter { it.recordedLocalDate() == selectedDate }
    }
}
