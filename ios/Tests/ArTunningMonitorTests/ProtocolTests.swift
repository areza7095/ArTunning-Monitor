import XCTest
@testable import ArTunningMonitor

/// Mirror persis unit test core:protocol Android (37 golden vectors).
/// Sumber vektor: docs/en/juken5-protocol-spec.md bagian11 + run CRC independen.
final class ProtocolTests: XCTestCase {

    // MARK: - FrameCodec

    func testFrameDenganParamHexLaluParamCRLF() {
        XCTAssertEqual(FrameCodec.buildLine(command: 0x1602, param: "0;5"), "1602;0;5\r\n")
    }

    func testFrameTanpaParamHanyaHexCRLF() {
        XCTAssertEqual(FrameCodec.buildLine(command: Opcodes.Ack.ACK_PING), "1B00\r\n")
        XCTAssertEqual(FrameCodec.buildLine(command: Opcodes.Read.ENTER_LIVE), "160A\r\n")
    }

    func testHexHurufBesarTanpaPaddingNol() {
        XCTAssertEqual(FrameCodec.buildLine(command: 0x1500), "1500\r\n")
        XCTAssertEqual(FrameCodec.buildLine(command: 5389), "150D\r\n") // literal "150d" app
        XCTAssertEqual(FrameCodec.buildLine(command: 6656), "1A00\r\n")
    }

    func testRawLineMemakaiCRLF() {
        XCTAssertEqual(FrameCodec.buildRawLine("#;8080;x;y;"), "#;8080;x;y;\r\n")
    }

    func testAsciiBytesBenar() {
        let bytes = FrameCodec.ascii("1B00\r\n")
        XCTAssertEqual(bytes.count, 6)
        XCTAssertEqual(bytes[0], UInt8(ascii: "1"))
        XCTAssertEqual(bytes[5], UInt8(ascii: "\n"))
    }

    // MARK: - LineSplitter

    func testSatuBarisLengkapDipotongDiLFDanCRDipangkas() {
        let s = LineSplitter()
        XCTAssertEqual(s.feed(Array("1B01\r\n".utf8)), ["1B01"])
    }

    func testBarisTerpecahAntarFeedDigabung() {
        let s = LineSplitter()
        XCTAssertEqual(s.feed(Array("160".utf8)), [])
        XCTAssertEqual(s.feed(Array("2;0;0\r\n".utf8)), ["1602;0;0"])
    }

    func testBeberapaBarisDalamSatuFeed() {
        let s = LineSplitter()
        XCTAssertEqual(s.feed(Array("1A00\r\nA603;1;2\r\n".utf8)), ["1A00", "A603;1;2"])
    }

    func testBarisMentahDuaByteAtauKurangDibuang() {
        let s = LineSplitter()
        XCTAssertEqual(s.feed(Array("a\r\n\r\n".utf8)), []) // "a\r"=2 byte, "\r"=1 byte
        XCTAssertEqual(s.feed(Array("ab\r\n".utf8)), ["ab"]) // "ab\r"=3 byte -> diterima
    }

    func testFilterCharsetMembuangKarakterDiLuarWhitelist() {
        let s = LineSplitter(filterValidChars: true)
        XCTAssertEqual(s.feed(Array("#B1#X\r\n".utf8)), ["B1X"]) // '#','\r' tidak valid
    }

    func testFilterNonAktifMempertahankanKarakterBulk() {
        let s = LineSplitter(filterValidChars: false)
        XCTAssertEqual(s.feed(Array("#B1#\r\n".utf8)), ["#B1#"])
    }

    func testKarakterWhitelistSesuaiCheckIfValidChar() {
        for c in Array("0aZ;., -+=") { XCTAssertTrue(LineSplitter.isValidChar(c), "'\(c)' harus valid") }
        for c in Array("#$%()\r\n/") { XCTAssertFalse(LineSplitter.isValidChar(c), "'\(c)' harus invalid") }
    }

    func testSisaByteMenungguFeedBerikutnya() {
        let s = LineSplitter()
        _ = s.feed(Array("9500;5A".utf8))
        XCTAssertEqual(s.feed(Array("3F\r\n".utf8)), ["9500;5A3F"])
    }

    // MARK: - ReplyDispatch

    func testParseFrameTelemetriA603() {
        let r = ReplyDispatch.parse("A603;12;12.6;0;3450")
        XCTAssertEqual(r.token, "A603")
        XCTAssertEqual(r.code, 0xA603)
        XCTAssertTrue(r.isTelemetry)
        XCTAssertFalse(r.isEchoAck)
        XCTAssertEqual(r.parts.count, 5)
        XCTAssertEqual(r.payload(0), "12")
        XCTAssertEqual(r.parts[1], "12")
    }

