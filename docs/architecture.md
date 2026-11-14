# アーキテクチャ（Android 送信側）

最終更新: 2026-11-15

## 1. 全体像

Android 送信側は「位置を取得する」「BLE ペリフェラルとして通知する」「画面 OFF でも動かす」の 3 つを担います。

```text
LocationManager (GPS + NETWORK)
        │  Location
        ▼
LocationProvider ──► LocationFilter（精度・外れ値除去）
        │  GpsFix
        ▼
GpsSenderService（前景サービス）
        │  LocationPacket / ExtendedPacket / StatusPacket
        ▼
GattServerManager（BluetoothLeAdvertiser + BluetoothGattServer）
        │  notify
        ▼
iPad（p2pgps-ios Receiver）
        │  Control (START / STOP / SET_INTERVAL / ...)
        ▼
GpsSenderService（コマンド処理）
```

- プロトコルの正本は `p2pgps-protocol` の `spec/protocol-v1.md`。本リポジトリの `protocol/` はその Android 実装です。
- 画面（Compose）は前景サービスの状態を `StateFlow` 経由で購読するだけです。BLE や位置取得のロジックは画面に置きません。

## 2. パッケージ構成

```text
app/src/main/java/jp/haruki0420/p2pgps/
├── protocol/   純 Kotlin（Android 非依存）
│   ├── GattUuids.kt        UUID 定数（仕様 §1）
│   ├── Bytes.kt            リトルエンディアンの読み書き（internal）
│   ├── LocationPacket.kt   Location Packet（20 bytes、仕様 §3）
│   ├── ExtendedPacket.kt   Extended Location Packet（8 bytes、仕様 §4）
│   ├── ControlCommand.kt   Control characteristic（仕様 §5）
│   ├── StatusPacket.kt     Status characteristic（仕様 §6）
│   ├── SequenceTracker.kt  sequence 巻き戻り検出（仕様 §3.3-6）
│   └── Hex.kt              ログ用 hex 表示
├── ble/
│   ├── GattServerManager.kt  BLE ペリフェラル本体
│   └── BleState.kt           アドバタイズ・接続・購読・MTU の状態
├── location/
│   ├── LocationProvider.kt   LocationManager（GPS + NETWORK）
│   ├── LocationFilter.kt     精度・外れ値除去（純 Kotlin）
│   ├── GpsFix.kt             フィルタ済みのドメインモデル
│   └── Geo.kt                Haversine 距離（純 Kotlin）
├── service/
│   ├── GpsSenderService.kt   前景サービス本体
│   ├── NotificationHelper.kt 常駐通知・通知チャンネル
│   └── SenderController.kt   画面からの操作ファサード
├── core/
│   ├── AppLog.kt             アプリ内ログ（直近 200 件）+ Logcat
│   ├── BatteryStatus.kt      バッテリー残量・充電状態
│   └── SenderState.kt        画面表示用の共有状態
└── ui/
    ├── MainActivity.kt       エントリポイント・権限要求
    ├── SenderScreen.kt       Compose 画面
    ├── Permissions.kt        権限のバージョン分岐
    └── theme/Theme.kt        配色
```

## 3. プロトコル層（`protocol/`）

- **純 Kotlin**。`java.util.UUID` 以外の Android API を使わないため、JVM ユニットテストで完全に検証できます。
- `LocationPacket.decode()` は「長さ 20 bytes」「version == 1」だけを検証し、それ以外の受信側検証（予約ビット・座標範囲・Null Island）は `validationError()` で行います。仕様 §3.3 の 1〜5 を分担する設計です。
- `ControlCommand.decode()` は未知 opcode を `StatusError.UNKNOWN_OPCODE`、不正長を `StatusError.BAD_LENGTH` として返します。`START` / `SET_INTERVAL` の interval は仕様 §5.1 に従い `clampInterval()` で正規化します（0→1000、1–99→100、100–60000→そのまま、60001–65535→60000）。
- `LocationPacket.encodeCoordinate()` は Python 参照実装の `ROUND_HALF_UP` に合わせ、.5 を 0 から遠ざかる方向へ丸めます。
- `SequenceTracker` は受信側のためのものですが、仕様のテストベクタ（sequence 巻き戻り）を JVM で検証するために本リポジトリにも実装しています。

## 4. BLE 層（`ble/`）

`GattServerManager` は 1 つの GATT サーバーとアドバタイザを保持します。

