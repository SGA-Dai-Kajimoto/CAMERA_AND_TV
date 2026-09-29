# Imaging Edge API 設計・認証フロー

## 条件
- Imaging Edge API（AccountPF / CommonLib）を使う場合

## 効果
- Bearer トークンで全 API を統一認証できる
- ログイン後に `GET /api/v1/user/me` でユーザーIDと account 名を取得し、以降の API パスに使い回す設計が正しい
- ファイル操作系（`/api/file/v1/{account}/...`）とフォルダ管理系（`/api/v1/folders/...`）は別ベースパスになっている

## 注意
- account 名は user_id とは別物。`/user/me` で返る値を使うこと
- 旧 API パス（`/storage/dir/list` 等）は廃止済み。使わない
- アップロードは「セッション開始 → バイナリ PUT → オブジェクト作成」の 3 ステップ
- ストレージタイプ（native / ci）は `GET /api/v1/user/props` で確認できる
## フォルダ / コンテンツ登録のハマりポイント

### POST /api/v1/folders（フォルダ作成）
- `app_id`（= `auth_info.json` の `app_type`，例: `"_trial_"`）と `utc_offset`（例: `"+09:00"`）が必須。無いと 400 Bad Request。

### POST /api/v1/folders/{folder_id}/contents（コンテンツ登録）
- `wait_time` クエリパラメータ（デフォルト `"0s"`、最大 `"10s"`）を付けるとサーバーが処理完了を待ってからレスポンスする。
- `wait_time=10s` でも 425 Too Early が返る場合がある。数秒待ってリトライすること。
- リクエストボディに必須: `upload_id`, `kind`, `name`, `bytes`, `utc_offset`。
- `content_type`（任意）で MIME を明示できる。指定した値が優先される。

### POST /api/v1/folders/{folder_id}/contents/{content_id}:setTags（タグ更新）
- **`tags` に空配列を渡すと 400 `{"error":{"code":400,"message":"Input validation error."}}` になる。**（2026-08-25 実測 / dev3 / Android TV アプリから）
- そのため「評価を外す＝タグを全部消す」は表現できない。TV アプリでは評価なしを `rating:0` というタグで表し、配列が空にならないようにしている（`ContentRating.applyTo`）。
- `rating:0` は `ContentRating.parseTag` が `null` を返すので、読むときは「評価なし」として扱われる。
- 実測ログ:
  - 修正前 `tag sync failed cid=c-8579ca6c` → 400
  - 修正後 `tag sync ok cid=c-8579ca6c tags=[rating:0]` → 再起動後 `judged` が 12→11、選別キューに復帰

### 対応フォーマット / HEIF
- HEIF は対応。`GET .../contents/{content_id}/metadata` の `kind` に `image_heif_meta`（HEIF専用メタデータ）が定義されている。`image_raw_meta` / `image_arq_meta` もあり RAW/ARQ も解析対象。
- アップロード時は `name` の拡張子（例 `.heif` / `.heic`）と `content_type` がサムネイル生成・メタデータ解析の判定に使われる。

### 複数枚アップロード（バッチ可否）
- アップロードの一括APIは無い。`upload`→`PUT`→`contents` の3ステップを1ファイルずつ実行する。
- 一括系が存在するのはダウンロード（`POST /api/v1/cms/contents:download`）・コピー（`contents:copy` 最大100件）・削除（`contents:remove` 最大1000件）・trash（最大100件）のみ。
- `contents/{content_id}/resources/{kind}/uploads`（multipart upload）は「1ファイルを分割送信」であり複数ファイルの一括ではない。
- → GUI 側で複数選択し順次アップロードするのが正しい方式。

### コンテンツ削除
- `DELETE /api/v1/folders/{folder_id}/contents/{content_id}` は存在しない。
- 正しくは `POST .../contents:remove` に `{"content_ids": [...]}` を送る。

### フォルダ・コンテンツの「削除リクエスト」状態
- `DELETE /api/v1/folders/{folder_id}` は即時削除ではない。フォルダに `removed_date` フィールドが付与され、しばらくしてから実際に削除される。
- `GET /api/v1/folders` には削除リクエスト状態のフォルダも返ってくる。`removed_date` があるものはフィルタリング必要。
- コンテンツ削除（`POST .../contents:remove`）も同様に非同期変更の可能性がある。
- `GET /api/v1/folders/{folder_id}/contents/{content_id}/resources/{kind}/binary` で画像バイナリを直接取得できる。
- `kind` には `"original"`, `"proxy"`, `"thumbnail_1920"` 等が利用可能（一部は未確認）。
