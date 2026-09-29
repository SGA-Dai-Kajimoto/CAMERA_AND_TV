"""
ループバック中継。中央のペアリングサーバーを、手元の PC の localhost:8000 として見せる。

なぜ必要か:
  AccountPF の redirect_url は localhost / 127.0.0.1 しか許可されていない（実測済み）。
  そのため「ログイン後の戻り先」は必ず *ブラウザが動いている PC 自身* の :8000 になる。
  中央サーバーを別の場所に置く構成では、そこに何も無いのでコールバックを受け取れない。

  このスクリプトを手元の PC で動かすと、localhost:8000 への通信がそのまま
  中央サーバーへ中継されるので、ブラウザだけ離れた場所にあっても成立する。

使い方（認証操作をする PC で実行する）:
  python server/loopback_bridge.py https://xxxx.trycloudflare.com

  起動後、ブラウザで http://localhost:8000/device?user_code=XXXXXXXX を開く。

注意:
  中継するだけで内容は一切保存しない。リダイレクトは追わずそのままブラウザへ返す
  （302 を追ってしまうと AccountPF への遷移が壊れるため）。
"""

from __future__ import annotations

import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

import requests

LISTEN_HOST = "127.0.0.1"
LISTEN_PORT = 8000

# ホップバイホップヘッダは中継してはいけない
HOP_BY_HOP = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
    "host",
    "content-length",
}

upstream = ""


class BridgeHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:  # noqa: N802
        self._forward("GET")

    def do_POST(self) -> None:  # noqa: N802
        self._forward("POST")

    def _forward(self, method: str) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_BY_HOP}

        try:
            res = requests.request(
                method,
                f"{upstream}{self.path}",
                headers=headers,
                data=body,
                allow_redirects=False,  # 302 はブラウザに解決させる
                timeout=30,
            )
        except requests.RequestException as e:
            self.send_error(502, f"upstream error: {e}")
            return

        content = res.content
        self.send_response(res.status_code)
        for key, value in res.headers.items():
            if key.lower() not in HOP_BY_HOP:
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"  {self.command} {self.path} -> {args[1] if len(args) > 1 else ''}")


def main() -> None:
    global upstream
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    upstream = sys.argv[1].rstrip("/")
    if not urlparse(upstream).scheme:
        print("中央サーバーの URL を https:// から指定してください。")
        sys.exit(1)

    try:
        health = requests.get(f"{upstream}/health", timeout=10)
        health.raise_for_status()
    except requests.RequestException as e:
        print(f"{upstream} に繋がりません: {e}")
        sys.exit(1)

    print(f"中継先        : {upstream}")
    print(f"手元の入口    : http://{LISTEN_HOST}:{LISTEN_PORT}")
    print("ブラウザで http://localhost:8000/device を開いてコードを入力してください。")
    ThreadingHTTPServer((LISTEN_HOST, LISTEN_PORT), BridgeHandler).serve_forever()


if __name__ == "__main__":
    main()
