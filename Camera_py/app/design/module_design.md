# モジュール構成 設計書

---

## ディレクトリ構成

```
camera/app/
├── api/                        # API層: 認証・HTTP通信
│   ├── __init__.py
│   ├── auth.py                 # auth_info.json の読み書き
│   └── client.py               # ApiClient（全APIメソッド）
├── gui/                        # GUI層: PyQt5 ウィジェット
│   ├── __init__.py
│   ├── worker.py               # Worker（QThread バックグラウンド実行）
│   └── main_window.py          # MainWindow（メイン画面）
├── design/                     # 設計書
│   ├── gui_design.md           # GUI画面・API対応表・アップロードフロー
│   └── module_design.md        # このファイル
├── check_api.py                # CUI エントリーポイント（疎通確認）
└── gui_app.py                  # GUI エントリーポイント（フォルダマネージャー）
```

---

## 各モジュールの責務

| ファイル | 役割 | 依存先 |
|---|---|---|
| `api/auth.py` | `AUTH_FILE` 定数、`load_auth()` / `save_auth()` | 標準ライブラリのみ |
| `api/client.py` | `ApiClient` クラス。全 API メソッドを持つ。401 時に自動リフレッシュ | `api.auth`, `requests` |
| `gui/worker.py` | `Worker(QThread)` — API 呼び出しを UI スレッドから分離 | `PyQt5` |
| `gui/main_window.py` | `MainWindow(QMainWindow)` — UI 構築・操作ハンドラ | `api.client`, `gui.worker`, `PyQt5` |
| `check_api.py` | 疎通確認の CUI ランナー。結果を print で出力 | `api.client` |
| `gui_app.py` | GUI アプリの起動のみ。QApplication 生成 + MainWindow 表示 | `api.client`, `gui.main_window`, `PyQt5` |

---

## import 依存関係

```
check_api.py ──────────────────► api/client.py ──► api/auth.py
                                                   └► requests

gui_app.py ────────────────────► api/client.py
           └──────────────────► gui/main_window.py ──► api/client.py
                                                    └► gui/worker.py
```

両エントリーポイントはモジュール起動時に以下を実行し、`api/` と `gui/` を解決可能にする:

```python
sys.path.insert(0, str(Path(__file__).parent))  # camera/app/ を sys.path に追加
```

---

## 実行コマンド

```bash
# CUI 疎通確認
python camera/app/check_api.py

# GUI アプリ起動
python camera/app/gui_app.py
```

---

## 各モジュールの行数目安

| ファイル | 内容 | 行数目安 |
|---|---|---|
| `api/auth.py` | 定数 + 関数 2 つ | ~30 行 |
| `api/client.py` | ApiClient クラス（11 メソッド） | ~150 行 |
| `gui/worker.py` | Worker クラス | ~25 行 |
| `gui/main_window.py` | MainWindow クラス（UI 構築 + 操作ハンドラ） | ~230 行 |
| `check_api.py` | CUI ランナー（3 check 関数 + main） | ~70 行 |
| `gui_app.py` | エントリーポイントのみ | ~50 行 |

---

## デバッグ時の注目ファイル

| 問題 | 確認するファイル |
|---|---|
| 認証エラー・トークン切れ | `api/auth.py`, `api/client.py` の `refresh_token()` |
| API リクエスト失敗 | `api/client.py` の対象メソッド |
| UI が応答しない / ボタンが効かない | `gui/main_window.py` の対象ハンドラ |
| バックグラウンド処理が終わらない | `gui/worker.py` の `run()` |
| 起動しない | `gui_app.py` または `check_api.py` |
