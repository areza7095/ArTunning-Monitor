// AUTO-GENERATED dari docs/opcodes.json - JANGAN EDIT TANGAN.
// Regenerasi: node scripts/gen-opcodes.mjs

public enum Opcodes {
    // Opcode keluarga ack (dari metadata Juken_5.exe).
    public enum Ack {
        public static let ACK: Int = 0x1A00
        public static let ACK_PING: Int = 0x1B00
        public static let ACK_BUSY: Int = 0x1C00
        public static let ECHO_ACK: Int = 0x1B00
        public static let FEEDBACK_ECHO_ACK: Int = 0x1B01
    }

    // Opcode keluarga read (dari metadata Juken_5.exe).
    public enum Read {
        public static let READ_FUEL_BASE: Int = 0x1601
        public static let READ_FUEL_COR1: Int = 0x1602
        public static let READ_FUEL_COR2: Int = 0x16A2
        public static let READ_FUEL_INJT: Int = 0x1603
        public static let READ_IGN_ANGLE: Int = 0x1605
        public static let READ_HISTORY: Int = 0x1610
        public static let READ_PATTERN: Int = 0x1614
        public static let READ_USER_DEF: Int = 0x1606
        public static let PC_READ_USER_DEF: Int = 0x1607
        public static let PC_READ_SENSOR: Int = 0x1609
        public static let READ_ECU_MODEL: Int = 0x1616
        public static let PC_READ_USER_DEF_2: Int = 0x1617
        public static let PC_READ_USER_DEF_3: Int = 0x1618
        public static let ENTER_LIVE: Int = 0x160A
        public static let EXIT_LIVE: Int = 0x160B
        public static let ENTER_AUTO_TUNE: Int = 0x160C
        public static let EXIT_AUTO_TUNE: Int = 0x160D
        public static let ENTER_SMART_TUNE: Int = 0x160E
        public static let EXIT_SMART_TUNE: Int = 0x160F
        public static let RMT_READ_HISTORY: Int = 0x161A
        public static let SEND_HISTORY_RMT: Int = 0x961A
        public static let END_HISTORY: Int = 0x1A1A
        public static let SAVE_EDIT_HISTORY: Int = 0x2682
        public static let READ_AFR_STD: Int = 0x1620
        public static let SEND_AFR_STD: Int = 0x9620
        public static let RESET_LOGGER: Int = 0x1632
        public static let READ_REC_LOGGER: Int = 0x1633
        public static let READ_REC_LOGGER2: Int = 0x1634
        public static let READ_LOG_ADDR: Int = 0x1635
        public static let READ_LOGGER_ALL: Int = 0x1636
        public static let READ_LOGGER_ALL_PARAMS: Int = 0x166F
        public static let READ_LOGGER_AFR: Int = 0x1661
        public static let READ_LOGGER_EOT: Int = 0x1662
        public static let READ_LOGGER_AFR_EOT: Int = 0x1663
        public static let READ_LOGGER_VBAT: Int = 0x1664
        public static let READ_LOGGER_AFR_VBAT: Int = 0x1665
        public static let READ_LOGGER_EOT_VBAT: Int = 0x1666
        public static let READ_LOGGER_AFR_EOT_VBAT: Int = 0x1667
        public static let READ_LOGGER_MAP: Int = 0x1668
        public static let READ_LOGGER_AFR_MAP: Int = 0x1669
        public static let READ_LOGGER_EOT_MAP: Int = 0x166A
        public static let READ_LOGGER_AFR_EOT_MAP: Int = 0x166B
        public static let READ_LOGGER_VBAT_MAP: Int = 0x166C
        public static let READ_LOGGER_AFR_VBAT_MAP: Int = 0x166D
        public static let READ_LOGGER_EOT_VBAT_MAP: Int = 0x166E
    }

