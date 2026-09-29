"""
Step 0: デバイスフロー実装の前提確認スクリプト。

確認したいことは独立した 2 つで、どちらも成立しないと PKCE 版デバイスフローは作れない。

  Q1. redirect_url に自前サーバー（SEN 内ホスト等）を指定できるか
  Q2. AccountPF が PKCE（code_challenge / code_verifier）を実際に検証しているか

Q2 は既存の許可済み redirect_url のままで確認できるため、インフラ不要で先に潰せる。

実測結果（2026-08-21 / dev3）:
  Q1 → ホスト単位のホワイトリスト。localhost / 127.0.0.1 は任意ポート・任意パスで許可。
         それ以外のホスト（*.sony.net の別ホストを含む）は 400 Invalid redirectUrl。
  Q2 → PKCE は強制されている。code_verifier 無しは 401 "Code verifier is required."、
         正しい code_verifier（snake_case）を JSON ボディに入れれば 200。
  ※ 認可コードはクエリパラメータ `auth_code=` で戻ってくる（`code=` ではない）。

使い方:
  # Q1: redirect_url ごとの反応を比較する（ログイン不要）
  python server/probe_oauth.py redirect

  # Q2-1: PKCE 付き認可 URL を発行する（表示された URL をブラウザで開いてログイン）
  python server/probe_oauth.py pkce-url

  # Q2-2: リダイレクト先 URL の code= を渡して、検証パターンを 3 通り試す
  python server/probe_oauth.py pkce-exchange <auth_code>

環境変数:
  IE_BASE_URL      ... AccountPF のベース URL
  IE_APP_TYPE      ... app_type（whitelist は app_type ごとに違う可能性が高い）
  IE_REDIRECT_URLS ... Q1 で試す redirect_url をカンマ区切りで追加指定
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import secrets
import sys
from pathlib import Path
from urllib.parse import urlencode

import requests

BASE_URL = os.environ.get("IE_BASE_URL", "https://ws.dev3.imagingedge.sony.net").rstrip("/")
APP_TYPE = os.environ.get("IE_APP_TYPE", "_trial_")
DEVICE_ID = os.environ.get("IE_DEVICE_ID", "probe-device-001")

AUTH_ENDPOINT = f"{BASE_URL}/api/v1/oauth2/auth"
TOKEN_ENDPOINT = f"{BASE_URL}/api/v1/oauth2/token"

# code_verifier は pkce-url と pkce-exchange をまたいで使うのでファイルに置く（git 管理外）
VERIFIER_FILE = Path(__file__).with_name(".pkce_verifier")

# Q1 で比較する redirect_url。上から順に「通って当然」→「通ってほしい」→「通らないはず」。
DEFAULT_REDIRECT_CANDIDATES = [
    # ベースライン: 既存フローで使えている Sony ドメイン
    f"{BASE_URL}/",
    # 同一ドメイン配下のパス違い（パスまで完全一致を要求するかの判定）
    f"{BASE_URL}/tv-pairing/callback",
    # 別の Sony ドメイン
    "https://w.dev3.creatorscloud.sony.net/callback",
    # 実際に使いたい自前サーバー（SEN 内ホスト。環境に合わせて IE_REDIRECT_URLS で指定する）
    "http://localhost:8000/callback",
    # 完全な外部ドメイン: 拒否されるのが正常。ここが通るなら whitelist 自体が無い
    "https://example.com/callback",
]


# ---------------------------------------------------------------- #
#  PKCE
# ---------------------------------------------------------------- #

def generate_pkce() -> tuple[str, str]:
    """RFC 7636 準拠の code_verifier / code_challenge(S256) を生成する。"""
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode().rstrip("=")
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    challenge = base64.urlsafe_b64encode(digest).decode().rstrip("=")
    return verifier, challenge


def build_auth_url(redirect_url: str, code_challenge: str | None = None) -> str:
    params = {
        "lang": "ja",
        "country": "JP",
        "redirect_url": redirect_url,
        "app_type": APP_TYPE,
        "device_id": DEVICE_ID,
        "device_type": "pc",
    }
    if code_challenge:
        params["code_challenge"] = code_challenge
        params["code_challenge_method"] = "S256"
    return f"{AUTH_ENDPOINT}?{urlencode(params)}"


# ---------------------------------------------------------------- #
#  Q1: redirect_url の許容範囲
# ---------------------------------------------------------------- #

def classify(response: requests.Response) -> str:
    """レスポンスが「WAF による遮断」か「OAuth のエラー」かを見分ける。対処法が全く違う。"""
    body = response.text[:400].lower()
    if response.status_code in (301, 302, 303, 307, 308):
        return "REDIRECT（ログイン画面へ誘導＝この redirect_url は受理された可能性が高い）"
    if response.status_code == 403:
        if "waf" in body or "access denied" in body or "request blocked" in body:
            return "WAF 遮断（ネットワーク層。SEN 内からなら通る可能性あり）"
        return "403（アプリ層の拒否か WAF か要判別）"
    if response.status_code == 400:
        return "400（invalid_redirect_uri 等のアプリ層エラーの可能性）"
    if response.status_code == 200:
        return "200（ログイン画面が返っている＝受理された可能性が高い）"
    return f"{response.status_code}"


def probe_redirects() -> None:
    candidates = list(DEFAULT_REDIRECT_CANDIDATES)
    extra = os.environ.get("IE_REDIRECT_URLS", "").strip()
    if extra:
        candidates = [u.strip() for u in extra.split(",") if u.strip()] + candidates

    print(f"base_url = {BASE_URL}")
    print(f"app_type = {APP_TYPE}")
    print("-" * 100)

    for redirect_url in candidates:
        url = build_auth_url(redirect_url)
        try:
            # リダイレクトを追わない。Location ヘッダそのものが判定材料になる
            res = requests.get(url, allow_redirects=False, timeout=15)
        except requests.RequestException as e:
            print(f"[ERR ] {redirect_url}\n       {e}\n")
            continue

        print(f"[{res.status_code:>3}] {redirect_url}")
        print(f"       判定: {classify(res)}")
        location = res.headers.get("Location")
        if location:
            print(f"       Location: {location[:160]}")
        server = res.headers.get("Server") or res.headers.get("X-Cache")
        if server:
            print(f"       Server: {server}")
        if res.status_code >= 400:
            print(f"       Body: {res.text[:200]!r}")
        print()

    print("-" * 100)
    print("読み方:")
    print("  ・ベースラインが REDIRECT/200 で、自前サーバーだけ 400 → アプリ層の whitelist。登録申請が要る")
    print("  ・自前サーバーが WAF 遮断 → ネットワーク層。SEN 内から同じ試験を再実行して比較する")
    print("  ・example.com まで通る → whitelist が機能していない。設計の前提を見直す")


# ---------------------------------------------------------------- #
#  Q2: PKCE が実際に検証されているか
# ---------------------------------------------------------------- #

def print_pkce_url() -> None:
    verifier, challenge = generate_pkce()
    VERIFIER_FILE.write_text(verifier, encoding="utf-8")

    redirect_url = os.environ.get("IE_REDIRECT_URL", f"{BASE_URL}/")
    url = build_auth_url(redirect_url, code_challenge=challenge)

    print("1) 以下の URL をブラウザで開いてログインしてください")
    print()
    print(url)
    print()
    print(f"   code_challenge = {challenge}")
    print(f"   code_verifier  = {VERIFIER_FILE}（保存済み・git 管理外にすること）")
    print()
    print("2) リダイレクト先 URL の auth_code= を控えて、次を実行してください")
    print("   （リダイレクト先にサーバーが無くても、アドレスバーの auth_code が取れれば OK）")
    print("   python server/probe_oauth.py pkce-exchange <auth_code> [none|wrong|ok|ok-camel]")


def probe_pkce_support() -> None:
    """
    ログインせずに PKCE パラメータの扱いを調べる。

    認可エンドポイントが code_challenge を受理し、かつログイン画面へ引き渡していれば
    サーバー側で保持される見込みがある。落とされていれば未対応の可能性が高い。
    """
    verifier, challenge = generate_pkce()
    redirect_url = os.environ.get("IE_REDIRECT_URL", f"{BASE_URL}/")

    for label, url in (
        ("PKCE なし", build_auth_url(redirect_url)),
        ("PKCE あり(S256)", build_auth_url(redirect_url, code_challenge=challenge)),
    ):
        res = requests.get(url, allow_redirects=False, timeout=15)
        location = res.headers.get("Location", "")
        print(f"[{res.status_code:>3}] {label}")
        if res.status_code >= 400:
            print(f"       Body: {res.text[:200]!r}")
        else:
            kept = [k for k in ("code_challenge", "code_challenge_method") if k in location]
            print(f"       ログイン画面へ引き継がれたPKCEパラメータ: {kept or 'なし'}")
        print(f"       Location: {location[:200]}")
        print()

    print("-" * 100)
    print("読み方:")
    print("  ・PKCE ありが 400 → code_challenge を受け付けない。未対応")
    print("  ・302 だがパラメータが引き継がれない → 黙って無視されている可能性。pkce-exchange で要確認")
    print("  ・302 でパラメータが引き継がれる → 対応の見込みあり。pkce-exchange で検証まで確認する")
    print()
    print(f"（このコマンドは検証用の verifier を保存しません: {verifier[:8]}… は破棄されます）")


def exchange(auth_code: str, extra: dict[str, object], label: str) -> None:
    body: dict[str, object] = {"app_type": APP_TYPE, "auth_code": auth_code}
    body.update(extra)

    try:
        res = requests.post(TOKEN_ENDPOINT, json=body, timeout=15)
    except requests.RequestException as e:
        print(f"[ERR ] {label}: {e}")
        return

    print(f"[{res.status_code:>3}] {label}")
    print(f"       送信キー: {sorted(body.keys())}")
    if res.status_code == 200:
        data = res.json()
        # トークン本体は絶対に全文を出さない。TTL は判断材料なのでそのまま出す
        preview = {
            k: (f"{str(v)[:12]}…" if k.endswith("_token") else v) for k, v in data.items()
        }
        print(f"       成功: {json.dumps(preview, ensure_ascii=False)}")
    else:
        print(f"       失敗: {res.text[:300]!r}")
    print()


# auth_code は 1 回しか使えないため、1 ログインにつき 1 ケースだけ試す
CASES: dict[str, str] = {
    "none": "code_verifier を送らない（PKCE が強制なら失敗するはず）",
    "wrong": "誤った code_verifier（必ず失敗するはず）",
    "ok": "正しい code_verifier（成功するはず）",
    "ok-camel": "正しい値を codeVerifier で送る（API はエラー文言が camelCase のため）",
}


def probe_pkce(auth_code: str, case: str) -> None:
    if case not in CASES:
        print(f"case は {list(CASES)} のいずれかを指定してください")
        sys.exit(1)
    if not VERIFIER_FILE.exists():
        print("code_verifier がありません。先に pkce-url を実行してください。")
        sys.exit(1)
    verifier = VERIFIER_FILE.read_text(encoding="utf-8").strip()

    extra: dict[str, object] = {
        "none": {},
        "wrong": {"code_verifier": "wrong-" + secrets.token_urlsafe(32)},
        "ok": {"code_verifier": verifier},
        "ok-camel": {"codeVerifier": verifier},
    }[case]

    exchange(auth_code, extra, f"{case}: {CASES[case]}")

    print("-" * 100)
    print("auth_code は使い切りの可能性が高いので、次のケースは pkce-url からやり直すこと。")
    print()
    print("最終的な読み方:")
    print("  ・none 失敗 / wrong 失敗 / ok 成功 → PKCE が効いている。設計どおり進められる")
    print("  ・none 成功                       → code_challenge が検証されていない。仲介サーバーが")
    print("                                      auth_code を握るだけでトークン化できるので設計見直し")
    print("  ・ok 失敗 かつ ok-camel 成功      → パラメータ名は codeVerifier")
    print("  ・ok も ok-camel も失敗           → パラメータ名不明。API 仕様の確認が必要")


# ---------------------------------------------------------------- #

def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "redirect":
        probe_redirects()
    elif command == "pkce-support":
        probe_pkce_support()
    elif command == "pkce-url":
        print_pkce_url()
    elif command == "pkce-exchange":
        if len(sys.argv) < 3:
            print("usage: python server/probe_oauth.py pkce-exchange <auth_code> [none|wrong|ok|ok-camel]")
            sys.exit(1)
        probe_pkce(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "none")
    else:
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
