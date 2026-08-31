# 認証情報 JSON 仕様書

## ファイル配置

```
camera/api/auth_info.json   ← 実際のトークン（git管理外）
```

`.gitignore` に `*.json` が登録されているため、このファイルはリポジトリに含まれません。  
**絶対にトークンをコミットしないこと。**

---

## JSON スキーマ

```json
{
  "base_url":          "https://ws.dev.imagingedge.sony.net",
  "app_type":          "_trial_",
  "access_token":      "<取得したアクセストークン>",
  "access_token_ttl":  0,
  "refresh_token":     "<取得したリフレッシュトークン>",
  "refresh_token_ttl": 0,
  "user_id":           "",
  "account":           ""
}
```

| フィールド | 説明 | 自動更新 |
|---|---|---|
| `base_url` | APIのベースURL | — |
| `app_type` | アプリ識別子（通常 `_trial_`） | — |
| `access_token` | 認証トークン（有効期限あり） | ✅ refresh時 |
| `access_token_ttl` | アクセストークンの有効期限（Unix時間） | ✅ refresh時 |
| `refresh_token` | アクセストークン更新用トークン | ✅ refresh時 |
| `refresh_token_ttl` | リフレッシュトークンの有効期限（Unix時間） | ✅ refresh時 |
| `user_id` | ログイン後に `/user/me` から自動取得 | ✅ check_api実行時 |
| `account` | ファイルAPIパス用ID（`user_id` と同値） | ✅ check_api実行時 |

---

## アクセストークンの取得手順

アクセストークンは OAuth2 認可コードフローで取得します。  
現時点では手動取得が必要です。

### Step 1: 認可URLを開く

ブラウザで以下のURLを開き、ログインする。

```
https://ws.dev.imagingedge.sony.net/api/v1/oauth2/auth
  ?lang=ja
  &country=JP
  &redirect_url=https%3A%2F%2Fws.dev.imagingedge.sony.net%2F
  &app_type=_trial_
  &device_id=test-device-001
  &device_type=pc
```

### Step 2: 認可コードを取得する

ログイン後、リダイレクト先URLの `code=` パラメータを取り出す。

```
https://ws.dev.imagingedge.sony.net/?code=XXXXXX&...
                                           ^^^^^^
                                           これが auth_code
```

### Step 3: アクセストークンに交換する

```bash
curl -X POST https://ws.dev.imagingedge.sony.net/api/v1/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{
    "app_type": "_trial_",
    "auth_code": "<Step2で取得したcode>"
  }'
```

レスポンスの `access_token` と `refresh_token` を `auth_info.json` に記入する。

---

## 使い方

### API疎通確認（CUI）

```bash
python camera/app/check_api.py
```

実行内容:
1. `auth_info.json` からトークンを読み込む
2. `GET /api/v1/user/me` でユーザー情報を確認
   - 401 の場合は `refresh_token` で自動更新して再試行
3. `GET /api/file/v1/{account}` でコンテナ一覧を確認
4. `GET /api/v1/folders` でフォルダ一覧を確認

### トークン更新

`check_api.py` 実行中に access_token が期限切れの場合、  
自動で refresh_token を使って更新し `auth_info.json` を上書きします。

---

## 注意事項

- `auth_info.json` は絶対に git にコミットしない
- `refresh_token` にも有効期限あり。切れた場合は Step 1〜3 を再実行する
- `base_url` を本番環境に変更する場合は別途確認が必要

---

## Refresh Token API 仕様（AccountPF）

### エンドポイント

```
POST /api/v1/oauth2/token
Content-Type: application/json
```

### リクエストボディ

| フィールド | 必須 | 説明 |
|---|---|---|
| `app_type` | — | アプリ識別子（例: `_trial_`） |
| `app_sub_type` | — | サブタイプ。複数アプリで同一トークンをリフレッシュする場合のみ使用 |
| `refresh_token` | ✅ | 現在保持中の Refresh Token |
| `refresh_token_ttl` | ✅ | Refresh Token の有効期限（Unix timestamp） |

> `auth_code` と `refresh_token` はどちらか一方のみ指定する（同時使用不可）。

