"""
コンテンツのリソース種別と共有機能を実測する。

確かめたいこと:
  1. RAW(.ARW) をアップロードしたとき、クラウド側で現像済みの JPEG
     （proxy / thumbnail_*）が生成されるか
  2. 期限つきでない公開リンク（SNS 用）を作る API があるか

認証は Camera_py/api/auth_info.json を使う。期限切れなら refresh_token で自動更新する。

使い方:
  python server/probe_resources.py login        # デバイスフローでログインしてトークンを保存
  python server/probe_resources.py kinds        # 各コンテンツで取れる kind を一覧化
  python server/probe_resources.py share        # 共有系エンドポイントの有無を調べる

login は server/device_flow.py が起動していることが前提。
"""

from __future__ import annotations

import base64
import hashlib
import json
import secrets
import sys
import time
from pathlib import Path

import requests

AUTH_FILE = Path(__file__).parent.parent / "Camera_py" / "api" / "auth_info.json"
PAIRING_SERVER = "http://127.0.0.1:8000"

# 画像で取り得る kind。存在しないものは 404 になる想定。
IMAGE_KINDS = ["original", "proxy", "thumbnail_1920", "thumbnail_1024", "thumbnail_400"]

RAW_EXTENSIONS = {".arw", ".arq", ".dng", ".raw"}


# ---------------------------------------------------------------- #
#  認証
# ---------------------------------------------------------------- #

def load_auth() -> dict:
    if not AUTH_FILE.exists():
        print(f"{AUTH_FILE} がありません。docs/auth_spec.md を参照してトークンを用意してください。")
        sys.exit(1)
    return json.loads(AUTH_FILE.read_text(encoding="utf-8"))


def refresh_token(auth: dict) -> dict:
    """refresh_token で access_token を更新し、ファイルへ書き戻す。"""
    base = str(auth["base_url"]).rstrip("/")
    res = requests.post(
        f"{base}/api/v1/oauth2/token",
        json={
            "app_type": auth.get("app_type", "_trial_"),
            "refresh_token": auth["refresh_token"],
            "refresh_token_ttl": int(auth.get("refresh_token_ttl") or 0),
        },
        timeout=15,
    )
    if res.status_code != 200:
        print(f"トークン更新に失敗しました: {res.status_code} {res.text[:200]}")
        print()
        print("refresh_token も失効しています。次のどちらかで取り直してください。")
        print("  ・ python server/device_flow.py を起動して")
        print("    python server/test_device_flow.py e2e でログインし、得られたトークンを")
        print(f"    {AUTH_FILE} に記入する")
        print("  ・ docs/auth_spec.md の手順で手動取得する")
        sys.exit(1)

    data = res.json()
    auth.update(
        access_token=data["access_token"],
        access_token_ttl=data.get("access_token_ttl", 0),
        refresh_token=data.get("refresh_token", auth["refresh_token"]),
        refresh_token_ttl=data.get("refresh_token_ttl", auth.get("refresh_token_ttl", 0)),
    )
    AUTH_FILE.write_text(json.dumps(auth, ensure_ascii=False, indent=2), encoding="utf-8")
    print("access_token を更新しました。")
    return auth


class Api:
    def __init__(self, auth: dict) -> None:
        self.auth = auth
        self.base = str(auth["base_url"]).rstrip("/")
        self.session = requests.Session()
        self._apply_token()

    def _apply_token(self) -> None:
        self.session.headers["Authorization"] = f"Bearer {self.auth['access_token']}"

    def get(self, path: str, **kwargs) -> requests.Response:
        """TTL はあてにならないので、401 を受けたら更新して 1 度だけ再試行する。"""
        res = self.session.get(f"{self.base}{path}", timeout=20, **kwargs)
        if res.status_code == 401:
            self.auth = refresh_token(self.auth)
            self._apply_token()
            res = self.session.get(f"{self.base}{path}", timeout=20, **kwargs)
        return res


# ---------------------------------------------------------------- #
#  1. kind の実在確認
# ---------------------------------------------------------------- #

def probe_kinds(api: Api) -> None:
    folders = api.get("/api/v1/folders").json().get("folders", [])
    if not folders:
        print("フォルダがありません。先に写真をアップロードしてください。")
        return

    print(f"フォルダ {len(folders)} 件")
    for folder in folders:
        fid = folder["folder_id"]
        contents = api.get(f"/api/v1/folders/{fid}/contents").json().get("contents", [])
        print(f"\n[{folder.get('display_name', fid)}]  {len(contents)} 件")
        if not contents:
            continue

        print(f"  {'ファイル名':<28} " + "  ".join(f"{k:<14}" for k in IMAGE_KINDS))
        for content in contents:
            name = content.get("filename") or content.get("display_name") or content["content_id"]
            cells = []
            for kind in IMAGE_KINDS:
                cells.append(f"{describe(api, fid, content['content_id'], kind):<14}")
            marker = " ← RAW" if Path(name).suffix.lower() in RAW_EXTENSIONS else ""
            print(f"  {name:<28} " + "  ".join(cells) + marker)

    print()
    print("=" * 78)
    print("読み方:")
    print("  ・RAW の行で proxy / thumbnail_* にサイズが出る → クラウド側で現像済み JPEG が作られている")
    print("    → TV はその kind を取るだけでよい。端末での RAW 現像は不要")
    print("  ・RAW の行が original 以外すべて 404 → クラウドは RAW を現像していない")
    print("    → TV 側で現像するか、アップロード時に JPEG も一緒に上げる必要がある")


