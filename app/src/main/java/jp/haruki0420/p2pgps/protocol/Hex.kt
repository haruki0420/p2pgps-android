package jp.haruki0420.p2pgps.protocol

/**
 * ログ表示用の hex 文字列（例: `01 03 2A 00 ...`）。
 *
 * 座標を含むパケットをそのままログへ出すとプライバシー上問題になるため、
 * 呼び出し側で必要最小限に留めること（仕様 §8）。
 */
fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
