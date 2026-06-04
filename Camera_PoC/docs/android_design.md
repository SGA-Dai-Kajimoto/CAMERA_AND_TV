# Android アプリ 設計書

対象プロジェクト: `Camera_PoC/`
参照元 PC GUI: `camera/app/gui_app.py`

---

## 概要

Imaging Edge クラウド上のフォルダと画像コンテンツを管理する Android アプリ。
PC 版 PyQt5 GUI (`camera/app/`) と同等の機能を Jetpack Compose + MVVM で実装する。

---

## 画面構成

### メイン画面（FolderManagerScreen）

```
┌───────────────────────────────────────────────────────────────┐
│ Imaging Edge フォルダマネージャー          [ログイン中: xxx]  │
├─────────────────────┬─────────────────────────────────────────┤
│ フォルダ            │ コンテンツ（フォルダ選択で表示）       │
│ ┌─────────────────┐ │ ┌─────────────────────────────────────┐ │
│ │ フォルダ名 A    │ │ │ image001.jpg                        │ │
│ │ フォルダ名 B  ← │ │ │ image002.arw                        │ │
│ │ フォルダ名 C    │ │ │ ...                                 │ │
│ └─────────────────┘ │ └─────────────────────────────────────┘ │
│ [新規] [変更] [削除]│ [アップロード] [表示] [削除]           │
│ [再読み込み]        │                                         │
├─────────────────────┴─────────────────────────────────────────┤
│ ステータス: 3 コンテンツ                                       │
└───────────────────────────────────────────────────────────────┘
```

- **スマートフォン**: フォルダ一覧画面 → コンテンツ一覧画面の Navigation遷移
- **タブレット (w >= 600dp)**: 左右ペインを Row で並列表示

### 画像ビューア画面（ImageViewerScreen）

コンテンツの「表示」ボタンで遷移。バイナリをダウンロードして Coil で表示。

---

## アーキテクチャ

MVVM + Repository パターン。

```
UI Layer                    Domain/Data Layer
──────────────────────────────────────────────────────
Composable                  ViewModel
  MainScreen ──────────────► MainViewModel
  FolderPanel                  └── ImagingEdgeRepository
  ContentPanel                       └── ImagingEdgeApi (Retrofit)
  ImageViewerScreen                  └── AuthInterceptor (OkHttp)
                                     └── TokenPreferences (DataStore)
```

---

## ディレクトリ構成

```
Camera_PoC/app/src/main/java/com/sony/dtv/carmera_poc/
├── MainActivity.kt                          # エントリーポイント・Navigation ホスト
├── data/
│   ├── model/
│   │   ├── Folder.kt                        # フォルダ DTO
│   │   └── Content.kt                       # コンテンツ DTO
│   ├── remote/
│   │   ├── ImagingEdgeApi.kt                # Retrofit インターフェース（全エンドポイント）
│   │   ├── AuthInterceptor.kt               # Bearer トークン付与・401 自動リフレッシュ
│   │   └── dto/
│   │       ├── FolderListResponse.kt        # GET /api/v1/folders レスポンス
│   │       ├── ContentListResponse.kt       # GET .../contents レスポンス
│   │       ├── TokenResponse.kt             # POST /oauth2/token レスポンス
│   │       └── UploadSessionResponse.kt     # POST .../upload レスポンス
│   ├── local/
│   │   └── TokenPreferences.kt              # DataStore: access_token / refresh_token 等
│   └── repository/
│       └── ImagingEdgeRepository.kt         # ApiClient 相当。全操作メソッドを持つ
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── main/
│   │   ├── MainViewModel.kt                 # フォルダ・コンテンツ操作の状態管理
│   │   ├── MainScreen.kt                    # メイン画面エントリー Composable
│   │   ├── FolderPanel.kt                   # フォルダリスト＋操作ダイアログ
│   │   └── ContentPanel.kt                  # コンテンツリスト＋操作ボタン
│   └── imageviewer/
│       └── ImageViewerScreen.kt             # 画像ビューア画面
└── docs/
    └── android_design.md                    # このファイル
```

---

## 依存ライブラリ

