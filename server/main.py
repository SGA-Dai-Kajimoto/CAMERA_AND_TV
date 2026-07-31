"""
トークン中継（ペアリング）用ローカルサーバー（FastAPI）。

背景:
  dev3 環境の OAuth は redirect_url が Sony ドメインにホワイトリスト固定されており、
  自作サーバーを redirect_url にした自動コールバック方式は使えない（WAF が 403）。
  ログインは PC の Creators' Cloud Web アプリで行い、トークンは
  localStorage の "accessTokenSet" に保存される。

方式（貼り付け不要・TV文字入力不要）:
  1. TV  : POST /pairing/start
             → session_id / device_secret / 6桁コード(user_code) を発行
  2. TV  : 6桁コードを画面表示し、GET /pairing/{session_id} をポーリング
  3. PC  : Creators' Cloud にログイン済みのタブでブックマークレットを実行
             → localStorage.accessTokenSet を読取り、6桁コードを入力
             → POST /submit {user_code, tokens} でサーバーへ送信
  4. TV  : ポーリングで completed を受信し、トークンを取得（1回限り）→ 保存

セキュリティ:
  - user_code    : TV と PC の投入を紐付ける（6桁）
  - device_secret: トークン取得を TV だけに限定
  - セッションTTL : 期限切れセッションは失効
  - 単回受渡し    : トークンは1度取得すると消費（再取得不可）

注意:
  PC のブラウザ(HTTPS)から http へ送るため、送信先は localhost(127.0.0.1) を使う。
  （127.0.0.1 は HTTPS ページからでも許可される。ログインとサーバーは同一 PC 前提。）
"""

import json
import secrets
import time
from dataclasses import dataclass, field
from threading import Lock

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse

import config

app = FastAPI(title="Camera TV Token Relay Server", version="2.0.0")

# ブックマークレットは Sony のオリジン(HTTPS)から実行され、本サーバーへ POST する。
# text/plain の単純リクエストにしてプリフライトを避けるが、レスポンス読取のため CORS を許可する。
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------- #
#  セッションストア（インメモリ）
# ---------------------------------------------------------------- #

@dataclass
class PairingSession:
    session_id: str
    device_secret: str
    user_code: str
    created_at: float
    status: str = "pending"  # pending | completed
    tokens: dict = field(default_factory=dict)
    consumed: bool = False


class SessionStore:
    """スレッドセーフなインメモリセッションストア。"""

    def __init__(self) -> None:
        self._by_id: dict[str, PairingSession] = {}
        self._by_code: dict[str, str] = {}
        self._lock = Lock()

    def create(self) -> PairingSession:
        with self._lock:
            self._prune_locked()
            # アクティブな中で重複しない6桁コードを生成する。
            code = ""
            for _ in range(20):
                candidate = f"{secrets.randbelow(1_000_000):06d}"
                if candidate not in self._by_code:
                    code = candidate
                    break
            if not code:
                raise HTTPException(status_code=503, detail="could not allocate code")
            session = PairingSession(
                session_id=secrets.token_urlsafe(16),
                device_secret=secrets.token_urlsafe(24),
                user_code=code,
                created_at=time.time(),
            )
            self._by_id[session.session_id] = session
            self._by_code[session.user_code] = session.session_id
        return session

    def get(self, session_id: str) -> PairingSession | None:
        with self._lock:
            self._prune_locked()
            return self._by_id.get(session_id)

    def get_by_code(self, user_code: str) -> PairingSession | None:
        with self._lock:
            self._prune_locked()
            sid = self._by_code.get(user_code)
            return self._by_id.get(sid) if sid else None

    def delete(self, session_id: str) -> None:
        with self._lock:
            session = self._by_id.pop(session_id, None)
            if session:
                self._by_code.pop(session.user_code, None)

    def _prune_locked(self) -> None:
        now = time.time()
        expired = [
            sid for sid, s in self._by_id.items()
            if now - s.created_at > config.SESSION_TTL_SEC
        ]
        for sid in expired:
            session = self._by_id.pop(sid, None)
            if session:
                self._by_code.pop(session.user_code, None)


store = SessionStore()


def _is_expired(session: PairingSession) -> bool:
    return time.time() - session.created_at > config.SESSION_TTL_SEC


# ---------------------------------------------------------------- #
#  1. TV: ペアリング開始
# ---------------------------------------------------------------- #

@app.post("/pairing/start")
async def pairing_start() -> JSONResponse:
    """TV が呼ぶ。セッションを作成し、画面表示する6桁コードを返す。"""
    session = store.create()
    return JSONResponse(
        {
            "session_id": session.session_id,
            "device_secret": session.device_secret,
            "user_code": session.user_code,
            "expires_in": config.SESSION_TTL_SEC,
        }
    )


# ---------------------------------------------------------------- #
#  3. PC: ブックマークレットからトークンを投入
# ---------------------------------------------------------------- #

