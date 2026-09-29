"""
実機確認の前チェック。TV から届く URL を確定させる。

やること:
  1. このPCが実際に使っている IPv4 アドレスを調べる
  2. ペアリングサーバーが起動しているか確認する
  3. そのアドレスで外部から到達できるか実際に叩いて確かめる
  4. TV の IP を渡した場合は、同じネットワークにいるかを判定する
  5. local.properties に貼る行をそのまま出力する

使い方（ペアリングサーバーを起動した状態で実行する）:
  python server/setup_check.py
  python server/setup_check.py 192.168.100.36     # TV の IP を渡すと同一ネットか判定する
"""

from __future__ import annotations

import ipaddress
import socket
import sys

import requests

import config

TIMEOUT = 3


def primary_ipv4(target: str = "8.8.8.8") -> str:
    """
    [target] へ通信するときに OS が選ぶインターフェースのアドレス。

    TV の IP を渡せば、有線と無線のように複数の経路がある環境でも
    「TV へ届く側」のアドレスが得られる。

    gethostname() の名前解決は社内 DNS の登録内容を返すことがあり、
    実インターフェースと食い違うため使わない。（UDP なのでパケットは飛ばない）
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect((target, 80))
        return str(sock.getsockname()[0])
    except OSError:
        return ""
    finally:
        sock.close()


def resolved_ipv4() -> list[str]:
    """ホスト名から引ける IPv4。参考情報で、実インターフェースとは限らない。"""
    found: set[str] = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            addr = info[4][0]
            if not addr.startswith(("127.", "169.254.")):
                found.add(addr)
    except socket.gaierror:
        pass
    return sorted(found)


def probe(url: str) -> tuple[bool, str]:
    try:
        res = requests.get(f"{url}/health", timeout=TIMEOUT)
        return res.status_code == 200, f"HTTP {res.status_code}"
    except requests.Timeout:
        return False, "タイムアウト"
    except requests.RequestException as e:
        return False, type(e).__name__


def print_cross_network_help() -> None:
    print("=" * 70)
    print("このままでは TV からサーバーに届きません。次のいずれかが必要です。")
    print()
    print("  A. PC を TV と同じ Wi-Fi / ルーターに接続する  ← 最も簡単")
    print("     接続し直してから、このスクリプトをもう一度実行してください。")
    print()
    print("  B. TV を PC と同じネットワークに接続する")
    print()
    print("  C. トンネルで公開する（ネットワークを跨げる）")
    print("       winget install --id Cloudflare.cloudflared")
    print(f"       cloudflared tunnel --url http://localhost:{config.PORT}")
    print("     発行された https://xxxx.trycloudflare.com を pairing.serverUrl に設定する。")
    print("     ブラウザはこの PC で開くので、サーバー側の設定変更は不要。")


def main() -> None:
    tv_ip = sys.argv[1] if len(sys.argv) > 1 else ""

    print("1) サーバーの起動確認")
    ok, detail = probe(f"http://127.0.0.1:{config.PORT}")
    if not ok:
        print(f"   NG  127.0.0.1:{config.PORT} が応答しません（{detail}）")
        print("       先に  python server/device_flow.py  を起動してください。")
        sys.exit(1)
    print(f"   OK  127.0.0.1:{config.PORT}")

    print("\n2) このPCのアドレス")
    # TV の IP が分かっていれば、そこへ届く経路のアドレスを選ぶ（有線/無線の併用に対応）
    primary = primary_ipv4(tv_ip) if tv_ip else primary_ipv4()
    if not primary:
        print("   NG  ネットワークに接続されていません。")
        sys.exit(1)
    if tv_ip:
        print(f"   TV へ向かう経路のアドレス : {primary}")
    else:
        print(f"   既定経路のアドレス : {primary}")
        print("   ※ 有線と無線を併用している場合は、TV の IP を引数に渡すと正確に判定できます")
    others = [a for a in resolved_ipv4() if a != primary]
    if others:
        print(f"   ホスト名の解決結果 : {', '.join(others)}")
        print("                        ※ 実際の経路とは異なるので使わないこと")

    print("\n3) 到達性の確認")
    ok, detail = probe(f"http://{primary}:{config.PORT}")
    print(f"   {'OK ' if ok else 'NG '} http://{primary}:{config.PORT}  {'' if ok else detail}")
    if not ok:
        print()
        print("   自分自身からも届きません。Windows ファイアウォールで受信を許可してください:")
        print('     New-NetFirewallRule -DisplayName "Camera TV Pairing Server" `')
        print(f"       -Direction Inbound -Protocol TCP -LocalPort {config.PORT} -Action Allow")
        sys.exit(1)

    if tv_ip:
        print(f"\n4) TV（{tv_ip}）との関係")
        try:
            tv = ipaddress.ip_address(tv_ip)
            # プレフィックス長は分からないので /24 で概算する
            pc_network = ipaddress.ip_interface(f"{primary}/24").network
        except ValueError:
            print("   NG  TV の IP アドレスの形式が正しくありません。")
            sys.exit(1)

        if tv in pc_network:
            print("   OK  同じネットワークにいます。そのまま繋がるはずです。")
        else:
            print(f"   NG  別のネットワークです（PC: {primary} / TV: {tv_ip}）")
            print()
            print_cross_network_help()
            sys.exit(1)

    print()
    print("=" * 70)
    print("Camera_tv/local.properties に以下を設定してください:")
    print()
    print(f"    pairing.serverUrl=http://{primary}:{config.PORT}")
    print()
    print("    # dev トークンは空にすること")
    print("    dev.accessToken=")
    print("    dev.refreshToken=")
    print()
    print("※ 設定後は再ビルドが必要です（BuildConfig に焼き込まれるため）。")
    print("※ ブラウザは必ずこの PC で開いてください（redirect_url が localhost のため）。")


if __name__ == "__main__":
    main()
