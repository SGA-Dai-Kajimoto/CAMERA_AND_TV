"""
TV ペアリングサーバー（PKCE ベースの擬似デバイスフロー）。

背景:
  AccountPF は RFC 8628 のデバイスフローを提供していないが、実測（server/probe_oauth.py）で
  次の 2 点が確認できたため、認可サーバー本体に手を入れずにデバイスフロー相当を実現できる。

    1. redirect_url は localhost / 127.0.0.1 なら任意ポート・任意パスが許可される
    2. PKCE が強制されている（code_verifier 無しのトークン交換は 401 で拒否される）

  2 のおかげで、auth_code を中継するだけでトークン交換は TV に任せられる。
  code_verifier を持つのは TV だけなので、このサーバーが侵害されても
  トークンは得られない。

フロー:
  1. TV  : POST /device/authorize   {code_challenge}
             → device_code / user_code / verification_uri を発行
  2. TV  : user_code と QR を画面表示し、POST /device/code をポーリング
  3. PC  : QR の URL（GET /device?user_code=...）をブラウザで開く
             → AccountPF の認可画面へ 302（TV が作った code_challenge をそのまま渡す）
  4. PC  : ログイン・同意
             → AccountPF が GET /callback/{state}?auth_code=... へ戻す
             → サーバーは auth_code を保持するだけ
  5. TV  : POST /device/code {device_code}
             → auth_code を受け取り、**TV 自身が** AccountPF でトークンに交換する

サーバーは code_verifier もトークンも一切見ない。auth_code だけを中継する。
PKCE が強制されているので、auth_code を盗まれても code_verifier が無ければ使えない。

state をパスに埋めているのは、AccountPF が任意の state をコールバックへ引き継ぐ保証が
無いため。localhost は任意パスが許可されるので、パスに入れる方が確実に紐付けられる。

起動:
  python server/device_flow.py
"""

from __future__ import annotations

import html
import secrets
import time
import webbrowser
from collections import defaultdict
from dataclasses import dataclass, field
from threading import Lock
from urllib.parse import urlencode

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from pydantic import BaseModel, Field

import config

app = FastAPI(title="Camera TV Device Flow Server", version="1.0.0")

AUTH_ENDPOINT = f"{config.DEFAULT_BASE_URL}/api/v1/oauth2/auth"

# user_code の文字集合。母音を外して意図しない単語を防ぎ、0/O・1/I の誤読も避ける。
USER_CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ"
USER_CODE_LENGTH = 8

# RFC 8628 に倣ったエラーコード
ERR_PENDING = "authorization_pending"
ERR_SLOW_DOWN = "slow_down"
ERR_EXPIRED = "expired_token"
ERR_DENIED = "access_denied"


# ---------------------------------------------------------------- #
#  セッション
# ---------------------------------------------------------------- #

@dataclass
class DeviceSession:
    device_code: str
    user_code: str
    state: str
    code_challenge: str
    device_id: str
    created_at: float
    auth_code: str = ""
    last_poll_at: float = 0.0

    @property
    def is_authorized(self) -> bool:
        return bool(self.auth_code)


