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

# ---------------------------------------------------------------- #
#  デバイスフロー（device_flow.py）用
# ---------------------------------------------------------------- #

# ブラウザ・TV から見たこのサーバーの URL。QR に載せる入口に使う。
# トンネル経由で公開する場合はここに公開 URL（https://xxx.trycloudflare.com 等）を入れる。
PUBLIC_BASE_URL: str = _env("PUBLIC_BASE_URL", f"http://localhost:{PORT}").rstrip("/")

# AccountPF に渡す redirect_url のベース。
# ホワイトリストが localhost / 127.0.0.1 しか許可していないため、公開 URL にしてはいけない。
# （実測済み: それ以外のホストは 400 Invalid redirectUrl）
REDIRECT_BASE_URL: str = _env("REDIRECT_BASE_URL", f"http://localhost:{PORT}").rstrip("/")

# TV に返すポーリング間隔（秒）
POLL_INTERVAL_SEC: int = int(_env("POLL_INTERVAL_SEC", "3"))

# デバイスフローで使う app_type。
# redirect_url のホワイトリストと PKCE の動作を _trial_ で実測済み（probe_oauth.py）。
DEVICE_APP_TYPE: str = _env("DEVICE_APP_TYPE", "_trial_")

# 同時に保持するペアリングセッションの上限。セッション量産によるメモリ枯渇を防ぐ。
MAX_ACTIVE_SESSIONS: int = int(_env("MAX_ACTIVE_SESSIONS", "200"))

# user_code の総当たり対策。同一 IP からの連続失敗がこの回数を超えたら一時的に拒否する。
MAX_LOOKUP_FAILURES: int = int(_env("MAX_LOOKUP_FAILURES", "10"))
LOOKUP_BLOCK_SEC: int = int(_env("LOOKUP_BLOCK_SEC", "300"))

# TV がペアリングを開始したときに、この PC のブラウザを自動で開くか。
# 検証スクリプトも /device/authorize を叩くため、既定は無効。
AUTO_OPEN_BROWSER: bool = _env("AUTO_OPEN_BROWSER", "0") == "1"
