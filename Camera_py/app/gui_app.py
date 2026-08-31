"""
Imaging Edge フォルダマネージャー GUI アプリ

使い方:
    python camera/app/gui_app.py

事前準備:
    camera/api/auth_info.json に access_token / refresh_token を記入すること。
    取得方法は docs/auth_spec.md を参照。

設計:
    camera/app/design/gui_design.md
    camera/app/design/module_design.md
"""

import sys
from pathlib import Path

# camera/app/ を sys.path に追加して api/ / gui/ パッケージをインポートできるようにする
sys.path.insert(0, str(Path(__file__).parent))

from PyQt5.QtWidgets import QApplication, QMessageBox  # noqa: E402

from api.client import ApiClient  # noqa: E402
from gui.main_window import MainWindow  # noqa: E402


# ------------------------------------------------------------------ #
#  エントリーポイント
# ------------------------------------------------------------------ #

def main() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName("Imaging Edge フォルダマネージャー")

    try:
        client = ApiClient()
    except FileNotFoundError as exc:
        QMessageBox.critical(None, "起動エラー", str(exc))
        sys.exit(1)

    window = MainWindow(client)
    window.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
