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
