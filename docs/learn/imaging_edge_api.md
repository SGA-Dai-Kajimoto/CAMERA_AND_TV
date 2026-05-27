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

### コンテンツ削除
- `DELETE /api/v1/folders/{folder_id}/contents/{content_id}` は存在しない。
- 正しくは `POST .../contents:remove` に `{"content_ids": [...]}` を送る。

### フォルダ・コンテンツの「削除リクエスト」状態
- `DELETE /api/v1/folders/{folder_id}` は即時削除ではない。フォルダに `removed_date` フィールドが付与され、しばらくしてから実際に削除される。
- `GET /api/v1/folders` には削除リクエスト状態のフォルダも返ってくる。`removed_date` があるものはフィルタリング必要。
- コンテンツ削除（`POST .../contents:remove`）も同様に非同期変更の可能性がある。
- `GET /api/v1/folders/{folder_id}/contents/{content_id}/resources/{kind}/binary` で画像バイナリを直接取得できる。
- `kind` には `"original"`, `"proxy"`, `"thumbnail_1920"` 等が利用可能（一部は未確認）。
