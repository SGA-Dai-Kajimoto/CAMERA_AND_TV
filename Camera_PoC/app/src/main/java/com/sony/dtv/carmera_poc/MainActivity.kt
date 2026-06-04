package com.sony.dtv.carmera_poc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sony.dtv.carmera_poc.data.local.TokenPreferences
import com.sony.dtv.carmera_poc.data.remote.AuthInterceptor
import com.sony.dtv.carmera_poc.data.remote.ImagingEdgeApi
import com.sony.dtv.carmera_poc.data.repository.ImagingEdgeRepository
import com.sony.dtv.carmera_poc.ui.imageviewer.ImageViewerScreen
import com.sony.dtv.carmera_poc.ui.main.ContentPanel
import com.sony.dtv.carmera_poc.ui.main.MainScreen
import com.sony.dtv.carmera_poc.ui.main.MainViewModel
import com.sony.dtv.carmera_poc.ui.theme.ImagingEdgeTheme
import com.google.gson.GsonBuilder
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
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenPrefs))
            .addInterceptor(logging)
            .build()
        val baseUrl = runBlocking {
            tokenPrefs.baseUrl.first().ifEmpty { "https://api.imaging-edge.sony.net/" }
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
        val api = retrofit.create(ImagingEdgeApi::class.java)
        ImagingEdgeRepository(api, tokenPrefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImagingEdgeTheme {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.factory(applicationContext),
                )
                val uiState by viewModel.uiState.collectAsState()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(viewModel = viewModel, navController = navController)
                    }
                    composable("contents") {
                        ContentPanel(
                            folder = uiState.selectedFolder,
                            contents = uiState.contents,
                            isLoading = uiState.isLoading,
                            onUpload = { folderId, fileName, fileBytes -> viewModel.uploadImage(folderId, fileName, fileBytes) },
                            onViewContent = { folderId, contentId ->
                                navController.navigate("imageviewer/$folderId/$contentId")
                            },
                            onDeleteContent = { folderId, contentId ->
                                viewModel.deleteContent(folderId, contentId)
                            },
                        )
                    }
                    composable("imageviewer/{folderId}/{contentId}") { backStackEntry ->
                        val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                        val contentId = backStackEntry.arguments?.getString("contentId") ?: ""
                        ImageViewerScreen(
                            folderId = folderId,
                            contentId = contentId,
                            repository = repository,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
