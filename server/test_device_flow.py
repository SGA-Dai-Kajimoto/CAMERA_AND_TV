"""
device_flow.py の疎通確認。ブラウザでのログインが要る部分以外を自動で通す。

前提: 別ターミナルで `python server/device_flow.py` が起動していること。

確認する内容:
  1. POST /device/authorize が device_code / user_code / QR用URL を返す
  2. 認可前の POST /device/token が authorization_pending を返す
  3. 連打すると slow_down になる
  4. GET /device?user_code=... が AccountPF へ 302 し、TV の code_challenge を引き継ぐ
  5. 未知の device_code が access_denied になる
  6. 不正な state のコールバックが弾かれる

使い方:
  python server/test_device_flow.py
"""

from __future__ import annotations

import base64
import hashlib
import secrets
import sys
import time
from urllib.parse import parse_qs, urlparse

import requests

import config
import device_flow

# localhost だと Windows で ::1 を先に試して失敗し、接続ごとに約 2 秒の遅延が入る。
# uvicorn は 0.0.0.0（IPv4）に bind するので、試験では 127.0.0.1 を直指定する。
# ポートはサーバーと同じ設定源（PORT 環境変数）から取る。固定にすると、
# 別ポートで起動したときに他プロセスへ繋がって全項目が謎の 404 になる。
BASE = f"http://127.0.0.1:{config.PORT}"

# レート制御を確かめるには連続して投げる必要があるので接続を使い回す
http = requests.Session()

passed = 0
failed = 0


def check(label: str, ok: bool, detail: str = "") -> None:
    global passed, failed
    if ok:
        passed += 1
        print(f"  OK   {label}")
    else:
        failed += 1
        print(f"  NG   {label}")
    if detail:
        print(f"       {detail}")


def main() -> None:
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode().rstrip("=")
    challenge = (
        base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    )

    try:
        requests.get(f"{BASE}/health", timeout=5)
    except requests.RequestException:
        print(f"{BASE} に繋がりません。先に python server/device_flow.py を起動してください。")
        sys.exit(1)

    print("1) POST /device/authorize")
    res = http.post(
        f"{BASE}/device/authorize",
        json={"code_challenge": challenge, "code_challenge_method": "S256", "device_id": "tv-test"},
        timeout=10,
    )
    check("200 が返る", res.status_code == 200, res.text[:200])
    data = res.json()
    device_code = data.get("device_code", "")
    user_code = data.get("user_code", "")
    check("device_code が発行される", len(device_code) >= 32)
    check(
        "user_code が 8 文字の英字",
        len(user_code) == 8 and user_code.isalpha() and user_code.isupper(),
        f"user_code={user_code}",
    )
    check(
        "QR 用 URL に user_code が入る",
        data.get("verification_uri_complete", "").endswith(user_code),
        data.get("verification_uri_complete", ""),
    )
    check("interval が返る", isinstance(data.get("interval"), int))

    print("\n2) 認可前の POST /device/code")
    res = http.post(f"{BASE}/device/code", json={"device_code": device_code}, timeout=10)
    check(
        "authorization_pending が返る",
        res.status_code == 400 and res.json().get("error") == "authorization_pending",
        res.text[:200],
    )

    print("\n3) 連打時のレート制御")
    res = http.post(f"{BASE}/device/code", json={"device_code": device_code}, timeout=10)
    check("slow_down が返る", res.json().get("error") == "slow_down", res.text[:200])

    print("\n4) GET /device?user_code=... のリダイレクト")
    res = http.get(f"{BASE}/device", params={"user_code": user_code}, allow_redirects=False, timeout=10)
    if res.status_code == 429:
        print("  ※ 前回の総当たり検証でこの IP がブロック中です。")
        print("    LOOKUP_BLOCK_SEC 秒待つか、サーバーを再起動してから実行してください。")
        sys.exit(1)
    check("302 が返る", res.status_code == 302, res.text[:200])
    location = res.headers.get("Location", "")
    query = parse_qs(urlparse(location).query)
    check("AccountPF の認可URLへ飛ぶ", "/api/v1/oauth2/auth" in location, location[:120])
    check(
        "TV の code_challenge が引き継がれる",
        query.get("code_challenge", [""])[0] == challenge,
    )
    redirect_url = query.get("redirect_url", [""])[0]
    check(
        "redirect_url が localhost の callback",
        redirect_url.startswith(f"{config.REDIRECT_BASE_URL}/callback/"),
        redirect_url,
    )
    check(
        "redirect_url に state が埋まっている",
        len(redirect_url.rsplit("/", 1)[-1]) >= 16,
        redirect_url,
    )

    print("\n5) 不正な入力")
    res = http.post(f"{BASE}/device/code", json={"device_code": "unknown-device-code"}, timeout=10)
    check(
        "未知の device_code は access_denied",
        res.json().get("error") == "access_denied",
        res.text[:200],
    )

    res = http.get(f"{BASE}/callback/not-a-real-state", params={"auth_code": "x"}, timeout=10)
    check("不正な state のコールバックは 410", res.status_code == 410, res.text[:120])

    res = http.get(f"{BASE}/device", params={"user_code": "ZZZZZZZZ"}, allow_redirects=False, timeout=10)
    check("未知の user_code は 404", res.status_code == 404)

    res = http.get(
        f"{BASE}/device",
        params={"user_code": user_code.lower()},
        allow_redirects=False,
        timeout=10,
    )
    check("小文字で入力しても受け付ける", res.status_code == 302)

    res = http.post(
        f"{BASE}/device/authorize",
        json={"code_challenge": challenge, "code_challenge_method": "plain"},
        timeout=10,
    )
    check("plain な challenge は拒否される", res.json().get("error") == "invalid_request", res.text[:200])

    print("\n6) ポーリング間隔をあければ再び pending が返る")
    time.sleep(2)
    res = http.post(f"{BASE}/device/code", json={"device_code": device_code}, timeout=10)
    check(
        "未認可のうちは pending が優先される",
        res.json().get("error") == "authorization_pending",
        res.text[:200],
    )

    print("\n7) user_code の総当たりを止める")
    # 同じ PC のブラウザからログインするので、ここが 429 になると自分が締め出される
    not_blocked = True
    for _ in range(15):
        res = http.get(
            f"{BASE}/device",
            params={"user_code": "".join(secrets.choice("BCDFGHJK") for _ in range(8))},
            allow_redirects=False,
            timeout=10,
        )
        if res.status_code == 429:
            not_blocked = False
            break
    check("同じ PC からの操作は締め出されない", not_blocked)

    # 締め出しそのものは HTTP を介さずに確かめる。
    # ここで実際にブロックさせると、直後のブラウザログインができなくなる。
    throttle = device_flow.LookupThrottle()
    remote = "203.0.113.9"
    for _ in range(config.MAX_LOOKUP_FAILURES):
        throttle.record_failure(remote)
    check("外部からの連続失敗はブロックされる", throttle.is_blocked(remote))

    loopback = device_flow.LookupThrottle()
    for _ in range(config.MAX_LOOKUP_FAILURES * 2):
        loopback.record_failure("127.0.0.1")
    check("ループバックは何度失敗してもブロックしない", not loopback.is_blocked("127.0.0.1"))

    print(f"\n{'=' * 60}")
    print(f"passed={passed} failed={failed}")
    if failed:
        sys.exit(1)
    print("\nここまで自動で確認できる範囲は全て OK。")
    print("ログインを含む通し確認は次で行えます:")
    print("  python server/test_device_flow.py e2e")


