# 共有テストベクタ（ミラー）

`p2pgps-protocol/tests/vectors/*.json` の手動ミラーです。`ProtocolVectorTest` が読み込み、
Android 実装の encode / decode / validate を仕様の値と突き合わせます。

## 由来

- 正本: `p2pgps-protocol` リポジトリの `tests/vectors/`
- ミラー元コミット: `654ae56`（+ 仕様 §5.1 明確化に伴う Control interval ベクタの更新を含む）
- 対象: `location.json`（13 ベクタ）、`extended.json`（5）、`status.json`（5）、`control.json`（22）、`sequences.json`（5 シナリオ）

## 同期方法

```bash
cp ../p2pgps-protocol/tests/vectors/{location,extended,status,control,sequences}.json \
   app/src/test/resources/vectors/
./gradlew :app:testDebugUnitTest
```

正本が更新されたら上記でコピーし直し、`ProtocolVectorTest` を通してください。
JSON のスキーマ（`wire` / `hex` / `expect` など）は正本の `tests/vectors/README.md` を参照してください。

## 注意

- `control.json` の `hex` は受信側が書き込む**生値**、`normalized_interval_ms` は送信側が
  仕様 §5.1（0→1000、1–99→100、100–60000→そのまま、60001–65535→60000）に従って適用する値です。
- このミラーは CI で `p2pgps-protocol` を checkout しないため、自動同期ではありません。
  実装間の乖離検出は、このミラーと正本の同期運用で担保します。
