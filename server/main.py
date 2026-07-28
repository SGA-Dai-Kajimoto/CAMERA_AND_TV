"""
QR コード認証用ローカルサーバー（FastAPI）。

役割:
  Sony Imaging Edge / Creators Cloud は「認可コードフロー（ブラウザリダイレクト型）」
  のみ提供し、デバイスコード方式（TVに数字/QRを出すペアリング）は無い。
  そのため本サーバーが OAuth クライアント兼ペアリング仲介役を担う。

フロー:
  1. TV  : POST /pairing/start
             → session_id / device_secret / pairing_url を発行
  2. TV  : pairing_url を QR 化して画面表示。GET /pairing/{session_id} をポーリング
  3. 携帯: QR を読み取り GET /p/{session_id} を開く
             → Sony の認可URL(redirect_url=このサーバー/callback)へ 302 リダイレクト
  4. 携帯: Creators Cloud にログイン
  5. Sony: GET /callback?code=...&state=... へリダイレクト
             → サーバーが code をトークンに交換し、セッションに保存
  6. TV  : ポーリングで completed を受信し、トークンを取得（1回限り）→ 保存

セキュリティ:
  - device_secret : トークン取得を TV だけに限定（QR を持つ携帯からは奪取不可）
  - state         : OAuth コールバックとペアリングセッションを厳密に紐付け（CSRF対策）
  - セッションTTL : 期限切れセッションは失効
  - 単回受渡し    : トークンは1度取得すると消費（再取得不可）
"""

import base64
import hashlib
import html
import secrets
import time
from dataclasses import dataclass, field
from threading import Lock
from urllib.parse import urlencode

import requests
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse

import config

app = FastAPI(title="Camera TV QR Auth Server", version="1.0.0")


# ---------------------------------------------------------------- #
#  セッションストア（インメモリ）
# ---------------------------------------------------------------- #

@dataclass
class PairingSession:
    session_id: str
    device_secret: str
    state: str
    device_id: str
    code_verifier: str
    created_at: float
    status: str = "pending"  # pending | completed
    tokens: dict = field(default_factory=dict)
    consumed: bool = False


class SessionStore:
    """スレッドセーフなインメモリセッションストア。"""

    def __init__(self) -> None:
        self._by_id: dict[str, PairingSession] = {}
        self._state_to_id: dict[str, str] = {}
        self._lock = Lock()

    def create(self, device_id: str) -> PairingSession:
        session = PairingSession(
            session_id=secrets.token_urlsafe(16),
            device_secret=secrets.token_urlsafe(24),
            state=secrets.token_urlsafe(24),
            device_id=device_id or "camera-tv",
            # PKCE (RFC 7636) code_verifier。43〜128文字の unreserved 文字。
            code_verifier=secrets.token_urlsafe(64),
            created_at=time.time(),
        )
        with self._lock:
            self._prune_locked()
            self._by_id[session.session_id] = session
            self._state_to_id[session.state] = session.session_id
        return session

    def get(self, session_id: str) -> PairingSession | None:
        with self._lock:
            self._prune_locked()
            return self._by_id.get(session_id)

    def get_by_state(self, state: str) -> PairingSession | None:
        with self._lock:
            self._prune_locked()
            sid = self._state_to_id.get(state)
            return self._by_id.get(sid) if sid else None

    def delete(self, session_id: str) -> None:
        with self._lock:
            session = self._by_id.pop(session_id, None)
            if session:
                self._state_to_id.pop(session.state, None)

    def _prune_locked(self) -> None:
        now = time.time()
        expired = [
            sid for sid, s in self._by_id.items()
            if now - s.created_at > config.SESSION_TTL_SEC
        ]
        for sid in expired:
            session = self._by_id.pop(sid, None)
            if session:
                self._state_to_id.pop(session.state, None)


store = SessionStore()


def _is_expired(session: PairingSession) -> bool:
    return time.time() - session.created_at > config.SESSION_TTL_SEC


# ---------------------------------------------------------------- #
#  1. TV: ペアリング開始
# ---------------------------------------------------------------- #

@app.post("/pairing/start")
async def pairing_start(request: Request) -> JSONResponse:
    """TV が呼ぶ。セッションを作成し、QR化する pairing_url を返す。"""
    device_id = ""
    try:
        body = await request.json()
        if isinstance(body, dict):
            device_id = str(body.get("device_id", "") or "")
    except Exception:  # noqa: BLE001  ボディ無しでも許容
        device_id = ""

    session = store.create(device_id)
    return JSONResponse(
        {
            "session_id": session.session_id,
            "device_secret": session.device_secret,
            "pairing_url": f"{config.PUBLIC_BASE_URL}/p/{session.session_id}",
            "expires_in": config.SESSION_TTL_SEC,
        }
    )


# ---------------------------------------------------------------- #
#  3. 携帯: QR から開く → Sony 認可URL へリダイレクト
# ---------------------------------------------------------------- #

