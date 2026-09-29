package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content
import java.time.LocalDate

/**
 * 選別（採用 / 見送り）のドメインロジック。
 *
 * ユーザーには二値しか見せないが、保存先は既存の `rating:N` タグに載せる。
 * 新しいタグ規約を増やさずに済み、スライドショー側の評価フィルタや
 * [SortMode.RatingDesc] がそのまま使える。
 *
 * 「見送り」は削除ではない。撮影者にとって削除の決断は重く手が止まるため、
 * 選別中は消さずに評価を下げるだけにする。
 */
object ContentCulling {

    /** 採用。スライドショーに出す。 */
    const val PICK = 3

    /** 見送り。残すがスライドショーには出さない。 */
    const val SKIP = 1

    /**
     * 選別で流すコンテンツを、新しい撮影日が先に来る順で返す。
     *
     * 判定済みも含める。以前は未判定だけにしていたが、日付で見た枚数と
     * 選別の枚数が合わず混乱する（判定済みが無告で飛ばされる）。
     * 「続きから」は開始位置を [firstUnjudgedIndex] にすることで保つ。
     *
     * [date] を渡すとその日に限定する。一日分ずつ区切って選別できるようにするため。
     */
    fun queue(contents: List<Content>, date: LocalDate? = null): List<Content> =
        contents
            .filter { date == null || it.recordedLocalDate() == date }
            .sortedByDescending { it.dateSortKey() }

    /** キューの中で最初の未判定の位置。全部判定済みなら先頭。 */
    fun firstUnjudgedIndex(queue: List<Content>): Int =
        queue.indexOfFirst { !it.isJudged() }.coerceAtLeast(0)

    /** 未判定が残っている日を新しい順で返す。 */
    fun unjudgedDates(contents: List<Content>): List<LocalDate> =
        contents.filterNot { it.isJudged() }
            .mapNotNull { it.recordedLocalDate() }
            .distinct()
            .sortedDescending()

    /**
     * 採用済みのコンテンツ。判定をやり直せるよう見直し画面に出す。
     *
     * [PICK] ちょうどではなく [PICK] 以上を見る。評価ダイアログで ★4 を付けた写真は
     * 判定済み扱いになるのに、ちょうどで絞るとどちらのタブにも出ず未判定に戻せなくなる。
     */
    fun picked(contents: List<Content>): List<Content> =
        sortedByDate(contents.filter { it.ratingValue() >= PICK })

    /** 見送り済みのコンテンツ。削除していないので、いつでも採用に戻せる。 */
    fun skipped(contents: List<Content>): List<Content> =
        sortedByDate(contents.filter { it.isJudged() && it.ratingValue() < PICK })

    private fun sortedByDate(contents: List<Content>): List<Content> =
        contents.sortedByDescending { it.dateSortKey() }
}

/** 選別結果の見直しで表示する側。 */
enum class CullFilter { Picked, Skipped }

/** 採用・見送りのいずれかが済んでいるか。 */
fun Content.isJudged(): Boolean = ratingValue() != ContentRating.NONE
