"""
バックグラウンドワーカー。

API 呼び出しを UI スレッドから分離して実行する汎用 QThread。
完了時は result(object) シグナル、例外発生時は error(str) シグナルを送出する。
"""

from PyQt5.QtCore import QThread, pyqtSignal


class Worker(QThread):
    result = pyqtSignal(object)
    error = pyqtSignal(str)

    def __init__(self, func, *args, **kwargs):
        super().__init__()
        self._func = func
        self._args = args
        self._kwargs = kwargs

    def run(self):
        try:
            result = self._func(*self._args, **self._kwargs)
            self.result.emit(result if result is not None else True)
        except Exception as exc:  # noqa: BLE001
            self.error.emit(str(exc))
