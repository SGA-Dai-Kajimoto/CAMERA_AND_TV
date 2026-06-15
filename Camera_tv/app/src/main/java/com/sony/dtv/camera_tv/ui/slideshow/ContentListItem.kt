package com.sony.dtv.camera_tv.ui.slideshow

import com.sony.dtv.camera_tv.data.model.Content
import java.time.LocalDate

/**
 * 日付グルーピング表示用の sealed class。
 * TvLazyColumn で DateHeader と ContentItem を混在させて表示する。
 */
sealed class ContentListItem {
    data class DateHeader(val date: LocalDate, val label: String) : ContentListItem()
    data class ContentItem(val content: Content) : ContentListItem()
}