| ライブラリ | 用途 | PC GUI の対応 |
|---|---|---|
| `androidx.compose.*` | UI フレームワーク | PyQt5 |
| `androidx.navigation:navigation-compose` | 画面遷移 | QDialog / QMainWindow |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel | MainWindow (状態管理部分) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 非同期処理 | Worker (QThread) |
| `com.squareup.retrofit2:retrofit` | HTTP クライアント | requests |
| `com.squareup.retrofit2:converter-gson` | JSON デシリアライズ | json.loads |
| `com.squareup.okhttp3:okhttp` | HTTP 基盤・インターセプター | requests Session |
| `com.squareup.okhttp3:logging-interceptor` | デバッグログ | — |
| `androidx.datastore:datastore-preferences` | トークン永続化 | auth_info.json |
| `io.coil-kt:coil-compose` | 画像表示 | QPixmap |
| `androidx.activity:activity-compose` | `setContent {}` 基盤 | QApplication |

---

## モジュール詳細

### `TokenPreferences.kt`

DataStore で以下のキーを管理する（`auth_info.json` に対応）：

| キー | 型 | 説明 |
|---|---|---|
| `base_url` | String | API ベース URL |
| `app_type` | String | アプリ識別子（`_trial_`） |
| `access_token` | String | アクセストークン |
| `access_token_ttl` | Long | アクセストークン有効期限 |
| `refresh_token` | String | リフレッシュトークン |
| `refresh_token_ttl` | Long | リフレッシュトークン有効期限 |
| `user_id` | String | ユーザーID |
| `account` | String | ファイル API 用 ID |

初期値は `assets/auth_config.json` から読み込む（`base_url`, `app_type`, 初期トークン）。

### `AuthInterceptor.kt`

OkHttp の `Interceptor` 実装。

- すべてのリクエストに `Authorization: Bearer <access_token>` を付与
- レスポンスが 401 の場合: `POST /api/v1/oauth2/token` でリフレッシュ → 再試行
- Python 版 `_request()` / `refresh_token()` と同等ロジック

### `ImagingEdgeApi.kt`

Retrofit インターフェース。`suspend fun` で定義。

| メソッド | HTTP | パス |
|---|---|---|
| `refreshToken(body)` | POST | `/api/v1/oauth2/token` |
| `getUserMe()` | GET | `/api/v1/user/me` |
| `listContainers(account)` | GET | `/api/file/v1/{account}` |
| `listFolders()` | GET | `/api/v1/folders` |
| `createFolder(body)` | POST | `/api/v1/folders` |
| `renameFolder(folderId, body)` | PUT | `/api/v1/folders/{folderId}` |
| `deleteFolder(folderId)` | DELETE | `/api/v1/folders/{folderId}` |
| `listContents(folderId)` | GET | `/api/v1/folders/{folderId}/contents` |
| `getContentBinary(folderId, contentId, kind)` | GET | `/api/v1/folders/{folderId}/contents/{contentId}/resources/{kind}/binary` |
| `deleteContent(folderId, body)` | POST | `/api/v1/folders/{folderId}/contents:remove` |
| `startUploadSession(folderId, body)` | POST | `/api/v1/folders/{folderId}/upload` |
| `registerContent(folderId, body)` | POST | `/api/v1/folders/{folderId}/contents` |

バイナリ取得 (`getContentBinary`) は `@Streaming` + `ResponseBody` で受け取る。

### `ImagingEdgeRepository.kt`

ApiClient 相当。`suspend fun` で各操作を提供。エラーは `Result<T>` で包む。

| メソッド | 説明 |
|---|---|
| `getUserMe()` | ユーザー情報取得・account/user_id を DataStore 保存 |
| `listFolders()` | フォルダ一覧（`removed_date` あり除外） |
| `createFolder(name)` | フォルダ作成 |
| `renameFolder(folderId, name)` | フォルダ名変更 |
| `deleteFolder(folderId)` | フォルダ削除 |
| `listContents(folderId)` | コンテンツ一覧 |
| `getContentBinary(folderId, contentId)` | コンテンツバイナリ取得 |
| `deleteContent(folderId, contentId)` | コンテンツ削除 |
| `uploadImage(folderId, uri)` | 3ステップアップロード |

### `MainViewModel.kt`

`ViewModel` + `StateFlow` で UI 状態を管理。

```kotlin
data class UiState(
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val loginUser: String = "",
    val folders: List<Folder> = emptyList(),
    val selectedFolder: Folder? = null,
    val contents: List<Content> = emptyList(),
    val error: String? = null,
)
```