class SessionStore:
    """インメモリのセッションストア。プロトタイプなので永続化はしない。"""

    def __init__(self) -> None:
        self._by_device_code: dict[str, DeviceSession] = {}
        self._by_user_code: dict[str, str] = {}
        self._by_state: dict[str, str] = {}
        self._lock = Lock()

    def create(self, code_challenge: str, device_id: str) -> DeviceSession:
        with self._lock:
            self._prune_locked()
            if len(self._by_device_code) >= config.MAX_ACTIVE_SESSIONS:
                raise HTTPException(status_code=503, detail="too many active sessions")
            user_code = self._allocate_user_code_locked()
            session = DeviceSession(
                device_code=secrets.token_urlsafe(32),
                user_code=user_code,
                state=secrets.token_urlsafe(24),
                code_challenge=code_challenge,
                device_id=device_id,
                created_at=time.time(),
            )
            self._by_device_code[session.device_code] = session
            self._by_user_code[session.user_code] = session.device_code
            self._by_state[session.state] = session.device_code
            return session

    def _allocate_user_code_locked(self) -> str:
        for _ in range(20):
            candidate = "".join(secrets.choice(USER_CODE_ALPHABET) for _ in range(USER_CODE_LENGTH))
            if candidate not in self._by_user_code:
                return candidate
        raise HTTPException(status_code=503, detail="could not allocate user_code")

    def by_device_code(self, device_code: str) -> DeviceSession | None:
        with self._lock:
            self._prune_locked()
            return self._by_device_code.get(device_code)

    def by_user_code(self, user_code: str) -> DeviceSession | None:
        with self._lock:
            self._prune_locked()
            key = self._by_user_code.get(user_code)
            return self._by_device_code.get(key) if key else None

    def by_state(self, state: str) -> DeviceSession | None:
        with self._lock:
            self._prune_locked()
            key = self._by_state.get(state)
            return self._by_device_code.get(key) if key else None

    def delete(self, device_code: str) -> None:
        with self._lock:
            self._delete_locked(device_code)

    def _delete_locked(self, device_code: str) -> None:
        session = self._by_device_code.pop(device_code, None)
        if session:
            self._by_user_code.pop(session.user_code, None)
            self._by_state.pop(session.state, None)

    def _prune_locked(self) -> None:
        now = time.time()
        expired = [
            code for code, s in self._by_device_code.items()
            if now - s.created_at > config.SESSION_TTL_SEC
        ]
        for code in expired:
            self._delete_locked(code)


store = SessionStore()


def _is_loopback(client: str) -> bool:
    """サーバーと同じ PC からのアクセスか。

    逆プロキシを前段に置くなら、遠隔地からの接続がループバックに見えるので
    この判定は見直すこと。現状は uvicorn が直接受けるのでそのまま使える。
    """
    return client in ("127.0.0.1", "::1", "localhost")


class LookupThrottle:
    """
    user_code の総当たりを止める。

    公開ホストに置く場合、当たった user_code のセッションを攻撃者が
    自分のアカウントで認証してしまえるため、失敗回数で頭打ちにする。

    ただしループバックは除外する。`/device` を叩くのはサーバーと同じ PC の
    ブラウザだけで（redirect_url が localhost 限定のため）、そこを止めても
    防げる相手がいない。逆に検証スクリプトや入力ミスで自分が閉め出される。
    """

    def __init__(self) -> None:
        self._failures: dict[str, list[float]] = defaultdict(list)
        self._lock = Lock()

    def is_blocked(self, client: str) -> bool:
        if _is_loopback(client):
            return False
        with self._lock:
            recent = self._recent_locked(client)
            return len(recent) >= config.MAX_LOOKUP_FAILURES

    def record_failure(self, client: str) -> None:
        if _is_loopback(client):
            return
        with self._lock:
            self._recent_locked(client).append(time.time())

    def clear(self, client: str) -> None:
        with self._lock:
            self._failures.pop(client, None)

    def _recent_locked(self, client: str) -> list[float]:
        cutoff = time.time() - config.LOOKUP_BLOCK_SEC
        kept = [t for t in self._failures[client] if t > cutoff]
        self._failures[client] = kept
        return kept


throttle = LookupThrottle()


def _client_of(request: Request) -> str:
    return request.client.host if request.client else "unknown"


def _is_expired(session: DeviceSession) -> bool:
    return time.time() - session.created_at > config.SESSION_TTL_SEC


def _error(code: str, status: int = 400, detail: str = "") -> JSONResponse:
    body: dict[str, str] = {"error": code}
    if detail:
        body["error_description"] = detail
    return JSONResponse(body, status_code=status)


# ---------------------------------------------------------------- #
#  1. TV: デバイス認可の開始
# ---------------------------------------------------------------- #

class AuthorizeRequest(BaseModel):
    code_challenge: str = Field(min_length=32, max_length=128)
    code_challenge_method: str = "S256"
    device_id: str = "camera-tv"


