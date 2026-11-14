# テストと検証

最終更新: 2026-11-15

## 1. 自動テスト

```bash
./gradlew :app:testDebugUnitTest
```

JVM ユニットテストは Android 実機・エミュレータ不要で実行できます。CI（GitHub Actions）でも同じコマンドを実行します。

### テスト一覧

| テストクラス | 対象 | 内容 |
| :--- | :--- | :--- |
| `protocol/ProtocolCodecTest` | `protocol/` 全体 | 仕様 §10 のテストベクタ（東京駅・南極・Null Island・範囲外・未知バージョン・sequence 巻き戻り・Extended・Control 各 opcode・Status） |
| `location/LocationFilterTest` | `location/` の純 Kotlin 部分 | 精度不足・外れ値・座標範囲・Haversine 距離・移動判定 |

### カバレッジの考え方

- **プロトコル層**は Android に依存しない純 Kotlin のため、encode/decode の全フィールドと検証ロジックを JVM テストで担保します。
- **位置フィルタ**も純 Kotlin のため JVM テストで担保します。
- **BLE ペリフェラル / 前景サービス / UI** は Android フレームワークに依存するため、CI ではコンパイル（`assembleDebug`）までを保証します。実機でのみ検証可能です。

### 主なテストベクタ（仕様 §10）

| ベクタ | 期待 |
| :--- | :--- |
| 東京駅（有効 fix、altitude/speed 有効） | 20 bytes のエンコード結果と round-trip |
| 南極（負の緯度・経度、altitude 負） | int32/int16 の 2 の補数エンコード |
| Null Island（0, 0 + VALID_FIX） | 検証で破棄（`NULL_ISLAND`） |
| 範囲外緯度・経度 | 検証で破棄（`LATITUDE_OUT_OF_RANGE` / `LONGITUDE_OUT_OF_RANGE`） |
| 境界（±90° / ±180°） | 受理（`±900_000_000` / `±1_800_000_000`） |
| 未知バージョン | decode が `null` |
| sequence 巻き戻り | `SequenceTracker` が検出するがパケットは受理 |
| Extended（provider / battery / course） | 8 bytes の round-trip、course の正規化 |
| Control 各 opcode | エンコード・デコード・interval の補正・未知 opcode・不正長 |
| Status | 8 bytes の round-trip、state / last_error の値 |

## 2. CI

`.github/workflows/ci.yml` が以下を実行します。

1. `actions/checkout@v6`
2. `actions/setup-java@v5`（Temurin JDK 21）
3. `gradle/actions/setup-gradle@v4`
4. `sdkmanager` で `platforms;android-36` / `build-tools;37.0.0` / `platform-tools` を導入
5. `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`
6. テスト結果とデバッグ APK を artifact として保存

## 3. 検証状況（2026-11-15）

CI と同等の環境をローカルに構築して確認しました。

| 項目 | 結果 |
| :--- | :--- |
| JDK | Temurin 21.0.13 |
| Android SDK | Platform 36 / Build-Tools 37.0.0 |
| Gradle | 9.8.2（Wrapper） |
| `:app:testDebugUnitTest` | 47 tests / 0 failures（ProtocolCodecTest 38、LocationFilterTest 9） |
| `:app:assembleDebug` | 成功（`app-debug.apk` 約 18.6 MB） |
| `:app:assembleRelease` | 成功（R8 による縮小。`app-release-unsigned.apk` 約 1.26 MB） |
| `:app:lintDebug` | 成功（エラーなし。警告は依存バージョン・targetSdk 35 の方針・`mipmap-anydpi-v26` の冗長 qualifier など） |
| APK manifest | `applicationId=jp.haruki0420.p2pgps`、minSdk 26 / targetSdk 35 / compileSdk 36、`MainActivity` 起動可能、`GpsSenderService` の `foregroundServiceType=0x18`（connectedDevice + location）、BLE / 位置 / 前景サービス / 通知の権限をすべて宣言 |

> 正式なゲートは GitHub Actions の CI です。上記は手元での事前確認記録です。

## 4. 実機での手動テスト手順

実機が無い開発環境では以下を実施できません。実機入手時に確認してください。

### 4.1 送信側の起動

1. `app-debug.apk` をインストールする。
2. アプリを起動し、権限（位置情報・Bluetooth・通知）を許可する。
3. 「送信を開始」を押す。ステータスレールの LED が「接続待ち」（amber）になり、ローカルネーム `P2PGPS-ANDROID-XXXX` が表示される。
4. 通知に「P2P GPS: 受信側の接続待ち」と送信間隔が表示される。

### 4.2 nRF Connect などでの GATT 確認

1. nRF Connect で `P2PGPS-ANDROID-XXXX` に接続する。
2. Service `B1E5A000-9C3D-4E7A-8F20-1A2B3C4D5E6F` の characteristic を確認する。
   - Location `...A001`: read / notify、20 bytes
   - Control `...A002`: write / write-without-response
   - Status `...A003`: read / notify、8 bytes
   - Extended `...A004`: read / notify、8 bytes
3. Status を read し、`01 00 E8 03 FF 00 00 00`（Idle / 1000ms）であることを確認する。
4. Location / Status の CCCD を notify 有効にする。
5. Control に `01 E8 03`（START 1000ms）を write する。
6. Location が 1 秒ごとに notify され、`version=1` / `flags` が妥当 / `timestamp` が現在時刻に近いことを確認する。
7. Control に `02`（STOP）を write し、notify が止まり Status が Idle になることを確認する。
8. Control に `10`（未知 opcode）を write し、Status の `last_error = 1` になることを確認する。

### 4.3 iPad Receiver との結合

1. iPad で `p2pgps-ios` の Receiver を起動し、スキャンを開始する。
2. Android から `P2PGPS-ANDROID-XXXX` に接続し、`START` が書き込まれることを確認する。
3. iPad の地図・リードアウトに座標が反映されることを確認する。
4. 画面 OFF にしても送信が継続することを確認する（数分放置）。
5. 通知の「停止」で停止できることを確認する。

### 4.4 デバッグ用シミュレート位置

1. 「デバッグ」セクションに緯度・経度を入力し、「シミュレートを開始」を押す。
2. リードアウトプレートに「シミュレート位置を送信中」と表示され、`flags` の SIMULATED ビットが立つことを確認する。
3. 「解除」で実測に戻ることを確認する。

## 5. 既知の未検証事項

- BLE ペリフェラルの実機動作（アドバタイズ・接続・CCCD・notify・MTU）
- 前景サービスの画面 OFF 継続（メーカー省電力設定の影響を含む）
- 端末名の一時変更と復元（`BluetoothAdapter.setName`）
- Compose 画面のレンダリング（ダークテーマ・Dynamic Type・Reduce Motion）
- iPad Receiver との結合動作
