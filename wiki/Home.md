# P2P GPS Android Sender

Android の現在地を Bluetooth で iPad に送る送信専用アプリです。受信は iPad の [p2pgps-ios](https://github.com/haruki0420/p2pgps-ios)（Receiver）を使います。

## 使い方

1. [Releases](https://github.com/haruki0420/p2pgps-android/releases) から APK をインストールする
2. 位置情報・Bluetooth・通知を許可する
3. 「送信を開始」を押す
4. iPad 側で送信元を選ぶ

iPad 側の準備はハブ Wiki の [Setup iPad](https://github.com/haruki0420/p2pgps/wiki/Setup-iPad) を見てください。

## ページ

### はじめて使う

- [Getting Started](Getting-Started) — ビルド・インストール・最初の接続
- [FAQ](FAQ)

### 開発する

- [Architecture](Architecture)
- [Design](Design)
- [Protocol](Protocol)
- [Testing](Testing)
- [Release Signing](Release-Signing)

### 困ったとき・状態

- [Troubleshooting](Troubleshooting)
- [Status](https://github.com/haruki0420/p2pgps/wiki/Status) — 検証状況の正本
- [Compatibility](https://github.com/haruki0420/p2pgps/wiki/Compatibility)
- [License](License)

## 必要なもの

- Android 8.0 以降・Bluetooth 対応端末
- 位置情報（正確）・Bluetooth・通知の権限（通知は Android 13 以降）
- 受信側の iPad に p2pgps-ios の Receiver と準備（pairing file / LocalDevVPN / DDI）
- 近距離・1 対 1 接続
