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

