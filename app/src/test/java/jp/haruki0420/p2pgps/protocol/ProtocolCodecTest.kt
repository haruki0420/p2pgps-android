package jp.haruki0420.p2pgps.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仕様 v1 §10 のテストベクタを JVM で検証する。
 *
 * 座標・タイムスタンプは仕様の規範定義（緯度経度 × 10^7、Unix エポック秒）から
 * 計算した値を期待値にしている。
 *
 * 注意: 仕様書 §3.2 の「エンコード例（東京駅）」に載っている hex は、
 * 同じ行の 10 進値（lat=356812360 / lon=1397671250 / ts=1763136000）と一致しない
 * （例の hex をデコードすると lat=356809832 / lon=1397899762 / ts=1763666944 になる）。
 * 本実装とテストは規範定義に従う。詳細は docs/protocol.md を参照。
 */
class ProtocolCodecTest {

    // ---------------------------------------------------------------------
    // Location Packet（仕様 §3）
    // ---------------------------------------------------------------------

    @Test
    fun tokyoStation_encodesToNormativeBytes() {
        val packet = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX or LocationPacket.FLAG_ALTITUDE_VALID,
            sequence = 42,
            latitude = LocationPacket.encodeCoordinate(35.6812360),
            longitude = LocationPacket.encodeCoordinate(139.7671250),
            altitudeM = 3,
            accuracyM = 5,
            speedMps = 0,
            timestamp = 1_763_136_000L,
        )

