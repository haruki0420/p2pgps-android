# P2P GPS Android Sender の ProGuard/R8 ルール。
#
# protocol/ パッケージはリフレクションを使わない純粋な Kotlin 実装なので、
# 追加の keep ルールは不要。将来 kotlinx.serialization などを導入する場合は
# ここにルールを追加する。
-keepattributes *Annotation*, InnerClasses
