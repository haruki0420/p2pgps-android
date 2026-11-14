package jp.haruki0420.p2pgps.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `p2pgps-protocol` の共有テストベクタ（JSON）を読んで encode/decode/validate を検証する。
 *
 * ベクタは `app/src/test/resources/vectors/` に手動ミラーしている
 * （`p2pgps-protocol/tests/vectors/` が正本）。ミラーの由来と同期方法は
 * `app/src/test/resources/vectors/README.md` を参照。
 *
 * このテストにより、仕様の値（hex・正規化後の interval など）と Android 実装の
 * 乖離を CI で検出できる。
 */
class ProtocolVectorTest {

    // ---------------------------------------------------------------------
    // Location Packet（仕様 §3）
    // ---------------------------------------------------------------------

    @Test
    fun locationVectors() {
        val vectors = load("location.json").getJSONArray("vectors")
        assertTrue("location ベクタが空です", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val id = vector.getString("id")
            val bytes = hexToBytes(vector.getString("hex"))
            val wire = vector.getJSONObject("wire")
            val expect = vector.getJSONObject("expect")

            if (wire.getInt("version") != LocationPacket.VERSION) {
                assertNull("$id: 未知バージョンは decode しない", LocationPacket.decode(bytes))
                continue
            }

            val packet = LocationPacket.decode(bytes)
            assertNotNull("$id: decode できること", packet)
            packet!!

            assertEquals("$id: flags", wire.getInt("flags"), packet.flags)
            assertEquals("$id: sequence", wire.getInt("sequence"), packet.sequence)
            assertEquals("$id: latitude", wire.getInt("latitude"), packet.latitude)
            assertEquals("$id: longitude", wire.getInt("longitude"), packet.longitude)
            assertEquals("$id: altitude_m", wire.getInt("altitude_m"), packet.altitudeM)
            assertEquals("$id: accuracy_m", wire.getInt("accuracy_m"), packet.accuracyM)
            assertEquals("$id: speed_mps", wire.getInt("speed_mps"), packet.speedMps)
            assertEquals("$id: timestamp", wire.getLong("timestamp"), packet.timestamp)
            assertEquals("$id: encode", vector.getString("hex"), packet.encode().toHexCompact())

            val expectedReason = if (expect.isNull("reason")) null else expect.getString("reason")
            assertEquals("$id: validation reason", expectedReason, packet.validationError()?.toVectorReason())
            assertEquals("$id: valid_fix", expect.getBoolean("valid_fix"), packet.hasValidFix)

            if (!vector.isNull("degrees")) {
                val degrees = vector.getJSONObject("degrees")
                assertEquals(
                    "$id: latitude degrees",
                    packet.latitude,
                    LocationPacket.encodeCoordinate(degrees.getString("latitude").toDouble()),
                )
                assertEquals(
                    "$id: longitude degrees",
                    packet.longitude,
                    LocationPacket.encodeCoordinate(degrees.getString("longitude").toDouble()),
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Extended Location Packet（仕様 §4）
    // ---------------------------------------------------------------------

    @Test
    fun extendedVectors() {
        val vectors = load("extended.json").getJSONArray("vectors")
        assertTrue("extended ベクタが空です", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val id = vector.getString("id")
            val bytes = hexToBytes(vector.getString("hex"))
            val wire = vector.getJSONObject("wire")
            val expect = vector.getJSONObject("expect")

            if (wire.getInt("version") != ExtendedPacket.VERSION) {
                assertNull("$id: 未知バージョンは decode しない", ExtendedPacket.decode(bytes))
                continue
            }

            val packet = ExtendedPacket.decode(bytes)
            assertNotNull("$id: decode できること", packet)
            packet!!

            assertEquals("$id: provider", wire.getInt("provider"), packet.provider)
            assertEquals("$id: battery_pct", wire.getInt("battery_pct"), packet.batteryPct)
            assertEquals("$id: satellites", wire.getInt("satellites"), packet.satellites)
            assertEquals("$id: course", wire.getInt("course"), packet.course)
            assertEquals(
                "$id: vertical_accuracy_m",
                wire.getInt("vertical_accuracy_m"),
                packet.verticalAccuracyM,
            )
            assertEquals("$id: encode", vector.getString("hex"), packet.encode().toHexCompact())
            assertEquals("$id: valid", expect.getBoolean("valid"), true)
        }
    }

    // ---------------------------------------------------------------------
    // Status（仕様 §6）
    // ---------------------------------------------------------------------

    @Test
    fun statusVectors() {
        val vectors = load("status.json").getJSONArray("vectors")
        assertTrue("status ベクタが空です", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val id = vector.getString("id")
            val bytes = hexToBytes(vector.getString("hex"))
            val wire = vector.getJSONObject("wire")
            val expect = vector.getJSONObject("expect")

            if (wire.getInt("version") != StatusPacket.VERSION) {
                assertNull("$id: 未知バージョンは decode しない", StatusPacket.decode(bytes))
                continue
            }

            val packet = StatusPacket.decode(bytes)
            assertNotNull("$id: decode できること", packet)
            packet!!

            assertEquals("$id: state", wire.getInt("state"), packet.state.code)
            assertEquals("$id: interval_ms", wire.getInt("interval_ms"), packet.intervalMs)
            assertEquals("$id: battery_pct", wire.getInt("battery_pct"), packet.batteryPct)
            assertEquals("$id: status_flags", wire.getInt("status_flags"), packet.statusFlags)
            assertEquals("$id: last_error", wire.getInt("last_error"), packet.lastError.code)
            assertEquals("$id: encode", vector.getString("hex"), packet.encode().toHexCompact())
            assertEquals("$id: valid", expect.getBoolean("valid"), true)
        }
    }

    // ---------------------------------------------------------------------
    // Control（仕様 §5 / §5.1）
    // ---------------------------------------------------------------------

    @Test
    fun controlVectors() {
        val vectors = load("control.json").getJSONArray("vectors")
        assertTrue("control ベクタが空です", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val id = vector.getString("id")
            val bytes = hexToBytes(vector.getString("hex"))
            val expect = vector.getJSONObject("expect")
            val result = ControlCommand.decode(bytes)

            if (!expect.getBoolean("valid")) {
                assertTrue("$id: 失敗するはず", result is ControlDecodeResult.Failure)
                val error = when (expect.getString("error")) {
                    "unknown_opcode" -> StatusError.UNKNOWN_OPCODE
                    "bad_length" -> StatusError.BAD_LENGTH
                    else -> {
                        fail("$id: 未知の error 名です: ${expect.getString("error")}")
                        return
                    }
                }
                assertEquals("$id: error", error, (result as ControlDecodeResult.Failure).error)
                assertEquals("$id: last_error", expect.getInt("last_error"), error.code)
                continue
            }

            assertTrue("$id: 成功するはず", result is ControlDecodeResult.Success)
            val command = (result as ControlDecodeResult.Success).command
            val decoded = expect.getJSONObject("decoded")
            val opcode = decoded.getString("opcode")

            when (opcode) {
                "START" -> {
                    assertTrue("$id: START", command is ControlCommand.Start)
                    assertIntervalVector(id, bytes, decoded, (command as ControlCommand.Start).intervalMs)
                }

                "SET_INTERVAL" -> {
                    assertTrue("$id: SET_INTERVAL", command is ControlCommand.SetInterval)
                    assertIntervalVector(
                        id,
                        bytes,
                        decoded,
                        (command as ControlCommand.SetInterval).intervalMs,
                    )
                }

                "STOP" -> assertTrue("$id: STOP", command is ControlCommand.Stop)
                "REQUEST_FIX" -> assertTrue("$id: REQUEST_FIX", command is ControlCommand.RequestFix)
                "RESET" -> assertTrue("$id: RESET", command is ControlCommand.Reset)
                "SET_EXTENDED" -> {
                    assertTrue("$id: SET_EXTENDED", command is ControlCommand.SetExtended)
                    assertEquals(
                        "$id: enabled",
                        decoded.getBoolean("enabled"),
                        (command as ControlCommand.SetExtended).enabled,
                    )
                    assertEquals("$id: encode", vector.getString("hex"), command.encode().toHexCompact())
                }

                else -> fail("$id: 未知の opcode 名です: $opcode")
            }
        }
    }

    private fun assertIntervalVector(
        id: String,
        rawBytes: ByteArray,
        decoded: JSONObject,
        actualIntervalMs: Int,
    ) {
        // hex は生値。offset 1 の uint16 LE が受信側の書き込み値。
        val rawInterval = (rawBytes[1].toInt() and 0xFF) or ((rawBytes[2].toInt() and 0xFF) shl 8)
        assertEquals("$id: raw interval_ms", decoded.getInt("interval_ms"), rawInterval)
        assertEquals(
            "$id: normalized_interval_ms",
            decoded.getInt("normalized_interval_ms"),
            ControlCommand.clampInterval(rawInterval),
        )
        assertEquals(
            "$id: decode は正規化後の値を持つ",
            decoded.getInt("normalized_interval_ms"),
            actualIntervalMs,
        )
    }

    // ---------------------------------------------------------------------
    // sequence（仕様 §3.3-6）
    // ---------------------------------------------------------------------

    @Test
    fun sequenceVectors() {
        val scenarios = load("sequences.json").getJSONArray("scenarios")
        assertTrue("sequence シナリオが空です", scenarios.length() > 0)

        for (i in 0 until scenarios.length()) {
            val scenario = scenarios.getJSONObject(i)
            val id = scenario.getString("id")
            val packets = scenario.getJSONArray("packets")
            val expect = scenario.getJSONObject("expect")
            val accepted = expect.getJSONArray("accepted")
            val statuses = expect.getJSONArray("sequence_status")
            val tracker = SequenceTracker()

            for (j in 0 until packets.length()) {
                val packet = LocationPacket.decode(hexToBytes(packets.getJSONObject(j).getString("hex")))
                assertNotNull("$id[$j]: decode できること", packet)
                packet!!
                val previous = tracker.lastSequence
                val observation = tracker.observe(packet.sequence)
                val status = when {
                    previous == null -> "first"
                    packet.sequence == previous -> "duplicate"
                    observation.rollback -> "regression"
                    observation.gap == 0 -> "ok"
                    else -> "gap"
                }
                assertEquals("$id[$j]: sequence_status", statuses.getString(j), status)
                assertTrue("$id[$j]: accepted", accepted.getBoolean(j))
            }
        }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private fun load(name: String): JSONObject {
        val stream = javaClass.getResourceAsStream("/vectors/$name")
            ?: throw AssertionError("テストベクタが見つかりません: /vectors/$name")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JSONObject(text.removePrefix("\uFEFF"))
    }

    private fun hexToBytes(hex: String): ByteArray {
        if (hex.length % 2 != 0) throw AssertionError("hex の長さが奇数です: $hex")
        return ByteArray(hex.length / 2) { index ->
            val high = Character.digit(hex[index * 2], 16)
            val low = Character.digit(hex[index * 2 + 1], 16)
            if (high < 0 || low < 0) throw AssertionError("hex が不正です: $hex")
            ((high shl 4) or low).toByte()
        }
    }

    private fun ByteArray.toHexCompact(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun ValidationError.toVectorReason(): String = when (this) {
        ValidationError.RESERVED_FLAG_SET -> "reserved_flag_set"
        ValidationError.LATITUDE_OUT_OF_RANGE -> "latitude_out_of_range"
        ValidationError.LONGITUDE_OUT_OF_RANGE -> "longitude_out_of_range"
        ValidationError.NULL_ISLAND -> "null_island"
    }
}
