# Architecture

## モジュール構成

```text
app/src/main/java/jp/haruki0420/p2pgps/
├── protocol/   純 Kotlin（Android 非依存、JVM テスト対象）
│   ├── GattUuids.kt / Bytes.kt / Hex.kt
│   ├── LocationPacket.kt（20 bytes、検証）
│   ├── ExtendedPacket.kt（8 bytes）
│   ├── ControlCommand.kt（START/STOP/...）
│   ├── StatusPacket.kt（8 bytes）
│   └── SequenceTracker.kt
├── ble/        BluetoothLeAdvertiser + BluetoothGattServer
├── location/   LocationManager（GPS + NETWORK）+ LocationFilter
├── service/    前景サービス・通知・画面からの操作
├── core/       ログ・バッテリー・共有状態
└── ui/         MainActivity + Compose 画面・権限・テーマ
```

## データフロー

```text
LocationManager ──► LocationProvider ──► LocationFilter ──► GpsSenderService
                                                                   │
                                          LocationPacket / ExtendedPacket / StatusPacket
                                                                   ▼
                                              GattServerManager（notify）
                                                                   │
                                                                   ▼
                                                    iPad Receiver（p2pgps-ios）
                                                                   │
                                                    Control write（START など）
                                                                   ▼
                                                    GpsSenderService（状態遷移）
```

## スレッドモデル

- BLE コールバックは Binder スレッド。
- `GattServerManager` の状態は `MutableStateFlow` で公開（スレッドセーフ）。
- サービスは `Dispatchers.Main.immediate` の `CoroutineScope` で Control コマンド適用・送信ループ・状態公開を直列化。
- 画面は `StateFlow` を `collectAsState()` で購読するだけ。

## 前景サービス

- タイプ: `connectedDevice|location`（Android 14 以降の要件に対応）。
- 通知: 現在座標・送信レート・接続状態・「停止」ボタン。ロック画面では内容を隠す（`VISIBILITY_PRIVATE`）。
- 購読者がいない間は送信をスキップし、Status は Idle。
- 状態変化時と 10 秒ごとに Status を notify。

## BLE の設計

- サービス: P2P GPS Service + Device Information Service（任意）。
- characteristic: Location / Control / Status / Extended。read は最後に送った値、notify は CCCD 購読中のみ。
- アドバタイズ: Service UUID をメイン、ローカルネームを Scan Response に配置。
- 端末名: Android の API 制約により、送信中だけ `BluetoothAdapter.setName("P2PGPS-ANDROID-XXXX")` を適用し、停止時に元へ戻す。

## テスト

- `protocol/` と `location/` は純 Kotlin のため JVM で検証（仕様 §10 のテストベクタ）。
- BLE・前景サービス・UI は実機のみ。CI は `assembleDebug` と `testDebugUnitTest` を実行。

詳細はリポジトリの [`docs/architecture.md`](https://github.com/haruki0420/p2pgps-android/blob/main/docs/architecture.md) を参照してください。
