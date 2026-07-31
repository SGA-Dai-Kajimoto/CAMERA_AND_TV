"""
トークン中継サーバーの設定。

環境変数で上書き可能（未設定時はデフォルト値を使用）:
  DEFAULT_BASE_URL ... TV に渡す API ベースURL（ブックマークレットが未指定の場合）
  DEFAULT_APP_TYPE ... TV に渡す app_type（リフレッシュ時に使用）
  LOGIN_URL        ... ログインを促す Creators' Cloud Web アプリの URL
  SESSION_TTL_SEC  ... ペアリングセッションの有効秒数（デフォルト 300）
  HOST / PORT      ... uvicorn の待受ホスト/ポート
"""

import os


def _env(key: str, default: str) -> str:
    value = os.environ.get(key, "").strip()
    return value if value else default


# TV に渡すトークンのデフォルト（ブックマークレットが未指定の場合に使用）
DEFAULT_BASE_URL: str = _env("DEFAULT_BASE_URL", "https://ws.dev3.imagingedge.sony.net").rstrip("/")
DEFAULT_APP_TYPE: str = _env("DEFAULT_APP_TYPE", "creatorsappweb")

# ログインを促す Creators' Cloud Web アプリの URL（セットアップページで別タブに開く）
LOGIN_URL: str = _env("LOGIN_URL", "https://w.dev3.creatorscloud.sony.net/app/ja-jp/")

# セッション有効期限（秒）
SESSION_TTL_SEC: int = int(_env("SESSION_TTL_SEC", "300"))

# uvicorn
HOST: str = _env("HOST", "0.0.0.0")
PORT: int = int(_env("PORT", "8000"))