    func testParseEchoAck1B01() {
        let r = ReplyDispatch.parse("1B01")
        XCTAssertEqual(r.code, Opcodes.Ack.FEEDBACK_ECHO_ACK)
        XCTAssertTrue(r.isEchoAck)
    }

    func testTokenDiUpperCaseDanDipotongEmpatKarakterTerakhir() {
        let r = ReplyDispatch.parse("ab9500;5a3f")
        XCTAssertEqual(r.token, "9500")
        XCTAssertEqual(r.code, 0x9500)
        // jalur korelasi app memakai token penuh
        XCTAssertEqual(r.correlationCode, 0xAB9500)
    }

    func testParseHexCaseInsensitive() {
        XCTAssertEqual(ReplyDispatch.parseHex("1a00"), 0x1A00)
        XCTAssertEqual(ReplyDispatch.parseHex("1602"), 0x1602)
        XCTAssertNil(ReplyDispatch.parseHex("ZZ"))
    }

    func testTabelKorelasiRequestBalasanDariCodegen() {
        XCTAssertEqual(COMMAND_TO_REPLY[0x1601], 0x9601)
        XCTAssertEqual(COMMAND_TO_REPLY[0x1602], 0x9602)
        XCTAssertEqual(COMMAND_TO_REPLY[0x1617], 0x9616)
        XCTAssertEqual(COMMAND_TO_REPLY[0x1500], 0x9500)
        XCTAssertEqual(COMMAND_TO_REPLY.count, 13)
    }

    func testKonstantaOpcodeHasilCodegenSesuaiSpesifikasi() {
        XCTAssertEqual(Opcodes.Read.READ_FUEL_COR1, 0x1602)
        XCTAssertEqual(Opcodes.Read.ENTER_LIVE, 0x160A)
        XCTAssertEqual(Opcodes.Write.SAVE_FUEL_COR1, 0x2602)
        XCTAssertEqual(Opcodes.Ack.ACK_PING, 0x1B00)
        XCTAssertEqual(Opcodes.Reply.FEEDBACK_READ_ENGINE_RUN, 0xA603)
        XCTAssertEqual(BulkMarkers.beginCal, "#B1#")
        XCTAssertEqual(BulkMarkers.beginMap, "#B2#")
        XCTAssertEqual(BulkMarkers.beginLog, "#B3#")
        XCTAssertEqual(BulkMarkers.endTrimmed, "*eE*")
        XCTAssertEqual(TELEMETRY_HEADER, "A603")
    }

    // MARK: - TelemetryParser

    private let sampleLine = "A603;12;12.6;0;3450;852;12.5;1013;345;1520;890"

    func testParseContohTranskripSpesifikasi() throws {
        let t = try XCTUnwrap(TelemetryParser.parse(sampleLine))
        XCTAssertEqual(t.tpsPercent, 12, accuracy: 0.0001)
        XCTAssertEqual(t.batteryVolt, 12.6, accuracy: 0.0001)
        XCTAssertEqual(t.coreId, 0)
        XCTAssertEqual(t.rpm, 3450, accuracy: 0.0001)
        XCTAssertEqual(t.eotCelsius, 85.2, accuracy: 0.0001)  // [5]/10
        XCTAssertEqual(t.afrRaw, 12.5, accuracy: 0.0001)       // mentah
        XCTAssertEqual(t.mapValue, 101.3, accuracy: 0.0001)    // [7]/10
        XCTAssertEqual(t.iatCelsius, 34.5, accuracy: 0.0001)   // [8]/10
        XCTAssertEqual(t.pumpAmps, 1.52, accuracy: 0.0001)     // [9]/1000
        XCTAssertEqual(t.injAmps, 0.89, accuracy: 0.0001)      // [10]/1000
        XCTAssertEqual(t.afrBandIndex, 3)                      // (int)12.5-9 =3
    }

    func testBukanFrameA603MengembalikanNil() {
        XCTAssertNil(TelemetryParser.parse("1A00"))
        XCTAssertNil(TelemetryParser.parse("9500;5A3F;9BC1"))
    }

    func testFrameA603TidakLengkapMengembalikanNil() {
        XCTAssertNil(TelemetryParser.parse("A603;12;12.6"))
    }

    func testBinningKolomRPM() {
        XCTAssertEqual(TelemetryParser.rpmColumn(500), 0)     // clamp1000
        XCTAssertEqual(TelemetryParser.rpmColumn(1000), 0)
        XCTAssertEqual(TelemetryParser.rpmColumn(3450), 10)   // q=3500
        XCTAssertEqual(TelemetryParser.rpmColumn(16000), 60)
        XCTAssertEqual(TelemetryParser.rpmColumn(20000), 60)  // clamp atas
        XCTAssertEqual(TelemetryParser.rpmColumnIgn(1000), 0)
        XCTAssertEqual(TelemetryParser.rpmColumnIgn(3450), 5)
    }