    // Opcode keluarga write (dari metadata Juken_5.exe).
    public enum Write {
        public static let SAVE_FUEL_BASE: Int = 0x2601
        public static let SAVE_FUEL_COR1: Int = 0x2602
        public static let SAVE_FUEL_COR2: Int = 0x26A2
        public static let SAVE_FUEL_INJT: Int = 0x2603
        public static let SAVE_IGN_ANGLE: Int = 0x2605
        public static let SAVE_FUEL_AS_BM: Int = 0x260A
        public static let SAVE_TEMP_DATA: Int = 0x260B
        public static let FACTORY_RESET: Int = 0x260C
        public static let SAVE_AUTO_CALCULATING: Int = 0x2610
        public static let GENERATE_PATTERN: Int = 0x2614
        public static let SAVE_EDIT_PATTERN: Int = 0x2615
        public static let READ_TPS_CAL: Int = 0x4601
        public static let READ_DIAG2: Int = 0x4602
        public static let READ_ENGINE_RUN: Int = 0x4603
        public static let READ_ENGINE_STOP: Int = 0x4605
        public static let READ_TIME_RUN: Int = 0x4606
        public static let CHECK_TPS_RANGE: Int = 0x4660
        public static let SAVE_EMAP_RANGE: Int = 0x3601
        public static let SAVE_RESET_MAP: Int = 0x3602
        public static let SAVE_EMAP: Int = 0x3603
        public static let SAVE_AUTO_CORRECTION: Int = 0x3604
        public static let SAVE_LIMITER: Int = 0x3605
        public static let SAVE_AUTO_IGN: Int = 0x3606
        public static let SAVE_CORE: Int = 0x3607
        public static let SAVE_TURBO: Int = 0x3608
        public static let SAVE_IMOB: Int = 0x3609
        public static let SAVE_DWELL: Int = 0x360A
        public static let SAVE_JET_FUEL: Int = 0x360B
        public static let SAVE_TPS_RATE: Int = 0x360C
        public static let SAVE_FUEL_START: Int = 0x360D
        public static let SAVE_WARM_UP: Int = 0x360E
        public static let SAVE_RPM_IDLE: Int = 0x360F
        public static let SAVE_FUEL_IDLE: Int = 0x3610
        public static let SAVE_FUEL_DEC: Int = 0x3611
        public static let SAVE_TPS_INITIAL: Int = 0x3612
        public static let SAVE_TPS_WOT: Int = 0x3613
        public static let SAVE_SHIFTER: Int = 0x3614
        public static let SAVE_JJSORE_SET: Int = 0x3615
        public static let SAVE_JJSORE_RANGE: Int = 0x3616
        public static let SAVE_VVA: Int = 0x3617
        public static let SAVE_SMART_TUNE: Int = 0x3618
        public static let SAVE_AUTO1_TUNE: Int = 0x3619
        public static let SAVE_FAN_TEMP: Int = 0x361A
        public static let SAVE_BANK_ANGLE: Int = 0x361B
        public static let SAVE_TURBO_ENGINE: Int = 0x361C
        public static let SAVE_SHIFT_LIGHT: Int = 0x361D
        public static let SAVE_AUTO_PROTECT: Int = 0x361E
        public static let SAVE_AUTO_TUNE: Int = 0x3630
        public static let AUTO_TUNE_RUN: Int = 0x3631
        public static let AUTO_TUNE_STOP: Int = 0x3632
        public static let SAVE_AGES_MAP: Int = 0x3633
        public static let READ_AGES_MAP: Int = 0x3634
        public static let MAINTAIN_START: Int = 0x3800
        public static let MAINTAIN_STOP: Int = 0x3900
        public static let MAINTAIN_REPORT: Int = 0xB800
    }

    // Opcode keluarga test (dari metadata Juken_5.exe).
    public enum Test {
        public static let TEST_PUMP: Int = 0x5001
        public static let TEST_VVA: Int = 0x5002
        public static let STOP_ENGINE: Int = 0x5003
        public static let TEST_LAMP: Int = 0x5004
        public static let START_ENGINE: Int = 0x5007
        public static let TEST_MIL: Int = 0x500A
        public static let TEST_TEMP_IND: Int = 0x500B
        public static let TEST_FAN: Int = 0x500C
        public static let TEST_CHOKE: Int = 0x500D
        public static let TEST_TACHO: Int = 0x500E
        public static let EXECUTE_UPDATE: Int = 0x5009
        public static let SEND_LOGGER: Int = 0x9630
        public static let END_LOGGER: Int = 0x9632
        public static let SEND_REC_ADDR: Int = 0x9635
    }