**リクエスト例:**

```bash
curl -X POST https://<host>/api/v1/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{
    "app_type": "_trial_",
    "refresh_token": "YYYYYYYYYYYYYYYY",
    "refresh_token_ttl": 1535281019
  }'
```

### レスポンスボディ

| フィールド | 説明 |
|---|---|
| `access_token` | 新しい Access Token |
| `access_token_ttl` | Access Token の有効期限（Unix timestamp） |
| `refresh_token` | 新しい Refresh Token |
| `refresh_token_ttl` | Refresh Token の有効期限（Unix timestamp） |

**レスポンス例:**

```json
{
  "access_token": "XXXXXXXXXXXXXXXXX",
  "access_token_ttl": 1532602619,
  "refresh_token": "YYYYYYYYYYYYYYYY",
  "refresh_token_ttl": 1535281019
}
```

### Refresh Token に関する注意事項（API仕様より）

| 項目 | 内容 |
|---|---|
| 事前確認 | リクエスト前に `refresh_token_ttl` が切れていないか確認すること |
| 使い切り | 一度使用または期限切れになった Refresh Token は再利用不可 |
| 再認証 | Refresh Token 期限切れ時は `GET /api/v1/oauth2/auth` から再ログイン |
| リトライ猶予 | ネットワーク障害で取得失敗した場合、**60秒以内**に限り同一の古い Refresh Token で1回リトライ可能 |

## ペアリングサーバーの秘密情報の扱い（2026-08-31 見直し）

### どこに何が渡るか

トークン交換は **TV 自身**が AccountPF へ直接行う。中継サーバーは `auth_code` を渡すだけ。

| データ | TV | ペアリングサーバー | ブラウザ | AccountPF |
|---|---|---|---|---|
| `code_verifier` | 生成・**メモリのみ** | **渡らない** | 渡らない | 交換時に受け取る |
| `code_challenge` | 生成 | セッションに保持 | URL に含む | 受け取る |
| `device_code` | 保持 | セッションに保持 | 渡らない | 渡らない |
| `user_code` | 画面表示 | セッションに保持 | URL に含む | 渡らない |
| `auth_code` | 受け取る | 中継する（**トークン化はできない**） | URL に含む | 発行 |
| `access_token` / `refresh_token` | DataStore に保存 | **渡らない** | 渡らない | 発行 |

### 成立している防御

- **PKCE が強制**されているため、`auth_code` を握っても単独ではトークン化できない
  （実測: `code_verifier` 無しは 401）。これは中継サーバーにも LAN の盗聴者にも当てはまる
- サーバーはトークンを一切見ない。`auth_code` も返却前に `store.delete()` して単回消費
- セッションはインメモリ・TTL 300秒・上限 200 件
- `device_code` は 256bit 乱数、`user_code` は 8 文字 / 20 種（約 2.6×10^10 通り）
  ＋ 外部 IP の総当たり制限

### 平文 HTTP で盗聴された場合

TV ⇔ 中継サーバーは平文 HTTP（`usesCleartextTraffic="true"`）だが、
流れるのは `auth_code` / `device_code` / `user_code` まで。
`code_verifier` が無ければトークンにできず、`code_challenge` から逆算もできない。
TV ⇔ AccountPF は HTTPS なので、トークンと `code_verifier` は LAN に出ない。

### 残っているリスク

| # | 内容 | 影響 |
|---|---|---|
| 1 | `user_code` を TV 画面・サーバーコンソール・logcat に出す | 5 分以内に第三者が先にログインすると、**その第三者のアカウント**が TV に紐づく |
| 2 | 平文 HTTP で `auth_code` が見える | 単体では使えないが、TV より先に `/device/code` を叩けば認証を妨害できる（DoS） |

### 修正済み

- トークン交換をサーバーから TV へ移した（2026-08-31）。
  以前は `POST /device/token` でサーバーが交換しており、その瞬間だけ
  `code_verifier`・`auth_code`・発行トークンを同時に保持していた。
- `/callback/{state}?error=...` の反射型 XSS（2026-08-31 実測で再現・`html.escape` で修正）。
