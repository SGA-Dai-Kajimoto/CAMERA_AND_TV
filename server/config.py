"""
QR 認証ローカルサーバーの設定。

環境変数で上書き可能（未設定時はデフォルト値を使用）:
  SONY_BASE_URL   ... Imaging Edge / Creators Cloud のベースURL
  APP_TYPE        ... アプリ識別子（例: _trial_）
  PUBLIC_BASE_URL ... このサーバー自身の到達可能URL。
                      Sony OAuth の redirect_url とQRの pairing_url に使う。
                      例: http://192.168.1.10:8000  （同一LANのTV/スマホから見えるURL）
  SESSION_TTL_SEC ... ペアリングセッションの有効秒数（デフォルト 300）
  HOST / PORT     ... uvicorn の待受ホスト/ポート
"""

import os


def _env(key: str, default: str) -> str:
    value = os.environ.get(key, "").strip()
    return value if value else default


# Sony 側
SONY_BASE_URL: str = _env("SONY_BASE_URL", "https://ws.dev.imagingedge.sony.net").rstrip("/")
APP_TYPE: str = _env("APP_TYPE", "_trial_")

# このサーバー自身の公開URL（TV/スマホから到達できるアドレス）
# 末尾スラッシュ無しに正規化する
PUBLIC_BASE_URL: str = _env("PUBLIC_BASE_URL", "http://localhost:8000").rstrip("/")

# セッション有効期限（秒）
SESSION_TTL_SEC: int = int(_env("SESSION_TTL_SEC", "300"))

# uvicorn
HOST: str = _env("HOST", "0.0.0.0")
PORT: int = int(_env("PORT", "8000"))
