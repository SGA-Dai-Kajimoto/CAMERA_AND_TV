# Imaging Edge AccountPF API 概要まとめ

## 認証
- すべてのAPIはBearerトークンによる認証が必要です。
  - ヘッダー例: `Authorization: Bearer <access_token>`

## 主なエンドポイントと機能

### 1. 利用規約・プライバシーポリシー
- 利用規約・プライバシーポリシーの取得・同意状況の確認・同意登録が可能。
  - `GET /api/v1/terms/apps/{app_type}/privacy_policy`
  - `GET /api/v1/terms/apps/{app_type}/terms_of_use`
  - `GET /api/v1/users/{user_id}/apps/{app_type}/privacy_policy_status`
  - `PUT /api/v1/users/{user_id}/apps/{app_type}/privacy_policy_status`
  - `GET /api/v1/users/{user_id}/apps/{app_type}/terms_of_use_status`
  - `PUT /api/v1/users/{user_id}/apps/{app_type}/terms_of_use_status`

### 2. 認証（OAuth2）
- サインインURL取得: `GET /api/v1/oauth2/auth`
- 認可コードからアクセストークン取得: `POST /api/v1/oauth2/token`
- サインアウト: `DELETE /api/v1/oauth2/token`

### 3. ユーザー管理
- セッション一覧取得: `GET /api/v1/user/sessions`
- デバイス単位でのサインアウト: `DELETE /api/v1/user/sessions/{device_id}`
- プロフィール取得: `GET /api/v1/user/me`
- ストレージタイプ取得: `GET /api/v1/user/props`
- プロフィール編集URL取得: `GET /api/v1/user/profileurl`
- ユーザー情報更新: `PUT /api/v1/users/{user_id}`
- プロフィール画像取得: `GET /api/v1/users/{user_id}/profileimages`
- ユーザー検索: `POST /api/v1/users:search`
- ユーザー属性の取得・更新・削除: `GET/PUT/POST /api/v1/users/{user_id}/properties`

### 4. グループ管理
- グループの作成・取得・更新・削除・メンバー管理・招待など多様なAPIあり。
  - 例: `POST /api/v1/groups`, `GET /api/v1/groups/{group_id}`

### 5. 製品登録
- 製品登録サポート状況取得、登録URL取得、登録済み製品URL取得など。
  - 例: `GET /api/v1/product_reg/users/{user_id}/status`, `POST /api/v1/product_reg/users/{user_id}:getRegistrationUrl`

### 6. CI認証・連携
- CIアカウント情報取得、ワークスペース連携、トークン発行など。

### 7. テナント管理
- テナントの作成・取得・更新・削除・メンバー管理・招待など。


---

# Imaging Edge API（CommonLib）主要仕様まとめ

## 認証
- すべてのAPIはBearerトークンによる認証が必要。
  - 例: `Authorization: Bearer <access_token>`

## 主なエンドポイント例

### システム情報
- サービス状態取得: `GET /api/v1/service`
  - サービス名・説明・稼働状況を返す。
- バージョン情報取得: `GET /api/v1/version`
  - APIリビジョン・ビルド日・デプロイ日を返す。

### ユーザー関連
- ストレージ使用量取得: `GET /api/v1/users/{user_id}/storage_usage`
  - 各種リソースごとの使用量や合計容量、月ごとのアップロード/ダウンロード量も取得可能。

### ファイル操作
- コンテナ一覧取得: `GET /api/file/v1/{account}`
- オブジェクト一覧取得: `GET /api/file/v1/{account}/{container}`
- オブジェクトダウンロード: `GET /api/file/v1/{account}/{container}/{object}`
- ダウンロードURL取得: `GET /api/file/v1/{account}/{container}/{object}:downloadUrl`
- アップロードセッション開始: `POST /api/file/v1/{account}:upload`
- オブジェクト作成: `PUT /api/file/v1/{account}/{container}/{object}`
- メタデータ取得（HEAD）・更新（POST）・削除（DELETE）も可能。

### フォルダ・コンテンツ管理
- フォルダ作成: `POST /api/v1/folders`
- フォルダ一覧・詳細・更新・削除: `GET/PUT/DELETE /api/v1/folders/{folder_id}`
- フォルダ内リソース・コンテンツ管理、タグ付け、共有ユーザー管理など多機能。

---

## リクエストボディ詳細（判明済み）

