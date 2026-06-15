# TASKS

## API疎通の確認
- [x] `camera/app/check_api.py` 実装済み（`python camera/app/check_api.py` で実行）
- [x] `camera/api/auth_info.json` にトークンを記入して実際に疎通確認を実施する
  - トークン取得手順は `docs/auth_spec.md` を参照
  - 確認済み: user_id=8857511748018, コンテナ1件, フォルダ0件 (2026-05-27)

## GUIアプリの実装

- [x] `camera/app/gui_app.py` 実装済み（`python camera/app/gui_app.py` で起動）
- [x] `camera/app/design/gui_design.md` 設計書作成済み
- [x] フォルダ一覧・作成・名前変更・削除の動作確認（2026-05-27）
- [x] コンテンツ一覧・削除の動作確認（2026-05-27）
- [x] 画像アップロードの動作確認（2026-05-27）
- [x] 画像表示機能（`GET .../resources/{kind}/binary`）の実装・動作確認（2026-05-27）

---

## Android アプリの実装（Camera_PoC）

設計書: `Camera_PoC/docs/android_design.md`
実装ガイド: `android_app_instructions.md`

### Phase 1: プロジェクト基盤
- [x] `Camera_PoC/gradle/libs.versions.toml` に依存ライブラリのバージョンを追加
      （Compose, Retrofit2, OkHttp3, Coroutines, DataStore, Coil, Navigation Compose）
- [x] `Camera_PoC/app/build.gradle.kts` を更新
      （Compose ビルドオプション、kotlin-compose プラグイン、依存ライブラリ追加）
- [x] `Camera_PoC/app/src/main/AndroidManifest.xml` に `INTERNET` パーミッションと `MainActivity` を追加
- [x] `MainActivity.kt` 作成（Compose Hello 表示）
- [x] `ui/theme/` 作成（Color, Type, Theme — ImagingEdgeTheme）

#### Phase 1 完了チェック
- [ ] **ビルド成功**: `./gradlew assembleDebug` がエラーなく完了する
- [ ] **Compose 動作**: `MainActivity` に `setContent { Text("Hello") }` を仮置きしてエミュレーターで起動・表示できる

---

### Phase 2: データ層

#### Step 2-1: テスト先行作成（実装前に作成）
- [x] `test/data/repository/ImagingEdgeRepositoryTest.kt` — リポジトリの単体テスト（スケルトン）
      - `getUserMe()` が JSON をパースして `user_id` を返すこと
      - `listFolders()` が `removed_date` ありのフォルダを除外すること
      - `createFolder()` / `renameFolder()` / `deleteFolder()` が正しいエンドポイントに送信すること
      - `listContents()` / `deleteContent()` が正しく動くこと
      - 401 レスポンス時に `AuthInterceptor` がトークンをリフレッシュして再試行すること
- [x] `test/data/local/TokenPreferencesTest.kt` — DataStore の読み書きテスト（スケルトン）
      - トークンの保存・読み込みが正しく動くこと
- [ ] テストが **RED（コンパイルエラーまたは失敗）** であることを確認

#### Step 2-2: 実装
- [x] `data/model/Folder.kt` — フォルダ DTO（`folder_id`, `display_name` など）
- [x] `data/model/Content.kt` — コンテンツ DTO（`content_id`, `display_name`, `filename` など）
- [x] `data/remote/ImagingEdgeApi.kt` — Retrofit インターフェース
      （全エンドポイントを suspend fun で定義）
- [x] `data/remote/AuthInterceptor.kt` — Bearer トークン付与 OkHttp インターセプター
      （401 時にリフレッシュして再試行）
- [x] `data/local/TokenPreferences.kt` — DataStore による access_token / refresh_token 保存
- [x] `data/repository/ImagingEdgeRepository.kt` — ApiClient 相当のリポジトリ
      （Retrofit呼び出し → ドメインモデル変換）

#### Phase 2 完了チェック
- [x] **単体テスト全通過**: `./gradlew test` で `ImagingEdgeRepositoryTest` が全件グリーン
  - `getUserMe()` が JSON をパースして `user_id` を返すこと
  - `listFolders()` が `removed_date` ありのフォルダを除外すること
  - `createFolder()` / `renameFolder()` / `deleteFolder()` が正しいエンドポイントに送信すること
  - `listContents()` / `deleteContent()` が正しく動くこと
  - 401 レスポンス時に `AuthInterceptor` がトークンをリフレッシュして再試行すること
- [x] **DataStore 読み書き**: `TokenPreferences` の read/write テスト（`runTest` + `TestCoroutineScheduler`）が通ること

---

### Phase 3: UI 層

#### Step 3-1: テスト先行作成（実装前に作成）
- [x] `test/ui/main/MainViewModelTest.kt` — ViewModel の単体テスト（スケルトン）
      - `loadFolders()` 後に `uiState.folders` が更新されること
      - `createFolder()` 後に `uiState.folders` が再取得されること
      - `selectFolder()` 後に `uiState.contents` が更新されること
      - エラー時に `uiState.error` が設定されること
- [x] テストが **RED（コンパイルエラーまたは失敗）** であることを確認

#### Step 3-2: 実装
- [x] `ui/theme/` — Compose テーマ設定（Color, Type, Theme）
- [x] `ui/main/MainViewModel.kt` — フォルダ・コンテンツ操作の状態管理
      （StateFlow で UiState を公開、Coroutines で非同期実行）