@app.post("/submit")
async def submit_tokens(request: Request) -> JSONResponse:
    """PC のブックマークレットが呼ぶ。6桁コードでセッションを特定しトークンを保存する。"""
    # ブックマークレットは text/plain で JSON 文字列を送る（プリフライト回避）。
    try:
        raw = (await request.body()).decode("utf-8")
        payload = json.loads(raw)
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="invalid body")

    user_code = str(payload.get("user_code", "")).strip()
    tokens = payload.get("tokens") or {}
    if not user_code or not isinstance(tokens, dict):
        raise HTTPException(status_code=400, detail="user_code and tokens required")

    session = store.get_by_code(user_code)
    if session is None or _is_expired(session):
        raise HTTPException(status_code=404, detail="コードが無効または期限切れです")

    access_token = str(tokens.get("access_token", "") or "")
    refresh_token = str(tokens.get("refresh_token", "") or "")
    if not access_token or not refresh_token:
        raise HTTPException(status_code=400, detail="トークンが不足しています")

    session.tokens = {
        "base_url": str(payload.get("base_url", "") or config.DEFAULT_BASE_URL),
        "app_type": str(payload.get("app_type", "") or config.DEFAULT_APP_TYPE),
        "access_token": access_token,
        "access_token_ttl": int(tokens.get("access_token_ttl", 0) or 0),
        "refresh_token": refresh_token,
        "refresh_token_ttl": int(tokens.get("refresh_token_ttl", 0) or 0),
    }
    session.status = "completed"
    return JSONResponse({"status": "ok"})


# ---------------------------------------------------------------- #
#  4. TV: ポーリングでトークン取得（device_secret 必須・単回消費）
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

    if not secrets.compare_digest(x_device_secret, session.device_secret):
        raise HTTPException(status_code=403, detail="invalid device secret")

    if session.status != "completed":
        return JSONResponse({"status": "pending"})

    tokens = session.tokens
    store.delete(session_id)  # 単回消費
    return JSONResponse({"status": "completed", "tokens": tokens})


# ---------------------------------------------------------------- #
#  ヘルスチェック
# ---------------------------------------------------------------- #

@app.get("/health")
async def health() -> JSONResponse:
    return JSONResponse({"status": "ok"})


# ---------------------------------------------------------------- #
#  セットアップ用ページ（ブックマークレット配布）
# ---------------------------------------------------------------- #

def _bookmarklet() -> str:
    """localStorage のトークンを読み取り本サーバーへ送るブックマークレット。"""
    js = (
        "javascript:(function(){"
        "try{"
        "var raw=localStorage.getItem('accessTokenSet');"
        "if(!raw){alert('トークンが見つかりません。Creators Cloud にログインしたタブで実行してください。');return;}"
        "var t=JSON.parse(raw);"
        "var c=prompt('TVに表示された6桁の番号を入力してください');"
        "if(!c){return;}"
        "fetch('http://127.0.0.1:__PORT__/submit',{method:'POST',headers:{'Content-Type':'text/plain'},"
        "body:JSON.stringify({user_code:c.trim(),base_url:'__BASE__',app_type:'__APPTYPE__',tokens:t})})"
        ".then(function(r){return r.json();})"
        ".then(function(j){alert(j.status==='ok'?'TVへ送信しました。TVをご確認ください。':'失敗: '+(j.detail||JSON.stringify(j)));})"
        ".catch(function(e){alert('送信エラー: '+e+'（サーバー起動と同一PC実行を確認してください）');});"
        "}catch(e){alert('エラー: '+e);}"
        "})();"
    )
    return (
        js.replace("__PORT__", str(config.PORT))
        .replace("__BASE__", config.DEFAULT_BASE_URL)
        .replace("__APPTYPE__", config.DEFAULT_APP_TYPE)
    )


@app.get("/", response_class=HTMLResponse)
async def index() -> HTMLResponse:
    bm = _bookmarklet()
    # href にそのまま入れる（JS 内はシングルクォートのみ・ダブルクォート無し）。
    return HTMLResponse(
        f"""
        <!doctype html><html lang="ja"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Camera TV トークン中継</title>
        <style>
          body{{font-family:sans-serif;max-width:760px;margin:24px auto;padding:0 16px;color:#222;line-height:1.7}}
          h1{{font-size:22px}} h2{{font-size:18px;margin-top:28px}}
          .bm{{display:inline-block;padding:10px 18px;background:#1565c0;color:#fff;border-radius:8px;text-decoration:none;font-weight:bold}}
          .login{{display:inline-block;padding:10px 18px;background:#2e7d32;color:#fff;border-radius:8px;text-decoration:none;font-weight:bold}}
          ol{{padding-left:22px}} code{{background:#f0f0f0;padding:2px 6px;border-radius:4px}}
          .note{{color:#666;font-size:14px}}
        </style></head>
        <body>
          <h1>Camera TV トークン中継サーバー</h1>
          <h2>1. Creators' Cloud にログイン</h2>
          <p>下のボタンから別タブでログインページを開き、ログインしてください。</p>
          <p><a class="login" href="{config.LOGIN_URL}" target="_blank" rel="noopener">Creators' Cloud を開く（別タブ）</a></p>
          <h2>2. ブックマークレットを登録（初回のみ）</h2>
          <p>下のボタンを<strong>ブラウザのブックマークバーにドラッグ</strong>して登録してください。</p>
          <p><a class="bm" href="{bm}">TVへトークン送信</a></p>
          <h2>3. 使い方</h2>
          <ol>
            <li>上のボタンで Creators' Cloud にログインする</li>
            <li>TV に表示された<strong>6桁の番号</strong>を確認する</li>
            <li>ログイン済みのタブで、登録したブックマーク「TVへトークン送信」をクリック</li>
            <li>プロンプトに6桁の番号を入力する</li>
            <li>「TVへ送信しました」と出れば完了。TV が自動でログインします</li>
          </ol>
          <p class="note">※ このページとログインは同じ PC で開いてください（送信先は 127.0.0.1:{config.PORT}）。<br>
          ※ 6桁番号は {config.SESSION_TTL_SEC} 秒で失効します。切れたら TV で再表示してください。</p>
        </body></html>
        """,
        status_code=200,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=config.HOST, port=config.PORT)
