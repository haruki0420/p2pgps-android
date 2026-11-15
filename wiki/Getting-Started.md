# Getting Started

## 1. インストール

1. [Releases](https://github.com/haruki0420/p2pgps-android/releases) から `p2pgps-android-<version>-release.apk` をダウンロードします。
2. Android 端末に転送してインストールします（提供元不明のアプリの許可が必要です）。
3. 初回起動時に、位置情報（正確）・Bluetooth・通知を許可します。

## 2. iPad 側の準備

iPad に P2P GPS の受信アプリを入れ、Developer Mode・LocalDevVPN・pairing file・DDI を準備します。

手順は [Setup iPad](https://github.com/haruki0420/p2pgps/wiki/Setup-iPad) を見てください。

## 3. つなぐ

1. 送信側の Android で「送信を開始」を押します。
2. iPad で「スキャン開始」を押し、`P2PGPS-ANDROID-...` を選びます。
3. iPad で「位置情報を反映」を ON にします。

## 4. 停止する

- 画面下部の「送信を停止」を押します。
- または通知の「停止」を押します。

## 5. ビルド（開発者向け）

```bash
git clone https://github.com/haruki0420/p2pgps-android.git
cd p2pgps-android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

必要な環境: JDK 21 / Android SDK Platform 36 / Build-Tools 37.0.0。

## 6. テスト用の固定位置

「開発者向け（通常は使いません）」セクションに緯度・経度を入力し、「テスト用の位置を送る」を押します。「解除」で実測に戻ります。

## うまくいかないとき

[困ったとき](Troubleshooting) を見てください。
