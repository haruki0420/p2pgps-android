# P2P GPS Android Sender — Wiki

Android 端末を BLE 送信側（Sender）として使い、取得した GPS 位置を iPad の P2P GPS Receiver へ転送するアプリの Wiki です。

## この Wiki の構成

| ページ | 内容 |
| :--- | :--- |
| [Getting Started](Getting-Started.md) | ビルド・インストール・最初の接続 |
| [Protocol](Protocol.md) | BLE GATT プロトコル v1 の要点 |
| [Architecture](Architecture.md) | モジュール構成とデータフロー |
| [Troubleshooting](Troubleshooting.md) | よくある問題と対処 |
| [FAQ](FAQ.md) | よくある質問 |

## 3 行でわかる概要

- Android が **BLE ペリフェラル** として位置情報（20 bytes の Location Packet）を notify します。
- iPad 側（[`p2pgps-ios`](https://github.com/haruki0420/p2pgps-ios) の Receiver モード）が接続し、`START` を書き込むと送信が始まります。
- 送信は前景サービスで行うため、画面 OFF でも継続します。通知の「停止」でいつでも止められます。

## リポジトリ

- [`p2pgps-android`](https://github.com/haruki0420/p2pgps-android) — 本アプリ（MIT）
- [`p2pgps-ios`](https://github.com/haruki0420/p2pgps-ios) — iPhone Sender / iPad Receiver（AGPL-3.0）
- [`p2pgps-protocol`](https://github.com/haruki0420/p2pgps-protocol) — プロトコル仕様の正本・Python リファレンス実装・テストベクタ（MIT）

## 前提

- Android 8.0（API 26）以上・BLE 対応端末
- 受信側は iPad + P2P GPS Receiver（Developer Mode / pairing file / LocalDevVPN などのセットアップが必要）
- v1 は近距離・1 対 1・認証なし