@app.post("/device/authorize")
def device_authorize(body: AuthorizeRequest) -> JSONResponse:
    """TV が code_challenge を登録し、画面表示用の user_code と QR 用 URL を受け取る。"""
    if body.code_challenge_method != "S256":
        return _error("invalid_request", detail="only S256 is supported")

    session = store.create(body.code_challenge, body.device_id)
    verification_uri = f"{config.PUBLIC_BASE_URL}/device"
    # QR にはこちらを載せる。ユーザーが番号を打たずに済む
    complete_uri = f"{verification_uri}?user_code={session.user_code}"

    # TV の画面を見て打ち直すのは手間なので、ここにそのまま開ける形で出す
    print("\n" + "=" * 70)
    print(f"TV がペアリングを開始しました（device_id={body.device_id}）")
    print(f"  コード : {session.user_code}")
    print(f"  URL   : {complete_uri}")
    print("  ↑ この URL をブラウザで開いてログインしてください（Ctrl+クリックで開けます）")
    print("=" * 70 + "\n", flush=True)

    if config.AUTO_OPEN_BROWSER:
        webbrowser.open(complete_uri)

    return JSONResponse(
        {
            "device_code": session.device_code,
            "user_code": session.user_code,
            "verification_uri": verification_uri,
            "verification_uri_complete": complete_uri,
            "expires_in": config.SESSION_TTL_SEC,
            "interval": config.POLL_INTERVAL_SEC,
        }
    )


# ---------------------------------------------------------------- #
#  2. PC: QR から開かれ、AccountPF の認可画面へ送る
# ---------------------------------------------------------------- #

@app.get("/device", response_model=None)
def device_verification(request: Request, user_code: str = "") -> RedirectResponse | HTMLResponse:
    """QR の遷移先。user_code からセッションを特定し AccountPF へリダイレクトする。"""
    client = _client_of(request)
    if throttle.is_blocked(client):
        return _message_page(
            "しばらく待ってからやり直してください",
            "入力の失敗が続いたため一時的に受け付けを止めています。",
            status=429,
        )

    # 大文字小文字とハイフンの揺れを吸収する
    code = user_code.strip().upper().replace("-", "").replace(" ", "")
    if not code:
        return _user_code_form()

    session = store.by_user_code(code)
    if session is None or _is_expired(session):
        throttle.record_failure(client)
        return _message_page(
            "コードが無効か、期限切れです",
            "テレビ側でもう一度やり直してください。",
            status=404,
        )
    throttle.clear(client)

    params = {
        "lang": "ja",
        "country": "JP",
        # localhost は任意パスが許可されるため、state をパスに埋めて確実に紐付ける
        "redirect_url": f"{config.REDIRECT_BASE_URL}/callback/{session.state}",
        "app_type": config.DEVICE_APP_TYPE,
        "device_id": session.device_id,
        "device_type": "pc",
        # TV が生成した challenge をそのまま渡す。サーバーは verifier を知らない
        "code_challenge": session.code_challenge,
        "code_challenge_method": "S256",
    }
    return RedirectResponse(f"{AUTH_ENDPOINT}?{urlencode(params)}", status_code=302)


# ---------------------------------------------------------------- #
#  3. AccountPF からのコールバック
# ---------------------------------------------------------------- #

@app.get("/callback/{state}")
def callback(state: str, auth_code: str = "", error: str = "") -> HTMLResponse:
    """認可コードを受け取る。トークンには交換しない（できない）。"""
    session = store.by_state(state)
    if session is None or _is_expired(session):
        return _message_page(
            "セッションが期限切れです",
            "テレビ側でもう一度やり直してください。",
            status=410,
        )

    if error:
        store.delete(session.device_code)
        return _message_page("ログインがキャンセルされました", error, status=400)

    if not auth_code:
        return _message_page("認可コードを受け取れませんでした", status=400)

    session.auth_code = auth_code
    return _message_page(
        "テレビに戻ってください",
        "認証が完了しました。このタブは閉じて構いません。",
    )


# ---------------------------------------------------------------- #
#  4. TV: ポーリングして認可コードを受け取る
# ---------------------------------------------------------------- #

class CodeRequest(BaseModel):
    device_code: str


