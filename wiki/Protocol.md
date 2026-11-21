# プロトコル実装メモ（BLE プロトコル v1）

最終更新: 2026-11-15

正本は `p2pgps-protocol` の `spec/protocol-v1.md` です。本ドキュメントは Android 送信側の実装が仕様のどこに対応するかを示すメモであり、仕様そのものではありません。

## 1. UUID

| 役割 | UUID | 実装 |
| :--- | :--- | :--- |
| P2P GPS Service | `B1E5A000-9C3D-4E7A-8F20-1A2B3C4D5E6F` | `GattUuids.SERVICE` |
| Location Packet | `B1E5A001-9C3D-4E7A-8F20-1A2B3C4D5E6F` | `GattUuids.LOCATION` |
| Control | `B1E5A002-9C3D-4E7A-8F20-1A2B3C4D5E6F` | `GattUuids.CONTROL` |
| Status | `B1E5A003-9C3D-4E7A-8F20-1A2B3C4D5E6F` | `GattUuids.STATUS` |
| Extended Location | `B1E5A004-9C3D-4E7A-8F20-1A2B3C4D5E6F` | `GattUuids.EXTENDED` |
| CCCD | `00002902-0000-1000-8000-00805F9B34FB` | `GattUuids.CCCD` |
| Device Information Service | `0000180A-0000-1000-8000-00805F9B34FB` | `GattUuids.DEVICE_INFORMATION_SERVICE` |

すべての値は `protocol/GattUuids.kt` に定義し、`ProtocolCodecTest.gattUuids_matchSpec` で文字列表現を検証しています。

## 2. Location Packet（20 bytes）

| Offset | Size | Field | 型 | 実装 |
| ---: | ---: | :--- | :--- | :--- |
| 0 | 1 | `version` | uint8 | 常に `0x01` |
| 1 | 1 | `flags` | uint8 | `FLAG_*` 定数 |
| 2 | 2 | `sequence` | uint16 LE | 送信ごとに +1、65535 の次は 0 |
| 4 | 4 | `latitude` | int32 LE | `encodeCoordinate(deg)` = round(deg × 10^7) |
| 8 | 4 | `longitude` | int32 LE | 同上 |
| 12 | 2 | `altitude_m` | int16 LE | 切り捨て、無効時 0 |
| 14 | 1 | `accuracy_m` | uint8 | 255 = 不明 |
| 15 | 1 | `speed_mps` | uint8 | 255 = 不明 |
| 16 | 4 | `timestamp` | uint32 LE | Unix エポック秒（UTC） |

### flags

| bit | 名称 | 実装 |
| ---: | :--- | :--- |
| 0 | `VALID_FIX` | `FLAG_VALID_FIX` |
| 1 | `ALTITUDE_VALID` | `FLAG_ALTITUDE_VALID` |
| 2 | `SPEED_VALID` | `FLAG_SPEED_VALID` |
| 3 | `SIMULATED` | `FLAG_SIMULATED`（デバッグ用シミュレート位置） |
| 4 | `CHARGING` | `FLAG_CHARGING` |
| 5 | `LOW_BATTERY` | `FLAG_LOW_BATTERY`（20% 未満） |
| 6 | `MOVING` | `FLAG_MOVING`（0.5 m/s 以上） |
| 7 | reserved | 常に 0。受信側検証で破棄対象 |

### 受信側検証（仕様 §3.3）の実装

`LocationPacket.decode()` が 1〜2、`LocationPacket.validationError()` が 3〜5 を担当します。

| 仕様 | 実装 |
| :--- | :--- |
| 1. 長さがちょうど 20 bytes | `decode()` が `null` |
| 2. `version == 0x01` | `decode()` が `null` |
| 3. `flags & 0x80 == 0` | `ValidationError.RESERVED_FLAG_SET` |
| 4. 緯度 ±90° / 経度 ±180° | `ValidationError.LATITUDE_OUT_OF_RANGE` / `LONGITUDE_OUT_OF_RANGE` |
| 5. VALID_FIX 時の Null Island（±0.0001°） | `ValidationError.NULL_ISLAND` |
| 6. sequence 巻き戻りは受理してログのみ | `SequenceTracker.observe()` が `rollback = true` を返す（パケットは受理） |
| 7. timestamp が ±24 時間超で stale 警告 | `isStale(nowEpochSeconds)` |

