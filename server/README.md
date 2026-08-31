# TV ペアリングサーバー（PKCE 擬似デバイスフロー）

TV に文字を入力せずに Creators' Cloud へログインするための中継サーバーです。

## なぜこの方式か

AccountPF は RFC 8628 のデバイスフローを提供していませんが、実測で次の 2 点が
分かったため、認可サーバー本体に手を入れずに同等の UX を作れます（`probe_oauth.py`）。

1. `redirect_url` は `localhost` / `127.0.0.1` なら任意ポート・任意パスが許可される
2. PKCE が強制されている（`code_verifier` 無しのトークン交換は 401）

## 認証フロー

```
TV → POST /device/authorize {code_challenge}  → device_code / user_code / QR URL
TV → user_code と QR を表示し、POST /device/code をポーリング
PC → QR の URL（GET /device?user_code=...）をブラウザで開く  → AccountPF へ 302
PC → ログイン・同意                        → GET /callback/{state}?auth_code=...
TV → POST /device/code {device_code}         → auth_code を受け取る（単回消費）
TV → POST {base_url}/api/v1/oauth2/token     → **TV 自身が**トークンに交換
```

- **中継サーバーは `code_verifier` もトークンも見ない**。`auth_code` だけを中継する
- PKCE 強制のおかげで、`auth_code` を盗まれても `code_verifier` が無ければ使えない
- **TV での文字入力不要**（TV は QR と番号を表示するだけ）
- ブラウザは **サーバーと同じ PC** で開くこと。`redirect_url` が localhost 限定のため、
  スマホで開くとコールバックがスマホ自身へ戻って届かない

## セットアップ

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## 起動

`server/start_pairing_server.bat` をダブルクリックするだけで起動します。

起動時に「TV アプリの `local.properties` に貼る行」を PC の LAN IP つきで表示します。
この IP は DHCP で変わるため、繋がらないときはまずここを確認してください。

bat は起動前に 2 つ確認します。

- 仮想環境（`.venv`）があるか
- ポートが空いているか（他プロジェクトの開発サーバーと衝突しがち）

ポートを変える場合:

```powershell
$env:PORT = "8010"
.\server\start_pairing_server.bat
```

コマンドから直接動かす場合:

```powershell
.\.venv\Scripts\python.exe server\device_flow.py
```

## 動作確認

サーバーを起動した状態で実行します。

```powershell
.\.venv\Scripts\python.exe server\test_device_flow.py        # 21項目（ログイン不要）
.\.venv\Scripts\python.exe server\test_device_flow.py e2e    # ブラウザログインを含む通し確認
```

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

| メソッド | パス | 用途 |
|---|---|---|
| POST | `/device/authorize` | デバイス認可の開始。`code_challenge` を登録し `user_code` を発行（TV） |
| GET | `/device?user_code=` | QR の遷移先。AccountPF の認可画面へ 302（ブラウザ） |
| GET | `/callback/{state}` | 認可コードの受け取り（AccountPF） |
| POST | `/device/code` | `auth_code` 取得ポーリング。単回消費（TV） |
| GET | `/health` | ヘルスチェック |

## セキュリティ上の注意

- **HTTP 平文**で動作するため、必ず**信頼できる LAN 内のみ**で使用すること。
  ただし平文を流れるのは `auth_code` までで、トークンと `code_verifier` は通らない。
- `code_verifier` は TV のメモリ上にしか存在しない。サーバーに送らない。
- `auth_code` は 1 度取得すると消費。セッションはインメモリ（再起動で消える）。
- `user_code` を知った第三者が先にログインすると、その人のアカウントが TV に紐づく。
  外部 IP からの総当たりは回数制限で止めている（ループバックは対象外）。

## 補足

- デバイスフローの `app_type` は `_trial_`（`DEVICE_APP_TYPE`）。
  認可時と交換時で揃えないと失敗するため、`/device/code` の応答で TV へ伝えている。
- TV から `DEFAULT_BASE_URL`（dev3 API）へ**直接到達できる必要がある**。
  トークン交換もコンテンツ取得も TV が直接行うため。
