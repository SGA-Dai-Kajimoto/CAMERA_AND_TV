---
name: accountpf-api-probe
description: 'AccountPF / Imaging Edge API の仕様を実測で確かめる。Use when checking redirect_url whitelist, PKCE support, OAuth token exchange parameters, or any AccountPF behaviour that must be verified rather than assumed. Also the record of what has already been measured (dev3, 2026-08-21).'
argument-hint: '確かめたいこと（例: redirect_url に自前ホストを使えるか）'
---

# AccountPF API の実測調査

このプロジェクトでは **API の挙動を推測で決めない**。`server/probe_oauth.py` で実際に叩いて確かめ、結果をここと `docs/` に記録する。

## 使い方

```powershell
# redirect_url ごとの反応を比較する（ログイン不要）
python server/probe_oauth.py redirect

# PKCE パラメータが受理・引き継ぎされるか（ログイン不要）
python server/probe_oauth.py pkce-support

# PKCE 付きの認可 URL を発行する → ブラウザでログイン
python server/probe_oauth.py pkce-url

# 認可コードでトークン交換を試す。1 ログインにつき 1 ケース
python server/probe_oauth.py pkce-exchange <auth_code> [none|wrong|ok|ok-camel]
```

環境変数で対象を変えられる。

| 変数 | 用途 |
|---|---|
| `IE_BASE_URL` | ベース URL |
| `IE_APP_TYPE` | `app_type`（ホワイトリストは app_type ごとに違う可能性がある） |
| `IE_REDIRECT_URLS` | `redirect` で追加検証したい URL をカンマ区切りで |

## 実測済みの結果（dev3 / 2026-08-21）

**推測ではなく実測値。** 環境や時期が変わったら再確認すること。

### redirect_url のホワイトリスト

`GET /api/v1/oauth2/auth` は受理すると 302、拒否すると
`400 {"error":{"code":400,"message":"Invalid redirectUrl"}}` を返す。**WAF ではなくアプリ層の判定。**

| redirect_url | 結果 |
|---|---|
| `https://ws.dev3.imagingedge.sony.net/<任意のパス>` | 受理 |
| `https://w.dev3.creatorscloud.sony.net/<任意のパス>` | 受理 |
| **`http(s)://localhost:<任意ポート>/<任意パス>`** | **受理** |
| **`http://127.0.0.1:<任意ポート>/<任意パス>`** | **受理** |
| `https://<その他のホスト>/callback`（`*.sony.net` の別ホストを含む） | 拒否 |
| `http://192.168.x.x:8000/callback` / `*.local` | 拒否 |

→ **ホスト単位の登録制。** SEN 内にサーバーを立てるだけでは通らず、ホスト名の登録が必要。
ループバックが空いているのは RFC 8252 の配慮によるもので、これを利用して仲介サーバーを組んでいる。

### PKCE

**強制されている。**

| 送信内容 | 結果 |
|---|---|
| `{app_type, auth_code}` のみ | `401 {"error":{"code":401,"message":"Code verifier is required."}}` |
| `+ code_verifier`（snake_case） | `200` トークン取得成功 |

- 認可リクエストの `code_challenge` はログイン画面へ引き継がれる。`code_challenge_method` は落ちる（S256 固定と思われる）
- **検証に失敗しても `auth_code` は消費されない。** 正しい値で再試行すると成功する
  → オンライン総当たりの余地があるため、仲介サーバー側で試行回数を制限している

### その他

- 認可コードは **`auth_code=`** で返る（`code=` ではない）
- エラーメッセージは camelCase（`Invalid redirectUrl`）だが、トークン交換のボディは snake_case
- 実体の IdP は `account.cassia.io`。AccountPF がラップしていて `state` / `nonce` は AccountPF が持つ

## 新しく調べるときの進め方

1. **ログイン不要で分かることから潰す。** 認可エンドポイントは 302 / 400 で多くを判別できる
2. `auth_code` は使い切りなので、**1 ログインにつき 1 ケース**に絞る
3. トークンは全文を出力しない（`probe_oauth.py` は先頭12文字だけ出す）
4. 分かったことは `docs/api_spec_summary.md` に、はまりどころは
   `docs/learn/imaging_edge_api.md` に**測定日と条件つきで**追記する
