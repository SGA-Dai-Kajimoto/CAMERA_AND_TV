# ドキュメント一覧

このディレクトリは、目的別に資料を分けて管理する。

## フォルダ構成

| フォルダ | 内容 |
|---|---|
| [auth/](auth/) | TV ペアリング認証、トークン仕様、説明資料、図、スクリーンショット |
| [api/](api/) | Imaging Edge / AccountPF API の仕様まとめ |
| [design/](design/) | TV アプリの画面設計、表示・グルーピング設計 |
| [learn/](learn/) | API 調査のはまりポイント、実測から得た教訓 |
| [log/](log/) | 作業ログ。まとまった検証をしたときだけ記録 |
| [project/](project/) | タスク一覧、プロジェクト進捗 |

## よく参照する資料

| 文書 | 用途 |
|---|---|
| [auth/flow.md](auth/flow.md) | TV ペアリング認証の全体フロー、データフロー、安全性確認 |
| [auth/spec.md](auth/spec.md) | 認証トークン JSON、Refresh Token API、秘密情報の扱い |
| [auth/auth_flow_presentation.pptx](auth/auth_flow_presentation.pptx) | 認証フロー説明用 PowerPoint |
| [api/api_spec_summary.md](api/api_spec_summary.md) | API エンドポイントとリクエスト仕様 |
| [design/tv_screen_design.md](design/tv_screen_design.md) | TV 画面設計 |
| [design/content_date_grouping_design.md](design/content_date_grouping_design.md) | 日付グルーピング設計 |
| [learn/imaging_edge_api.md](learn/imaging_edge_api.md) | Imaging Edge API の実測メモ |
| [project/TASKS.md](project/TASKS.md) | タスクと進捗 |

## 更新ルール

重要な変更をしたときだけ、対応する資料を更新する。
推測と実測を混ぜず、実測で判明した事実には測定日と条件を添える。