@app.get("/p/{session_id}")
async def pairing_redirect(session_id: str) -> RedirectResponse:
    """携帯が QR から開く。Sony の OAuth 認可URLへ 302 リダイレクトする。"""
    session = store.get(session_id)
    if session is None or _is_expired(session):
        return _error_page("このQRコードは無効または期限切れです。TVで再度QRを表示してください。", status=410)

    # PKCE: code_challenge = BASE64URL( SHA256( code_verifier ) )（パディング無し）
    challenge = (
        base64.urlsafe_b64encode(hashlib.sha256(session.code_verifier.encode()).digest())
        .rstrip(b"=")
        .decode()
    )
    # device_type は Enum(pc/mobile/mobile_sso)のみで tv は無い。
    # スマホのブラウザでログインするため、device_type は省略し Web ブラウザ扱いにする
    # （省略すると app_version / platform 等の必須項目も不要になる）。
    params = {
        "lang": "ja",
        "country": "JP",
        "redirect_url": f"{config.PUBLIC_BASE_URL}/callback",
        "app_type": config.APP_TYPE,
        "device_id": session.device_id,
        "code_challenge": challenge,
        "state": session.state,
    }
    auth_url = f"{config.SONY_BASE_URL}/api/v1/oauth2/auth?{urlencode(params)}"
    return RedirectResponse(auth_url, status_code=302)


# ---------------------------------------------------------------- #
#  5. Sony: 認可後のコールバック → code をトークンに交換
# ---------------------------------------------------------------- #

@app.get("/callback")
async def oauth_callback(
    code: str | None = None,
    state: str | None = None,
    error: str | None = None,
) -> HTMLResponse:
    """Sony からのリダイレクトを受け、auth_code をトークンに交換してセッションに保存する。"""
    if error:
        return _error_page(f"認証がキャンセルまたは失敗しました: {html.escape(error)}")
    if not code or not state:
        return _error_page("認証パラメータが不足しています。")

    session = store.get_by_state(state)
    if session is None or _is_expired(session):
        return _error_page("セッションが無効または期限切れです。TVで再度QRを表示してください。", status=410)

    try:
        resp = requests.post(
            f"{config.SONY_BASE_URL}/api/v1/oauth2/token",
            json={
                "app_type": config.APP_TYPE,
                "auth_code": code,
                "code_verifier": session.code_verifier,
            },
            timeout=15,
        )
        resp.raise_for_status()
        data = resp.json()
    except requests.RequestException as exc:
        return _error_page(f"トークン交換に失敗しました: {html.escape(str(exc))}")

    session.tokens = {
        "base_url": config.SONY_BASE_URL,
        "app_type": config.APP_TYPE,
        "access_token": data.get("access_token", ""),
        "access_token_ttl": int(data.get("access_token_ttl", 0) or 0),
        "refresh_token": data.get("refresh_token", ""),
        "refresh_token_ttl": int(data.get("refresh_token_ttl", 0) or 0),
    }
    session.status = "completed"

    return _success_page()


# ---------------------------------------------------------------- #
#  6. TV: ポーリングでトークン取得（device_secret 必須・単回消費）
# ---------------------------------------------------------------- #

@app.get("/pairing/{session_id}")
async def pairing_poll(
    session_id: str,
    x_device_secret: str = Header(default=""),
) -> JSONResponse:
    """TV がポーリングする。completed なら1度だけトークンを返し、セッションを破棄する。"""
    session = store.get(session_id)
    if session is None or _is_expired(session):
        raise HTTPException(status_code=410, detail="session expired")

    # device_secret を定数時間比較で検証（QR を持つ第三者からのトークン奪取を防ぐ）
    if not secrets.compare_digest(x_device_secret, session.device_secret):
        raise HTTPException(status_code=403, detail="invalid device secret")

    if session.status != "completed":
        return JSONResponse({"status": "pending"})

    tokens = session.tokens
    # 単回消費: 取得後はセッションを破棄する
    store.delete(session_id)
    return JSONResponse({"status": "completed", "tokens": tokens})


# ---------------------------------------------------------------- #
#  ヘルスチェック
# ---------------------------------------------------------------- #

@app.get("/health")
async def health() -> JSONResponse:
    return JSONResponse({"status": "ok"})


# ---------------------------------------------------------------- #
#  HTML レスポンス（携帯ブラウザ向け）
# ---------------------------------------------------------------- #

def _success_page() -> HTMLResponse:
    return HTMLResponse(
        """
        <!doctype html><html lang="ja"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>認証完了</title>
        <style>
          body{font-family:sans-serif;text-align:center;padding:48px 24px;color:#222}
          .card{max-width:420px;margin:0 auto}
          .ok{font-size:56px;color:#2e7d32}
          h1{font-size:22px} p{color:#555;line-height:1.7}
        </style></head>
        <body><div class="card">
          <div class="ok">&#10004;</div>
          <h1>認証が完了しました</h1>
          <p>TV に戻ってください。<br>自動的にログインが完了します。<br>この画面は閉じて構いません。</p>
        </div></body></html>
        """,
        status_code=200,
    )


def _error_page(message: str, status: int = 400) -> HTMLResponse:
    return HTMLResponse(
        f"""
        <!doctype html><html lang="ja"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>エラー</title>
        <style>
          body{{font-family:sans-serif;text-align:center;padding:48px 24px;color:#222}}
          .card{{max-width:420px;margin:0 auto}}
          .ng{{font-size:56px;color:#c62828}}
          h1{{font-size:22px}} p{{color:#555;line-height:1.7}}
        </style></head>
        <body><div class="card">
          <div class="ng">&#10006;</div>
          <h1>認証できませんでした</h1>
          <p>{html.escape(message)}</p>
        </div></body></html>
        """,
        status_code=status,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=config.HOST, port=config.PORT)
