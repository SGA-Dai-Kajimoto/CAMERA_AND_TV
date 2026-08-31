package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content

/**
 * 5段階評価（お気に入り）のドメインロジック。
 *
 * Imaging Edge API には評価用のフィールドが無いため、コンテンツの TAG に
 * `rating:N`（N=1..5）という規約で埋め込んで表現する。
 * タグ文字列の組み立て・解釈はすべてこのファイルに閉じる。
 */
object ContentRating {

    const val TAG_PREFIX = "rating:"

    /** 評価なし。 */
    const val NONE = 0
    const val MIN = 1
    const val MAX = 5

    /** 表示可能な評価値の範囲に丸める。 */
    fun clamp(rating: Int): Int = rating.coerceIn(NONE, MAX)

    /** `rating:N` 形式のタグから評価値を取り出す。該当しないタグは null。 */
    fun parseTag(tag: String): Int? =
        if (tag.startsWith(TAG_PREFIX)) {
            tag.removePrefix(TAG_PREFIX).toIntOrNull()?.takeIf { it in MIN..MAX }
        } else {
            null
        }

    /** 評価値をサーバー保存用のタグ文字列にする。 */
    fun toTag(rating: Int): String = "$TAG_PREFIX$rating"

    /**
     * 既存タグの評価だけを差し替えた新しいタグ一覧を返す。
     * 評価以外のタグは保持し、rating が [NONE] のときは評価タグを取り除く。
     *
     * ただし結果が空になるときだけは `rating:0` を残す。
     * `POST :setTags` は空配列を 400 Input validation error で拒否するため（2026-08-25 実測）。
     * `rating:0` は [parseTag] が null を返すので「評価なし」として読まれる。
     */
    fun applyTo(tags: List<String>?, rating: Int): List<String> {
        val clamped = clamp(rating)
        val others = tags.orEmpty().filterNot { it.startsWith(TAG_PREFIX) }
        return when {
            clamped != NONE -> others + toTag(clamped)
            others.isEmpty() -> listOf(toTag(NONE))
            else -> others
        }
    }
}

/**
 * tags に含まれる `rating:N` から評価値を取り出す。
 * 複数ある場合は最大値、未設定なら [ContentRating.NONE]。
 */
fun Content.ratingValue(): Int =
    tags?.mapNotNull(ContentRating::parseTag)?.maxOrNull() ?: ContentRating.NONE

/** 評価を差し替えた新しいタグ一覧を返す（サーバー送信用）。 */
fun Content.tagsWithRating(rating: Int): List<String> = ContentRating.applyTo(tags, rating)