    // Opcode keluarga bulk_command (dari metadata Juken_5.exe).
    public enum BulkCommand {
        public static let REQ_CRC: Int = 0x1500
        public static let REQ_PARAM: Int = 0x1501
        public static let REQ_MAP: Int = 0x1502
        public static let RCV_PARAM: Int = 0x1503
        public static let RCV_MAP: Int = 0x1504
        public static let REQ_LOG_SIZE: Int = 0x1505
        public static let REQ_LOG_DATA: Int = 0x166F
        public static let REQ_NOT_CALC: Int = 0x1507
    }

    // Opcode keluarga bulk_reply (dari metadata Juken_5.exe).
    public enum BulkReply {
        public static let FeedBack_CRC: Int = 0x9500
        public static let SEND_CRC_PARAM: Int = 0x9501
        public static let SEND_CRC_MAP: Int = 0x9502
        public static let SEND_LOG_SIZE: Int = 0x9505
    }

    // Opcode keluarga reply (dari metadata Juken_5.exe).
    public enum Reply {
        public static let FEEDBACK_READ_USER_DEF_1: Int = 0x9606
        public static let FEEDBACK_READ_USER_DEF_2: Int = 0x9616
        public static let FEEDBACK_READ_FUEL_BASE: Int = 0x9601
        public static let FEEDBACK_READ_FUEL_COR1: Int = 0x9602
        public static let FEEDBACK_READ_FUEL_COR2: Int = 0x96A2
        public static let FEEDBACK_READ_FUEL_INJT: Int = 0x9603
        public static let FEEDBACK_READ_IGN_ANGLE: Int = 0x9605
        public static let FEEDBACK_READ_HISTORY: Int = 0x9610
        public static let FEEDBACK_READ_PATTERN: Int = 0x9614
        public static let FEEDBACK_READ_TPS_CAL: Int = 0xA601
        public static let FEEDBACK_READ_DIAG2: Int = 0xA602
        public static let FEEDBACK_READ_ENGINE_RUN: Int = 0xA603
        public static let FEEDBACK_READ_ENGINE_STOP: Int = 0xA605
        public static let ECU_SEND_REG_OK: Int = 0xA607
        public static let ECU_SEND_VALID: Int = 0xA608
        public static let SEND_ENGINE_RUN: Int = 0xA603
        public static let RESET_TIME_REC: Int = 0xA610
        public static let SAVE_TIMEOUT_SERVICE: Int = 0x3636
        public static let DISP_TIME_RUN: Int = 0xA606
    }

    // Opcode keluarga other (dari metadata Juken_5.exe).
    public enum Other {
        public static let ENTER_BOOTLOADER: Int = 0x7329
    }

}

/// Pasang korelasi request -> kode balasan yang ditunggu (sumber: JukenCommProtocol.CreateDictionary).
public let COMMAND_TO_REPLY: [Int: Int] = [
    0x1500: 0x9500,
    0x1601: 0x9601,
    0x1602: 0x9602,
    0x1603: 0x9603,
    0x1605: 0x9605,
    0x1606: 0x9606,
    0x1607: 0x9606,
    0x1610: 0x9610,
    0x1614: 0x9614,
    0x1616: 0x9616,
    0x1617: 0x9616,
    0x1620: 0x9620,
    0x16A2: 0x96A2
]

/// Marker bulk transfer (spec bagian 7).
public enum BulkMarkers {
    public static let beginCal = "#B1#"
    public static let beginMap = "#B2#"
    public static let beginLog = "#B3#"
    public static let endTrimmed = "*eE*"
    public static let endRaw = "*eE*\\r\\n"
}

/// Header frame telemetri/status (spec bagian 9).
public let TELEMETRY_HEADER = "A603"
