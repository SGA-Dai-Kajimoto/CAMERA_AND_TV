package com.sony.dtv.camera_tv.data.remote

import java.io.IOException

/**
 * Imaging Edge API がエラーステータスを返したことを表す例外。
 *
 * 文字列 message だけだと呼び出し側がステータスで分岐できないため、
 * HTTP ステータスコードを型として持たせる。
 */
class ApiException(
    val code: Int,
    val errorBody: String? = null,
) : IOException("API error $code${errorBody?.let { ": $it" } ?: ""}")
