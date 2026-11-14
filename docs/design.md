# Android デザイン実装メモ

最終更新: 2026-11-15

正本はリポジトリ直下の [`DESIGN.md`](../DESIGN.md)（プロジェクト共通の視覚世界・トークン・レイアウト・モーション・コピー）と [`PRODUCT.md`](../PRODUCT.md) です。本ドキュメントは、その Android 側の実装（`ui/theme/Theme.kt` と `ui/SenderScreen.kt`）への落とし込みを記録します。

## 1. 視覚世界

「測量標（benchmark）」— 地図と座標が主役の計測器。装飾ではなく読むための画面。

- 画面の主役は**座標の数字**。UI は黒子に徹する。
- 構造は **1px のヘアラインと余白**。影・グラデーション・カードの山は使わない。
- 記憶に残る要素は 1 つだけ: **リードアウトプレート**（座標パネル）。
- 色は paper / ink / pine / live の 4 系統に絞る。

## 2. カラートークン

`ui/theme/Theme.kt` の `P2PColors`（`P2PTheme.colors` でアクセス）に定義。

| token | Light | Dark | 用途 |
| :--- | :--- | :--- | :--- |
| `paper` | `#F2F4F2` | `#0F1A1E` | 画面背景 |
| `surface` | `#FBFCFB` | `#16252A` | リードアウトプレート |
| `ink` | `#17252A` | `#E8EEEC` | 本文・座標 |
| `inkSecondary` | `#55666A` | `#9FB0B2` | ラベル・補助 |
| `line` | `#C9D3CE` | `#2A3B40` | ヘアライン |
| `pine` | `#1E6B5A` | `#6FBFA8` | 主アクション・接続済み |
| `live` | `#A8502E` | `#E08A5F` | ライブ状態のテキスト |
| `liveFill` | `#B85C38` | `#C96A42` | ライブの塗り |
| `amber` | `#8A5A12` | `#E0B25C` | 警告・接続待ち |
| `danger` | `#A32E24` | `#E0705F` | エラー |
| `mist` | `#E7ECE8` | `#22333A` | paper と line の中間（Material の surfaceVariant 用に導出） |

- `MaterialTheme` の `colorScheme` へマップする（`primary = pine`、`background = paper`、`surface = surface`、`outline = line`、`error = danger`、`tertiary = amber`）。
- 既定の青 tint は使わない。ブランド色は `pine`。
- 状態色は必ず**アイコン + テキスト + 形**と併用する（色だけに依存しない）。

## 3. タイポグラフィ

カスタムフォントは使わない。`P2PType`（`ui/theme/Theme.kt`）で定義。

| 役割 | 実装 |
| :--- | :--- |
| 画面タイトル・状態 | `MaterialTheme.typography.titleLarge`（Roboto） |
| 本文・ラベル | `bodyMedium` / `labelMedium` |
| **座標** | `P2PType.coordinate` = `titleMedium` + `FontFamily.Monospace` + SemiBold + `fontFeatureSettings = "tnum"` |
| 補助数値（精度・標高・速度・送信間隔） | `P2PType.numeric` = `bodySmall` + Monospace + `tnum` |
| hex / ログ | `P2PType.log` = `bodySmall` + Monospace |

- 座標が画面で最も大きい数字になるよう、他の数値は `bodySmall` に落とす。
- ALL CAPS のアイブロウラベルは使わない（セクション見出しは通常の日本語 + `titleMedium`）。

## 4. レイアウト（Sender 画面）

