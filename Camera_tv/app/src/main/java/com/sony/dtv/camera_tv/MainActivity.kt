package com.sony.dtv.camera_tv

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.AuthInterceptor
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import com.sony.dtv.camera_tv.data.remote.pairing.PairingClient
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import com.sony.dtv.camera_tv.ui.auth.AuthScreen
import com.sony.dtv.camera_tv.ui.slideshow.SlideshowScreen
import com.sony.dtv.camera_tv.ui.theme.CameraTvTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val tokenPrefs: TokenPreferences by lazy {
        TokenPreferences.create(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logDisplayInfo()

        // local.properties の dev トークンを DataStore に注入（dev フォールバック）。
        // シード（refresh_token）が変わったときだけ書き込み、変更が無ければ
        // リフレッシュでローテーションされた最新トークンを保持する。
        // dev トークンが空なら何もしない（＝QR認証画面へ進む）。
        runBlocking {
            tokenPrefs.seedTokens(
                baseUrl = BuildConfig.DEV_BASE_URL.ifEmpty { TokenPreferences.DEFAULT_BASE_URL },
                appType = TokenPreferences.DEFAULT_APP_TYPE,
                accessToken = BuildConfig.DEV_ACCESS_TOKEN,
                refreshToken = BuildConfig.DEV_REFRESH_TOKEN,
            )
        }

        setContent {
            CameraTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(
                        tokenPrefs = tokenPrefs,
                        pairingServerUrl = BuildConfig.PAIRING_SERVER_URL,
                    )
                }
            }
        }
    }

    /**
     * UI レイヤーの描画解像度とパネルの物理解像度を記録する。
     * この 2 つが食い違う機種（1080p 描画 → 4K アップスケール）では、
     * 写真をいくら高解像度でデコードしても画質に反映されない。
     */
    private fun logDisplayInfo() {
        val metrics = resources.displayMetrics
        val mode = display?.mode
        Log.i(
            TAG,
            "display ui=${metrics.widthPixels}x${metrics.heightPixels} " +
                "density=${metrics.density} " +
                "panel=${mode?.physicalWidth}x${mode?.physicalHeight} " +
                "refresh=${mode?.refreshRate}",
        )
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

/**
 * 認証ゲート。
 * - refresh_token が未取得 → QR 認証画面（AuthScreen）
 * - 取得済み            → スライドショー（Repository を生成して表示）
 */
@Composable
private fun AppRoot(
    tokenPrefs: TokenPreferences,
    pairingServerUrl: String,
) {
    val context = LocalContext.current
    // 初回は null（未確定）＝ローディング。以降は "" or トークン文字列。
    val refreshToken by tokenPrefs.refreshToken.collectAsState(initial = null)

    when {
        refreshToken == null -> LoadingBox()

        refreshToken.isNullOrEmpty() -> {
            if (pairingServerUrl.isBlank()) {
                ConfigErrorBox()
            } else {
                val pairingApi = remember(pairingServerUrl) { PairingClient.create(pairingServerUrl) }
                AuthScreen(
                    pairingApi = pairingApi,
                    tokenPreferences = tokenPrefs,
                    onAuthenticated = { /* refreshToken フローが更新され自動遷移 */ },
                )
            }
        }

        else -> {
            val repository = remember { buildRepository(context.applicationContext, tokenPrefs) }
            val scope = rememberCoroutineScope()
            SlideshowScreen(
                repository = repository,
                // トークンを消すと refreshToken フローが空になり認証画面へ戻る
                onSignOut = { scope.launch { tokenPrefs.clear() } },
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ConfigErrorBox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "認証サーバーが設定されていません。\n" +
                "local.properties の pairing.serverUrl を設定してください。",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Sony API 用の Repository を生成する。
 * AuthInterceptor が Bearer 付与と 401 リフレッシュを担う。
 */
private fun buildRepository(
    context: Context,
    tokenPrefs: TokenPreferences,
): ImagingEdgeRepository {
    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    // 既定の maxRequestsPerHost=5 だと、サムネイルの並列取得で枠を使い切り
    // 表示中の写真の取得がキューで待たされる。
    val dispatcher = Dispatcher().apply {
        maxRequests = 24
        maxRequestsPerHost = 12
    }
    val okHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .addInterceptor(AuthInterceptor(tokenPrefs))
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val baseUrl = runBlocking {
        tokenPrefs.baseUrl.first().ifEmpty { TokenPreferences.DEFAULT_BASE_URL }
    }
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val api = retrofit.create(ImagingEdgeApi::class.java)
    return ImagingEdgeRepository(api, tokenPrefs)
}
