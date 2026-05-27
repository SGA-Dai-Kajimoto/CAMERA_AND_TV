"""
Imaging Edge API クライアント。

すべての API 呼び出しをメソッドとして提供する。
認証エラー (401) は自動でトークンをリフレッシュして再試行する。
エラー時は requests.HTTPError または ValueError を送出する（print/sys.exit はしない）。
"""

import datetime
import time
from pathlib import Path

import requests

from api.auth import load_auth, save_auth


class ApiClient:
    """Imaging Edge API の全操作を担うクライアント。"""

    def __init__(self):
        self.auth = load_auth()
        if not self.auth.get("access_token") and self.auth.get("refresh_token"):
            self.refresh_token()

    # ---------------------------------------------------------------- #
    #  内部ヘルパー
    # ---------------------------------------------------------------- #

    def _base_url(self) -> str:
        return self.auth.get("base_url", "https://ws.dev.imagingedge.sony.net")

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self.auth['access_token']}"}

    def _request(self, method: str, path: str, **kwargs) -> requests.Response:
        """HTTP リクエスト共通処理。401 時に自動リフレッシュして再試行する。"""
        url = f"{self._base_url()}{path}"
        timeout = kwargs.pop("timeout", 30)
        resp = requests.request(method, url, headers=self._headers(), timeout=timeout, **kwargs)
        if resp.status_code == 401:
            self.refresh_token()
            resp = requests.request(method, url, headers=self._headers(), timeout=timeout, **kwargs)
        return resp

    # ---------------------------------------------------------------- #
    #  認証
    # ---------------------------------------------------------------- #

    def refresh_token(self) -> None:
        """refresh_token を使って access_token を更新し、ファイルに保存する。"""
        resp = requests.post(
            f"{self._base_url()}/api/v1/oauth2/token",
            json={
                "app_type": self.auth.get("app_type", "_trial_"),
                "refresh_token": self.auth["refresh_token"],
                "refresh_token_ttl": self.auth.get("refresh_token_ttl", 0),
            },
            timeout=10,
        )
        resp.raise_for_status()
        data = resp.json()
        self.auth["access_token"] = data["access_token"]
        self.auth["access_token_ttl"] = data.get("access_token_ttl", 0)
        if "refresh_token" in data:
            self.auth["refresh_token"] = data["refresh_token"]
            self.auth["refresh_token_ttl"] = data.get("refresh_token_ttl", 0)
        save_auth(self.auth)

    # ---------------------------------------------------------------- #
    #  ユーザー情報
    # ---------------------------------------------------------------- #

    def get_user_me(self) -> dict:
        """GET /api/v1/user/me — ユーザー情報取得。user_id / account を auth に保存する。"""
        resp = self._request("GET", "/api/v1/user/me")
        resp.raise_for_status()
        data = resp.json()
        self.auth["user_id"] = data.get("user_id", "")
        if not self.auth.get("account"):
            self.auth["account"] = data.get("user_id", "")
        save_auth(self.auth)
        return data

    # ---------------------------------------------------------------- #
    #  コンテナ（ファイルストレージ）
    # ---------------------------------------------------------------- #

    def list_containers(self) -> list:
        """GET /api/file/v1/{account} — コンテナ一覧取得。"""
        account = self.auth.get("account", "")
        resp = self._request("GET", f"/api/file/v1/{account}")
        resp.raise_for_status()
        return resp.json()

    # ---------------------------------------------------------------- #
    #  フォルダ操作
    # ---------------------------------------------------------------- #

    def list_folders(self) -> list:
        """GET /api/v1/folders — フォルダ一覧取得。削除リクエスト済み（removed_date あり）は除外。"""
        resp = self._request("GET", "/api/v1/folders")
        resp.raise_for_status()
        folders = resp.json().get("folders", [])
        return [f for f in folders if not f.get("removed_date")]

    def _utc_offset(self) -> str:
        """ローカルタイムゾーンの UTC オフセットを '+09:00' 形式で返す。"""
        offset = datetime.datetime.now(datetime.timezone.utc).astimezone().strftime("%z")
        return f"{offset[:3]}:{offset[3:]}"

    def create_folder(self, display_name: str) -> dict:
        """POST /api/v1/folders — フォルダ作成。"""
        resp = self._request(
            "POST",
            "/api/v1/folders",
            json={
                "display_name": display_name,
                "app_id": self.auth.get("app_type", "_trial_"),
                "utc_offset": self._utc_offset(),
            },
        )
        resp.raise_for_status()
        return resp.json()

    def rename_folder(self, folder_id: str, display_name: str) -> dict:
        """PUT /api/v1/folders/{folder_id} — フォルダ名変更。"""
        resp = self._request(
            "PUT", f"/api/v1/folders/{folder_id}", json={"display_name": display_name}
        )
        resp.raise_for_status()
        return resp.json()

    def delete_folder(self, folder_id: str) -> None:
        """DELETE /api/v1/folders/{folder_id} — フォルダ削除。"""
        resp = self._request("DELETE", f"/api/v1/folders/{folder_id}")
        resp.raise_for_status()

    # ---------------------------------------------------------------- #
    #  コンテンツ操作
    # ---------------------------------------------------------------- #

    def list_contents(self, folder_id: str) -> list:
        """GET /api/v1/folders/{folder_id}/contents — コンテンツ一覧取得。"""
        resp = self._request("GET", f"/api/v1/folders/{folder_id}/contents")
        resp.raise_for_status()
        return resp.json().get("contents", [])

    def get_content_binary(self, folder_id: str, content_id: str, kind: str = "original") -> bytes:
        """GET /api/v1/folders/{folder_id}/contents/{content_id}/resources/{kind}/binary — コンテンツのバイナリ取得。"""
        resp = self._request(
            "GET",
            f"/api/v1/folders/{folder_id}/contents/{content_id}/resources/{kind}/binary",
            timeout=60,
        )
        resp.raise_for_status()
        return resp.content

    def delete_content(self, folder_id: str, content_id: str) -> None:
        """POST /api/v1/folders/{folder_id}/contents:remove — コンテンツ削除。"""
        resp = self._request(
            "POST",
            f"/api/v1/folders/{folder_id}/contents:remove",
            json={"content_ids": [content_id]},
        )
        resp.raise_for_status()

    # ---------------------------------------------------------------- #
    #  画像アップロード
    # ---------------------------------------------------------------- #

    def upload_image(self, folder_id: str, file_path: Path) -> dict:
        """
        3ステップのアップロードフロー:
          [1] POST /api/v1/folders/{folder_id}/upload  → upload_id / upload_url 取得
          [2] PUT <upload_url> にバイナリ送信
          [3] POST /api/v1/folders/{folder_id}/contents でフォルダへ登録
        """
        file_size = file_path.stat().st_size

        # [1] アップロードセッション開始
        resp = self._request(
            "POST",
            f"/api/v1/folders/{folder_id}/upload",
            json={"name": file_path.name, "target": "content/original/image"},
        )
        resp.raise_for_status()
        session = resp.json()
        upload_id = session.get("upload_id")
        upload_url = session.get("upload_url")

        if not upload_url:
            raise ValueError(f"upload_url が取得できませんでした。レスポンス: {session}")

        # [2] ファイルをアップロード
        with open(file_path, "rb") as f:
            put_resp = requests.put(upload_url, data=f, timeout=120)
            put_resp.raise_for_status()

        # [3] フォルダへコンテンツ登録（wait_time で処理待ち、425 時はリトライ）
        for attempt in range(4):
            reg_resp = self._request(
                "POST",
                f"/api/v1/folders/{folder_id}/contents",
                params={"wait_time": "10s"},
                json={
                    "upload_id": upload_id,
                    "kind": "original",
                    "name": file_path.name,
                    "bytes": file_size,
                    "utc_offset": self._utc_offset(),
                },
            )
            if reg_resp.status_code != 425:
                break
            if attempt < 3:
                time.sleep(3)
        reg_resp.raise_for_status()
        return reg_resp.json()
