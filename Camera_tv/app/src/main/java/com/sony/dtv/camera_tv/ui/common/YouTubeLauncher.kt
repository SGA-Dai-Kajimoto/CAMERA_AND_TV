package com.sony.dtv.camera_tv.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "YouTubeLauncher"

/**
 * TV の YouTube アプリで動画を開く。
 *
 * 自前のプレイヤーで流さないのは、埋め込みプレイヤー以外での再生が YouTube の規約で
 * 許されていないため。公式アプリへ渡せば規約も守れて、リモコン操作も向こうが面倒を見る。
 */
object YouTubeLauncher {

    /** TV アプリ用の `vnd.youtube` を先に試し、無ければ通常の watch URL へ落とす。 */
    private fun intentsFor(videoId: String): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")),
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /**
     * この TV で開けるか。開けないときはボタンを出さず、QR だけを見せる。
     * 判定には AndroidManifest の `<queries>` 宣言が要る。
     */
    fun canOpen(context: Context, videoId: String): Boolean =
        intentsFor(videoId).any { it.resolveActivity(context.packageManager) != null }

    /** 開けたら true。判定を通っても起動時に失敗することはあるので戻り値で確かめる。 */
    fun open(context: Context, videoId: String): Boolean {
        intentsFor(videoId).forEach { intent ->
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "cannot handle ${intent.data}", e)
            }
        }
        return false
    }
}
