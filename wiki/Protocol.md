# Protocol

BLE GATT プロトコル v1 の要点です。正本は [`p2pgps-protocol`](https://github.com/haruki0420/p2pgps-protocol) の `spec/protocol-v1.md`。

## UUID

| 役割 | UUID |
| :--- | :--- |
| P2P GPS Service | `B1E5A000-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Location Packet（read / notify） | `B1E5A001-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Control（write / write-without-response） | `B1E5A002-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Status（read / notify） | `B1E5A003-9C3D-4E7A-8F20-1A2B3C4D5E6F` |
| Extended Location（read / notify） | `B1E5A004-9C3D-4E7A-8F20-1A2B3C4D5E6F` |

## Location Packet（20 bytes、リトルエンディアン）

| Offset | Size | Field | 説明 |
| ---: | ---: | :--- | :--- |
| 0 | 1 | `version` | `0x01` |
| 1 | 1 | `flags` | bit0 VALID_FIX, bit1 ALTITUDE, bit2 SPEED, bit3 SIMULATED, bit4 CHARGING, bit5 LOW_BATTERY, bit6 MOVING |
| 2 | 2 | `sequence` | 送信ごとに +1、65535 の次は 0 |
| 4 | 4 | `latitude` | int32、緯度 × 10^7 |
| 8 | 4 | `longitude` | int32、経度 × 10^7 |
| 12 | 2 | `altitude_m` | int16、無効時 0 |
| 14 | 1 | `accuracy_m` | uint8、255 = 不明 |
| 15 | 1 | `speed_mps` | uint8、255 = 不明 |
| 16 | 4 | `timestamp` | uint32、Unix エポック秒 |

- 20 bytes 固定なので ATT MTU 23 でもフラグメンテーション不要です。
- 座標の検証範囲は `lat ±900_000_000` / `lon ±1_800_000_000`（deg × 10^7）。
- `VALID_FIX` かつ (0, 0) 近傍（±0.0001°）は Null Island として無効扱いです。

## Control（受信側 → 送信側）

| opcode | 名称 | payload |
| ---: | :--- | :--- |
| `0x01` | START | uint16 LE interval_ms（0 = 1000） |
| `0x02` | STOP | なし |
| `0x03` | SET_INTERVAL | uint16 LE interval_ms |
| `0x04` | REQUEST_FIX | なし |
| `0x05` | SET_EXTENDED | uint8 enabled |
| `0x7F` | RESET | なし |

- interval は仕様 §5.1 に従って正規化します（0→1000、1–99→100、100–60000→そのまま、60001–65535→60000）。Control の書き込み `hex` は生値で、Status の `interval_ms` は正規化後の値です。Android は decode 時に正規化した値を返します。
- `RESET` は既定設定（interval=1000 / extended=off / sequence=0）に戻しますが、ストリーミング状態は変更しません（停止は `STOP`）。
- 未知 opcode は無視して `last_error = 1`、不正長は `last_error = 2`。

## Status（8 bytes）

| Offset | Size | Field |
| ---: | ---: | :--- |
| 0 | 1 | `version`（0x01） |
| 1 | 1 | `state`（0=Idle, 1=Streaming, 2=Paused, 3=Error） |
| 2 | 2 | `interval_ms` |
| 4 | 1 | `battery_pct`（255 = 不明） |
| 5 | 1 | `status_flags`（bit0 extended, bit1 fix, bit2 charging, bit3 low battery） |
| 6 | 1 | `last_error`（0=none, 1=unknown opcode, 2=bad length, 3=location unavailable, 4=internal） |
| 7 | 1 | reserved |

## シーケンス

1. Sender がアドバタイズ（Service UUID + ローカルネーム）。
2. Receiver が接続し、Status を read。
3. Receiver が `START(1000)` を書き込む。
4. Sender が Status → Location（1 Hz）→ Extended（有効時）を notify。
5. Receiver が `STOP` を書き込むか切断すると Idle に戻る。

## Extended Location（8 bytes、任意）

`version`(1) / `provider`(1) / `battery_pct`(1) / `satellites`(1) / `course`(2, ×100) / `vertical_accuracy_m`(1) / reserved(1)。

## セキュリティ

v1 は認証・暗号化なし（Just Works 相当）です。近距離の第三者が偽の位置を送れるため、受信側は「許可した送信元のみ自動接続」「注入は既定 OFF」「注入中は常時バナー」を必須としています。位置情報はクラウドへ送りません。