- **サービス構成**: P2P GPS Service に Location / Control / Status / Extended の 4 characteristic。加えて任意の Device Information Service（Manufacturer=`P2PGPS`, Model=`Android`, Firmware=`BuildConfig.VERSION_NAME`）。
- **CCCD 購読管理**: characteristic ごとに購読中デバイスの集合を持ち、`onDescriptorWriteRequest` で更新します。notify は購読中のデバイスにだけ送ります。
- **read**: 最後に notify した値を返します。未送信時は flags=0 / state=0 の「無効」値です（仕様 §1.1）。
- **Control write**: `write` / `write-without-response` の両方を受け、`ControlCommand.decode()` の結果をサービスへコールバックします。offset != 0 の書き込みは `GATT_INVALID_OFFSET` で拒否します。デコード失敗時は GATT 応答自体は成功を返し、`last_error` で通知します（仕様 §5）。
- **MTU**: `onMtuChanged` で交渉結果を保持します。20 bytes 固定のため MTU 23 でも分割不要です。
- **アドバタイズ**: メインの AdvertiseData に Service UUID、Scan Response にローカルネームを載せます。`AdvertiseSettings` は connectable・timeout 0・BALANCED モードです。開始失敗時はアドバタイザをリセットし、端末名を復元してから再試行できます。
- **端末名**: Android には任意のローカルネームをアドバタイズする API がないため、`BluetoothAdapter.setName("P2PGPS-ANDROID-XXXX")` を送信中だけ適用し、停止時に元の名前へ戻します。変更前の名前は未保存のときだけ捕捉し、失敗して再試行しても二重変更しません。

### スレッドモデル

- BLE コールバックは Binder スレッドから呼ばれます。
- 購読者集合・接続中デバイス集合・最後に送った値は `synchronized(lock)` で保護します。notify はロック内でスナップショットを取ってからロック外で送信します。
- `GattServerManager` の公開状態は `MutableStateFlow`（スレッドセーフ）で共有します。
- サービスは `Dispatchers.Main.immediate` の `CoroutineScope` を持ち、Control コマンドの適用・送信ループ・状態公開をメインスレッドに直列化します。

## 5. 位置層（`location/`）

- `LocationProvider` は `LocationManager` の `GPS_PROVIDER` と `NETWORK_PROVIDER` を購読します。Google Play Services には依存しません。
- FINE 権限が無い場合は GPS_PROVIDER を要求せず NETWORK_PROVIDER のみ購読します（coarse のみの付与で SecurityException になり、NETWORK まで購読できなくなるのを避けるため）。provider 単位で例外を処理し、flow 全体は閉じません。
- 送信開始には FINE 権限を必須とし、coarse のみの場合は画面に「正確な位置情報が必要です」と表示します。
- `LocationFilter` は「精度が 100m より悪い fix」「直前の採用点から 1km 超かつ 10 秒未満の移動」を外れ値として捨てます。どちらも純 Kotlin で JVM テスト可能です。拒否理由に座標値は含めません（ログのプライバシー方針）。
- 位置更新の Flow は `callbackFlow` で実装し、collect が止まると `removeUpdates` します。

## 6. サービス層（`service/`）

`GpsSenderService` は前景サービスとして次を行います。

1. `onCreate` で通知チャンネル・GATT サーバー・位置プロバイダを初期化し、BLE 状態の購読と 10 秒ハートビートを開始。
2. `onStartCommand` で必ず `ServiceCompat.startForeground()` を呼び、`connectedDevice|location` タイプで前景化。
3. `START` で GATT サーバー開始・アドバタイズ開始・位置購読開始・送信ループ開始。
4. Control コマンドに応じて状態遷移（`START` / `STOP` / `SET_INTERVAL` / `REQUEST_FIX` / `SET_EXTENDED` / `RESET`）。`RESET` は既定設定（interval=1000 / extended=off / sequence=0）に戻しますが、ストリーミング状態は変更しません（仕様 §5）。
5. 送信ループは `intervalMs` ごとに最新 fix から `LocationPacket` を組み立てて notify。Extended 有効時は `ExtendedPacket` も notify。Status は状態変化時と 10 秒ごとに notify。通知の更新は 2 秒に 1 回へ間引き、状態変化時は即時反映する。
6. 購読者がいない間は送信をスキップし、Status を `Idle` として通知（仕様 §7）。
7. `onDestroy` で送信ループ・位置購読・GATT サーバー・通知を解放。

