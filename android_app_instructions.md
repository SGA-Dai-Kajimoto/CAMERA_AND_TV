# android_app_instructions.md — Imaging Edge Android アプリ実装ガイド

このファイルは LLM が Android アプリ（`Camera_PoC/`）を実装する際に
**読むべきドキュメントとその目的**をまとめたガイドです。

---

## 実装を始める前に読むべきファイル（必須）

| # | ファイル | 読む理由 |
|---|---|---|
| 1 | `Camera_PoC/docs/android_design.md` | **Android アプリの設計書**。ディレクトリ構成・クラス設計・依存ライブラリ・API対応表・アップロードフローをすべて含む。**最初に読む** |
| 2 | `docs/TASKS.md` の「Android アプリの実装」セクション | 実装タスクの一覧と完了状況。何が済んでいて何が残っているかを確認する |
| 3 | `camera/app/design/gui_design.md` | PC 版 GUI の設計書。画面レイアウト・API 対応表・アップロードフローを Android 版と対照するために読む |
| 4 | `docs/auth_spec.md` | 認証情報の JSON スキーマ・トークン取得手順。Android 版の `TokenPreferences.kt` / `assets/auth_config.json` の実装に必要 |
| 5 | `docs/api_spec_summary.md` | 全 API エンドポイント一覧。`ImagingEdgeApi.kt` の Retrofit インターフェース定義に使う |

---

## 実装フェーズ別に参照するファイル

### Phase 1: プロジェクト基盤（ライブラリ追加）

| ファイル | 目的 |
|---|---|
| `Camera_PoC/docs/android_design.md` の「build.gradle.kts 変更点」セクション | 追加すべきライブラリのバージョン・依存定義をそのままコピーできる |
| `Camera_PoC/gradle/libs.versions.toml` | 現在のバージョン定義を確認し、重複を避けながら追記する |
| `Camera_PoC/app/build.gradle.kts` | 現在のビルド設定を確認して Compose 対応を追記する |

### Phase 2: データ層

| ファイル | 目的 |
|---|---|
| `camera/app/api/auth.py` | `TokenPreferences.kt` の実装参考（保存キー・デフォルト値） |
| `camera/app/api/client.py` | `ImagingEdgeRepository.kt` の実装参考（全メソッドのロジック・401リフレッシュ処理） |
| `docs/api_spec_summary.md` | `ImagingEdgeApi.kt` のエンドポイント定義（リクエスト/レスポンス形式） |
| `Camera_PoC/docs/android_design.md` の「モジュール詳細」セクション | 各クラスの責務・メソッド一覧 |

### Phase 3: UI 層

| ファイル | 目的 |
|---|---|
| `camera/app/gui/main_window.py` | `MainScreen.kt` / `FolderPanel.kt` / `ContentPanel.kt` の実装参考（レイアウト・操作ハンドラ） |
| `camera/app/design/gui_design.md` | 画面レイアウトの ASCII アートと機能一覧 |
| `Camera_PoC/docs/android_design.md` の「MainViewModel.kt」セクション | UiState 定義・公開メソッド一覧 |

---

## 実装時の注意点

### 認証情報
- PC 版は `camera/api/auth_info.json` でトークンを管理している
- Android 版は `app/src/main/assets/auth_config.json` に初期値を置き、`DataStore` で管理する
- `auth_config.json` は `.gitignore` に追加してコミットしないこと

### 非同期処理
- PC 版の `Worker (QThread)` は Android では `Kotlin Coroutines` に置き換える
- `ViewModel.viewModelScope.launch` でコルーチンを起動し、`StateFlow` で UI に通知する

### アップロードフロー
- 3 ステップ（セッション開始 → PUT → コンテンツ登録）は PC 版と同一
- 詳細は `camera/app/api/client.py` の `upload_image()` と `Camera_PoC/docs/android_design.md` のアップロードフローセクションを参照

### 画像表示
- PC 版は `get_content_binary()` でバイナリを取得して `QPixmap` で表示
- Android 版は同じ API からバイナリを取得し、`coil-compose` の `AsyncImage` で表示する
- `@Streaming` アノテーションと `ResponseBody` を使って大きなファイルにも対応する

---

## ファイル間の対応関係（PC 版 → Android 版）

| PC 版ファイル | Android 版ファイル |
|---|---|
| `camera/api/auth_info.json` | `assets/auth_config.json` + `data/local/TokenPreferences.kt` |
| `camera/app/api/auth.py` | `data/local/TokenPreferences.kt` |
| `camera/app/api/client.py` | `data/remote/ImagingEdgeApi.kt` + `data/remote/AuthInterceptor.kt` + `data/repository/ImagingEdgeRepository.kt` |
| `camera/app/gui/worker.py` | Kotlin Coroutines（`viewModelScope.launch`） |
| `camera/app/gui/main_window.py` | `ui/main/MainViewModel.kt` + `ui/main/MainScreen.kt` + `ui/main/FolderPanel.kt` + `ui/main/ContentPanel.kt` |
| `camera/app/gui_app.py` | `MainActivity.kt` |
