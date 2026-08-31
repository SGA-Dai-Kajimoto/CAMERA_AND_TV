package com.sony.dtv.camera_tv.domain

import com.sony.dtv.camera_tv.data.model.Content
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * コンテンツの「撮影日」を解決する。
 *
 * API は日付フィールドを複数返し、機種やアップロード経路によって埋まるものが異なるため、
 * 精度の高い順にフォールバックする:
 *   recorded_date（タイムゾーン付き） → recorded_date_local_time → created_date
 *
 * どれも解析できない場合は null（＝日付不明）。
 */
fun Content.recordedLocalDate(): LocalDate? =
    parseOffsetDate(recordedDate)
        ?: parseLocalDate(recordedDateLocalTime)
        ?: parseOffsetDate(createdDate)

/** 並び替え用のソートキー。日付不明は常に最後（＝最小値）に落とす。 */
internal fun Content.dateSortKey(): Long =
    recordedLocalDate()?.toEpochDay() ?: Long.MIN_VALUE

private fun parseOffsetDate(value: String?): LocalDate? =
    value?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }

private fun parseLocalDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() }
