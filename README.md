# p2pgps-android

iPhone / Android で取得した GPS 位置情報を BLE (Bluetooth Low Energy) で iPad へ送り、iPad 上で「その位置にいる」ように位置情報を反映（シミュレート）するための **Android 送信側（Sender）アプリ**です。Kotlin + Jetpack Compose で実装しています。

- 受信側（iPad）: [`p2pgps-ios`](https://github.com/haruki0420/p2pgps-ios) の Receiver モード
- プロトコル仕様の正本: [`p2pgps-protocol`](https://github.com/haruki0420/p2pgps-protocol)

本リポジトリは **Android 送信側のみ** を提供します。Google Play Services（FusedLocationProvider）には依存せず、`LocationManager` と Android 標準の BLE API だけで動作します。

## 主な機能

- **BLE ペリフェラル**: `BluetoothLeAdvertiser` + `BluetoothGattServer` による GATT サーバー。Service UUID `B1E5A000-9C3D-4E7A-8F20-1A2B3C4D5E6F` をアドバタイズし、ローカルネーム `P2PGPS-ANDROID-XXXX` を Scan Response に載せます。
- **位置パケット送信**: 20 bytes 固定の Location Packet を 1 Hz（100〜60000 ms で可変）で notify。Extended Location（8 bytes）にも対応。
- **Control コマンド**: 受信側からの `START` / `STOP` / `SET_INTERVAL` / `REQUEST_FIX` / `SET_EXTENDED` / `RESET` を処理し、結果を Status notify で返します。
- **前景サービス**: `connectedDevice` + `location` タイプの前景サービスで、画面 OFF でも送信を継続。通知に現在座標・送信レート・接続状態と「停止」ボタンを表示します。
- **位置フィルタ**: 精度不足・外れ値を除去してから送信します。
- **Compose UI**: 送信 ON/OFF、接続状態、現在地、送信レート、権限、ログ、デバッグ用シミュレート位置を 1 画面に表示します。
- **JVM ユニットテスト**: プロトコル層（encode/decode）と位置フィルタを Android 非依存の純 Kotlin として実装し、仕様 §10 のテストベクタで検証します。

## 画面

視覚世界は「測量標（benchmark）」— 地図と座標が主役の計測器です。カードを並べず、1px のヘアラインと余白で構造化し、座標を画面で最も大きい数字として表示します。詳細は [`DESIGN.md`](DESIGN.md) と [`docs/design.md`](docs/design.md) を参照してください。

| セクション | 内容 |
| :--- | :--- |
| ステータスレール | 接続 LED、ローカルネーム（mono）、送信間隔（mono） |
| リードアウトプレート | 緯度・経度（mono、小数 5 桁）、精度、fix 品質バー（高/良/中/低/測位なし）、標高・速度、シミュレート表示 |
| 状態 | 「送信中」「受信側の接続待ち」「停止中」、送信間隔、BLE の 3 状態（アドバタイズ中 / 接続 N 台 / 購読 N 台） |
| 送信レート | 200 / 500 / 1000 / 2000 / 5000 ms の選択（受信側からの変更も反映） |
| 権限 | 位置情報・Bluetooth・通知の付与状態（アイコン + テキスト）と一括要求 |
| デバッグ | シミュレート位置（緯度・経度）の設定と解除 |
| ログ | 直近 200 件のアプリ内ログ（mono） |
| 主ボタン | 下部固定・52dp の「送信を開始」/「送信を停止」 |

## プロトコル概要（v1）

仕様の詳細は [`docs/protocol.md`](docs/protocol.md) と上流の `p2pgps-protocol` を参照してください。

| 役割 | UUID |
| :--- | :--- |
| P2P GPS Service | `B1E5A000-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Location Packet（read / notify） | `B1E5A001-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Control（write / write-without-response） | `B1E5A002-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Status（read / notify） | `B1E5A003-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Extended Location（read / notify） | `B1E5A004-9C3D-4E7A-8F20-1A2B3C4D5E6F` |

- バイトオーダーはすべて **リトルエンディアン**。浮動小数点は使いません。
- Location Packet は 20 bytes 固定のため、ATT MTU 23（ペイロード 20 bytes）でもフラグメンテーション不要です。
- ボンディング / ペアリングは行いません（Just Works 相当）。v1 は認証・暗号化なしのため、近距離の第三者が偽の位置を送れる点に注意してください。

## 必要環境

| 項目 | 要件 |
| :--- | :--- |
| OS | macOS / Linux / Windows |
| JDK | JDK 21（CI と同じ。ソース/ターゲットは Java 17） |
| Android SDK | Platform 36 / Build-Tools 37.0.0 |
| Gradle | 9.8.2（Gradle Wrapper が自動取得） |
| 実機 | Android 8.0 (API 26) 以上・BLE 対応端末 |

## ビルド

```bash
# 単体テスト
./gradlew :app:testDebugUnitTest

# デバッグ APK
./gradlew :app:assembleDebug
```

生成物: `app/build/outputs/apk/debug/app-debug.apk`

CI（GitHub Actions）は Ubuntu + JDK 21 + Gradle で `:app:testDebugUnitTest` と `:app:assembleDebug` を実行し、APK を artifact として保存します。ワークフローは [`.github/workflows/ci.yml`](.github/workflows/ci.yml) を参照してください。

<details>
<summary>コマンドライン環境での Android SDK セットアップ例（Linux）</summary>

```bash
export ANDROID_HOME=/path/to/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager "platform-tools" "platforms;android-36" "build-tools;37.0.0"
```

</details>

## 使い方

1. iPad 側で `p2pgps-ios` の Receiver モードを起動し、スキャンを開始します。
2. Android で本アプリを起動し、権限（位置情報・Bluetooth・通知）を許可します。
3. 「位置情報を送信」を ON にします。BLE アドバタイズが始まり、`P2PGPS-ANDROID-XXXX` として iPad から見えます。
4. iPad から接続すると、Status を read → `START(1000)` が書き込まれ、位置の notify が始まります。
5. 停止するときは画面のスイッチを OFF にするか、通知の「停止」を押します。

> 実機での動作確認手順は [`docs/testing.md`](docs/testing.md) と Wiki の [Troubleshooting](wiki/Troubleshooting.md) を参照してください。

## 権限

| 権限 | 用途 | 必要な Android |
| :--- | :--- | :--- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 位置情報の取得 | すべて |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | BLE ペリフェラル | 〜 Android 11（`maxSdkVersion=30`） |
| `BLUETOOTH_ADVERTISE` | アドバタイズ | Android 12 (API 31) 以降（実行時） |
| `BLUETOOTH_CONNECT` | GATT サーバー・端末名 | Android 12 (API 31) 以降（実行時） |
| `BLUETOOTH_SCAN` | 宣言のみ（本アプリはスキャンしない） | Android 12 (API 31) 以降 |
| `FOREGROUND_SERVICE` | 前景サービス | Android 9 (API 28) 以降 |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | BLE 接続維持の前景サービス | Android 14 (API 34) 以降 |
| `FOREGROUND_SERVICE_LOCATION` | 位置取得の前景サービス | Android 14 (API 34) 以降 |
| `POST_NOTIFICATIONS` | 常駐通知 | Android 13 (API 33) 以降（実行時） |

- Android 12 以降では `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` の実行時許可が必要です。
- Android 13 以降では通知の実行時許可が必要です（拒否しても送信自体は継続できます）。
- 前景サービスタイプは `connectedDevice|location` を併用しています。Android 14 以降は位置取得を行う前景サービスに `location` タイプの宣言が求められるためです。

## アーキテクチャ

```text
app/src/main/java/jp/haruki0420/p2pgps/
├── protocol/   # 純 Kotlin。UUID・LocationPacket・ExtendedPacket・ControlCommand・StatusPacket・SequenceTracker
├── ble/        # BluetoothLeAdvertiser + BluetoothGattServer（ペリフェラル）
├── location/   # LocationManager（GPS + NETWORK）と LocationFilter
├── service/    # 前景サービス・通知・画面からの操作ファサード
├── core/       # アプリ内ログ・バッテリー・共有状態
└── ui/         # MainActivity + Jetpack Compose 画面・権限処理・テーマ
```

- `protocol/` は Android に依存しない純 Kotlin です。JVM ユニットテストで encode/decode を担保します。
- BLE は実機なしでは検証できないため、`ble/` はプロトコルの encode/decode を `protocol/` に委譲し、接続・購読・MTU の状態管理だけを担当します。
- 詳細は [`docs/architecture.md`](docs/architecture.md) を参照してください。
- デザインの正本は [`PRODUCT.md`](PRODUCT.md) / [`DESIGN.md`](DESIGN.md)、Android への落とし込みは [`docs/design.md`](docs/design.md) を参照してください。

## テスト

```bash
./gradlew :app:testDebugUnitTest
```

- 仕様 §10 のテストベクタ（東京駅・南極・Null Island・範囲外・未知バージョン・sequence 巻き戻り・Extended・Control 各 opcode）を [`ProtocolCodecTest.kt`](app/src/test/java/jp/haruki0420/p2pgps/protocol/ProtocolCodecTest.kt) で検証します。
- 位置フィルタは [`LocationFilterTest.kt`](app/src/test/java/jp/haruki0420/p2pgps/location/LocationFilterTest.kt) で検証します。
- BLE ペリフェラルと前景サービスは **実機でのみ** 検証可能です。手順は [`docs/testing.md`](docs/testing.md) を参照してください。

## 既知の制約

- **実機での動作検証は未実施です。** このリポジトリは CI でのコンパイル・ユニットテストまでを保証します。
- Android には「アドバタイズデータへ任意のローカルネームを入れる API」がありません。そのため仕様 §1.2 の `P2PGPS-ANDROID-XXXX` を載せる目的で、送信中は **端末の Bluetooth 名を一時的に変更** し、停止時に元へ戻します（`BluetoothAdapter.setName`）。他のアプリや設定画面から見える名前が送信中だけ変わります。
- BLE は近距離・1 対 1 を想定しています（v1）。同時多接続は v2 以降です。
- v1 は認証・暗号化を行いません。位置情報は端末外（クラウド）へ送信しませんが、近距離の第三者によるなりすましは防げません。
- 位置情報のバックグラウンド取得は前景サービス経由で行います。省電力のため OS が位置更新を遅らせる場合があり、送信レートは厳密な周期ではありません。
- 端末の Bluetooth 名の変更・位置情報の取得には、メーカー独自の省電力設定が影響する場合があります。
- 仕様書 §3.2 のエンコード例（東京駅）の hex は、同じ行の 10 進値と一致しません。本実装は仕様の規範定義（緯度経度 × 10^7、Unix エポック秒）に従っています。詳細は [`docs/protocol.md`](docs/protocol.md) を参照してください。

## ライセンス

[MIT License](LICENSE) © 2026 haruki0420
