package com.sony.dtv.camera_tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.AuthInterceptor
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import com.sony.dtv.camera_tv.ui.slideshow.SlideshowScreen
import com.sony.dtv.camera_tv.ui.theme.CameraTvTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {

    // Repository を Activity スコープで一度だけ生成
    private val repository: ImagingEdgeRepository by lazy {
        val tokenPrefs = TokenPreferences.create(applicationContext)

        // local.properties のトークンを DataStore に初期注入（未設定時のみ）
        runBlocking {
            tokenPrefs.initIfEmpty(
                baseUrl = BuildConfig.DEV_BASE_URL.ifEmpty { TokenPreferences.DEFAULT_BASE_URL },
                appType = TokenPreferences.DEFAULT_APP_TYPE,
                accessToken = BuildConfig.DEV_ACCESS_TOKEN,
                accessTokenTtl = 3600L,
                refreshToken = BuildConfig.DEV_REFRESH_TOKEN,
                refreshTokenTtl = 86400L,
            )
        }

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenPrefs))
            .addInterceptor(logging)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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
        ImagingEdgeRepository(api, tokenPrefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CameraTvNavGraph(repository = repository)
                }
            }
        }
    }
}

@Composable
private fun CameraTvNavGraph(repository: ImagingEdgeRepository) {
    SlideshowScreen(
        repository = repository,
    )
}