def describe(api: Api, folder_id: str, content_id: str, kind: str) -> str:
    """kind ごとの download_url を引き、実体のサイズまで確かめる。"""
    res = api.get(f"/api/v1/folders/{folder_id}/contents/{content_id}/resources/{kind}/download_url")
    if res.status_code != 200:
        return f"-({res.status_code})"
    url = res.json().get("download_url", "")
    if not url:
        return "-(no url)"
    try:
        head = requests.head(url, timeout=20, allow_redirects=True)
        size = int(head.headers.get("Content-Length") or 0)
        return f"{size // 1024}KB" if size else "OK"
    except requests.RequestException:
        return "OK(size?)"


# ---------------------------------------------------------------- #
#  2. 共有機能の有無
# ---------------------------------------------------------------- #

# API 概要に「共有ユーザー管理」の記載があるため、その周辺だけを確認する。
SHARE_CANDIDATES = [
    "/api/v1/folders/{folder_id}/shared_users",
    "/api/v1/folders/{folder_id}/share",
    "/api/v1/folders/{folder_id}/shares",
    "/api/v1/folders/{folder_id}/public_url",
    "/api/v1/cms/contents:share",
    "/api/v1/groups",
]


def probe_share(api: Api) -> None:
    folders = api.get("/api/v1/folders").json().get("folders", [])
    fid = folders[0]["folder_id"] if folders else "dummy"

    print("共有系エンドポイントの反応")
    print("（404 = 存在しない / 401,403 = 権限 / 200,405 = 何らかの形で存在する）\n")
    for template in SHARE_CANDIDATES:
        path = template.format(folder_id=fid)
        try:
            res = api.get(path)
        except requests.RequestException as e:
            print(f"  ERR  {path}  {e}")
            continue
        print(f"  {res.status_code:<4} {path}")
        if res.status_code == 200:
            print(f"       {res.text[:200]}")

    print()
    print("=" * 78)
    print("読み方:")
    print("  ・200/405 が返るものがあれば、公式の共有機能が存在する可能性が高い")
    print("  ・すべて 404 なら、恒久的な公開リンクを作る API は無い")
    print("    → download_url は 600 秒で切れるため SNS には貼れない")
    print("    → スマホへ渡して、スマホの SNS アプリから投稿する導線が現実的")


def login() -> None:
    """デバイスフローでログインし、取得したトークンを auth_info.json へ保存する。"""
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode().rstrip("=")
    challenge = (
        base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    )

    http = requests.Session()
    try:
        res = http.post(
            f"{PAIRING_SERVER}/device/authorize",
            json={"code_challenge": challenge, "device_id": "probe-resources"},
            timeout=10,
        )
        res.raise_for_status()
    except requests.RequestException as e:
        print(f"{PAIRING_SERVER} に繋がりません: {e}")
        print("先に python server/device_flow.py を起動してください。")
        sys.exit(1)

    data = res.json()
    print("ブラウザで次の URL を開いてログインしてください")
    print(f"  {data['verification_uri_complete']}")
    print(f"ポーリング中（最大 {data['expires_in']} 秒）…")

    deadline = time.time() + data["expires_in"]
    while time.time() < deadline:
        time.sleep(data["interval"])
        res = http.post(
            f"{PAIRING_SERVER}/device/code",
            json={"device_code": data["device_code"]},
            timeout=15,
        )
        if res.status_code == 200:
            granted = res.json()
            # 中継サーバーは auth_code までしか渡さない。交換はこちらで行う
            exchanged = http.post(
                f"{granted['base_url']}/api/v1/oauth2/token",
                json={
                    "app_type": granted["app_type"],
                    "auth_code": granted["auth_code"],
                    "code_verifier": verifier,
                },
                timeout=15,
            )
            if exchanged.status_code != 200:
                print(f"\nトークン交換に失敗: {exchanged.status_code} {exchanged.text[:200]}")
                sys.exit(1)
            tokens = exchanged.json()
            AUTH_FILE.parent.mkdir(parents=True, exist_ok=True)
            existing = json.loads(AUTH_FILE.read_text(encoding="utf-8")) if AUTH_FILE.exists() else {}
            existing.update(
                base_url=granted["base_url"],
                app_type=granted["app_type"],
                access_token=tokens["access_token"],
                access_token_ttl=tokens.get("access_token_ttl", 0),
                refresh_token=tokens["refresh_token"],
                refresh_token_ttl=tokens.get("refresh_token_ttl", 0),
            )
            AUTH_FILE.write_text(json.dumps(existing, ensure_ascii=False, indent=2), encoding="utf-8")
            print(f"\n{AUTH_FILE} に保存しました。")
            return
        error = res.json().get("error", "")
        if error not in ("authorization_pending", "slow_down"):
            print(f"\n失敗: {res.status_code} {res.text[:200]}")
            sys.exit(1)

    print("\nタイムアウトしました。")
    sys.exit(1)


def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "login":
        login()
        return
    if command not in ("kinds", "share"):
        print(__doc__)
        sys.exit(1)

    api = Api(load_auth())
    if command == "kinds":
        probe_kinds(api)
    else:
        probe_share(api)


if __name__ == "__main__":
    main()