座標は `deg × 10^7` で保持するため、検証範囲は次のスカラー値になります（仕様 §3.3-4）。

| フィールド | 範囲（deg） | 範囲（スケール済み int32） |
| :--- | :--- | :--- |
| `latitude` | -90.0 〜 +90.0 | `-900_000_000` 〜 `+900_000_000` |
| `longitude` | -180.0 〜 +180.0 | `-1_800_000_000` 〜 `+1_800_000_000` |

## 3. Extended Location Packet（8 bytes）

| Offset | Size | Field | 型 |
| ---: | ---: | :--- | :--- |
| 0 | 1 | `version` | uint8（0x01） |
| 1 | 1 | `provider` | uint8（0=unknown, 1=GPS, 2=Network, 3=Fused, 4=Manual/Simulated） |
| 2 | 1 | `battery_pct` | uint8（255 = 不明） |
| 3 | 1 | `satellites` | uint8（255 = 不明） |
| 4 | 2 | `course` | uint16 LE（× 100、65535 = 不明） |
| 6 | 1 | `vertical_accuracy_m` | uint8（255 = 不明） |
| 7 | 1 | reserved | uint8（0） |

- 本アプリは `provider` に `LocationManager` の provider 名（`gps` / `network`）またはシミュレート（`MANUAL`）を設定します。
- `satellites` は `LocationManager` から取得しないため常に 255（不明）です。
- `course` は `Location.getBearing()` から `encodeCourse()` で 0〜35999 に正規化します。

## 4. Control characteristic（受信側 → 送信側）

| opcode | 名称 | payload | 実装 |
| ---: | :--- | :--- | :--- |
| `0x01` | `START` | uint16 LE `interval_ms`（0 = 1000） | `ControlCommand.Start` |
| `0x02` | `STOP` | なし | `ControlCommand.Stop` |
| `0x03` | `SET_INTERVAL` | uint16 LE `interval_ms` | `ControlCommand.SetInterval` |
| `0x04` | `REQUEST_FIX` | なし | `ControlCommand.RequestFix` |
| `0x05` | `SET_EXTENDED` | uint8 `enabled` | `ControlCommand.SetExtended` |
| `0x7F` | `RESET` | なし | `ControlCommand.Reset` |

- 未知 opcode（`0x10` の AUTH 予約を含む）→ `ControlDecodeResult.Failure(UNKNOWN_OPCODE)`
- 不正長 → `ControlDecodeResult.Failure(BAD_LENGTH)`
- 書き込み `hex` は**生値**。送信側は仕様 §5.1 に従って正規化した値を使う（`normalized_interval_ms`）。

### 4.1 `interval_ms` の正規化（仕様 §5.1）

| 生値 | 正規化後 | 実装 |
| ---: | ---: | :--- |
| `0` | `1000` | `clampInterval(0)` |
| `1`–`99` | `100`（下限クランプ） | `clampInterval(1..99)` |
| `100`–`60000` | そのまま | `clampInterval(100..60000)` |
| `60001`–`65535` | `60000`（上限クランプ） | `clampInterval(60001..65535)` |

- Status の `interval_ms` は正規化後の値を通知します（仕様 §6）。
- 共有テストベクタ `control.json` は `hex`（生値）と `normalized_interval_ms`（適用値）を別フィールドで持ちます。
- **Android 実装は `ControlCommand.decode()` の時点で正規化を適用した値**（`Start.intervalMs` / `SetInterval.intervalMs`）を返します。Python / iOS は生値を保持し、適用時に正規化します。挙動は同じですが API が返す値が異なる点に注意してください（生値は書き込みバイト列の offset 1 の uint16 LE）。

`RESET` は既定設定（interval=1000 / extended=off / sequence=0）に戻しますが、**ストリーミング状態は変更しません**（仕様 §5）。停止したい場合は `STOP` を使用します。

## 5. Status characteristic（8 bytes）