```text
┌──────────────────────────────────────────────┐
│ ● P2PGPS-ANDROID-A1B2            1000 ms     │  status rail（52dp、下辺ヘアライン）
├──────────────────────────────────────────────┤
│ 緯度  35.68124                               │  readout plate（surface、上角丸 16）
│ 経度 139.76713                               │  座標は mono titleMedium
│ 精度 ±5 m                         ▂▄▆█ 高     │  精度 + fix 品質バー
├──────────────────────────────────────────────┤
│ 送信中                              1000 ms   │  状態（titleLarge）+ 送信間隔（mono）
│ 位置を 1000 ms 間隔で送信しています。          │
├──────────────────────────────────────────────┤
│ BLE                                          │
│ アドバタイズ中 / 接続 1 台 / 購読 0 台         │  3 状態を明示
├──────────────────────────────────────────────┤
│ 送信レート（ms） [200][500][1000][2000][5000] │  48dp の選択ボタン
├──────────────────────────────────────────────┤
│ 権限 / デバッグ / ログ                        │  ヘアライン区切り（カードなし）
├──────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────┐ │
│ │              送信を開始                   │ │  主ボタン 52dp、pine 塗り、角丸 10
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

- 画面全体は `Scaffold(containerColor = paper)`。コンテンツは縦スクロール、主ボタンは `bottomBar` に固定。
- リードアウトプレートのみ角丸（上端 16dp）。他はヘアラインと余白で構造化する。
- 主ボタンは高さ 52dp・角丸 10dp・`pine` 塗り・白文字。押下で 0.98 スケール。
- 送信レートの選択ボタンは高さ 48dp（タップ領域 44pt 以上を確保）。
- セクション間は `HorizontalDivider(color = line, thickness = 1.dp)`。

## 5. コンポーネント

| 名前 | 実装 |
| :--- | :--- |
| Status rail | 高さ 52dp。左に 8dp の LED + ローカルネーム（mono）、右に送信間隔（mono） |
| LED | 接続済み = `pine` 塗り、接続待ち = `amber` 塗り、エラー = `danger` 塗り、未接続 = `inkSecondary` の輪郭。`semantics` で状態を読み上げ |
| Fix quality bars | 3×10dp のバー 4 本（`ink` / `line`）+ 「高/良/中/低/測位なし」テキスト |
| Readout plate | `surface`、上角丸 16dp、内側 16dp パディング、座標は `P2PType.coordinate` |
| Primary action | 高さ 52dp、角丸 10dp、`pine` 塗り / 白文字、押下 0.98 スケール |
| Interval option | 高さ 48dp、角丸 10dp。選択時 `pine` 塗り + 白文字、非選択時 `surface` + `line` 枠 |
| Log row | 時刻・レベル・メッセージをすべて monospace。レベルはテキスト（debug/info/warn/error）+ 色 |

## 6. モーション

- fix 到着時: 座標数字の **120ms クロスフェード**のみ（`Crossfade` + `tween(120)`）。スライドインは使わない。
- 主ボタン押下: 0.98 スケール（120ms）。
- **Reduce Motion** を尊重: `Settings.Global.ANIMATOR_DURATION_SCALE == 0` のとき、クロスフェードは 0ms、押下スケールは無効。
- LED のパルスは Sender 画面では使わない（接続待ちは静止表示）。

## 7. コピー

- 日本語。ボタンは動作を名指し: 「送信を開始」「送信を停止」「権限を許可する」「シミュレートを開始」「解除」「クリア」。
- 状態: 「送信中」「受信側の接続待ち」「停止中」「接続 1 台」「購読 0 台」「±5 m」「1000 ms」。
- エラーは 2 文（何が起きたか + どう直すか）。例:
  - 「Bluetooth が OFF です。端末の設定で Bluetooth を ON にしてから再試行してください。」
  - 「まだ測位できていません。屋外で待つか、デバッグのシミュレート位置を使用してください。」
- 感嘆符・絵文字・「〜しましょう」調のフィラーは使わない。

## 8. Anti-slop チェックリスト（自己点検結果）

| 項目 | 結果 |
| :--- | :--- |
| クリーム背景 + セリフ見出し + テラコッタの組み合わせ | 未使用（paper `#F2F4F2` + Roboto + pine/live） |
| 近黒背景 + 単一の蛍光アクセント | 未使用（dark は paper `#0F1A1E` + pine `#6FBFA8`） |
| 同一角丸のカードを並べていない / 影を撒いていない | カード自体を使用しない。ヘアライン + 余白のみ。影なし |
| ALL CAPS アイブロウ / `A · B · C` / `WORD — fragment` / `→` 付きボタン | 未使用 |
| グラデーション・ガラス風ブラー・絵文字アイコン | 未使用。アイコンは Material Icons（outlined）のみ |
| 01 / 02 / 03 の番号 | 未使用 |
| 座標が主役（画面で最も大きい数字） | リードアウトプレートの座標が最大の数字（mono titleMedium、他は bodySmall） |
| 状態が色だけで伝わっていない | LED + テキスト、アイコン + テキスト、品質バー + テキスト、レベル文字 + 色 |
| タップ領域 44×44pt 以上 | 主ボタン 52dp、選択ボタン 48dp、権限ボタン 48dp |
| Dynamic Type / Reduce Motion | システムフォントスケールに追従。Reduce Motion でクロスフェード 0ms・押下スケール無効 |

## 9. 既知の割り切り

- DESIGN.md の座標タイポは Android では `titleMedium`（16sp）。画面内で最大の数字にはなるが、端末の文字サイズ設定によっては相対的に小さく見える。Dynamic Type 追従を優先した。
- レンダリング検証は実機が無いため未実施（CI はコンパイルのみ）。実機確認時に余白・タップ領域・ダーク配色を再確認する。
