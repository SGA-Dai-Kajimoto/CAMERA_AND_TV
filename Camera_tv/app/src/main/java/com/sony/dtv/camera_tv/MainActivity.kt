package com.sony.dtv.camera_tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.AuthInterceptor
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import com.sony.dtv.camera_tv.ui.folderlist.FolderListScreen
import com.sony.dtv.camera_tv.ui.settings.SettingsScreen
import com.sony.dtv.camera_tv.ui.slideshow.SlideshowScreen
import com.sony.dtv.camera_tv.ui.theme.CameraTvTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {

    private val tokenPreferences: TokenPreferences by lazy {
        TokenPreferences.create(applicationContext)
    }

    // Repository を Activity スコープで一度だけ生成
    private val repository: ImagingEdgeRepository by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenPreferences))
            .addInterceptor(logging)
            .build()
        val baseUrl = runBlocking {
            tokenPreferences.baseUrl.first().ifEmpty { TokenPreferences.DEFAULT_BASE_URL }
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(ImagingEdgeApi::class.java)
        ImagingEdgeRepository(api, tokenPreferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // local.properties の dev トークンを DataStore に自動投入（画面表示なし）
        // BuildConfig にトークンがあれば毎回上書きする（local.properties 更新時に即反映）
        runBlocking {
            if (BuildConfig.DEV_ACCESS_TOKEN.isNotEmpty()) {
                tokenPreferences.saveBaseUrl(
                    BuildConfig.DEV_BASE_URL.ifEmpty { TokenPreferences.DEFAULT_BASE_URL }
                )
                tokenPreferences.saveAccessToken(BuildConfig.DEV_ACCESS_TOKEN)
                tokenPreferences.saveRefreshToken(BuildConfig.DEV_REFRESH_TOKEN)
            }
        }

        // 起動時にトークンを確認（未設定なら settings に飛ばす）
        val hasToken = runBlocking { tokenPreferences.accessToken.first().isNotEmpty() }

        setContent {
            CameraTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CameraTvNavGraph(
                        tokenPreferences = tokenPreferences,
                        repository = repository,
                        startDestination = if (hasToken) "folderlist" else "settings",
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraTvNavGraph(
    tokenPreferences: TokenPreferences,
    repository: ImagingEdgeRepository,
    startDestination: String = "folderlist",
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("settings") {
            SettingsScreen(
                tokenPreferences = tokenPreferences,
                onSaved = {
                    navController.navigate("folderlist") {
                        popUpTo("settings") { inclusive = true }
                    }
                },
            )
        }
        composable("folderlist") {
            FolderListScreen(
                repository = repository,
                onFolderSelected = { folderId ->
                    navController.navigate("slideshow/$folderId")
                },
            )
        }
        composable("slideshow/{folderId}") { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
            SlideshowScreen(
                folderId = folderId,
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
