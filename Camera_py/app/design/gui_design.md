# GUI アプリ 設計書

対象ファイル: `camera/app/gui_app.py`

---

## 概要

Imaging Edge クラウド上のフォルダと画像コンテンツを管理するデスクトップGUIアプリ。
`check_api.py` で確認済みの認証・トークン管理の仕組みを流用し、PyQt5 で画面を構成する。

---

## 画面構成

```
┌───────────────────────────────────────────────────────────────┐
│ Imaging Edge フォルダマネージャー                              │
├─────────────────────┬─────────────────────────────────────────┤
│ フォルダ            │ コンテンツ                              │
│ ┌─────────────────┐ │ ┌─────────────────────────────────────┐ │
│ │ フォルダ名 A    │ │ │ image001.jpg                        │ │
│ │ フォルダ名 B  ← │ │ │ image002.arw                        │ │
│ │ フォルダ名 C    │ │ │ ...                                 │ │
│ └─────────────────┘ │ └─────────────────────────────────────┘ │
│ [新規] [変更] [削除]│ [アップロード]           [削除]         │
│ [再読み込み]        │                                         │
├─────────────────────┴─────────────────────────────────────────┤
│ ステータスバー: 3 コンテンツを取得しました                    │
└───────────────────────────────────────────────────────────────┘
```

---

## クラス構成

### `ApiClient`

認証情報の読み書きと全API呼び出しを担うクラス。

| メソッド | 説明 |
|---|---|
| `__init__()` | auth_info.json を読み込み、必要に応じてトークンをリフレッシュ |
| `refresh_token()` | POST /api/v1/oauth2/token でアクセストークンを更新 |
| `get_user_me()` | GET /api/v1/user/me |
| `list_folders()` | GET /api/v1/folders |
| `create_folder(name)` | POST /api/v1/folders |
| `rename_folder(id, name)` | PUT /api/v1/folders/{folder_id} |
| `delete_folder(id)` | DELETE /api/v1/folders/{folder_id} |
| `list_contents(folder_id)` | GET /api/v1/folders/{folder_id}/contents |
| `delete_content(folder_id, content_id)` | DELETE /api/v1/folders/{folder_id}/contents/{content_id} |
| `upload_image(folder_id, file_path)` | アップロードフロー（下記参照） |

内部の `_get / _post / _put / _delete` は 401 時に自動リフレッシュして再試行する共通処理。

### `Worker(QThread)`

API呼び出しをバックグラウンドで実行する汎用ワーカー。
`result(object)` / `error(str)` シグナルで結果を通知する。

### `MainWindow(QMainWindow)`

UIの本体。左パネル（フォルダ）・右パネル（コンテンツ）をQSplitterで分割。

---

## API 対応表

| ユーザー操作 | 呼び出すAPI |
|---|---|
| アプリ起動 | GET /api/v1/user/me (認証確認) |
| フォルダ一覧表示・再読み込み | GET /api/v1/folders |
| フォルダ選択 | GET /api/v1/folders/{folder_id}/contents |
| フォルダ新規作成 | POST /api/v1/folders |
| フォルダ名変更 | PUT /api/v1/folders/{folder_id} |
| フォルダ削除 | DELETE /api/v1/folders/{folder_id} |
| 画像アップロード | アップロードフロー (下記) |
| コンテンツ削除 | DELETE /api/v1/folders/{folder_id}/contents/{content_id} |

---

## アップロードフロー

```
[1] POST /api/file/v1/{account}:upload
    body: { "filename": "<ファイル名>" }
    → upload_url, object_id を取得

[2] PUT <upload_url>
    body: バイナリデータ
    → ストレージへアップロード

[3] POST /api/v1/folders/{folder_id}/contents
    body: { "object_id": "<object_id>", "filename": "<ファイル名>" }
    → フォルダにコンテンツを登録
```

> **注意**: ステップ3のリクエストボディの正確なスキーマは未確認。
> エラーが発生した場合は API ドキュメントを確認し `upload_image()` を修正する。

---

## 非機能事項

- API呼び出しはすべて QThread (Worker) で実行し、UIをブロックしない
- 401 レスポンスは自動でトークンリフレッシュして再試行する
- エラーはステータスバーと QMessageBox で通知する
- ワーカーの参照を `_workers` リストで保持しGCを防ぐ

---

## 未確定・要確認事項

| 項目 | 内容 |
|---|---|
| コンテンツ一覧のレスポンス形式 | `contents` 配列の各要素のキー名 (`content_id`, `display_name` など) |
| アップロードのリクエストボディ | `POST /api/file/v1/{account}:upload` のパラメータ仕様 |
| フォルダへのコンテンツ登録 | `POST /api/v1/folders/{folder_id}/contents` のリクエストボディ |
| コンテンツ削除のエンドポイント | パスの正確な形式 |