- [x] `ui/main/MainScreen.kt` — メイン画面 Composable
      （左ペイン: フォルダリスト＋操作ボタン、右ペイン: コンテンツリスト＋操作ボタン）
- [x] `ui/main/FolderPanel.kt` — フォルダパネル Composable
      （新規作成・名前変更・削除ダイアログを含む）
- [x] `ui/main/ContentPanel.kt` — コンテンツパネル Composable
      （アップロード・削除ボタン、コンテンツ一覧）
- [x] `ui/imageviewer/ImageViewerScreen.kt` — 画像ビューア画面 Composable
      （バイナリダウンロード → Coil で表示）
- [x] `MainActivity.kt` 更新 — Navigation Compose でルーティング設定

---

## TVアプリ 画面リニューアル（日付グルーピング対応）

設計書:
- `docs/tv_screen_design.md`（画面遷移設計）
- `docs/content_date_grouping_design.md`（日付グルーピング設計）

### Phase 5: データモデル・API拡張

- [x] `data/model/Content.kt` に `recordedDate`, `recordedDateLocalTime`, `createdDate` フィールドを追加
- [x] `data/remote/dto/ContentListResponse.kt` に `lastItem` フィールドを追加
- [x] `data/remote/ImagingEdgeApi.kt` の `listContents()` に `orderBy`, `limit`, `startFrom` クエリパラメータを追加
- [x] `data/repository/ImagingEdgeRepository.kt` に `listContentsPaged()` メソッドを追加

### Phase 6: グルーピングロジック

- [ ] `ui/slideshow/ContentListItem.kt` 作成（`sealed class`: `DateHeader` / `ContentItem`）
- [ ] `ui/slideshow/SlideshowViewModel.kt` に `groupByDate()` / `extractDate()` ロジックを追加
- [ ] `SlideshowUiState` に `groupedItems: List<ContentListItem>` を追加
- [ ] ページネーション対応: `loadMoreContents()` で `last_item` を使い追加読み込み

### Phase 7: TV画面UI実装

#### 画面1: メイン写真表示
- [ ] 起動時に最新日付グループの写真をフルスクリーン表示
- [ ] 左右ボタンで同一日付グループ内の写真切替
- [ ] 下ボタン or 決定ボタンで操作メニュー（画面2）をオーバーレイ表示

#### 画面2: 操作メニュー（オーバーレイ）
- [ ] メニューバーUI実装（日付選択 / お気に入り登録 / 削除）
- [ ] Backキーでメニュー非表示 → 画面1に戻る
- [ ] フォーカス移動: 左右ボタンでメニューボタン間移動

#### 日付選択画面
- [ ] 日付ごとにグルーピングされたコンテンツ一覧を表示（`TvLazyColumn`）
- [ ] 日付選択後、画面1に戻り選択日付グループの写真を表示
- [ ] D-Padフォーカス対応（↑↓でリスト移動、決定で確定、Backで戻る）

#### お気に入り・削除機能
- [ ] お気に入り登録: タグAPI（`POST .../contents/{content_id}:setTags`）で `favorite:1` を付与
- [ ] 削除ボタン押下時に削除確認Toastを表示
- [ ] 確認後 `POST .../contents:remove` で削除実行 → 次の写真を自動表示

### Phase 8: TV画面 結合・動作確認

- [ ] アプリ起動 → 最新日付の写真がフルスクリーン表示される
- [ ] 左右キーで写真切替が正常動作する
- [ ] 下ボタン/決定で操作メニューが表示される
- [ ] Backキーでメニューが閉じる
- [ ] 日付選択 → 日付一覧表示 → 選択後に該当日付の写真が表示される
- [ ] お気に入り登録が正常動作する（タグ付与）
- [ ] 削除 → Toast確認 → 削除実行が正常動作する
- [ ] ページネーション: 300件以上のコンテンツで追加読み込みが動作する

#### Phase 3 完了チェック
- [x] **ViewModel 単体テスト全通過**: `./gradlew test` で `MainViewModelTest` が全件グリーン
  - `loadFolders()` 後に `uiState.folders` が更新されること
  - `createFolder()` 後に `uiState.folders` が再取得されること
  - `selectFolder()` 後に `uiState.contents` が更新されること
  - エラー時に `uiState.error` が設定されること
- [ ] **Compose プレビュー**: `FolderPanel` / `ContentPanel` / `ImageViewerScreen` に `@Preview` アノテーションを付けて Android Studio でプレビュー表示できること
- [x] **ビルド成功**: `./gradlew assembleDebug` がエラーなく完了する

---

### Phase 4: 結合・動作確認（エミュレーター or 実機）
- [ ] アプリ起動時に `GET /api/v1/user/me` が呼ばれ、ステータスバーにログインユーザーが表示される
- [ ] フォルダ一覧が表示される
- [ ] フォルダ作成・名前変更・削除が動作する（操作後にリストが更新される）
- [ ] フォルダ選択後にコンテンツ一覧が表示される
- [ ] 画像アップロードが動作する（ファイル選択 → アップロード → コンテンツ一覧に追加される）
- [ ] コンテンツの「表示」で画像ビューア画面に遷移して画像が表示される
- [ ] コンテンツ削除が動作する（削除後にリストから消える）

