---
name: tv-device-auth
description: 'TV のペアリング認証（QR / デバイスフロー）を動かす・直す。Use when the TV app shows the auth screen, pairing fails, SocketTimeoutException / connection refused from the TV, the app is unexpectedly already signed in, the auth URL needs to be found, or the pairing server has to be started for on-device testing.'
argument-hint: '症状（例: TVからつながらない / 認証画面が出ない）'
---

# TV ペアリング認証の運用とトラブルシュート

このプロジェクトで**最も時間を溶かしてきた領域**。原因が似た症状に化けるので、必ず上から順に切り分ける。

## 全体像

```
TV ──(1) pairing.serverUrl ──> ペアリングサーバー(PC:8000)
PC ブラウザ ──(2) localhost:8000 ──> 同じサーバー
PC ブラウザ <──(3) AccountPF ログイン ──> redirect_url=localhost:8000/callback/{state}
TV ──(4) /device/token ポーリング ──> トークン取得
```

**(1) だけがネットワークの制約を受ける。** (2)(3) は同一 PC 内で完結する。

## 手順

### 1. サーバーを起動する

```powershell
python server/device_flow.py
```

起動していないと TV 側は接続エラーになる。**セッションをまたぐと落ちているので必ず確認する。**

```powershell
Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue
```

### 2. TV から届く URL を確定する

```powershell
python server/setup_check.py            # 既定経路のアドレスを表示
python server/setup_check.py <TVのIP>   # TV へ届く経路を正確に判定（有線/無線併用時は必須）
```

出力された行を `Camera_tv/local.properties` の `pairing.serverUrl` に設定し、**再ビルドして再インストール**する。
`BuildConfig` に焼き込まれるため、再インストールしないと反映されない。

### 3. 認証 URL を開く

TV がペアリングを開始すると、**サーバーのコンソールに URL が出る**。TV の画面を見て打ち直す必要はない。

```
======================================================================
TV がペアリングを開始しました（device_id=camera-tv）
  コード : GZBVDQWM
  URL   : http://localhost:8000/device?user_code=GZBVDQWM
======================================================================
```

VS Code のターミナルなら Ctrl+クリックで開く。自動で開かせたい場合は `AUTO_OPEN_BROWSER=1` を設定する
（検証スクリプトもタブを開いてしまうため既定は無効）。

TV 側の logcat にも出る。

```powershell
adb logcat -s AuthVM
```

## 症状別の切り分け

### `SocketTimeoutException` / `failed to connect`

| 確認 | 対処 |
|---|---|
| サーバーは起動しているか | `python server/device_flow.py` |
| `pairing.serverUrl` は現在のアドレスか | DHCP で変わる。`setup_check.py` で再確認して再ビルド |
| PC と TV が同じネットワークか | `python server/setup_check.py <TVのIP>` で判定 |
| ファイアウォール | 自ホストからも届かない場合のみ疑う（下記） |

> **アドレスが古いだけでもタイムアウトになる。** ファイアウォールを疑う前に必ずアドレスを確認する。
> `getaddrinfo(gethostname())` は社内 DNS の値を返して実インターフェースと食い違うことがある。

自ホストからも届かないときだけ、管理者 PowerShell で受信を許可する。

```powershell
New-NetFirewallRule -DisplayName "Camera TV Pairing Server" `
  -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow
```

### 認証画面が出ず、最初からサインイン済みになる

トークンは `local.properties` ではなく**端末内の DataStore（`token_prefs`）**にある。
`dev.accessToken` / `dev.refreshToken` を空にしても、`seedTokens` は
「シードが変わったときだけ書き込む」ため**既存のトークンは消えない**。

- アプリのメニュー →「その他」→「サインアウト」
- または `adb shell pm clear com.sony.dtv.camera_tv`

### ブラウザで開いても「テレビに戻ってください」にならない

- **ブラウザはサーバーと同じ PC で開いているか。** `redirect_url` が `localhost` 固定のため、
  別の PC やスマホでは成立しない
- 別 PC から操作したい場合は `python server/loopback_bridge.py <サーバーURL>` を操作側で動かす

### TV と PC が別ネットワークから動かせない

1. PC を TV と同じ Wi-Fi に繋ぐ（最も簡単）
2. TV を PC 側のネットワークに繋ぐ
3. トンネル（`cloudflared tunnel --url http://localhost:8000`）で `pairing.serverUrl` だけ公開する。
   ブラウザは PC で開くのでサーバー設定の変更は不要。ただし**トークンが第三者を経由する**

## 動作確認

```powershell
python server/test_device_flow.py       # ログイン不要（19項目）
python server/test_device_flow.py e2e   # ブラウザログインを含む通し確認
```

## 変えてはいけないこと

- `REDIRECT_BASE_URL` を `localhost` / `127.0.0.1` 以外にしない（AccountPF が 400 で拒否する）
- サーバーに `code_verifier` やトークンを保存しない
- TV 側の `code_verifier` を永続化しない（メモリのみ）