    func testBinningBarisTPS() {
        XCTAssertEqual(TelemetryParser.tpsRow(0), 0)
        XCTAssertEqual(TelemetryParser.tpsRow(1), 1)
        XCTAssertEqual(TelemetryParser.tpsRow(2), 1)
        XCTAssertEqual(TelemetryParser.tpsRow(2.5), 0) // di luar[1,2]&[3,94]&<95 ->0
        XCTAssertEqual(TelemetryParser.tpsRow(10), 3)
        XCTAssertEqual(TelemetryParser.tpsRow(94), 19)
        XCTAssertEqual(TelemetryParser.tpsRow(95), 20)
        XCTAssertEqual(TelemetryParser.tpsRow(100), 20)
    }

    func testPembulatanBankerSesuaiMathRoundNET() {
        XCTAssertEqual(TelemetryParser.roundBanker(13.8), 14, accuracy: 1e-9)
        XCTAssertEqual(TelemetryParser.roundBanker(4.5), 4, accuracy: 1e-9) // ke genap
        XCTAssertEqual(TelemetryParser.roundBanker(5.5), 6, accuracy: 1e-9) // ke genap
        XCTAssertEqual(TelemetryParser.roundBanker(2.5), 2, accuracy: 1e-9) // ke genap
        XCTAssertEqual(TelemetryParser.roundBanker(3.5), 4, accuracy: 1e-9) // f=3 ganjil ->4
    }

    // MARK: - Crc32Stm32 (vektor dari implementasi referensi Node.js independen)

    func testDataKosongMengembalikanInitial() {
        XCTAssertEqual(Crc32Stm32.toHex8(Crc32Stm32.compute([])), "FFFFFFFF")
    }

    func testVektorCrcIndependent() {
        func crc(_ s: String) -> String {
            Crc32Stm32.toHex8(Crc32Stm32.compute(Array(s.utf8)))
        }
        XCTAssertEqual(crc("12345678"), "FEFC54F9")
        XCTAssertEqual(crc("1234567890123456"), "4B29DF61")
        XCTAssertEqual(crc("12345678901234567890"), "FE4E032E")
        XCTAssertEqual(crc("JUKEN5P"), "D058C9A0") //7 -> pad ke8
    }

    func testFormatHeksapan8HurufBesar() {
        XCTAssertEqual(Crc32Stm32.toHex8(-1), "FFFFFFFF")
        XCTAssertEqual(Crc32Stm32.toHex8(Int32(bitPattern: 0xFEFC54F9)), "FEFC54F9")
    }

    // MARK: - LiveSession

    private let telemetry = "A603;12;12.6;0;3450;852;12.5;1013;345;1520;890"

    func testHandshakeMengirimPingDanMasukHandshaking() {
        let s = LiveSession()
        XCTAssertEqual(s.onConnected(nowMs: 0), ["1B00\r\n"])
        XCTAssertEqual(s.state, .handshaking)
        XCTAssertTrue(s.connected)
        XCTAssertFalse(s.linkLost)
    }