### 状態管理

- 画面表示用の状態は `core/SenderStateHolder`（`StateFlow<SenderState>`）に集約。
- `SenderState` はサービス稼働・ストリーミング・BLE 状態・現在地・送信レート・バッテリー・エラーを含む。
- 画面（`MainActivity` / `SenderScreen`）は `collectAsStateWithLifecycle()` で購読するだけ。

### エラー処理

| 状況 | 挙動 |
| :--- | :--- |
| Bluetooth OFF / 権限不足 | 前景サービスは起動したままメッセージを画面に表示 |
| 測位できない | `last_error = LOCATION_UNAVAILABLE`、Status は `Error`。測位でき次第 `none` に戻る |
| 未知 opcode / 不正長 | `last_error = UNKNOWN_OPCODE` / `BAD_LENGTH` を Status に設定。次の成功コマンドでクリア |
| アドバタイズ失敗 | `BleState.lastError` にメッセージを表示し、端末名を復元して再試行可能 |

## 7. UI 層（`ui/`）

- `MainActivity` が `enableEdgeToEdge()` と権限ランチャーを担当。`ON_RESUME` で権限状態を再評価する。
- `SenderScreen` は 1 画面。視覚世界「測量標（benchmark）」に従い、カードを並べず 1px のヘアラインと余白で構造化する。
  - 上部: ステータスレール（LED + 状態テキスト（接続済み / 接続待ち / エラー / 未接続）・ローカルネーム・送信間隔）
  - リードアウトプレート: 緯度・経度（mono、小数 7 桁、画面で最も大きい数字）・精度・fix 品質バー
  - 中央: 状態（送信中 / 受信側の接続待ち / 待機中 / 停止中）と BLE の 3 状態（アドバタイズ中 / 接続 N 台 / 購読 N 台）
  - 下部: 送信レート・権限・デバッグ・ログの各セクション
  - 最下部固定: 52dp の主ボタン「送信を開始」「送信を停止」
- テーマは `ui/theme/Theme.kt`。`P2PColors`（paper / surface / ink / line / pine / live / amber / danger）を CompositionLocal で提供し、MaterialTheme の colorScheme へマップする。座標・数値は Roboto Mono（`FontFamily.Monospace`）で tabular。主ボタン・選択ボタンの文字色は `onPrimary` を使う（ダークの pine は白文字だとコントラスト不足のため）。
- 座標は TalkBack で「北緯 …、東経 …」と読み上げる（`clearAndSetSemantics`）。文字サイズが大きいときはラベルと値を縦積みにする。
- IME 表示時は `imePadding()` でデバッグ入力を隠さない。
- モーションは fix 到着時の座標 120ms クロスフェードと主ボタン押下の 0.98 スケールのみ。Reduce Motion（`Settings.Global.ANIMATOR_DURATION_SCALE == 0`）で無効化する（設定変更は画面再生成まで反映されない）。
- 権限は Android 12（Bluetooth）と Android 13（通知）で要求内容を切り替え（`Permissions.required()`）。
- 送信開始に必須なのは「位置情報 + Bluetooth」のみ。通知が拒否されても送信は継続できる。
- デザインの正本はリポジトリ直下の `DESIGN.md` / `PRODUCT.md`、実装メモは [`docs/design.md`](design.md)。

## 8. テスト戦略

| 層 | 方法 |
| :--- | :--- |
| protocol | JUnit4。仕様 §10 のテストベクタを `ProtocolCodecTest` で検証。加えて `ProtocolVectorTest` が `p2pgps-protocol` の共有 JSON（手動ミラー）を読んで突き合わせる |
| location | JUnit4。`LocationFilterTest` で精度・外れ値・距離計算を検証 |
| ble / service / ui | 実機のみ。CI ではコンパイル（`assembleDebug`）と lint までを保証 |

## 9. 既知の設計上の割り切り

- 端末名の一時変更（前述）は Android の API 制約によるものです。強制終了時は端末設定から手動で戻す必要があります。
- v1 は 1 対 1 接続を想定していますが、GATT サーバー自体は複数セントラルの購読を保持できます（v2 への布石）。
- 位置情報のバックグラウンド取得は前景サービスに依存します。OS の省電力制御により更新間隔が延びる場合があります。
- Reduce Motion の設定変更は画面再生成まで反映されません（`remember` で初回のみ評価）。
