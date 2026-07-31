# トークン中継ローカルサーバー

PC で Creators' Cloud にログインして得たアクセストークンを、**貼り付けなしで TV へ渡す**
ためのローカルサーバーです。

## なぜこの方式か

dev3 環境の OAuth は `redirect_url` が Sony ドメインにホワイトリスト固定されており、
自作サーバーを `redirect_url` にした自動コールバック方式は WAF に **403** で弾かれます。
ログインは PC の Web アプリで行い、トークンは `localStorage.accessTokenSet` に保存されます。
そこで、その値を **ブックマークレット**（PC ブラウザで1クリック）で読み取り、
6桁コードで対象の TV を指定してサーバー経由で渡します。

## 認証フロー

```
TV → POST /pairing/start                → session_id / device_secret / user_code(6桁)
TV → 6桁コードを画面表示・ポーリング
PC → Creators' Cloud にログイン
PC → ブックマークレット実行（localStorage 読取 + 6桁入力）
PC → POST /submit {user_code, tokens}   → セッションに保存
TV → GET /pairing/{session_id}          → completed でトークン取得（単回消費）
```

- **貼り付け不要**（ブックマークレットが自動でトークンを読む）
- **TV での文字入力不要**（TV は6桁を表示するだけ）
- **ログインURLを変えない**（WAF/ホワイトリストに触れない）

## セットアップ

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## 起動

ログインと同じ PC で起動します。TV は LAN 経由でポーリングするため、
`Camera_tv/local.properties` の `pairing.serverUrl` には PC の LAN IP を指定します。

```powershell
# 既定は 0.0.0.0:8000。ポートを変える場合のみ PORT を設定
$env:PORT = "8010"
python main.py
```

起動後、PC ブラウザで `http://localhost:<PORT>/` を開くと、
ログインボタンとブックマークレット登録ページが表示されます。

## 環境変数

| 変数 | 既定値 | 説明 |
|---|---|---|
| `DEFAULT_BASE_URL` | `https://ws.dev3.imagingedge.sony.net` | TV に渡す API ベースURL |
| `DEFAULT_APP_TYPE` | `creatorsappweb` | TV に渡す app_type（リフレッシュ時に使用） |
| `LOGIN_URL` | `https://w.dev3.creatorscloud.sony.net/app/ja-jp/` | ログインを促す Web アプリ |
| `SESSION_TTL_SEC` | `300` | 6桁コード / セッションの有効秒数 |
| `HOST` | `0.0.0.0` | uvicorn 待受ホスト |
| `PORT` | `8000` | uvicorn 待受ポート |

## TV アプリ側の設定

`Camera_tv/local.properties`（git 管理外）:

```properties
# QR認証を使う場合、dev.accessToken / dev.refreshToken は空にすること
pairing.serverUrl=http://192.168.11.4:8010
```

## エンドポイント

| メソッド | パス | 用途 | 認証 |
|---|---|---|---|
| GET | `/` | セットアップページ（ログイン導線・ブックマークレット配布） | — |
| POST | `/pairing/start` | ペアリング開始・6桁コード発行（TV） | — |
| POST | `/submit` | トークン投入（PC ブックマークレット） | `user_code` |
| GET | `/pairing/{session_id}` | トークン取得ポーリング（TV） | `X-Device-Secret` |
| GET | `/health` | ヘルスチェック | — |

## セキュリティ上の注意

- **HTTP 平文**で動作するため、必ず**信頼できる LAN 内のみ**で使用すること。
- ブックマークレットは HTTPS ページ(Sony)から `http://127.0.0.1:<PORT>` へ送る。
  `127.0.0.1` は HTTPS からでも許可されるため、**ログインとサーバーは同一 PC** が前提。
- `user_code`（6桁）で TV セッションを特定、`device_secret` で TV 以外の取得を防止。
- トークンは1度取得すると消費（再取得不可）。セッションはインメモリ（再起動で消える）。

## 補足

- `app_type` は、トークンを発行した Web アプリに合わせ `creatorsappweb` を既定にしている。
  リフレッシュが失敗する場合は `DEFAULT_APP_TYPE=_trial_` で再試行する。
- TV から `DEFAULT_BASE_URL`（dev3 API）へ直接到達できる必要がある。
  dev3 が IP 制限されている場合、認証後の API 呼び出しが失敗しうる点に注意。
