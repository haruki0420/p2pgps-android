# P2P GPS for Android

Android で取得した現在地を Bluetooth で iPad に送るアプリです。

## 必要なもの

- Android 8.0 以降
- 位置情報（正確）と Bluetooth の権限
- 受信側の iPad に P2P GPS の受信アプリと準備（pairing file / LocalDevVPN / DDI）

## 使い方

1. APK をインストール
2. 位置情報と Bluetooth を許可
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