def e2e() -> None:
    """
    ログインを含む通し確認。code_verifier をプロセス内に保持したままポーリングする。

    TV が実際に行う手順と同じで、違いはブラウザを人間が開く点だけ。
    """
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode().rstrip("=")
    challenge = (
        base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    )

    res = http.post(
        f"{BASE}/device/authorize",
        json={"code_challenge": challenge, "device_id": "camera-tv-e2e"},
        timeout=10,
    )
    res.raise_for_status()
    data = res.json()
    device_code = data["device_code"]
    interval = data["interval"]

    print("TV に表示される想定の内容:")
    print(f"  番号 : {data['user_code']}")
    print(f"  QR   : {data['verification_uri_complete']}")
    print()
    print("上の QR の URL を PC のブラウザで開いてログインしてください。")
    print(f"ポーリング中（{interval} 秒間隔 / 最大 {data['expires_in']} 秒）…")

    deadline = time.time() + data["expires_in"]
    while time.time() < deadline:
        time.sleep(interval)
        res = http.post(f"{BASE}/device/code", json={"device_code": device_code}, timeout=15)
        if res.status_code == 200:
            granted = res.json()
            print(f"\n認可コードを受け取りました: {granted['auth_code'][:8]}…")

            # ここが本題。中継サーバーではなく AccountPF を直接叩く
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
                print(f"\nトークン交換に失敗: {exchanged.status_code} {exchanged.text[:300]}")
                sys.exit(1)

            print("認証に成功しました。TV が保存する内容:")
            for key, value in exchanged.json().items():
                shown = f"{str(value)[:12]}…" if key.endswith("_token") else value
                print(f"  {key} = {shown}")
            print("\n中継サーバーは code_verifier もトークンも一切見ていません。")
            return

        error = res.json().get("error", "")
        if error in ("authorization_pending", "slow_down"):
            print(f"  … {error}")
            continue
        print(f"\n失敗: {res.status_code} {res.text[:300]}")
        sys.exit(1)

    print("\nタイムアウトしました。")
    sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "e2e":
        e2e()
    else:
        main()