| Offset | Size | Field | 型 |
| ---: | ---: | :--- | :--- |
| 0 | 1 | `version` | uint8（0x01） |
| 1 | 1 | `state` | uint8（0=Idle, 1=Streaming, 2=Paused, 3=Error） |
| 2 | 2 | `interval_ms` | uint16 LE |
| 4 | 1 | `battery_pct` | uint8（255 = 不明） |
| 5 | 1 | `status_flags` | uint8（bit0 extended, bit1 fix, bit2 charging, bit3 low battery） |
| 6 | 1 | `last_error` | uint8（0=none, 1=unknown opcode, 2=bad length, 3=location unavailable, 4=internal） |
| 7 | 1 | reserved | uint8（0） |

- 状態変化時と 10 秒ごとのハートビートで notify します。
- 購読者がいない間は `state = Idle` を通知します（仕様 §7）。
- `Paused` は v1 では使用しません（受信側からの STOP は Idle として扱う）。

## 6. テストベクタ（仕様 §10）

### 6.1 ハンドコードされたテスト

`app/src/test/java/jp/haruki0420/p2pgps/protocol/ProtocolCodecTest.kt` が仕様 §10 のベクタを個別に検証します。

| ベクタ | テスト |
| :--- | :--- |
| 東京駅（有効 fix、altitude/speed 有効） | `tokyoStation_encodesToNormativeBytes`, `tokyoStation_roundTrips` |
| 南極（負の緯度・経度、altitude 負） | `antarctica_negativeCoordinatesAndAltitude` |
| Null Island（無効扱い） | `nullIsland_isRejectedWhenValidFix`, `nullIslandThreshold_*` |
| 範囲外緯度（破棄） | `outOfRangeCoordinates_areRejected` |
| 未知バージョン（破棄） | `decode_rejectsUnknownVersion` |
| sequence 巻き戻り | `sequenceTracker_detectsRollbackButPacketIsStillAccepted` |
| Extended（バッテリー・プロバイダ・コース） | `extendedPacket_*`, `encodeCourse_*`, `providerType_*` |
| Control 各 opcode | `controlCommands_encode`, `controlCommands_decodeAllOpcodes`, `controlCommand_*` |
| Status | `statusPacket_*`, `statusCodes_matchSpec` |
| 座標の丸め | `encodeCoordinate_roundsHalfAwayFromZero` |

### 6.2 共有テストベクタとの突き合わせ

`p2pgps-protocol/tests/vectors/*.json` を `app/src/test/resources/vectors/` に**手動ミラー**し、
`ProtocolVectorTest.kt` が全ベクタ（location 13 / extended 5 / status 5 / control 22 / sequences 5）を
読み込んで encode / decode / validate を検証します。

- ミラー元と同期方法は [`app/src/test/resources/vectors/README.md`](https://github.com/haruki0420/p2pgps-android/blob/main/app/src/test/resources/vectors/README.md) を参照。
- CI では `p2pgps-protocol` を checkout しないため自動同期ではありません。正本が更新されたら手動でコピーし直します。
- Control は `hex`（生値）と `normalized_interval_ms`（仕様 §5.1 の適用値）を突き合わせます。

東京駅の hex は仕様 §3.2 のエンコード例と一致します。

```text
01 03 2A 00 48 86 44 15 52 C5 4E 53 03 00 05 00 00 52 17 69
```

## 7. 仕様との対応で注意した点

- **バイトオーダー**: すべてリトルエンディアン。`Bytes.kt` で明示的に組み立て、`ByteBuffer` の既定（ビッグエンディアン）には依存しません。
- **符号付き整数**: 2 の補数。`putI32Le` / `putI16Le` は `ushr` と `and 0xFFFF` で下位ビットを落とさずに書き込みます。
- **uint32 timestamp**: Kotlin の `Long` で保持し、0〜4294967295 の範囲を検証します。
- **座標の丸め**: Python 参照実装の `ROUND_HALF_UP` に合わせ、.5 は 0 から遠ざかる方向へ丸めます（`encodeCoordinate`）。
- **20 bytes 固定**: MTU 23 でもフラグメンテーション不要。read は offset 0 のみ対応し、それ以外は `GATT_INVALID_OFFSET` を返します。
- **未送信時の read 値**: flags=0 / state=0 の「無効」値（仕様 §1.1）。
- **アドバタイズ**: Service UUID を AdvertiseData、ローカルネームを Scan Response に載せます。
