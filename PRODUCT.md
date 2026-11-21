# Product — p2pgps-android

> プロジェクト共通の正本はハブ Wiki の [Product](https://github.com/haruki0420/p2pgps/wiki/Product)。
> 本ファイルは Android 送信側に固有の差分（対象ユーザー・スコープ・制約）だけを記録します。
> 矛盾がある場合はハブ Wiki を優先します。

## Platform

`adaptive` のうち **Android（Jetpack Compose）**。iPadOS / iOS は別リポジトリ（`p2pgps-ios`）が担当します。

## Stack

- Kotlin 2.x + Jetpack Compose（Material 3）
- `BluetoothLeAdvertiser` + `BluetoothGattServer`（BLE ペリフェラル）
- `LocationManager`（GPS + NETWORK、Google Play Services 非依存）
- Gradle / GitHub Actions（Ubuntu + JDK 21）

## Users

- **本人（第一利用者）**: Mac を持たず、iPad の位置を Android から合わせたい技術志向のユーザー。位置情報ゲーム・位置依存アプリの検証・デモが目的。
- **アプリ開発者・QA**: 任意の座標を iPad に流し込み、位置依存機能を再現テストしたい人。

## Product Purpose

Android が取得した GPS 位置を BLE GATT で iPad へ転送し、iPad 上で「その位置にいる」ように位置情報を反映する。クラウドを介さない P2P。成功とは「送信側で開始し、受信側で 1 回接続し、注入を ON にすれば、以降は放置で位置が追従している」こと。

## Positioning

- 近距離・1 対 1・アカウント不要・クラウドなし。座標は端末外に出さない。
- Google Play Services に依存しない（`LocationManager` + 標準 BLE API のみ）。
- 画面 OFF でも送信を継続する前景サービス内蔵。

## Operating Context

- Sender は屋外・移動中。画面 OFF / バックグラウンドが多い。バッテリー制約あり。
- 日本語 UI。技術用語（BLE / GATT / MTU）はそのまま提示し、短い説明を添える。

## Capabilities and Constraints

- できること: 送信 ON/OFF、BLE ペリフェラル（Location / Control / Status / Extended）、送信レート変更、現在地表示、権限の可視化、ログ、デバッグ用シミュレート位置。
- 制約: 実機なしでは BLE を検証できない（CI はコンパイル + JVM テストのみ）。v1 は同時 1 台接続・ボンディングなし・認証なし。位置情報のバックグラウンド取得は前景サービスに依存。
- ライセンス: MIT。

## Brand Commitments

- 名称: **P2P GPS**（`p2pgps`）。日本語 UI。
- ネイティブ作法（Material 3）を守る。web 風 UI はしない。
- 派手さより「状態が一目で分かること」。ただしシステム既定のままの無個性な画面にはしない（`DESIGN.md` の視覚世界に従う）。

## Product Principles

1. **セットアップの詰まりを可視化する。** 何が足りないかを画面が名指しする。
2. **送信側は「置いておけば動く」。** 画面 OFF でも継続し、通知から停止できる。
3. **計器のように正確に読める。** 座標・精度・送信間隔は一目で比較できる。
4. **ローカル完結。** 座標をクラウドに出さない。ログも座標そのものは流さない。

## Accessibility & Inclusion

- システムフォントスケール（Dynamic Type 相当）に追従。
- タップ領域 44×44pt 以上（主ボタン 52dp、選択ボタン 48dp）。
- 色だけで状態を伝えない（アイコン + テキスト + 形の併用）。
- Reduce Motion（`Settings.Global.ANIMATOR_DURATION_SCALE == 0`）を尊重。
- 日本語を既定とし、英語併記はしない（v1）。