    func testKeepalivePingTiapTigaDetik() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        s.autoLive = false // isolasi: uji keepalive murni tanpa auto-live
        XCTAssertEqual(s.tick(nowMs: 2999), [])
        XCTAssertEqual(s.tick(nowMs: 3000), ["1B00\r\n"])
        XCTAssertEqual(s.tick(nowMs: 4000), [])
        XCTAssertEqual(s.tick(nowMs: 6001), ["1B00\r\n"])
    }

    func testBalasan1B01ResetWatchdogDanPicuAutoLive() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        s.autoLive = false // isolasi: tanpa auto, fase watchdog tetap di Handshaking
        _ = s.tick(nowMs: 17001) // tanpa echo reply -> >17000
        XCTAssertTrue(s.linkLost)

        // AUTO-LIVE jalur1: handshake diterima -> langsung1617 + state LiveRequested
        s.autoLive = true
        XCTAssertEqual(s.onRxLine("1B01", nowMs: 17100), ["1617\r\n"])
        XCTAssertFalse(s.linkLost)
        XCTAssertEqual(s.state, .liveRequested)
    }

    func testAutoLiveMemicuDariBalasan1B01LaluStreaming() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        XCTAssertEqual(s.onRxLine("1B01", nowMs: 100), ["1617\r\n"])
        XCTAssertEqual(s.state, .liveRequested)

        // retry160A mulai500 ms setelah enter (lastLiveTxMs =100)
        XCTAssertEqual(s.tick(nowMs: 599), [])
        XCTAssertEqual(s.tick(nowMs: 600), ["160A\r\n"])

        _ = s.onRxLine(telemetry, nowMs: 700)
        XCTAssertEqual(s.state, .streaming)
        XCTAssertEqual(s.tick(nowMs: 800), []) // tidak ada160A lagi
    }

    func testAutoLiveFallbackSatuSetengahDetikTanpa1B01() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        XCTAssertEqual(s.tick(nowMs: 1499), [])          // belum1500 ms
        XCTAssertEqual(s.tick(nowMs: 1500), ["1617\r\n"]) // fallback -> masuk live
        XCTAssertEqual(s.state, .liveRequested)
        XCTAssertEqual(s.tick(nowMs: 2000), ["160A\r\n"]) // >=500 ms ->160A
    }

    func testStopLiveMenonaktifkanAutoLiveSampaiConnectUlang() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        _ = s.onRxLine("1B01", nowMs: 100)   // auto -> LiveRequested
        _ = s.onRxLine(telemetry, nowMs: 600) // -> Streaming

        XCTAssertEqual(s.stopLive(nowMs: 1000), ["160B\r\n"])
        XCTAssertEqual(s.state, .handshaking)
        XCTAssertFalse(s.autoLive)

        //1B01 berikutnya TIDAK memicu auto (keputusan manual)
        XCTAssertEqual(s.onRxLine("1B01", nowMs: 2000), [])
        XCTAssertEqual(s.state, .handshaking)
        // fallback juga mati
        XCTAssertFalse(s.tick(nowMs: 100_000).contains { $0.hasPrefix("1617") })

        // connect ulang -> auto kembali aktif
        _ = s.onConnected(nowMs: 200_000)
        XCTAssertTrue(s.autoLive)
        XCTAssertEqual(s.onRxLine("1B01", nowMs: 200_100), ["1617\r\n"])
    }

    func testWatchdogTidakJalanSaatStreaming() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        _ = s.onRxLine("1B01", nowMs: 100)
        _ = s.requestLive(nowMs: 1000)
        _ = s.onRxLine(telemetry, nowMs: 1600)
        XCTAssertEqual(s.state, .streaming)

        _ = s.tick(nowMs: 100_000) // jauh melebihi17 dtk tapi streaming
        XCTAssertFalse(s.linkLost)
    }

    func testMasukLive1617Lalu160ATiap500msMaks25x() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        XCTAssertEqual(s.requestLive(nowMs: 1000), ["1617\r\n"])
        XCTAssertEqual(s.state, .liveRequested)

        XCTAssertEqual(s.tick(nowMs: 1400), [])       // belum500 ms
        XCTAssertEqual(s.tick(nowMs: 1500), ["160A\r\n"])
        XCTAssertEqual(s.tick(nowMs: 1900), [])
        XCTAssertEqual(s.tick(nowMs: 2000), ["160A\r\n"])

        //4600: keepalive (>=3000 sejak ping terakhir0) + live tick
        XCTAssertEqual(s.tick(nowMs: 4600), ["1B00\r\n", "160A\r\n"])

        _ = s.onRxLine(telemetry, nowMs: 4700) // -> streaming, retry berhenti
        XCTAssertEqual(s.state, .streaming)
        XCTAssertEqual(s.tick(nowMs: 5000), [])
        XCTAssertEqual(s.tick(nowMs: 7600), ["1B00\r\n"])
    }

    func testRetryLiveBerhentiSetelahBatasPercobaan() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        _ = s.requestLive(nowMs: 0)
        var liveSent = 0
        var now: Int64 = 0
        for _ in 0..<40 {
            now += 500
            if s.tick(nowMs: now).contains(where: { $0.hasPrefix("160A") }) { liveSent += 1 }
        }
        XCTAssertEqual(liveSent, 25)
    }

    func testKeluarLiveMengirim160B() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        _ = s.requestLive(nowMs: 0)
        _ = s.onRxLine(telemetry, nowMs: 600)
        XCTAssertEqual(s.state, .streaming)

        XCTAssertEqual(s.stopLive(nowMs: 1000), ["160B\r\n"])
        XCTAssertEqual(s.state, .handshaking)
    }

    func testTelemetriDiabaikanBilaTidakSedangRequestLive() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        s.autoLive = false // isolasi: tanpa auto,1B01 juga tidak memicu apa pun
        XCTAssertEqual(s.onRxLine("1B01", nowMs: 100), [])
        _ = s.onRxLine(telemetry, nowMs: 200)
        XCTAssertEqual(s.state, .handshaking)
    }

    func testSetelahDisconnectTidakAdaTxLagi() {
        let s = LiveSession()
        _ = s.onConnected(nowMs: 0)
        s.onDisconnected()
        XCTAssertEqual(s.state, .idle)
        XCTAssertEqual(s.tick(nowMs: 1_000_000), [])
        XCTAssertEqual(s.requestLive(nowMs: 1_000_000), [])
    }
}
