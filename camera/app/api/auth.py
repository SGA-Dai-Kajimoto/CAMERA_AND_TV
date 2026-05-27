"""
auth_info.json の読み書きユーティリティ。

AUTH_FILE: 認証情報JSONのパス（camera/api/auth_info.json）
load_auth: 読み込み（ファイルが無ければ FileNotFoundError）
save_auth: 書き込み（上書き）
"""

import json
from pathlib import Path

# camera/app/api/auth.py → parent×3 = camera/ → api/auth_info.json
AUTH_FILE = Path(__file__).parent.parent.parent / "api" / "auth_info.json"


def load_auth() -> dict:
    if not AUTH_FILE.exists():
        raise FileNotFoundError(
            f"認証情報ファイルが見つかりません: {AUTH_FILE}\n"
            "camera/api/auth_info.json にトークンを記入してください（docs/auth_spec.md 参照）"
        )
    with open(AUTH_FILE, encoding="utf-8") as f:
        return json.load(f)


def save_auth(auth: dict) -> None:
    with open(AUTH_FILE, "w", encoding="utf-8") as f:
        json.dump(auth, f, indent=2, ensure_ascii=False)
