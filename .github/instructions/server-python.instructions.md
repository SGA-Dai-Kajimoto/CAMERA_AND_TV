---
description: "Use when writing or modifying Python under server/ (FastAPI pairing server, OAuth probe scripts): endpoint conventions, secret handling, verification scripts, Windows networking gotchas."
applyTo: "server/**/*.py"
---

# server/（ペアリングサーバー）の実装ルール

## 役割

TV を Creators Cloud にサインインさせるための仲介サーバー。PKCE ベースの擬似デバイスフローを実装している。

| ファイル | 役割 |
|---|---|
| `device_flow.py` | 本体。`/device/authorize` `/device` `/callback/{state}` `/device/token` |
| `config.py` | 環境変数で上書きできる設定。ハードコードしない |
| `setup_check.py` | 実機確認の前チェック（到達性・同一ネットワーク判定） |
| `test_device_flow.py` | 疎通確認。`e2e` 引数でログインまで含めた通し確認 |
| `probe_oauth.py` | AccountPF の仕様を実測する調査スクリプト |
| `loopback_bridge.py` | 別 PC のブラウザから使う場合の中継 |

## 設計上の不変条件

これらを壊すと安全性の前提が崩れる。変更するときは理由を明示すること。

- **サーバーは `code_verifier` を保存しない。** 受け取ったその場で AccountPF に渡すだけ
- **サーバーはトークンを保持しない。** TV へ返したらセッションごと破棄する（単回消費）
- **`REDIRECT_BASE_URL` は必ず `localhost` / `127.0.0.1`。** AccountPF のホワイトリストが
  それ以外を拒否する。TV 向けの `PUBLIC_BASE_URL` とは役割が違うので混同しない
- `state` は `redirect_url` の**パスに埋める**。AccountPF がクエリの `state` を返す保証がない

## 実装の約束

- エラーは RFC 8628 に倣った `{"error": "..."}` 形式で返す
  （`authorization_pending` / `slow_down` / `expired_token` / `access_denied`）
- 外部公開されうる前提で書く。総当たり可能な値にはレート制限を入れる
- ブロッキング I/O（`requests`）を使うハンドラは `async def` にしない。
  同期 `def` にして FastAPI のスレッドプールに逃がす
- 戻り値が複数の Response 型のユニオンになるハンドラは `@app.get(..., response_model=None)` を付ける

## 秘密情報の扱い

- トークンを標準出力に**全文で出さない**。出すときは先頭12文字＋`…`
- `auth_code` / `code_verifier` をファイルに残すなら `.gitignore` に入れる

## 確認方法

```powershell
python server/device_flow.py          # 起動
python server/test_device_flow.py     # ログイン不要の自動確認
python server/test_device_flow.py e2e # ブラウザログインを含む通し確認
```

## Windows でのはまりどころ

- uvicorn は `0.0.0.0`（IPv4）に bind する。クライアントが `localhost` を `::1` に解決すると
  **接続ごとに約2秒待たされる**。スクリプトからは `127.0.0.1` を直接指定し、
  `requests.Session` で接続を使い回す
- `socket.getaddrinfo(gethostname())` は社内 DNS の登録値を返し、実インターフェースと食い違う。
  自ホストのアドレスは**UDP ソケットを connect して `getsockname()`** で取る
- レート制限のテストは最後に実行する。先に走らせると後続のテストが 429 で落ちる
