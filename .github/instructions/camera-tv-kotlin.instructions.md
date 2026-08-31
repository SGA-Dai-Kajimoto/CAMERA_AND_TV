---
description: "Use when writing or modifying Kotlin code in Camera_tv (Android TV app): architecture layers, UI tokens, remote-control key handling, ViewModel patterns, build and test commands."
applyTo: "Camera_tv/**/*.kt"
---

# Camera_tv（Android TV アプリ）の実装ルール

## ビルドとテスト

`JAVA_HOME` は環境変数に設定されていない。毎回プレフィックスを付ける。

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd Camera_tv
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain
```

- テスト結果 XML は UTF-8。`Get-Content -Raw -Encoding UTF8` してから `[xml]` にキャストする
- `android { testOptions { unitTests.isReturnDefaultValues = true } }` は必須。ViewModel と
  `AuthInterceptor` が `android.util.Log` を呼ぶため
- `local.properties` の値は `BuildConfig` に焼き込まれる。**変更したら再ビルドと再インストールが必要**

## レイヤー構成

```
data (remote / local / repository / model) → domain (純粋 Kotlin) → ui
```

- `domain/` に **Android 依存を持ち込まない**。純粋関数にして単体テストで担保する
- タグ規約・日付解決・グルーピング・選別といった判断ロジックは `domain/` に閉じる
- `Repository` の公開関数は `Result<T>` を返す。`CancellationException` は握りつぶさず再スローする
- エラーは `data/remote/ApiException(code, errorBody)` で表現し、テストでは**メッセージではなく `.code`** を検証する

## UI

- 色・余白・角丸・文字サイズは `ui/theme/TvTokens.kt` の `TvColors` / `TvDimens` / `TvShapes` /
  `TvTextSizes` を使う。**画面ファイルに `Color(0xFF...)` や `16.dp` を直書きしない**
- ユーザーに見える文言はすべて `res/values/strings.xml`（日本語）
- 画面 Composable は `SlideshowUiState` と `@Immutable SlideshowActions` を受け取る。
  **ViewModel を直接渡さない**（プレビューが書けなくなるため）
- 共通部品は `ui/common/` に置く。同じ処理を画面ファイルに複製しない

## リモコン操作

- キー入力は `ui/common/KeyInputSurface` を使う。各画面で `FocusRequester` を組み直さない
- **`KeyInputSurface` を同時に2つ描画しない。** フォーカスを奪い合ってキーが届かなくなる。
  オーバーレイを出すときは背後の画面を描画しない（early return する）
- `PhotoScreen` で `Key.Back` を消費しない（アプリを終了できなくなる）
- ボタンの並びは enum で持つ。インデックスを直書きしない

## ViewModel

- `SlideshowViewModel(repository, externalScope: CoroutineScope? = null)` の形にして、
  テストではテスト用スコープを渡す
- 期限やタイムアウトは**実時計ではなく試行回数**で判定する。`runTest` の仮想時間が使えるようにするため
- 同じコンテンツの取得が進行中なら**取り消して再実行しない**。原寸取得は数秒かかるため丸ごと無駄になる
- 連続操作で発生する更新（選別中の評価など）は楽観的に状態を進め、送信はキューで直列化する

## テスト

- テスト名は日本語でバッククォート内に書く
- `domain/` は網羅的に、`ui/` は ViewModel の状態遷移を検証する
- Retrofit の `@Body Map` は **API インターフェース側**で
  `Map<String, @JvmSuppressWildcards Any>` と書く。書かないと wildcard で実行時に落ちる