@app.post("/device/code")
def device_code_result(body: CodeRequest) -> JSONResponse:
    """
    device_code と引き換えに auth_code を返す。単回消費。

    トークン交換は TV が自分で行う。ここで交換すると、その瞬間だけ
    サーバーが code_verifier と発行トークンを同時に見ることになる。
    auth_code だけなら PKCE のおかげで単体では使えないので、経路が平文でも盗む価値がない。
    """
    session = store.by_device_code(body.device_code)
    if session is None:
        return _error(ERR_DENIED, detail="unknown device_code")
    if _is_expired(session):
        store.delete(session.device_code)
        return _error(ERR_EXPIRED)

    now = time.time()
    if now - session.last_poll_at < config.POLL_INTERVAL_SEC * 0.5:
        return _error(ERR_SLOW_DOWN)
    session.last_poll_at = now

    if not session.is_authorized:
        return _error(ERR_PENDING)

    auth_code = session.auth_code
    store.delete(session.device_code)
    return JSONResponse(
        {
            "auth_code": auth_code,
            "base_url": config.DEFAULT_BASE_URL,
            # 認可時の app_type と揃えないと交換に失敗する
            "app_type": config.DEVICE_APP_TYPE,
        }
    )


# ---------------------------------------------------------------- #
#  画面
# ---------------------------------------------------------------- #

_STYLE = """
body{font-family:sans-serif;max-width:640px;margin:64px auto;padding:0 16px;color:#222;line-height:1.8}
h1{font-size:24px} p{color:#555}
input{font-size:28px;letter-spacing:.3em;padding:10px 14px;width:220px;text-align:center}
button{font-size:18px;padding:10px 24px;margin-left:8px;cursor:pointer}
code{background:#f0f0f0;padding:2px 6px;border-radius:4px}
"""


def _message_page(title: str, detail: str = "", status: int = 200) -> HTMLResponse:
    # detail には AccountPF からの error クエリがそのまま入ることがある。
    # エスケープしないと /callback に細工した URL で任意スクリプトが動く。
    safe_title = html.escape(title)
    safe_detail = html.escape(detail)
    return HTMLResponse(
        f"""<!doctype html><html lang="ja"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>{safe_title}</title><style>{_STYLE}</style></head>
        <body><h1>{safe_title}</h1><p>{safe_detail}</p></body></html>""",
        status_code=status,
    )


def _user_code_form() -> HTMLResponse:
    return HTMLResponse(
        f"""<!doctype html><html lang="ja"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>テレビにログイン</title><style>{_STYLE}</style></head>
        <body>
          <h1>テレビにログイン</h1>
          <p>テレビの画面に表示されているコードを入力してください。</p>
          <form method="get" action="/device">
            <input name="user_code" maxlength="12" autocapitalize="characters" autofocus>
            <button type="submit">次へ</button>
          </form>
        </body></html>""",
    )


@app.get("/", response_class=HTMLResponse)
def index() -> HTMLResponse:
    return _message_page(
        "Camera TV ペアリングサーバー",
        f"稼働中です。テレビの QR から <code>{config.PUBLIC_BASE_URL}/device</code> が開かれます。",
    )


@app.get("/health")
def health() -> JSONResponse:
    return JSONResponse({"status": "ok"})


if __name__ == "__main__":
    import uvicorn

    from setup_check import primary_ipv4

    host = config.REDIRECT_BASE_URL
    if "localhost" not in host and "127.0.0.1" not in host:
        print(f"[WARN] REDIRECT_BASE_URL={host} はホワイトリスト外です。AccountPF に拒否されます。")

    # TV が叩く URL は PC の LAN IP に依存し、DHCP で変わる。
    # 古いアドレスのままだと、症状はファイアウォール遮断と見分けがつかない。
    lan_ip = primary_ipv4()
    print("=" * 70)
    if lan_ip:
        print("TV アプリの Camera_tv/local.properties に次の行を入れてください:")
        print()
        print(f"    pairing.serverUrl=http://{lan_ip}:{config.PORT}")
        print()
        print("※ この IP は DHCP で変わります。つながらないときはまずここを疑う。")
    else:
        print("[WARN] LAN IP を特定できませんでした。ネットワーク接続を確認してください。")
    print("-" * 70)
    print(f"ブラウザ向け : {config.PUBLIC_BASE_URL}")
    print(f"redirect_url : {config.REDIRECT_BASE_URL}/callback/<state>")
    print("=" * 70)

    uvicorn.run(app, host=config.HOST, port=config.PORT)
