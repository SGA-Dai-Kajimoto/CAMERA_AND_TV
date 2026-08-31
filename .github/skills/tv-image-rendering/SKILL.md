---
name: tv-image-rendering
description: 'TV での写真表示・デコード・拡大の設計ルール。Use when changing how photos are decoded or displayed on the TV: downsampling, resolution targets, zoom / pixel-level focus checking, thumbnail kinds, image fetch performance, blurry or slow photos, OutOfMemory or texture-limit crashes.'
argument-hint: '扱う内容（例: 拡大時のピント確認 / 表示が粗い）'
---

# TV での写真表示・デコード設計

一眼の写真を扱うため、**素朴に実装すると必ず破綻する**領域。ここのルールは実測に基づいている。

## 3つの制約

| 制約 | 内容 |
|---|---|
| GPU テクスチャ上限 | 多くの Android TV 機で 4096px。7008px の Bitmap は**描画できずクラッシュする** |
| メモリ | 7008×4672 の ARGB_8888 は約 131MB。TV はスマホよりヒープが厳しい |
| パネル解像度 | 4K でも 3840×2160。**全画面表示では原寸を送っても表示できる情報は増えない** |

→ **原寸をそのまま Bitmap 化しない。** ただし縮小しすぎてもいけない（下記）。

## デコードの原則

`ui/common/BitmapDecoder.kt`

- `inSampleSize` は 2 冪しか取れない。**要求値を割り込むまで縮めると解像度が大幅に不足する**
  （7008px を 2160px 要求で 1/4 すると 1752px になる）
- 要求以上を保つ最小の 2 冪を選び、**端数は `inScaled` / `inDensity` / `inTargetDensity`** で詰める
- 要求ピクセル数は定数にせず `rememberFullscreenReqPx()` を使う。
  Android TV は機種によって **UI レイヤーを 1080p で描画して 4K へアップスケール**するため、
  決め打ちだと過剰デコードか画質劣化のどちらかになる
- `TvDimens.FullscreenMaxPx` はメモリとテクスチャ上限を守るための**キャップ**であって目標値ではない

実機の描画解像度は起動時ログで確認できる。

```
MainActivity  display ui=1920x1080 density=2.0 panel=3840x2160 refresh=60.0
```

`ui` と `panel` が食い違う機種では、高解像度でデコードしても画質に反映されない。
その場合は写真表示部分だけ `SurfaceView` + `setFixedSize` で真の 4K サーフェスを確保する必要がある。

## 拡大（ピント確認）

`ui/common/RegionBitmap.kt`

- **全体をデコードしてから拡大しない。** `BitmapRegionDecoder` で
  **表示する矩形だけを原寸から切り出す**。全画面表示時と同じメモリで 1:1 の画素が得られる
- 倍率は「画面ピクセル ÷ 元画像ピクセル」。切り出す範囲は倍率に反比例するので、
  **倍率を上げてもメモリは増えない**
- 倍率の段階は `SlideshowViewModel.ZOOM_MAGNIFICATIONS`（先頭の `0f` は拡大なし）
- **縮小して表示しない。** ピント確認の意味がなくなる
- **拡大位置は候補を切り替えても保持する。** 同じ座標の画像が入れ替わることで差が
  「ちらつき」として見える（ブリンク比較）。位置がリセットされると比較の価値が消える

## 表示の伸縮

- サムネイルも全画面も **`ContentScale.Fit`**。黒帯が出てよい。
  `Crop` は構図を切り落とすため使わない
- 一覧でフォーカス時に拡大するときは `zIndex` を上げる。隣のセルの下に潜り込む

## 取得する解像度の使い分け

| 用途 | kind |
|---|---|
| 一覧のサムネイル | `thumbnail_400` →（失敗時）`thumbnail_1024` → `original` |
| 選別中のプレビュー | `thumbnail_1920` →（失敗時）`original` |
| 全画面表示 | `original` |
| 拡大（等倍確認） | `original`（拡大したときだけ取りに行く） |

**選別中に 1 枚ずつ原寸を落とすと送りが止まる。** 軽い方を先に試し、原寸は拡大時のみ。

## 取得の無駄をなくす

`/binary` は S3 へ 303 リダイレクトするため、1 枚につき 2 ホストへの接続が要る。初回は遅い。

- **取得中の同じ写真を取り消して再実行しない。** `loadingContentId` で見張る
- 表示対象が変わっていないなら `currentImageBytes` を破棄しない
- OkHttp の既定 `maxRequestsPerHost=5` はサムネイルの並列取得で埋まる。
  `Dispatcher` で広げて、表示中の写真がキューで待たされないようにする

ログに `IOException: Canceled` の直後に**同じ URL の再取得**が出ていたら、この無駄が起きている。
