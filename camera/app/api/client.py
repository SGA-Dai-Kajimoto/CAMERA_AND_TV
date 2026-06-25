"""
Imaging Edge API クライアント。

すべての API 呼び出しをメソッドとして提供する。
認証エラー (401) は自動でトークンをリフレッシュして再試行する。
エラー時は requests.HTTPError または ValueError を送出する（print/sys.exit はしない）。
"""

import datetime
import tempfile
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

    # 拡張子 → MIME タイプ。バイナリ PUT の Content-Type ヘッダに使用する。
    #   動作実績のある Android 実装と完全一致させる（jpg/jpeg/png/arw のみマップ、
    #   HEIF/HEIC/TIFF/RAW を含むそれ以外は application/octet-stream）。
    _CONTENT_TYPES = {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".arw": "image/x-sony-arw",
    }

    @classmethod
    def _guess_content_type(cls, file_path: Path) -> str:
        """拡張子から MIME タイプを推測する。不明な場合は octet-stream。"""
        return cls._CONTENT_TYPES.get(file_path.suffix.lower(), "application/octet-stream")

    # HEIF/HEIC はサーバー側のサムネイル生成が失敗し 601006 になるため、
    # アップロード前にクライアント側で JPEG に変換する。
    _HEIF_SUFFIXES = {".heif", ".heic"}

    @classmethod
    def _convert_heif_to_jpeg(cls, file_path: Path) -> Path:
        """
        HEIF/HEIC を一時 JPEG ファイルに変換し、そのパスを返す。
        撮影日時の自動抽出のため Exif を可能な範囲で引き継ぐ。
        呼び出し側は使用後に返り値のファイルを削除すること。
        """
        try:
            import pillow_heif
            from PIL import Image
        except ImportError as exc:  # noqa: BLE001
            raise RuntimeError(
                "HEIF の変換には pillow と pillow-heif が必要です。"
                "`pip install pillow pillow-heif` を実行してください。"
            ) from exc

        # 一部の Sony HEIF は ispe(画像サイズ)と HEVC デコード結果のサイズが
        # 一致せず、新しい libheif の厳格チェックで「Invalid image size」エラーに
        # なる。pillow-heif==0.22.0(libheif 1.19.7)は許容するため、そのバージョンに
        # 固定している(requirements.txt 参照)。
        pillow_heif.register_heif_opener()

        with Image.open(file_path) as img:
            exif = img.info.get("exif")
            rgb = img.convert("RGB")
            tmp = tempfile.NamedTemporaryFile(
                suffix=".jpg", prefix=f"{file_path.stem}_", delete=False
            )
            tmp_path = Path(tmp.name)
            tmp.close()
            save_kwargs = {"quality": 95}
            if exif:
                save_kwargs["exif"] = exif
            rgb.save(tmp_path, "JPEG", **save_kwargs)
        return tmp_path

    def upload_image(self, folder_id: str, file_path: Path) -> dict:
        """
        3ステップのアップロードフロー:
          [1] POST /api/v1/folders/{folder_id}/upload  → upload_id / upload_url 取得
          [2] PUT <upload_url> にバイナリ送信
          [3] POST /api/v1/folders/{folder_id}/contents でフォルダへ登録

        HEIF/HEIC はサーバー側のサムネイル生成が失敗（601006）するため、
        アップロード前に JPEG へ変換し、JPEG として登録する。
        """
        is_heif = file_path.suffix.lower() in self._HEIF_SUFFIXES
        upload_path = self._convert_heif_to_jpeg(file_path) if is_heif else file_path
        upload_name = file_path.with_suffix(".jpg").name if is_heif else file_path.name
        try:
            file_size = upload_path.stat().st_size
            content_type = self._guess_content_type(upload_path)

            # [1] アップロードセッション開始
            resp = self._request(
                "POST",
                f"/api/v1/folders/{folder_id}/upload",
                json={"name": upload_name, "target": "content/original/image"},
            )
            resp.raise_for_status()
            session = resp.json()
            upload_id = session.get("upload_id")
            upload_url = session.get("upload_url")

            if not upload_url:
                raise ValueError(f"upload_url が取得できませんでした。レスポンス: {session}")

            # [2] ファイルをアップロード。
            #   サーバーはアップロードされたオブジェクトの Content-Type でファイル種別を判定するため、
            #   PUT に Content-Type ヘッダを必ず付ける（未指定だと 601006: upload process failed になる）。
            with open(upload_path, "rb") as f:
                put_resp = requests.put(
                    upload_url,
                    data=f,
                    headers={"Content-Type": content_type},
                    timeout=120,
                )
                put_resp.raise_for_status()

            # [3] フォルダへコンテンツ登録
            #   動作実績のある Android 実装と完全一致させる（wait_time=10s, 425 で最大3回リトライ）。
            #   ファイル種別は name の拡張子と PUT の Content-Type からサーバーが判定するため、
            #   content_type はここでは送らない。
            body = {
                "upload_id": upload_id,
                "kind": "original",
                "name": upload_name,
                "bytes": file_size,
                "utc_offset": self._utc_offset(),
            }

            reg_resp = None
            for attempt in range(4):
                reg_resp = self._request(
                    "POST",
                    f"/api/v1/folders/{folder_id}/contents",
                    params={"wait_time": "10s"},
                    json=body,
                )
                if reg_resp.status_code != 425:
                    break
                time.sleep(3)

            if reg_resp.status_code >= 400:
                self._raise_with_body(reg_resp, "コンテンツ登録に失敗しました")
            return reg_resp.json()
        finally:
            # 変換で作成した一時 JPEG を削除する。
            if is_heif and upload_path != file_path:
                upload_path.unlink(missing_ok=True)

    @staticmethod
    def _raise_with_body(resp: requests.Response, context: str) -> None:
        """HTTP エラーをサーバーのレスポンス本文付きで送出する。"""
        try:
            detail = resp.text
        except Exception:  # noqa: BLE001
            detail = ""
        raise requests.HTTPError(
            f"{context}: {resp.status_code} {resp.reason}\nURL: {resp.url}\n{detail}".strip(),
            response=resp,
        )

    def upload_images(self, folder_id: str, file_paths, progress=None) -> dict:
        """
        複数ファイルを順次アップロードする。

        API にバッチアップロードは無いため、各ファイルを upload_image で1枚ずつ送信する。
        progress(index, total, name) が指定された場合、各ファイル送信前に呼び出す。
        戻り値: {"succeeded": [...], "failed": [(name, error_msg), ...]}
        """
        paths = [Path(p) for p in file_paths]
        total = len(paths)
        succeeded = []
        failed = []
        for index, path in enumerate(paths, start=1):
            if progress is not None:
                progress(index, total, path.name)
            try:
                succeeded.append(self.upload_image(folder_id, path))
            except Exception as exc:  # noqa: BLE001
                failed.append((path.name, str(exc)))
        return {"succeeded": succeeded, "failed": failed}
