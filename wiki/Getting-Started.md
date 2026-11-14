# Getting Started

## 1. ビルド

```bash
git clone https://github.com/haruki0420/p2pgps-android.git
cd p2pgps-android

# 単体テスト
./gradlew :app:testDebugUnitTest

# デバッグ APK
./gradlew :app:assembleDebug
```

生成物は `app/build/outputs/apk/debug/app-debug.apk` です。

### 必要な環境

| 項目 | 要件 |
| :--- | :--- |
| JDK | 21 |
| Android SDK | Platform 36 / Build-Tools 37.0.0 |
| Gradle | 9.8.2（Wrapper が自動取得） |

GitHub Actions でも同じビルドを実行できます。`.github/workflows/ci.yml` を参照してください。

## 2. インストール

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

または APK を端末へ転送してインストールします（提供元不明のアプリの許可が必要）。

## 3. 権限の許可

初回起動時に「権限を許可する」を押し、以下を許可します。

- 位置情報（**正確な位置情報**が必要です。おおよその位置情報だけでは測位できません）
- Bluetooth（Android 12 以降: アドバタイズ・接続）
- 通知（Android 13 以降。拒否しても送信自体は可能）

## 4. 送信を開始する

1. iPad で P2P GPS Receiver を起動し、スキャンを開始します。
2. Android で「送信を開始」ボタンを押します。
3. ステータスレールに `P2PGPS-ANDROID-XXXX` が表示され、状態テキストが「接続待ち」になります。
4. iPad から接続すると状態テキストが「接続済み」になり、送信が始まると「送信中」に変わります。
5. 位置が取得できると、リードアウトプレートに緯度・経度・精度が表示されます。

## 5. 停止する

- 画面下部の「送信を停止」を押す
- または通知の「停止」を押す

停止するとアドバタイズも終了し、端末の Bluetooth 名は元に戻ります。

## 6. デバッグ用のシミュレート位置

実測値の代わりに固定座標を送りたい場合は「デバッグ」セクションに緯度・経度を入力し、「シミュレートを開始」を押します。`flags` の `SIMULATED` ビットが立ち、受信側でシミュレートとして扱えます。「解除」で実測に戻ります。

## 7. 受信側（iPad）のセットアップ

iPad 側の準備（Developer Mode / pairing file / LocalDevVPN / DDI）は `p2pgps-ios` の Wiki を参照してください。本アプリは BLE 送信のみを担当します。
