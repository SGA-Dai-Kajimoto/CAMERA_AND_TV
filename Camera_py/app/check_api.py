"""
Imaging Edge API 疎通確認スクリプト (CUI)

使い方:
    python camera/app/check_api.py

事前準備:
    camera/api/auth_info.json に access_token / refresh_token を記入すること。
    取得方法は docs/auth_spec.md を参照。

モジュール構成:
    camera/app/design/module_design.md を参照。
"""

import sys
from pathlib import Path

# camera/app/ を sys.path に追加して api/ パッケージをインポートできるようにする
sys.path.insert(0, str(Path(__file__).parent))

from api.client import ApiClient  # noqa: E402


# ------------------------------------------------------------------ #
#  各確認項目（結果を標準出力に表示する CUI 専用ラッパー）
# ------------------------------------------------------------------ #

def check_user_me(client: ApiClient) -> dict:
    """[1] GET /api/v1/user/me — ユーザー情報取得"""
    print("[1] GET /api/v1/user/me")
    data = client.get_user_me()
    print(f"  → OK  user_id={data.get('user_id')}  email={data.get('email')}")
    return data


def check_containers(client: ApiClient) -> None:
    """[2] GET /api/file/v1/{account} — コンテナ一覧取得"""
    account = client.auth.get("account", "")
    print(f"\n[2] GET /api/file/v1/{account}")
    containers = client.list_containers()
    print(f"  → OK  {len(containers)} コンテナ")
    for c in containers:
        print(f"       - {c.get('name')}  ({c.get('count')} objects, {c.get('bytes')} bytes)")


def check_folders(client: ApiClient) -> None:
    """[3] GET /api/v1/folders — フォルダ一覧取得"""
    print("\n[3] GET /api/v1/folders")
    folders = client.list_folders()
    print(f"  → OK  {len(folders)} フォルダ")
    for folder in folders[:5]:
        print(f"       - [{folder.get('folder_id')}] {folder.get('display_name')}")
    if len(folders) > 5:
        print(f"       ... 他 {len(folders) - 5} 件")


# ------------------------------------------------------------------ #
#  エントリーポイント
# ------------------------------------------------------------------ #

def main() -> None:
    print("=" * 48)
    print("  Imaging Edge API 疎通確認")
    print("=" * 48)

    try:
        client = ApiClient()
    except FileNotFoundError as exc:
        print(f"[ERROR] {exc}")
        sys.exit(1)

    if not client.auth.get("access_token") and not client.auth.get("refresh_token"):
        print("[ERROR] auth_info.json にトークンが設定されていません。")
        print("  docs/auth_spec.md を参照してトークンを取得・記入してください。")
        sys.exit(1)

    try:
        check_user_me(client)
    except Exception as exc:
        print(f"\n認証に失敗したため確認を中断します: {exc}")
        sys.exit(1)

    try:
        check_containers(client)
    except Exception as exc:
        print(f"  → NG  {exc}")

    try:
        check_folders(client)
    except Exception as exc:
        print(f"  → NG  {exc}")

    print("\n" + "=" * 48)
    print("  完了")
    print("=" * 48)


if __name__ == "__main__":
    main()
