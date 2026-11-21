# P2P GPS for Android

Android で取得した現在地を Bluetooth で iPad に送る送信専用アプリです。受信は iPad の [p2pgps-ios](https://github.com/haruki0420/p2pgps-ios)（Receiver）を使います。

- **これは何**: Android の GPS を BLE で iPad に送る Sender。位置の書き換えは行わない。
- **誰向け**: Mac を持たず、Android から iPad の位置を合わせたい技術志向の人。位置依存アプリの QA・デモ担当。
- **制約**: Android 8.0 以降。位置情報（正確）と Bluetooth の権限が必要。通知は Android 13 以降で要求するが、拒否しても送信は継続できる。受信には iPad 側の Receiver と追加準備が要る。
- **状態**: 検証状況はハブ Wiki の [Status](https://github.com/haruki0420/p2pgps/wiki/Status) を参照。

## 必要なもの

- Android 8.0 以降
- 位置情報（正確）と Bluetooth の権限
- 通知の権限（Android 13 以降。拒否しても送信は継続できます）
- 受信側の iPad に p2pgps-ios の Receiver と準備（pairing file / LocalDevVPN / DDI）

## 使い方

1. APK をインストール
2. 位置情報・Bluetooth・通知を許可
3. 「送信を開始」を押す
4. iPad 側で送信元を選ぶ

詳しい手順: [Getting Started](https://github.com/haruki0420/p2pgps-android/wiki/Getting-Started)

## ビルド

```bash
./gradlew :app:assembleDebug
```

## ドキュメント

- [Wiki の Home](https://github.com/haruki0420/p2pgps-android/wiki)
- [困ったとき](https://github.com/haruki0420/p2pgps-android/wiki/Troubleshooting)
- [よくある質問](https://github.com/haruki0420/p2pgps-android/wiki/FAQ)

## ライセンス

MIT
