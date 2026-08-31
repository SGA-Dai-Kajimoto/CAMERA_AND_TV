"""
メインウィンドウ。

左パネル（フォルダ）と右パネル（コンテンツ）を QSplitter で分割したメイン画面。
ApiClient を受け取り、各操作を Worker スレッド経由で非同期に実行する。
"""

from PyQt5.QtCore import Qt
from PyQt5.QtGui import QPixmap
from PyQt5.QtWidgets import (
    QApplication,
    QDialog,
    QDialogButtonBox,
    QFileDialog,
    QGroupBox,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QScrollArea,
    QSplitter,
    QStatusBar,
    QVBoxLayout,
    QWidget,
)

from api.client import ApiClient
from gui.worker import Worker


class MainWindow(QMainWindow):
    def __init__(self, client: ApiClient):
        super().__init__()
        self.client = client
        self.current_folder_id = None
        self._workers = []  # Worker の GC を防ぐために参照を保持

        self.setWindowTitle("Imaging Edge フォルダマネージャー")
        self.resize(900, 600)

        self._build_ui()
        self._verify_auth_and_load()

    # ---------------------------------------------------------------- #
    #  UI 構築
    # ---------------------------------------------------------------- #

    def _build_ui(self) -> None:
        central = QWidget()
        self.setCentralWidget(central)
        root_layout = QHBoxLayout(central)

        splitter = QSplitter(Qt.Horizontal)
        root_layout.addWidget(splitter)

        splitter.addWidget(self._build_folder_panel())
        splitter.addWidget(self._build_content_panel())
        splitter.setSizes([280, 620])

        self.status_bar = QStatusBar()
        self.setStatusBar(self.status_bar)

    def _build_folder_panel(self) -> QGroupBox:
        box = QGroupBox("フォルダ")
        layout = QVBoxLayout(box)

        self.folder_list = QListWidget()
        self.folder_list.currentItemChanged.connect(self._on_folder_selected)
        layout.addWidget(self.folder_list)

        btn_row = QHBoxLayout()
        self.btn_new_folder = QPushButton("新規作成")
        self.btn_rename_folder = QPushButton("名前変更")
        self.btn_delete_folder = QPushButton("削除")
        btn_row.addWidget(self.btn_new_folder)
        btn_row.addWidget(self.btn_rename_folder)
        btn_row.addWidget(self.btn_delete_folder)
        layout.addLayout(btn_row)

        self.btn_reload_folders = QPushButton("再読み込み")
        layout.addWidget(self.btn_reload_folders)

        self.btn_new_folder.clicked.connect(self._create_folder)
        self.btn_rename_folder.clicked.connect(self._rename_folder)
        self.btn_delete_folder.clicked.connect(self._delete_folder)
        self.btn_reload_folders.clicked.connect(self._load_folders)

        return box

    def _build_content_panel(self) -> QGroupBox:
        box = QGroupBox("コンテンツ")
        layout = QVBoxLayout(box)

        self.content_list = QListWidget()
        layout.addWidget(self.content_list)

        btn_row = QHBoxLayout()
        self.btn_upload = QPushButton("画像アップロード")
        self.btn_view_content = QPushButton("表示")
        self.btn_delete_content = QPushButton("削除")
        btn_row.addWidget(self.btn_upload)
        btn_row.addWidget(self.btn_view_content)
        btn_row.addWidget(self.btn_delete_content)
        layout.addLayout(btn_row)

        self.btn_upload.clicked.connect(self._upload_image)
        self.btn_view_content.clicked.connect(self._view_content)
        self.btn_delete_content.clicked.connect(self._delete_content)

        return box

    # ---------------------------------------------------------------- #
    #  ヘルパー
    # ---------------------------------------------------------------- #

    def _set_status(self, msg: str) -> None:
        self.status_bar.showMessage(msg)

    def _run(self, func, *args, on_result=None, on_error=None, on_progress=None, **kwargs) -> None:
        """func をワーカースレッドで実行し、完了時に on_result / on_error を呼ぶ。"""
        worker = Worker(func, *args, emit_progress=on_progress is not None, **kwargs)
        if on_result:
            worker.result.connect(on_result)
        if on_error:
            worker.error.connect(on_error)
        if on_progress:
            worker.progress.connect(on_progress)
        worker.error.connect(self._default_error_handler)
        worker.finished.connect(
            lambda: self._workers.remove(worker) if worker in self._workers else None
        )
        self._workers.append(worker)
        worker.start()

    def _default_error_handler(self, msg: str) -> None:
        self._set_status(f"エラー: {msg}")
        self._show_copyable_error("エラー", msg)

    def _show_copyable_error(self, title: str, message: str) -> None:
        """エラー内容を選択・コピー可能なテキスト欄付きダイアログで表示する。"""
        dialog = QDialog(self)
        dialog.setWindowTitle(title)
        dialog.resize(600, 320)

        layout = QVBoxLayout(dialog)
        layout.addWidget(QLabel("以下のエラー内容を選択してコピーできます:"))

        text_edit = QPlainTextEdit(message)
        text_edit.setReadOnly(True)
        text_edit.setTextInteractionFlags(
            Qt.TextSelectableByMouse | Qt.TextSelectableByKeyboard
        )
        text_edit.setLineWrapMode(QPlainTextEdit.WidgetWidth)
        layout.addWidget(text_edit)

        buttons = QDialogButtonBox(QDialogButtonBox.Close)
        copy_btn = buttons.addButton("クリップボードにコピー", QDialogButtonBox.ActionRole)

        def copy_to_clipboard():
            QApplication.clipboard().setText(message)
            copy_btn.setText("コピーしました")

        copy_btn.clicked.connect(copy_to_clipboard)
        buttons.rejected.connect(dialog.reject)
        layout.addWidget(buttons)

        dialog.exec_()

    def _set_buttons_enabled(self, enabled: bool) -> None:
        for btn in (
            self.btn_new_folder,
            self.btn_rename_folder,
            self.btn_delete_folder,
            self.btn_reload_folders,
            self.btn_upload,
            self.btn_view_content,
            self.btn_delete_content,
        ):
            btn.setEnabled(enabled)

    # ---------------------------------------------------------------- #
    #  起動時の認証確認
    # ---------------------------------------------------------------- #

    def _verify_auth_and_load(self) -> None:
        self._set_status("認証確認中...")
        self._set_buttons_enabled(False)

        def on_result(data):
            self._set_status(f"ログイン中: {data.get('email', '')}")
            self._set_buttons_enabled(True)
            self._load_folders()

        def on_error(msg):
            self._set_status(f"認証エラー: {msg}")
            self._set_buttons_enabled(True)
            self._show_copyable_error(
                "認証エラー", f"ユーザー情報の取得に失敗しました。\n\n{msg}"
            )

        self._run(self.client.get_user_me, on_result=on_result, on_error=on_error)

    # ---------------------------------------------------------------- #
    #  フォルダ操作
    # ---------------------------------------------------------------- #

    def _load_folders(self) -> None:
        self._set_status("フォルダを読み込んでいます...")

        def on_result(folders):
            self.folder_list.clear()
            for folder in folders:
                item = QListWidgetItem(folder.get("display_name") or folder.get("folder_id", ""))
                item.setData(Qt.UserRole, folder.get("folder_id"))
                self.folder_list.addItem(item)
            self._set_status(f"{len(folders)} フォルダ")

        self._run(self.client.list_folders, on_result=on_result)

    def _on_folder_selected(self, current, _previous) -> None:
        if current is None:
            self.current_folder_id = None
            self.content_list.clear()
            return
        self.current_folder_id = current.data(Qt.UserRole)
        self._load_contents(self.current_folder_id)

    def _create_folder(self) -> None:
        name, ok = QInputDialog.getText(self, "フォルダ作成", "フォルダ名:")
        if not ok or not name.strip():
            return
        self._set_status("フォルダを作成しています...")

        def on_result(_):
            self._set_status("フォルダを作成しました")
            self._load_folders()

        self._run(self.client.create_folder, name.strip(), on_result=on_result)

    def _rename_folder(self) -> None:
        item = self.folder_list.currentItem()
        if item is None:
            QMessageBox.information(self, "情報", "名前を変更するフォルダを選択してください。")
            return
        folder_id = item.data(Qt.UserRole)
        name, ok = QInputDialog.getText(self, "名前変更", "新しいフォルダ名:", text=item.text())
        if not ok or not name.strip():
            return
        self._set_status("フォルダ名を変更しています...")

        def on_result(_):
            self._set_status("フォルダ名を変更しました")
            self._load_folders()

        self._run(self.client.rename_folder, folder_id, name.strip(), on_result=on_result)

    def _delete_folder(self) -> None:
        item = self.folder_list.currentItem()
        if item is None:
            QMessageBox.information(self, "情報", "削除するフォルダを選択してください。")
            return
        folder_id = item.data(Qt.UserRole)
        reply = QMessageBox.question(
            self,
            "確認",
            f'フォルダ「{item.text()}」を削除しますか？\n※フォルダ内のコンテンツも削除される場合があります。',
            QMessageBox.Yes | QMessageBox.No,
        )
        if reply != QMessageBox.Yes:
            return
        self._set_status("フォルダを削除しています...")

        def on_result(_):
            self.current_folder_id = None
            self.content_list.clear()
            self._set_status("フォルダを削除しました")
            self._load_folders()

        self._run(self.client.delete_folder, folder_id, on_result=on_result)

    # ---------------------------------------------------------------- #
    #  コンテンツ操作
    # ---------------------------------------------------------------- #

    def _load_contents(self, folder_id: str) -> None:
        self._set_status("コンテンツを読み込んでいます...")
        self.content_list.clear()

        def on_result(contents):
            for content in contents:
                label = (
                    content.get("display_name")
                    or content.get("filename")
                    or content.get("content_id", "")
                )
                item = QListWidgetItem(label)
                item.setData(Qt.UserRole, content.get("content_id"))
                self.content_list.addItem(item)
            self._set_status(f"{len(contents)} コンテンツ")

        self._run(self.client.list_contents, folder_id, on_result=on_result)

    def _upload_image(self) -> None:
        if not self.current_folder_id:
            QMessageBox.information(self, "情報", "アップロード先のフォルダを選択してください。")
            return
        file_paths, _ = QFileDialog.getOpenFileNames(
            self,
            "アップロードする画像を選択（複数選択可）",
            "",
            "画像ファイル (*.jpg *.jpeg *.png *.heif *.heic *.raw *.arw *.tiff *.tif)",
        )
        if not file_paths:
            return
        total = len(file_paths)
        self._set_status(f"アップロード中: 0/{total} ...")
        folder_id = self.current_folder_id

        def on_progress(index, count, name):
            self._set_status(f"アップロード中: {index}/{count} {name} ...")

        def on_result(summary):
            succeeded = summary.get("succeeded", [])
            failed = summary.get("failed", [])
            if failed:
                detail = "\n".join(f"・{name}: {msg}" for name, msg in failed)
                self._set_status(f"アップロード完了: 成功 {len(succeeded)} / 失敗 {len(failed)}")
                self._show_copyable_error(
                    "一部失敗",
                    f"{len(failed)} 件のアップロードに失敗しました。\n\n{detail}",
                )
            else:
                self._set_status(f"アップロード完了: {len(succeeded)} 件")
            self._load_contents(folder_id)

        self._run(
            self.client.upload_images,
            folder_id,
            file_paths,
            on_progress=on_progress,
            on_result=on_result,
        )

    def _view_content(self) -> None:
        item = self.content_list.currentItem()
        if item is None:
            QMessageBox.information(self, "情報", "表示するコンテンツを選択してください。")
            return
        content_id = item.data(Qt.UserRole)
        self._set_status(f"ダウンロード中: {item.text()} ...")

        def on_result(data: bytes):
            self._set_status("表示完了")
            pixmap = QPixmap()
            pixmap.loadFromData(data)
            if pixmap.isNull():
                QMessageBox.warning(self, "表示エラー", "画像として表示できないデータです。")
                return
            dlg = QDialog(self)
            dlg.setWindowTitle(item.text())
            dlg.resize(900, 700)
            scroll = QScrollArea(dlg)
            scroll.setWidgetResizable(True)
            label = QLabel()
            label.setAlignment(Qt.AlignCenter)
            scaled = pixmap.scaled(880, 680, Qt.KeepAspectRatio, Qt.SmoothTransformation)
            label.setPixmap(scaled)
            scroll.setWidget(label)
            layout = QVBoxLayout(dlg)
            layout.addWidget(scroll)
            dlg.exec_()

        self._run(
            self.client.get_content_binary,
            self.current_folder_id,
            content_id,
            on_result=on_result,
        )

    def _delete_content(self) -> None:
        item = self.content_list.currentItem()
        if item is None:
            QMessageBox.information(self, "情報", "削除するコンテンツを選択してください。")
            return
        content_id = item.data(Qt.UserRole)
        reply = QMessageBox.question(
            self,
            "確認",
            f'「{item.text()}」を削除しますか？',
            QMessageBox.Yes | QMessageBox.No,
        )
        if reply != QMessageBox.Yes:
            return
        self._set_status("コンテンツを削除しています...")
        folder_id = self.current_folder_id

        def on_result(_):
            self._set_status("コンテンツを削除しました")
            self._load_contents(folder_id)

        self._run(self.client.delete_content, folder_id, content_id, on_result=on_result)