### POST /api/v1/folders（フォルダ作成）
| フィールド | 要否 | 説明 |
|---|---|---|
| `display_name` | **required** | フォルダ名。最大128文字 |
| `app_id` | **required** | アプリID（`auth_info.json` の `app_type` を使用） |
| `utc_offset` | **required** | UTCオフセット。形式: `+09:00` |
| `description` | optional | 説明。最大1024文字 |
| `tags` | optional | タグ配列。最大20件 |

レスポンス（201）: `{ "folder_id": "<user_id>-f-<8桁hex>" }`

### PUT /api/v1/folders/{folder_id}（フォルダ情報更新）
| フィールド | 要否 | 説明 |
|---|---|---|
| `display_name` | optional | 新しいフォルダ名 |
| `description` | optional | 説明 |
| `tags` | optional | タグ配列 |

### POST /api/v1/folders/{folder_id}/upload（アップロードセッション開始）
| フィールド | 要否 | 説明 |
|---|---|---|
| `name` | optional | ファイル名（拡張子がサムネイル生成判定に使用される） |
| `target` | optional | バックエンド動作指定。例: `"content/original/image"` |

レスポンス（200）: `{ "upload_id": "...", "upload_url": "https://..." }`

### POST /api/v1/folders/{folder_id}/contents（コンテンツ登録）
| フィールド | 要否 | 説明 |
|---|---|---|
| `upload_id` | **required** | アップロードセッションで取得した ID |
| `kind` | **required** | リソース種別。例: `"original"`, `"proxy"` |
| `name` | **required** | ファイル名 |
| `bytes` | **required** | ファイルサイズ（バイト） |
| `utc_offset` | **required** | UTCオフセット。形式: `+09:00` |
| `content_type` | optional | MIMEタイプ。例: `"image/jpeg"` |

**クエリパラメータ**:
| `wait_time` | optional | サーバー側の処理待ち時間。デフォルト `"0s"`、最大 `"10s"` |

**レスポンス**:
- 200: `{ "content_id": "c-<8桁hex>" }`
- **425 Too Early**: `upload_id` のオブジェクトがまだ処理中。`wait_time=10s` を付けるか、数秒待ってリトライすること。

> **注意**: コンテンツのアップロードは3ステップ:
> 1. `POST /api/v1/folders/{folder_id}/upload` → `upload_id` + `upload_url` 取得
> 2. `PUT <upload_url>` にバイナリ送信
> 3. `POST /api/v1/folders/{folder_id}/contents` でフォルダへ登録
>
> ~~`POST /api/file/v1/{account}:upload`~~ はファイルストレージ用（フォルダ管理とは別系統）。

### POST /api/v1/folders/{folder_id}/contents:remove（コンテンツ削除）
| フィールド | 要否 | 説明 |
|---|---|---|
| `content_ids` | **required** | 削除するコンテンツIDの配列（最大1000件） |

レスポンス（200）: `{ "deleted_content_ids": [...] }`

### GET /api/v1/folders/{folder_id}/contents/{content_id}/resources/{kind}/binary（コンテンツバイナリ取得）

パスパラメータ:
| パラメータ | 説明 |
|---|---|
| `kind` | リソース種別。例: `"original"`, `"proxy"`, `"thumbnail_1920"` |

**レスポンス（200）**: コンテンツのバイナリデータ（Content-Type: image/jpeg など）

### 画像リタッチ・動画編集
- 画像リタッチ: `POST /api/image/v1/folders/{folder_id}/contents/{content_id}/resources/{kind}:retouch`
- 動画編集ジョブ作成・進捗取得・編集済みコンテンツ登録など。

### デバイス管理
- 所有デバイス一覧取得: `GET /api/v1/devices`
- カメラ・レンズの登録/更新/削除、ライセンス管理など。

### WebSocket
- ユーザーごとのWebSocketチャネル作成: `POST /api/v1/cms/websocket/users/{user_id}/channels`
  - ping/pongによるKeepAlive対応。

### クライアントログ
- ログアップロードセッション開始: `POST /api/v1/clientlogs/{app_type}/log:upload`
- ログファイル登録（operationlog / errorlog）: `POST /api/v1/clientlogs/{app_type}/log`

### アクションログ
- アクションログアップロード: `POST /api/v1/actionlogs/{app_type}`

## 備考
- ほとんどのAPIはBearer認証が必要。
- OpenAPI仕様書も提供。
- パラメータやリクエストボディはエンドポイントごとに詳細な指定が必要。
