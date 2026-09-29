package com.sony.dtv.camera_tv.ui.common

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

private const val TAG = "HelpVideoOverlay"

/**
 * 埋め込みページの origin。AndroidX が WebView のローカル配信用に予約している名前で、
 * 実在しないが https の正規 origin として扱われる。
 */
private const val EMBED_ORIGIN = "https://appassets.androidplatform.net"

/** 1 回の左右キーで動かす秒数。TV の早送りとしては 10 秒が扱いやすい。 */
private const val SEEK_STEP_SEC = 10
private const val VIDEO_HINT_VISIBLE_MS = 4000L

/**
 * JS へ文字列として埋め込むため、YouTube の動画 ID として妥当な形だけを通す。
 * 将来 ID を外部から渡すようになってもスクリプト注入にならないようにしておく。
 */
private val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * 解説動画をアプリ内で再生する全画面オーバーレイ。
 *
 * 公式の IFrame Player API を WebView で動かす。YouTube アプリへ飛ばすとサインインを
 * 求められることがあるが、埋め込みプレイヤーならサインイン不要で再生できる。
 * ストリームを自前で取得する方法は規約違反なので採らない。
 *
 * リモコン操作は WebView に渡さず、こちらのキー入力から JS を呼んで操作する。
 * WebView 内の再生コントロールは D-pad でまともに辿れないため。
 */
@Composable
fun HelpVideoOverlay(
    videoId: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var failed by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(true) }
    var interactionCount by remember { mutableIntStateOf(0) }

    fun call(script: String) {
        interactionCount++
        webView?.evaluateJavascript(script, null)
    }

    LaunchedEffect(interactionCount) {
        hintVisible = true
        delay(VIDEO_HINT_VISIBLE_MS)
        hintVisible = false
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                it.loadUrl("about:blank")
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
            webView = null
        }
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            when (key) {
                Key.Enter, Key.DirectionCenter -> { call("togglePlay();"); true }
                Key.DirectionLeft -> { call("seekBy(-$SEEK_STEP_SEC);"); true }
                Key.DirectionRight -> { call("seekBy($SEEK_STEP_SEC);"); true }
                Key.Back -> { onClose(); true }
                else -> false
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
            modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Text(
                        text = stringResource(R.string.help_video_unavailable),
                        color = TvColors.OnSurfaceMuted,
                        fontSize = TvTextSizes.Body,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(TvDimens.ScreenPadding),
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            runCatching { createPlayerWebView(ctx, videoId) }
                                .onFailure {
                                    Log.w(TAG, "WebView を作れなかった", it)
                                    failed = true
                                }
                                .getOrElse { android.view.View(ctx) }
                                .also { view -> webView = view as? WebView }
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = hintVisible || failed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TvDimens.SpaceLg),
            ) {
                KeyHint(text = stringResource(R.string.help_video_hint))
            }
        }
    }

    // WebView が使えない TV では YouTube アプリへ逃がす。どちらも無ければ QR で見てもらう
    LaunchedEffect(failed) {
        if (failed) YouTubeLauncher.open(context, videoId)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPlayerWebView(context: android.content.Context, videoId: String): WebView {
    require(VIDEO_ID_PATTERN.matches(videoId)) { "不正な動画 ID" }
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // 自動再生させる。TV には「タップ」に相当する操作が無い
        settings.mediaPlaybackRequiresUserGesture = false
        keepScreenOn = true
        setBackgroundColor(android.graphics.Color.BLACK)
        // キーはこの上の KeyInputSurface で捌く。WebView に取られるとフォーカスが戻らない
        isFocusable = false
        isFocusableInTouchMode = false

        // プレイヤーが出ない原因は JS 側にしか現れないので、必ず logcat へ流す
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(
                    TAG,
                    "console ${message.messageLevel()} ${message.message()} " +
                        "@${message.sourceId()}:${message.lineNumber()}",
                )
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            // EMBED_ORIGIN は名乗るだけで実在しない。favicon などを取りに行かせると
            // 毎回 ERR_NAME_NOT_RESOLVED になるので、ここで空を返して止める
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                if (request.url.toString().startsWith(EMBED_ORIGIN)) {
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                } else {
                    null
                }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                Log.w(TAG, "load error ${error.errorCode} ${error.description} ${request.url}")
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                response: WebResourceResponse,
            ) {
                Log.w(TAG, "http ${response.statusCode} ${request.url}")
            }
        }

        // 埋め込み元として名乗る origin。youtube.com を名乗ると自己埋め込み扱いで
        // player error 152 になる（2026-09-15 実機で確認）
        loadDataWithBaseURL(
            EMBED_ORIGIN,
            playerHtml(videoId),
            "text/html",
            "utf-8",
            null,
        )
    }
}

/** IFrame Player API を読み込むだけの最小ページ。動画 ID 以外に外部入力は埋め込まない。 */
private fun playerHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  html, body { margin:0; padding:0; background:#000; height:100%; overflow:hidden; }
    #player { display:block; width:100%; height:100%; border:0; }
</style>
</head>
<body>
<div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player;
  console.log('html loaded origin=' + location.origin + ' href=' + location.href);
  function onYouTubeIframeAPIReady() {
    console.log('iframe api ready');
    player = new YT.Player('player', {
      videoId: '$videoId',
      playerVars: {
        autoplay: 1, controls: 1, rel: 0, playsinline: 1,
        modestbranding: 1, hl: 'ja', cc_lang_pref: 'ja'
      },
      events: {
        onReady: function (e) { console.log('player ready'); e.target.playVideo(); },
        onStateChange: function (e) { console.log('state ' + e.data); },
        onError: function (e) { console.error('player error ' + e.data); }
      }
    });
  }
  setTimeout(function () {
    if (!player) console.error('iframe api did not load in 8s');
  }, 8000);
  function togglePlay() {
    if (!player || !player.getPlayerState) return;
    if (player.getPlayerState() === YT.PlayerState.PLAYING) player.pauseVideo();
    else player.playVideo();
  }
  function seekBy(sec) {
    if (!player || !player.getCurrentTime) return;
    player.seekTo(Math.max(0, player.getCurrentTime() + sec), true);
  }
</script>
</body>
</html>
""".trimIndent()
