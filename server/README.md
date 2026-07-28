# QR コード認証ローカルサーバー

TV に表示した QR コードをスマホで読み取り、Creators Cloud にログインして
アクセストークンを TV へ渡すための **OAuth 仲介ローカルサーバー**です。

Sony の API は「認可コードフロー（ブラウザリダイレクト型）」のみで、
TV に数字/QR を出すデバイスコード方式は無いため、本サーバーが
OAuth クライアント兼ペアリング仲介役を担います。

## 認証フロー

```
TV → POST /pairing/start                → session_id / device_secret / pairing_url
TV → pairing_url を QR 表示・ポーリング
携帯 → QR 読み取り GET /p/{session_id}  → Sony 認可URLへ 302
携帯 → Creators Cloud ログイン
Sony → GET /callback?code=&state=       → code をトークンに交換して保存
TV → GET /pairing/{session_id}          → completed でトークン取得（単回消費）
```

## セットアップ

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## 起動

`PUBLIC_BASE_URL` には **TV とスマホの両方から到達できる** このサーバーの
アドレス（＝PC の LAN IP）を指定します。`localhost` は TV から届かないため不可。

```powershell
# 例: PC の LAN IP が 192.168.1.10 の場合
$env:PUBLIC_BASE_URL = "http://192.168.1.10:8000"
$env:SONY_BASE_URL   = "https://ws.dev.imagingedge.sony.net"
$env:APP_TYPE        = "_trial_"
python main.py
```

PC の LAN IP は `ipconfig` の「IPv4 アドレス」で確認できます。

## 環境変数

| 変数 | 既定値 | 説明 |
|---|---|---|
| `SONY_BASE_URL` | `https://ws.dev.imagingedge.sony.net` | Sony API のベースURL |
| `APP_TYPE` | `_trial_` | アプリ識別子 |
| `PUBLIC_BASE_URL` | `http://localhost:8000` | **このサーバーの到達可能URL**（redirect_url と QR に使用） |
| `SESSION_TTL_SEC` | `300` | ペアリングセッションの有効秒数 |
| `HOST` | `0.0.0.0` | uvicorn 待受ホスト |
| `PORT` | `8000` | uvicorn 待受ポート |

## TV アプリ側の設定

`Camera_tv/local.properties` に、このサーバーの URL を設定します。

```properties
pairing.serverUrl=http://192.168.1.10:8000
```

## エンドポイント

| メソッド | パス | 用途 | 認証 |
|---|---|---|---|
| POST | `/pairing/start` | ペアリング開始（TV） | — |
| GET | `/p/{session_id}` | QR から開く→Sony認可へ（携帯） | — |
| GET | `/callback` | Sony からのコールバック | state 検証 |
| GET | `/pairing/{session_id}` | トークン取得ポーリング（TV） | `X-Device-Secret` |
| GET | `/health` | ヘルスチェック | — |

## セキュリティ上の注意

- **HTTP 平文**で動作するため、必ず**信頼できる LAN 内のみ**で使用すること。
- 公開ネットワークに出す場合は HTTPS（リバースプロキシ等）を前提とすること。
- **PKCE (RFC 7636)** を使用（`code_challenge`/`code_verifier`）し、認可コード横取りを防止。
- `redirect_url` に自作サーバーURLを指定できるかは Sony 側アプリ設定に依存する。
  指定不可の場合は本方式は成立しないため、事前確認が必要。
- セッションはインメモリ管理（プロセス再起動で消える）。単一プロセス運用が前提。

## API 仕様上の注意（確認済み）

- `GET /api/v1/oauth2/auth` の `device_type` は Enum **`pc` / `mobile` / `mobile_sso`** のみで
  `tv` は無い。本サーバーはスマホのブラウザでログインするため `device_type` を**省略**し、
  Web ブラウザ扱いとしている（省略により `app_version` / `platform` 等の必須化も回避）。
- トークン交換は `POST /api/v1/oauth2/token` に `auth_code` + `code_verifier`（PKCE）を送る。

## 重要な未確認事項

Sony の `_trial_` アプリで、**任意の `redirect_url`（このローカルサーバー）を
許可できるか**が本方式全体の前提です。許可されていない場合、`/p/{session_id}`
からのリダイレクト後に Sony 側でエラーになります。まず1件、実機で疎通確認を
してください。