公開メソッド（GUI の `MainWindow` ハンドラに対応）：

| メソッド | 対応する GUI 操作 |
|---|---|
| `loadFolders()` | `_load_folders()` |
| `selectFolder(folder)` | `_on_folder_selected()` |
| `createFolder(name)` | `_create_folder()` |
| `renameFolder(folderId, name)` | `_rename_folder()` |
| `deleteFolder(folderId)` | `_delete_folder()` |
| `uploadImage(folderId, uri)` | `_upload_image()` |
| `getContentBinary(folderId, contentId)` | `_view_content()` → ImageViewer 遷移 |
| `deleteContent(folderId, contentId)` | `_delete_content()` |

### `MainScreen.kt`

レスポンシブレイアウト:
- `LocalConfiguration.current.screenWidthDp >= 600` → Row で左右ペイン並列表示
- それ以外 → NavHost で FolderPanel / ContentPanel を遷移

### `FolderPanel.kt`

- `LazyColumn` でフォルダ一覧表示
- 「新規作成」「名前変更」「削除」ボタン → `AlertDialog` / `TextField` ダイアログ
- 「再読み込み」ボタン

### `ContentPanel.kt`

- `LazyColumn` でコンテンツ一覧表示
- 「アップロード」ボタン → `ActivityResultContracts.GetContent` でファイル選択
- 「表示」ボタン → ImageViewerScreen に遷移
- 「削除」ボタン → 確認ダイアログ

### `ImageViewerScreen.kt`

- `content_id` と `folder_id` を受け取り、バイナリをダウンロードして `AsyncImage` (Coil) で表示
- ローディング中はプログレスインジケーター表示

---

## API 対応表

| ユーザー操作 | 呼び出す API |
|---|---|
| アプリ起動 | GET /api/v1/user/me（認証確認） |
| フォルダ一覧・再読み込み | GET /api/v1/folders |
| フォルダ選択 | GET /api/v1/folders/{folder_id}/contents |
| フォルダ新規作成 | POST /api/v1/folders |
| フォルダ名変更 | PUT /api/v1/folders/{folder_id} |
| フォルダ削除 | DELETE /api/v1/folders/{folder_id} |
| 画像アップロード | アップロードフロー（下記） |
| コンテンツ表示 | GET .../contents/{content_id}/resources/original/binary |
| コンテンツ削除 | POST /api/v1/folders/{folder_id}/contents:remove |

---

## アップロードフロー

PC 版と同一の 3 ステップ:

```
[1] POST /api/v1/folders/{folder_id}/upload
    body: { "name": "<ファイル名>", "target": "content/original/image" }
    → upload_id, upload_url 取得

[2] PUT <upload_url>
    body: ファイルバイナリ（OkHttp RequestBody）
    → ストレージへアップロード

[3] POST /api/v1/folders/{folder_id}/contents
    body: { "upload_id": "<upload_id>" }
    → フォルダにコンテンツ登録
    ※ 425 レスポンス時はリトライ（最大5回、wait_time 秒待機）
```

---

## 認証情報の扱い

PC 版は `camera/api/auth_info.json` をファイルで管理しているが、
Android 版は以下に変更する:

- **初期トークン**: `app/src/main/assets/auth_config.json` に記述（`access_token`, `refresh_token` を手動設定）
- **ランタイム保存**: `DataStore<Preferences>` に保存（`TokenPreferences.kt`）
- **自動更新**: `AuthInterceptor` が 401 検知時に自動リフレッシュ

> `assets/auth_config.json` はバージョン管理対象外（`.gitignore` に追加）

---

## build.gradle.kts 変更点

### libs.versions.toml に追加するバージョン

```toml
[versions]
compose-bom = "2025.01.00"
navigation-compose = "2.8.5"
lifecycle = "2.8.7"
coroutines = "1.9.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
datastore = "1.1.2"
coil = "2.7.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.9.3" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
# Retrofit + OkHttp
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
# Coil
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

[plugins]
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### app/build.gradle.kts に追加する設定

```kotlin
plugins {
    alias(libs.plugins.kotlin.compose)  // 追加
}

android {
    buildFeatures {
        compose = true  // 追加
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    debugImplementation(libs.compose.ui.tooling)
}
```