        assertEquals(356_812_360, packet.latitude)
        assertEquals(1_397_671_250, packet.longitude)
        assertEquals(
            "01 03 2A 00 48 86 44 15 52 C5 4E 53 03 00 05 00 00 52 17 69",
            packet.encode().toHexString(),
        )
    }

    @Test
    fun tokyoStation_roundTrips() {
        val packet = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX or LocationPacket.FLAG_ALTITUDE_VALID,
            sequence = 42,
            latitude = 356_812_360,
            longitude = 1_397_671_250,
            altitudeM = 3,
            accuracyM = 5,
            speedMps = 0,
            timestamp = 1_763_136_000L,
        )

        val decoded = LocationPacket.decode(packet.encode())
        assertNotNull(decoded)
        assertEquals(packet, decoded)
        assertNull(decoded!!.validationError())
        assertTrue(decoded.hasValidFix)
        assertTrue(decoded.hasAltitude)
        // flags に SPEED_VALID を立てていないため、速度値は入っていても「不明」扱い。
        assertFalse(decoded.hasSpeed)
        assertFalse(decoded.isSimulated)
    }

    @Test
    fun antarctica_negativeCoordinatesAndAltitude() {
        // 南極（南緯 77.8419 / 東経 166.6863 / 標高 -100m）。
        val packet = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX or LocationPacket.FLAG_ALTITUDE_VALID,
            sequence = 0,
            latitude = LocationPacket.encodeCoordinate(-77.8419),
            longitude = LocationPacket.encodeCoordinate(166.6863),
            altitudeM = -100,
            accuracyM = 12,
            speedMps = LocationPacket.UNKNOWN_U8,
            timestamp = 1_763_136_000L,
        )

        assertEquals(-778_419_000, packet.latitude)
        assertEquals(1_666_863_000, packet.longitude)
        assertEquals(
            "01 03 00 00 C8 44 9A D1 98 4F 5A 63 9C FF 0C FF 00 52 17 69",
            packet.encode().toHexString(),
        )

        val decoded = LocationPacket.decode(packet.encode())
        assertNotNull(decoded)
        assertEquals(-100, decoded!!.altitudeM)
        assertFalse(decoded.hasSpeed)
        assertNull(decoded.validationError())
    }

    @Test
    fun nullIsland_isRejectedWhenValidFix() {
        val packet = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 0,
            longitude = 0,
        )
        assertEquals(ValidationError.NULL_ISLAND, packet.validationError())
    }

    @Test
    fun nullIslandThreshold_isInclusiveAtPointZeroZeroZeroOneDegree() {
        val atThreshold = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 1_000,
            longitude = -1_000,
        )
        assertEquals(ValidationError.NULL_ISLAND, atThreshold.validationError())

        val justOutside = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 1_001,
            longitude = 0,
        )
        assertNull(justOutside.validationError())
    }

    @Test
    fun nullIsland_isAllowedWhenFixIsNotValid() {
        // VALID_FIX が 0 のパケットは「まだ測位できていない」を表すので破棄しない。
        val packet = LocationPacket(flags = 0, latitude = 0, longitude = 0)
        assertNull(packet.validationError())
    }

    @Test
    fun outOfRangeCoordinates_areRejected() {
        // 座標は deg × 10^7。緯度 ±90° = ±900_000_000、経度 ±180° = ±1_800_000_000。
        val latitudeTooLarge = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 900_000_001,
            longitude = 1_390_000_000,
        )
        assertEquals(ValidationError.LATITUDE_OUT_OF_RANGE, latitudeTooLarge.validationError())

        val latitudeTooSmall = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = -900_000_001,
            longitude = 1_390_000_000,
        )
        assertEquals(ValidationError.LATITUDE_OUT_OF_RANGE, latitudeTooSmall.validationError())

        val longitudeTooLarge = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 350_000_000,
            longitude = 1_800_000_001,
        )
        assertEquals(ValidationError.LONGITUDE_OUT_OF_RANGE, longitudeTooLarge.validationError())

        val longitudeTooSmall = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 350_000_000,
            longitude = -1_800_000_001,
        )
        assertEquals(ValidationError.LONGITUDE_OUT_OF_RANGE, longitudeTooSmall.validationError())
    }

    @Test
    fun boundaryCoordinates_areAccepted() {
        // 北緯 90° / 東経 180°（deg × 10^7 の上限）。
        val northEast = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 900_000_000,
            longitude = 1_800_000_000,
        )
        assertNull(northEast.validationError())

        // 南緯 90° / 西経 180°（下限）。
        val southWest = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = -900_000_000,
            longitude = -1_800_000_000,
        )
        assertNull(southWest.validationError())

        // 東京駅（9 桁）も当然ながら有効。
        val tokyo = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 356_812_360,
            longitude = 1_397_671_250,
        )
        assertNull(tokyo.validationError())
    }

    @Test
    fun reservedFlagBit_isReportedByValidation() {
        val bytes = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 356_812_360,
            longitude = 1_397_671_250,
        ).encode()
        // flags の bit7（予約）を立てる。
        bytes[1] = (bytes[1].toInt() or 0x80).toByte()

        val decoded = LocationPacket.decode(bytes)
        assertNotNull(decoded)
        assertEquals(ValidationError.RESERVED_FLAG_SET, decoded!!.validationError())
    }

    @Test
    fun decode_rejectsWrongLength() {
        assertNull(LocationPacket.decode(ByteArray(0)))
        assertNull(LocationPacket.decode(ByteArray(19)))
        assertNull(LocationPacket.decode(ByteArray(21)))
    }

    @Test
    fun decode_rejectsUnknownVersion() {
        val bytes = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 356_812_360,
            longitude = 1_397_671_250,
        ).encode()
        bytes[0] = 0x02
        assertNull(LocationPacket.decode(bytes))

        bytes[0] = 0x00
        assertNull(LocationPacket.decode(bytes))
    }

    @Test
    fun timestamp_isUnsigned32Bit() {
        val packet = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 356_812_360,
            longitude = 1_397_671_250,
            timestamp = 0xFFFF_FFFFL,
        )
        val bytes = packet.encode()
        assertEquals("FF FF FF FF", bytes.copyOfRange(16, 20).toHexString())

        val decoded = LocationPacket.decode(bytes)
        assertNotNull(decoded)
        assertEquals(0xFFFF_FFFFL, decoded!!.timestamp)
    }

    @Test
    fun sequenceWrapsAt65535() {
        val packet = LocationPacket(flags = LocationPacket.FLAG_VALID_FIX, sequence = 0xFFFF)
        assertEquals(0, packet.nextSequence().sequence)
        assertEquals(1, packet.copy(sequence = 0).nextSequence().sequence)
    }

    @Test
    fun flagsHelpers_matchSpecBits() {
        val packet = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX or
                LocationPacket.FLAG_ALTITUDE_VALID or
                LocationPacket.FLAG_SPEED_VALID or
                LocationPacket.FLAG_SIMULATED or
                LocationPacket.FLAG_CHARGING or
                LocationPacket.FLAG_LOW_BATTERY or
                LocationPacket.FLAG_MOVING,
        )
        assertTrue(packet.hasValidFix)
        assertTrue(packet.hasAltitude)
        assertTrue(packet.hasSpeed)
        assertTrue(packet.isSimulated)
        assertTrue(packet.isCharging)
        assertTrue(packet.isLowBattery)
        assertTrue(packet.isMoving)
        assertEquals(0x7F, packet.flags)
    }

    @Test
    fun staleTimestamp_isDetectedButNotRejected() {
        val packet = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            latitude = 356_812_360,
            longitude = 1_397_671_250,
            timestamp = 1_000_000L,
        )
        assertTrue(packet.isStale(nowEpochSeconds = 2_000_000L))
        assertFalse(packet.isStale(nowEpochSeconds = 1_000_000L))
        // stale は破棄理由ではない。
        assertNull(packet.validationError())
    }

    @Test
    fun encodeCoordinate_roundTrip() {
        assertEquals(356_812_360, LocationPacket.encodeCoordinate(35.6812360))
        assertEquals(-778_419_000, LocationPacket.encodeCoordinate(-77.8419))
        assertEquals(0, LocationPacket.encodeCoordinate(0.0))
        assertEquals(35.6812360, LocationPacket.decodeCoordinate(356_812_360), 1e-9)
    }

    // ---------------------------------------------------------------------
    // Extended Location Packet（仕様 §4）
    // ---------------------------------------------------------------------

    @Test
    fun extendedPacket_roundTrip() {
        val packet = ExtendedPacket(
            provider = ProviderType.GPS.code,
            batteryPct = 87,
            satellites = 12,
            course = 12_345,
            verticalAccuracyM = 8,
        )
        assertEquals("01 01 57 0C 39 30 08 00", packet.encode().toHexString())

        val decoded = ExtendedPacket.decode(packet.encode())
        assertNotNull(decoded)
        assertEquals(packet, decoded)
    }

    @Test
    fun extendedPacket_unknownValuesAreAllOnes() {
        val packet = ExtendedPacket()
        assertEquals(
            "01 00 FF FF FF FF FF 00",
            packet.encode().toHexString(),
        )
        val decoded = ExtendedPacket.decode(packet.encode())
        assertNotNull(decoded)
        assertEquals(ExtendedPacket.UNKNOWN_COURSE, decoded!!.course)
        assertEquals(ExtendedPacket.UNKNOWN_U8, decoded.batteryPct)
        assertEquals(ProviderType.UNKNOWN.code, decoded.provider)
    }

    @Test
    fun extendedPacket_rejectsWrongLengthAndVersion() {
        assertNull(ExtendedPacket.decode(ByteArray(7)))
        assertNull(ExtendedPacket.decode(ByteArray(9)))
        val bytes = ExtendedPacket().encode()
        bytes[0] = 0x02
        assertNull(ExtendedPacket.decode(bytes))
    }

    @Test
    fun encodeCourse_normalizesAndScales() {
        assertEquals(12_345, ExtendedPacket.encodeCourse(123.45))
        assertEquals(35_999, ExtendedPacket.encodeCourse(359.999))
        assertEquals(35_000, ExtendedPacket.encodeCourse(-10.0))
        assertEquals(0, ExtendedPacket.encodeCourse(720.0))
        assertEquals(ExtendedPacket.UNKNOWN_COURSE, ExtendedPacket.encodeCourse(Double.NaN))
    }

    @Test
    fun providerType_fromProviderName() {
        assertEquals(ProviderType.GPS, ProviderType.fromProviderName("gps"))
        assertEquals(ProviderType.NETWORK, ProviderType.fromProviderName("network"))
        assertEquals(ProviderType.FUSED, ProviderType.fromProviderName("fused"))
        assertEquals(ProviderType.MANUAL, ProviderType.fromProviderName("simulated"))
        assertEquals(ProviderType.UNKNOWN, ProviderType.fromProviderName(null))
        assertEquals(ProviderType.UNKNOWN, ProviderType.fromCode(99))
    }

    // ---------------------------------------------------------------------
    // Control characteristic（仕様 §5）
    // ---------------------------------------------------------------------

    @Test
    fun controlCommands_encode() {
        assertEquals("01 E8 03", ControlCommand.Start(1_000).encode().toHexString())
        assertEquals("02", ControlCommand.Stop.encode().toHexString())
        assertEquals("03 2C 01", ControlCommand.SetInterval(300).encode().toHexString())
        assertEquals("04", ControlCommand.RequestFix.encode().toHexString())
        assertEquals("05 01", ControlCommand.SetExtended(true).encode().toHexString())
        assertEquals("05 00", ControlCommand.SetExtended(false).encode().toHexString())
        assertEquals("7F", ControlCommand.Reset.encode().toHexString())
    }

    @Test
    fun controlCommands_decodeAllOpcodes() {
        assertEquals(
            ControlCommand.Start(1_000),
            (ControlCommand.decode(byteArrayOf(0x01, 0xE8.toByte(), 0x03)) as ControlDecodeResult.Success).command,
        )
        assertEquals(
            ControlCommand.Stop,
            (ControlCommand.decode(byteArrayOf(0x02)) as ControlDecodeResult.Success).command,
        )
        assertEquals(
            ControlCommand.SetInterval(300),
            (ControlCommand.decode(byteArrayOf(0x03, 0x2C, 0x01)) as ControlDecodeResult.Success).command,
        )
        assertEquals(
            ControlCommand.RequestFix,
            (ControlCommand.decode(byteArrayOf(0x04)) as ControlDecodeResult.Success).command,
        )
        assertEquals(
            ControlCommand.SetExtended(true),
            (ControlCommand.decode(byteArrayOf(0x05, 0x01)) as ControlDecodeResult.Success).command,
        )
        assertEquals(
            ControlCommand.SetExtended(false),
            (ControlCommand.decode(byteArrayOf(0x05, 0x00)) as ControlDecodeResult.Success).command,
        )
        assertEquals(
            ControlCommand.Reset,
            (ControlCommand.decode(byteArrayOf(0x7F)) as ControlDecodeResult.Success).command,
        )
    }

    @Test
    fun controlCommand_unknownOpcodeIsReported() {
        // 0x10 は v2 の AUTH 用予約。v1 では未知として扱う。
        val auth = ControlCommand.decode(byteArrayOf(0x10))
        assertTrue(auth is ControlDecodeResult.Failure)
        assertEquals(StatusError.UNKNOWN_OPCODE, (auth as ControlDecodeResult.Failure).error)

        val unknown = ControlCommand.decode(byteArrayOf(0x06))
        assertEquals(StatusError.UNKNOWN_OPCODE, (unknown as ControlDecodeResult.Failure).error)
    }

    @Test
    fun controlCommand_badLengthIsReported() {
        val cases = listOf(
            ByteArray(0),
            byteArrayOf(0x01, 0xE8.toByte()),
            byteArrayOf(0x02, 0x00),
            byteArrayOf(0x03),
            byteArrayOf(0x04, 0x00),
            byteArrayOf(0x05),
            byteArrayOf(0x7F, 0x00),
        )
        for (bytes in cases) {
            val result = ControlCommand.decode(bytes)
            assertTrue("bytes=${bytes.toHexString()}", result is ControlDecodeResult.Failure)
            assertEquals(
                "bytes=${bytes.toHexString()}",
                StatusError.BAD_LENGTH,
                (result as ControlDecodeResult.Failure).error,
            )
        }
    }

    @Test
    fun controlCommand_intervalZeroUsesDefault() {
        val result = ControlCommand.decode(byteArrayOf(0x01, 0x00, 0x00))
        assertEquals(
            ControlCommand.Start(ControlCommand.DEFAULT_INTERVAL_MS),
            (result as ControlDecodeResult.Success).command,
        )
    }

    @Test
    fun controlCommand_intervalIsClamped() {
        // 1ms → 100ms
        assertEquals(
            ControlCommand.Start(100),
            (ControlCommand.decode(byteArrayOf(0x01, 0x01, 0x00)) as ControlDecodeResult.Success).command,
        )
        // 65535ms → 60000ms
        assertEquals(
            ControlCommand.Start(60_000),
            (ControlCommand.decode(byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte())) as ControlDecodeResult.Success).command,
        )
        // SET_INTERVAL も同様に補正する。
        assertEquals(
            ControlCommand.SetInterval(60_000),
            (ControlCommand.decode(byteArrayOf(0x03, 0x61, 0xEA.toByte())) as ControlDecodeResult.Success).command,
        )
        assertEquals(
            ControlCommand.SetInterval(100),
            (ControlCommand.decode(byteArrayOf(0x03, 0x63, 0x00)) as ControlDecodeResult.Success).command,
        )
    }

    @Test
    fun clampInterval_matchesSpec() {
        assertEquals(1_000, ControlCommand.clampInterval(0))
        assertEquals(100, ControlCommand.clampInterval(99))
        assertEquals(100, ControlCommand.clampInterval(100))
        assertEquals(1_000, ControlCommand.clampInterval(1_000))
        assertEquals(60_000, ControlCommand.clampInterval(60_000))
        assertEquals(60_000, ControlCommand.clampInterval(60_001))
    }

    // ---------------------------------------------------------------------
    // Status characteristic（仕様 §6）
    // ---------------------------------------------------------------------

    @Test
    fun statusPacket_roundTrip() {
        val packet = StatusPacket(
            state = StatusState.STREAMING,
            intervalMs = 1_000,
            batteryPct = 88,
            statusFlags = StatusPacket.FLAG_EXTENDED_ENABLED or
                StatusPacket.FLAG_FIX_AVAILABLE or
                StatusPacket.FLAG_CHARGING,
            lastError = StatusError.NONE,
        )
        assertEquals("01 01 E8 03 58 07 00 00", packet.encode().toHexString())

        val decoded = StatusPacket.decode(packet.encode())
        assertNotNull(decoded)
        assertEquals(packet, decoded)
        assertTrue(decoded!!.extendedEnabled)
        assertTrue(decoded.fixAvailable)
        assertTrue(decoded.charging)
        assertFalse(decoded.lowBattery)
    }

    @Test
    fun statusPacket_errorState() {
        val packet = StatusPacket(
            state = StatusState.ERROR,
            intervalMs = 60_000,
            batteryPct = StatusPacket.UNKNOWN_U8,
            statusFlags = StatusPacket.FLAG_LOW_BATTERY,
            lastError = StatusError.LOCATION_UNAVAILABLE,
        )
        assertEquals("01 03 60 EA FF 08 03 00", packet.encode().toHexString())
        val decoded = StatusPacket.decode(packet.encode())
        assertNotNull(decoded)
        assertEquals(StatusError.LOCATION_UNAVAILABLE, decoded!!.lastError)
        assertTrue(decoded.lowBattery)
    }

    @Test
    fun statusPacket_rejectsWrongLengthAndVersion() {
        assertNull(StatusPacket.decode(ByteArray(7)))
        assertNull(StatusPacket.decode(ByteArray(9)))
        val bytes = StatusPacket().encode()
        bytes[0] = 0x02
        assertNull(StatusPacket.decode(bytes))
    }

    @Test
    fun statusCodes_matchSpec() {
        assertEquals(0, StatusState.IDLE.code)
        assertEquals(1, StatusState.STREAMING.code)
        assertEquals(2, StatusState.PAUSED.code)
        assertEquals(3, StatusState.ERROR.code)
        assertEquals(0, StatusError.NONE.code)
        assertEquals(1, StatusError.UNKNOWN_OPCODE.code)
        assertEquals(2, StatusError.BAD_LENGTH.code)
        assertEquals(3, StatusError.LOCATION_UNAVAILABLE.code)
        assertEquals(4, StatusError.INTERNAL.code)
    }

    // ---------------------------------------------------------------------
    // sequence 巻き戻り（仕様 §3.3-6）
    // ---------------------------------------------------------------------

    @Test
    fun sequenceTracker_detectsRollbackButPacketIsStillAccepted() {
        val tracker = SequenceTracker()
        assertFalse(tracker.observe(10).rollback)
        assertFalse(tracker.observe(11).rollback)
        assertTrue(tracker.observe(9).rollback)

        // 巻き戻ったパケットも decode 自体は成功する（受理する）。
        val bytes = LocationPacket(
            flags = LocationPacket.FLAG_VALID_FIX,
            sequence = 9,
            latitude = 356_812_360,
            longitude = 1_397_671_250,
        ).encode()
        assertNotNull(LocationPacket.decode(bytes))
    }

    @Test
    fun sequenceTracker_wrapIsNotRollback() {
        val tracker = SequenceTracker()
        assertFalse(tracker.observe(0xFFFF).rollback)
        assertFalse(tracker.observe(0).rollback)
    }

    @Test
    fun sequenceTracker_reportsGaps() {
        val tracker = SequenceTracker()
        tracker.observe(10)
        val observation = tracker.observe(13)
        assertFalse(observation.rollback)
        assertEquals(2, observation.gap)
    }

    // ---------------------------------------------------------------------
    // UUID（仕様 §1）
    // ---------------------------------------------------------------------

    @Test
    fun gattUuids_matchSpec() {
        assertEquals("b1e5a000-9c3d-4e7a-8f20-1a2b3c4d5e6f", GattUuids.SERVICE.toString())
        assertEquals("b1e5a001-9c3d-4e7a-8f20-1a2b3c4d5e6f", GattUuids.LOCATION.toString())
        assertEquals("b1e5a002-9c3d-4e7a-8f20-1a2b3c4d5e6f", GattUuids.CONTROL.toString())
        assertEquals("b1e5a003-9c3d-4e7a-8f20-1a2b3c4d5e6f", GattUuids.STATUS.toString())
        assertEquals("b1e5a004-9c3d-4e7a-8f20-1a2b3c4d5e6f", GattUuids.EXTENDED.toString())
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", GattUuids.CCCD.toString())
        assertEquals("0000180a-0000-1000-8000-00805f9b34fb", GattUuids.DEVICE_INFORMATION_SERVICE.toString())
    }

    @Test
    fun packetSizes_matchSpec() {
        assertEquals(20, LocationPacket().encode().size)
        assertEquals(8, ExtendedPacket().encode().size)
        assertEquals(8, StatusPacket().encode().size)
    }

    @Test
    fun hexString_formatting() {
        assertArrayEquals(
            byteArrayOf(0x01, 0xFF.toByte(), 0x00),
            byteArrayOf(0x01, 0xFF.toByte(), 0x00),
        )
        assertEquals("01 FF 00", byteArrayOf(0x01, 0xFF.toByte(), 0x00).toHexString())
    }
}
