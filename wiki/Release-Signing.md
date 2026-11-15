# リリース署名（Android）

Android のリリース APK は、**固定のリリース用 keystore** で署名します。これにより、同じ鍵で署名された APK は**アンインストールなしで更新**できます（同じ鍵で署名した APK は更新インストールできます）。

## keystore の場所とバックアップ

- ローカル: リポジトリ外の安全な場所（例: パスワードマネージャ管理のフォルダ）に保管し、コミットしない
- パスワード等: keystore とは別の安全な場所に保管し、コミットしない
- **必ず安全な場所にバックアップしてください。** 紛失すると、既存ユーザーへの更新を二度と配布できなくなります（Android は署名鍵の変更を許可しません）。

- 証明書 SHA-256 フィンガープリント: `53:14:4E:C4:2F:27:57:A4:E8:7B:8B:43:56:52:B3:DF:B8:D6:EB:79:11:70:16:F0:C7:EB:4C:C2:2B:A7:5E:D0`

## GitHub Secrets

`p2pgps-android` リポジトリに以下を登録済みです。

| Secret | 内容 |
| :--- | :--- |
| `ANDROID_KEYSTORE_BASE64` | keystore ファイルの Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore のパスワード |
| `ANDROID_KEY_ALIAS` | キーエイリアス（`p2pgps`） |
| `ANDROID_KEY_PASSWORD` | キーのパスワード |

Release APK ワークフローは keystore をデコードして `assembleRelease` を実行し、`apksigner verify` で署名を検証してから `p2pgps-android-<tag>-release.apk` をアップロードします。

## ローカルで署名ビルドする場合

```bash
export ANDROID_KEYSTORE_PATH=/path/to/p2pgps-release.keystore
export ANDROID_KEYSTORE_PASSWORD='...'
export ANDROID_KEY_ALIAS=p2pgps
export ANDROID_KEY_PASSWORD='...'
./gradlew :app:assembleRelease -PappVersionName=1.0.3 -PappVersionCode=10003
```

環境変数がない場合、release APK は**未署名**になります（ローカル確認用）。

## バージョンと更新

- `versionName` / `versionCode` はリリースタグから CI が刻印します（`v1.0.3` → `1.0.3` / `10003`）。
- 更新には **同じ署名鍵** と **より大きい versionCode** が必要です。
- 過去に debug 署名 APK をインストールしている場合、リリース署名 APK とは署名が異なるため、**初回だけアンインストール**が必要です。

## 注意

- keystore とパスワードをリポジトリ・Issue・ログに書かないでください。
- 鍵をローテーションする場合は、新しい applicationId にするか、ユーザーに再インストールを案内する必要があります。
