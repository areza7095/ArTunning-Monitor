# ECU Protocol Specification — Bintang Racing Team Juken 5 Plus

**English** · [Bahasa Indonesia](../id/juken5-protocol-spec.md)

> Interoperability reference document for building custom applications (Android/ESP/desktop)
> that talk directly to a Juken 5 Plus ECU. Every claim is backed by **evidence**
> in the form of type/method + IL offsets from the official binary.
>
> **Document version:** 1.0 (2026-10-27)
> **Machine-readable data:** [`opcodes.json`](../opcodes.json) (opcode table, struct layout, timing, transport)

---

## 0. Executive summary

- The ECU↔host protocol is a **line-based ASCII protocol** terminated with `CRLF`, identical on
  every transport (USB-serial / COM, WiFi-TCP, Bluetooth-SPP-virtual-COM).
- TX frame: `<HEX_CMD>` or `<HEX_CMD>;<param>` — `HEX_CMD` = 16-bit opcode in **uppercase**
  hex (`ToString("X")`, no zero padding), params separated by `;` (decimal numbers).
- RX telemetry/status frame: `A603;<TPS>;<BAT>;<CORE>;<RPM>;<EOT×10>;<AFR>;<MAP×10>;<IAT×10>;<PUMP×1000>;<INJ×1000>`
- Bulk transfers (read map/param/log) use markers `#B1#` / `#B2#` / `#B3#` … `*eE*\r\n` with
  **STM32 CRC32** integrity (init `0xFFFFFFFF`, poly `0x04C11DB7`).
- **Bluetooth is absent from the main desktop app** (`Juken_5.exe`); the ECU's BT module appears as a
  Windows virtual COM port (SPP) and is only accessed by `ApplicationUpdater.exe`. On a phone: use a
  BluetoothSocket RFCOMM then send the same protocol bytes (see §8.3).

---

## 1. Sources & analysis method (evidence)

| Item | Value |
|---|---|
| Binary | `C:\Program Files\Bintang Racing Team\Juken 5 Plus (EN)\Juken_5.exe` |
| SHA-256 | `10150ed6d3c315277cbe55342af7498bce9c46abf2707191b29f48b8b86c3112` |
| App version | `Juken_5` 1.0.18.18 (.NET Framework 4.6.1, IL-only, x86_64 PE) |
| PDB (build leftover) | `C:\Desando\Juken 5+ 1.0.13\JUKEN5_Get_Map_New\Juken 5 PC\Juken_5\Juken_5\obj\Debug\Juken_5.pdb` |
| REA evidence | `ev_ae30fa63a024a99d3043cc2ef72a06b4248e3566af16190e0beefc20f1c06f4c` (`inspect_managed_artifact`), `ev_5407f3e4cc08b6715296d7d3d180df389658f94f4a93d12cc5b18a5572febd52` (`inspect_managed_members`) |
| Method | (1) REA managed static inspection (metadata, AssemblyRef, enums, ldstr, call edges) — execution-free; (2) read-only CIL dump via .NET reflection (reads method-body bytes, **without executing target code**); (3) `Marshal.SizeOf/OffsetOf` for struct layout (reads marshaling metadata only) |
| Second binary | `ApplicationUpdater.exe` (Bluetooth path, see §8.3) |

Method note: every opcode number in this document is recomputed from the enum's original decimal
constants via reflection (see `opcodes.json` → `command_families`), not converted by hand.

---

## 2. Topology & transport matrix

```
                         ┌──────────────────────────────────────────┐
   [USB-Serial CH340/    │              ECU Juken 5 Plus            │
    Prolific PL2303] ────┤  identical ASCII line protocol           │
   [WiFi TCP 192.168.2.6:80] ─┤                                    │
   [BT SPP virtual COM] ─┤   (see §8: BT = COM port via SPP)       │
                         └──────────────────────────────────────────┘
```

| Transport | App implementation | Endpoint | Evidence |
|---|---|---|---|
| Serial/COM | `Serial.ReliableSerialPort` (subclass of `SerialPort`) → `SerialCommHandler` | COMn, 57600/460800, 8N1, DTR on | `ReliableSerialPort..ctor IL_0001–IL_0057` |
| WiFi | `SimpleWifi` joins the ECU AP → `WifiCommHandler` (`TcpClient`) | **TCP `192.168.2.6:80`**, probe `0x00` | `WifiCommHandler..ctor IL_0000–IL_007B`, `Connect_Wifi.OnConnectedComplete IL_0029–IL_0031` |
| Bluetooth | **not in `Juken_5.exe`**; only in `ApplicationUpdater.exe` (virtual SPP COM) | BT COM port + custom UUID `2DA0CC0E-1927-4CE8-A6BF-1B7DAF4BB9D4` | enum `ConnectionType = {WIFI, SERIALCOM}`; string scan for `bluetooth` = 0 matches; `ApplicationUpdater.SerialCommunication.scan/connect` |

Serial port auto-detection (USB): WMI `Win32_PnPEntity` looks for `Description = "Prolific USB-to-Serial Comm Port"`
or `"USB-SERIAL CH340"`, then takes `(COMn)` from `Caption` → connects directly
(`Form1_Shown IL_0011–IL_00AC`).

---

## 3. Frame layer (wire format)

### 3.1 TX (host → ECU)

Source: `CommManagement.AddCommMessage(Int32 command, String param, LiveData)`
(`Juken_5.Protocol.CommManagement IL_0000–IL_0057`):

```
param == "" :  "<HEX>"        + "\r\n"
param != "" :  "<HEX>;<param>" + "\r\n"
```

- `<HEX>` = `command.ToString("X")` → **uppercase hex, no zero padding**
  (example: `5633` → `1601`, `6912` → `1B00`).
- `param` is the result of `String.Format` (**decimal** numbers); it already contains `;` for
  multi-value payloads, e.g. a queue entry `(0x1602, "0;5")` → wire: `1602;0;5\r\n`.
- Sending: `SerialCommHandler.WriteCommand(String)` → writes ASCII bytes to the stream
  (`WriteCommand IL_0015–IL_0032`). There is also a `WriteCommand(Byte[])` overload for raw data
  (`AddCommMessageByte`, used by bulk/log operations).
- The app itself once used a lowercase literal (`"150d;0\r\n"`, `SwitchBaudrate IL_0007`) → the ECU
  side is assumed to accept case-insensitive hex *(not yet verified on hardware)*.

### 3.2 RX (ECU → host)

Pipeline (`SerialCommHandler` → `Form1`):

1. `SerialPort_DataReceived` → bytes accumulate in `rawData` (`List<byte>`).
2. `ExtractDataToArray()` splits on **byte `0x0A` (LF)**; the last complete chunk is queued as a
   `List<byte>` **only when its length > 2 bytes**; the remainder stays in `rawData`
   (`ExtractDataToArray IL_0000–IL_00E8`, delimiter `ldc.i4.s 10` in lambda `b__0`).
3. Non-bulk: each line is filtered by `Form1.checkIfValidChar` → only
   `[0-9 A-Z a-z ; . , space - + =]` (`IL_0000–IL_0046`), then `Encoding.ASCII.GetString`
   → `Form1.ProcessSerialData` → `Form1.SetText*` (`CompleteSerialClientHandler_NewDataReceived IL_0034–IL_005F`).
4. Bulk (`SerialCommHandler.BulkReceive = true`): raw bytes (unfiltered) are collected into
   `CommManagement.bulkReceiveBufferByte` with progress `ReceivedCommCountByte/TargetCommCountByte`
   (`CompleteSerialClientHandler_NewDataReceived IL_0088–IL_017D`).

Normalization in `Form1.SetText` (`IL_0482–IL_04F5`):

```
text.Replace("\r\n","").Replace("\r","").ToUpper()  →  split(';') → newData[]
token0 = newData[0]; if length > 4 → take the LAST 4 characters (Substring(len-4))
```

> The decimal separator is `.` (`CultureInfo.InvariantCulture`); the field separator is `;`.

---

## 4. Opcode tables

All values = original enum constants from metadata (`Juken_5.Protocol.CommandList+*`).
Full JSON: `opcodes.json → command_families`.

### 4.1 Ack (`AckCommand`)

| Name | Dec | Hex | Description |
|---|---:|---:|---|
| ACK | 6656 | `0x1A00` | generic ack |
| ACK_PING / ECHO_ACK | 6912 | `0x1B00` | keepalive ping (TX) |
| ACK_BUSY | 7168 | `0x1C00` | ECU busy |
| FEEDBACK_ECHO_ACK | 6913 | `0x1B01` | ping reply (RX) |

### 4.2 Read (`ReadCommand`)

| Name | Dec | Hex | | Name | Dec | Hex |
|---|---:|---:|---|---|---:|---:|
| READ_FUEL_BASE | 5633 | `0x1601` | | ENTER_LIVE | 5642 | `0x160A` |
| READ_FUEL_COR1 | 5634 | `0x1602` | | EXIT_LIVE | 5643 | `0x160B` |
| READ_FUEL_INJT | 5635 | `0x1603` | | ENTER_AUTO_TUNE | 5644 | `0x160C` |
| READ_IGN_ANGLE | 5637 | `0x1605` | | EXIT_AUTO_TUNE | 5645 | `0x160D` |
| READ_USER_DEF | 5638 | `0x1606` | | ENTER_SMART_TUNE | 5646 | `0x160E` |
| PC_READ_USER_DEF | 5639 | `0x1607` | | EXIT_SMART_TUNE | 5647 | `0x160F` |
| PC_READ_SENSOR | 5641 | `0x1609` | | READ_HISTORY | 5648 | `0x1610` |
| READ_ECU_MODEL | 5654 | `0x1616` | | RMT_READ_HISTORY | 5658 | `0x161A` |
| PC_READ_USER_DEF_2 | 5655 | `0x1617` | | READ_PATTERN | 5652 | `0x1614` |
| PC_READ_USER_DEF_3 | 5656 | `0x1618` | | READ_AFR_STD | 5664 | `0x1620` |
| READ_FUEL_COR2 | 5794 | `0x16A2` | | RESET_LOGGER | 5682 | `0x1632` |
| READ_REC_LOGGER | 5683 | `0x1633` | | READ_REC_LOGGER2 | 5684 | `0x1634` |
| READ_LOG_ADDR | 5685 | `0x1635` | | READ_LOGGER_ALL | 5686 | `0x1636` |
| READ_LOGGER_AFR | 5729 | `0x1661` | | READ_LOGGER_EOT | 5730 | `0x1662` |
| READ_LOGGER_AFR_EOT | 5731 | `0x1663` | | READ_LOGGER_VBAT | 5732 | `0x1664` |
| READ_LOGGER_AFR_VBAT | 5733 | `0x1665` | | READ_LOGGER_EOT_VBAT | 5734 | `0x1666` |
| READ_LOGGER_AFR_EOT_VBAT | 5735 | `0x1667` | | READ_LOGGER_MAP | 5736 | `0x1668` |
| READ_LOGGER_AFR_MAP | 5737 | `0x1669` | | READ_LOGGER_EOT_MAP | 5738 | `0x166A` |
| READ_LOGGER_AFR_EOT_MAP | 5739 | `0x166B` | | READ_LOGGER_VBAT_MAP | 5740 | `0x166C` |
| READ_LOGGER_AFR_VBAT_MAP | 5741 | `0x166D` | | READ_LOGGER_EOT_VBAT_MAP | 5742 | `0x166E` |
| READ_LOGGER_ALL_PARAMS | 5743 | `0x166F` | | *(= REQ_LOG_DATA, same value)* | | |
| SEND_HISTORY_RMT | 38426 | `0x961A` | | SEND_AFR_STD | 38432 | `0x9620` |
| END_HISTORY | 6682 | `0x1A1A` | | SAVE_EDIT_HISTORY | 9858 | `0x2682` |

### 4.3 Write (`WriteCommand`)

| Name | Dec | Hex | | Name | Dec | Hex |
|---|---:|---:|---|---|---:|---:|
| SAVE_FUEL_BASE | 9729 | `0x2601` | | SAVE_FUEL_COR1 | 9730 | `0x2602` |
| SAVE_FUEL_INJT | 9731 | `0x2603` | | SAVE_IGN_ANGLE | 9733 | `0x2605` |
| SAVE_FUEL_AS_BM | 9738 | `0x260A` | | SAVE_TEMP_DATA | 9739 | `0x260B` |
| FACTORY_RESET | 9740 | `0x260C` | | SAVE_AUTO_CALCULATING | 9744 | `0x2610` |
| GENERATE_PATTERN | 9748 | `0x2614` | | SAVE_EDIT_PATTERN | 9749 | `0x2615` |
| SAVE_FUEL_COR2 | 9890 | `0x26A2` | | SAVE_EDIT_HISTORY* | 9858 | `0x2682` |
| READ_TPS_CAL | 17921 | `0x4601` | | READ_DIAG2 | 17922 | `0x4602` |
| READ_ENGINE_RUN | 17923 | `0x4603` | | READ_ENGINE_STOP | 17925 | `0x4605` |
| READ_TIME_RUN | 17926 | `0x4606` | | CHECK_TPS_RANGE | 18016 | `0x4660` |
| SAVE_EMAP_RANGE | 13825 | `0x3601` | | SAVE_RESET_MAP | 13826 | `0x3602` |
| SAVE_EMAP | 13827 | `0x3603` | | SAVE_AUTO_CORRECTION | 13828 | `0x3604` |
| SAVE_LIMITER | 13829 | `0x3605` | | SAVE_AUTO_IGN | 13830 | `0x3606` |
| SAVE_CORE | 13831 | `0x3607` | | SAVE_TURBO | 13832 | `0x3608` |
| SAVE_IMOB | 13833 | `0x3609` | | SAVE_DWELL | 13834 | `0x360A` |
| SAVE_JET_FUEL | 13835 | `0x360B` | | SAVE_TPS_RATE | 13836 | `0x360C` |
| SAVE_FUEL_START | 13837 | `0x360D` | | SAVE_WARM_UP | 13838 | `0x360E` |
| SAVE_RPM_IDLE | 13839 | `0x360F` | | SAVE_FUEL_IDLE | 13840 | `0x3610` |
| SAVE_FUEL_DEC | 13841 | `0x3611` | | SAVE_TPS_INITIAL | 13842 | `0x3612` |
| SAVE_TPS_WOT | 13843 | `0x3613` | | SAVE_SHIFTER | 13844 | `0x3614` |
| SAVE_JJSORE_SET | 13845 | `0x3615` | | SAVE_JJSORE_RANGE | 13846 | `0x3616` |
| SAVE_VVA | 13847 | `0x3617` | | SAVE_SMART_TUNE | 13848 | `0x3618` |
| SAVE_AUTO1_TUNE | 13849 | `0x3619` | | SAVE_FAN_TEMP | 13850 | `0x361A` |
| SAVE_BANK_ANGLE | 13851 | `0x361B` | | SAVE_TURBO_ENGINE | 13852 | `0x361C` |
| SAVE_SHIFT_LIGHT | 13853 | `0x361D` | | SAVE_AUTO_PROTECT | 13854 | `0x361E` |
| SAVE_AUTO_TUNE | 13872 | `0x3630` | | AUTO_TUNE_RUN | 13873 | `0x3631` |
| AUTO_TUNE_STOP | 13874 | `0x3632` | | SAVE_AGES_MAP | 13875 | `0x3633` |
| READ_AGES_MAP | 13876 | `0x3634` | | MAINTAIN_START | 14336 | `0x3800` |
| MAINTAIN_STOP | 14592 | `0x3900` | | MAINTAIN_REPORT | 47104 | `0xB800` |

\* `SAVE_EDIT_HISTORY` is listed under the Read family but carries a Write-space value (`0x2682`).

### 4.4 Test (`TestCommand`)

| Name | Dec | Hex | | Name | Dec | Hex |
|---|---:|---:|---|---|---:|---:|
| TEST_PUMP | 20481 | `0x5001` | | TEST_VVA | 20482 | `0x5002` |
| STOP_ENGINE | 20483 | `0x5003` | | TEST_LAMP | 20484 | `0x5004` |
| START_ENGINE | 20487 | `0x5007` | | EXECUTE_UPDATE | 20489 | `0x5009` |
| TEST_MIL | 20490 | `0x500A` | | TEST_TEMP_IND | 20491 | `0x500B` |
| TEST_FAN | 20492 | `0x500C` | | TEST_CHOKE | 20493 | `0x500D` |
| TEST_TACHO | 20494 | `0x500E` | | SEND_LOGGER | 38448 | `0x9630` |
| END_LOGGER | 38450 | `0x9632` | | SEND_REC_ADDR | 38453 | `0x9635` |

### 4.5 Bulk (`BulkDataCommand` / `BulkDataReply`)

| Name | Dec | Hex | | Name | Dec | Hex |
|---|---:|---:|---|---|---:|---:|
| REQ_CRC | 5376 | `0x1500` | | FeedBack_CRC | 38144 | `0x9500` |
| REQ_PARAM | 5377 | `0x1501` | | SEND_CRC_PARAM | 38145 | `0x9501` |
| REQ_MAP | 5378 | `0x1502` | | SEND_CRC_MAP | 38146 | `0x9502` |
| RCV_PARAM | 5379 | `0x1503` | | SEND_LOG_SIZE | 38149 | `0x9505` |
| RCV_MAP | 5380 | `0x1504` | | | | |
| REQ_LOG_SIZE | 5381 | `0x1505` | | | | |
| REQ_NOT_CALC | 5383 | `0x1507` | | | | |
| REQ_LOG_DATA | 5743 | `0x166F` | | | | |

### 4.6 Reply (`ReplyCommand`)

| Name | Dec | Hex | | Name | Dec | Hex |
|---|---:|---:|---|---|---:|---:|
| FEEDBACK_READ_FUEL_BASE | 38401 | `0x9601` | | FEEDBACK_READ_FUEL_COR1 | 38402 | `0x9602` |
| FEEDBACK_READ_FUEL_INJT | 38403 | `0x9603` | | FEEDBACK_READ_IGN_ANGLE | 38405 | `0x9605` |
| FEEDBACK_READ_USER_DEF_1 | 38406 | `0x9606` | | FEEDBACK_READ_HISTORY | 38416 | `0x9610` |
| FEEDBACK_READ_PATTERN | 38420 | `0x9614` | | FEEDBACK_READ_USER_DEF_2 | 38422 | `0x9616` |
| FEEDBACK_READ_FUEL_COR2 | 38562 | `0x96A2` | | FEEDBACK_READ_TPS_CAL | 42497 | `0xA601` |
| FEEDBACK_READ_DIAG2 | 42498 | `0xA602` | | **FEEDBACK_READ_ENGINE_RUN = SEND_ENGINE_RUN** | 42499 | **`0xA603`** |
| FEEDBACK_READ_ENGINE_STOP | 42501 | `0xA605` | | DISP_TIME_RUN | 42502 | `0xA606` |
| ECU_SEND_REG_OK | 42503 | `0xA607` | | ECU_SEND_VALID | 42504 | `0xA608` |
| RESET_TIME_REC | 42512 | `0xA610` | | SAVE_TIMEOUT_SERVICE | 13878 | `0x3636` |

### 4.7 Other

| Name | Dec | Hex |
|---|---:|---:|
| ENTER_BOOTLOADER | 29481 | `0x7329` |

### 4.8 Frame literals outside the enum (`ldstr` evidence in IL)

| Frame | Direction | Function | Evidence |
|---|---|---|---|
| `150d;0` / `150d;1` | TX | switch ECU baud → 57600 / 460800 | `Form1.SwitchBaudrate IL_0007` |
| `4605` | TX | request **status** (reply = `A603` frame) | `Form1.SendStatus IL_001F` |
| `8601;…` `8602;…` `8603;…` `8605;…` | TX | remote-map row write (61/61/21/31 values) | `Form1.SetText IL_167F, IL_18FD, IL_1CC4, IL_2073` |
| `6601;…`–`6605;…` | TX | remote set progress step after reply `7601`–`7605` | `Form1.SetText IL_45F1–IL_4DCA` |
| `#;8080;<SSID>;<PASS>;` | TX | ECU WiFi provisioning | `Form1.ChangeSSIDMain IL_0012–IL_0038` |
| `2602;<core>;<tpsIdx>;v0..v60` | TX | write 1 FuelCor1 row (61 RPM values) | `Form1.SetTextTelemetri IL_0177–IL_0220` |
| `360C;<a>;<b>;<c>` | TX | SAVE_TPS_RATE | `Form1.SetText IL_3BDE` |

---

## 5. Reply dispatch & command correlation

### 5.1 Queued command correlation (`CommandValuePairs`)

The app keeps `CommManagement.commandOut` = the opcode currently awaiting a reply.
Early part of `Form1.SetText` (`IL_0026–IL_00BF`):

1. `parts = text.Split(';')`; `int.TryParse(parts[0], NumberStyles.HexNumber /*515*/, out reply)`
   → **token[0] is parsed as hexadecimal**.
2. If `commandOut > 0` and `CommandValuePairs[commandOut] == reply` → **Dequeue** the pending queue,
   `commandOut = 0`, continue processing the reply.

13 correlation pairs (`JukenCommProtocol.CreateDictionary IL_0006–IL_00D6`) — also present in
`opcodes.json → command_to_expected_reply`:

| Request | Expected reply |
|---|---|
| `1601` | `9601` |
| `1602` | `9602` |
| `16A2` | `96A2` |
| `1603` | `9603` |
| `1605` | `9605` |
| `1610` | `9610` |
| `1614` | `9614` |
| `1606` | `9606` |
| `1607` | `9606` |
| `1616` | `9616` |
| `1617` | `9616` |
| `1620` | `9620` |
| `1500` | `9500` |

### 5.2 First-token dispatch (hash-switch, `Form1.SetText IL_053F–IL_076E`)

`token0` (last 4 characters, already `ToUpper`) is switched; known cases:

| Token | Meaning / behavior | Jump evidence |
|---|---|---|
| `9113` | data-log: `ListIndexDataLog.Add(newData[1])` | `IL_064E → IL_076F` |
| `9606` | stop `timer2` (user-def read step), `isSendStatus=0` | `IL_065F → IL_0787` |
| `9616` | reply to `1617`: live-entry preparation (`var_awal=1`…); if `isLiveState` → `isLiveState=0` | `IL_0670 → IL_07C2..IL_0847` |
| `9618` | close `GetMapAwal` progress window | `IL_0681 → IL_0848` |
| **`A603`** | **telemetry/status frame** → parse gauges (§9) | `IL_0692 → IL_0854` |
| `1B01` | `ConnectionState=1`, `time_echo` reset | `IL_06A3 → IL_0DEA`, reset at `IL_0E48` |
| `9610` | history read reply | `IL_06B4 → IL_0E4E` |
| `9620` | AFR std reply | `IL_06C5 → IL_10C2` |
| `9614` | pattern read reply | `IL_06D6 → IL_1253` |
| `1A00` | **ACK** → further state machine (send remote map rows, save steps, `4605` refresh, etc.) | `IL_06E7 → IL_140A..` |
| `A602` | DIAG2: decode status bits `00=OK,01=ERROR,10=NA,11=RESERVED`/`E.OFF` | `IL_06F8 → IL_3DAA..` |
| `A601` | TPS_CAL: parse value + voltage display (format `N2`, unit `V`) | `IL_0709 → IL_4268..` |
| `B800` | MAINTAIN_REPORT: runtime counter (convert with `3600`/`60`) | `IL_071A → IL_43B6..` |
| `7602`/`7601`/`7603`/`7605` | remote-map step reply → send `6601/6602/6603/6605` | `IL_072B/073C/074D/075E` |
| others | **ignored** (`ret`) | `IL_057A, IL_066F…` |

The correlated `9500` reply is handled separately (`IL_00C2–IL_03F8`):
- **3 tokens** → `calCRC = UInt32(parts[1],16)`, `mapCRC = UInt32(parts[2],16)` (hex!)
  → `JukenDataManagement.isGetMapValid(calCRC, mapCRC)` → `"New Get Map Complete Finish CRC"` / `"…Failed"`.
- **2 tokens** → the log CRC path (`"New Get Data Log CRC Complete"`).

---

## 6. Connection state machine

### 6.1 Connect & handshake

```
open transport
  serial : ReliableSerialPort.Open() → ContinuousRead (async 4096-byte loop)
  wifi   : TcpClient(ip,80).GetStream() → WriteByte(0x00) → BeginReceive(4095)
send "1B00\r\n" repeatedly:
  USB path : directly (WriteCombination) + sleep 100 ms, max 5×   (SerialHandler_NewConnection IL_00B8–IL_00E1)
  WiFi path: WriteCommand_ (extra 1500 ms wait before write) + sleep 500 ms, max 7× (IL_0053–IL_008B; WriteCommand_ IL_0015)
every incoming RX byte → isWifiReady = 1 (CompleteSerialClientHandler IL_000D)
"1B01" reply → ConnectionState = 1, time_echo = 0 (SetText IL_0DEA/IL_0E48)
failure → "CONNECTION ERROR, PLEASE TRY AGAIN"
```

### 6.2 Keepalive & watchdog

| Timer | Interval | Behavior | Evidence |
|---|---:|---|---|
| `ulang_echo` | **3000 ms** | send `1B00\r\n` | `Form1.ulang_echo_Tick IL_0001` |
| `echo` | **1700 ms** | `time_echo++`; if `>10` and not live → label "Not Connected", stop timer, `diskonek` (≈17 s without a reply) | `Form1.echo_Tick IL_0002–IL_0093` |
| `lanjut_echo()` | once | reset `time_echo=0`, send `1B00` | `Form1.lanjut_echo IL_0001–IL_0038` |

### 6.3 Entering live mode

```
send "1617\r\n"            → wait for reply "9616"        (SerialHandler_NewConnection IL_0129)
timer_live_enter (500 ms): send "160A\r\n" repeatedly     (timer_live_enter_Tick IL_0028)
   max 25×, log "kirim live awal" / "live awal stop"
first "A603" reply: newData[3] (core) == mapping_live ?
   yes → masuk_live = 2 (ready to render) ; "Stopa603"     (SetText IL_09A2–IL_09DE)
   no  → reset + message "Different Core"                  (IL_09DF–IL_09FD)
leave live : send "160B\r\n" (timer_live_enter_Tick IL_0078)
gauge render : live_counter timer 10 ms → jalan_live()     (live_counter_Tick IL_0001)
pattern status : SendStatus() → send "4605\r\n", Sleep(500) (SendStatus IL_001F–IL_002E)
```

> **Android & iOS app behavior (auto-live):** since the MVP version, the live session starts
> **automatically without a Start button** — hybrid trigger: the first `1B01` reply **OR** a
> **1.5 s** fallback after the connection opens (specifically for when the ECU never answers the
> echo). The frame order remains exactly as above: `1617` then `160A` every 500 ms (max 25×).
> The **Stop Live** button disables auto until the next connection session; the
> **Start Live** button remains usable manually at any time. The trigger is implemented in
> `LiveSession` (`autoLive` + `autoLiveFallbackMs=1500`) on both platforms.

### 6.4 Command queue (TX) — `BulkCommandProcess`

`CommManagement.BulkCommandProcess` → async loop `<<BulkCommandProcess>b__0>d.MoveNext` (292 instructions):

1. Wait for `SerialComBusy == false` (poll `Task.Delay(100)`).
2. Set `SerialComBusy = true`, `totalCommandQueue = queue.Count`.
3. Per queue entry: `Peek()` → **`commandOut = key`** (`IL_0100`) → `AddCommMessage(cmd, param, liveData)`
   → set `RetryTimeoutCount = 10`, budget `timeout = 100` (`IL_013B–IL_0145`).
4. Poll the reply every **`Task.Delay(10)`** (10 ms); exit when `commandOut == 0`
   (reset by `SetText` when the correlation matches) → progress `% = remaining/total`.
5. Timeout → recount, `RetryTimeoutCount--`, **resend** (`IL_0243–IL_026D`);
   exhausted → drop the queue, log `"BulkCommandTimeout too long"` / `"…retry timeout"`.
6. `RetryTimeoutCount` defaults to **2** in the ctor, set to **10** when sending
   (`CommManagement..ctor IL_0001`, MoveNext `IL_013B`).

Related delays: `timerDelayBetweenSend` = **1 ms** one-shot timer (`CommManagement..ctor IL_0020–IL_003A`),
`WriteCommand_` waits **1500 ms** before writing (`SerialCommHandler.WriteCommand_ IL_0015`),
serial `WriteTimeout` **100 ms** → **10000 ms** during `AddCommMessageByte` (`IL_001F/IL_0045`).

---

## 7. Bulk transfer & CRC

### 7.1 Markers & flow

| Role | Value | Evidence |
|---|---|---|
| Begin (cal/param) | `#B1#` | `CommandList..cctor IL_0000–IL_000A` |
| Begin (map) | `#B2#` | `IL_000F–IL_0014` |
| Begin (log) | `#B3#` | `IL_0019–IL_001E` |
| End (all) | `*eE*\r\n` (bytes `42,101,69,42,13,10`; lookup uses the `Trim("\r\n")` result → `*eE*`) | `IL_001E–IL_0028`; `ProcessBulkReceiveMessage IL_0034–IL_004E` |

Bulk RX flow (`CommManagement.ProcessBulkReceiveMessage IL_01F9–IL_03C4`), selected from
`commandOut` when the bulk starts:

| commandOut | Marker | Payload | Handling |
|---|---|---|---|
| `1501` REQ_PARAM | `#B1#` | exactly `GetCalSize()` = **1012** bytes | `GetCalStructure` → `cal` / `cal_fac` (param token `"0"`/`"1"`) |
| `1502` REQ_MAP | `#B2#` | exactly `GetMapSize()` = **10248** bytes | `GetMapStructure` → `map` |
| `166F` REQ_LOG | `#B3#` | multiple of `GetLogSize()` = **28** bytes | `CRC_LOG = STM32CRC(payload)`; `ConvertByteToStructureArray` → `log_t[]` |

Progress: `ReceivedCommCountByte += n`, `% = received*100 / TargetCommCountByte`
(`CompleteSerialClientHandler IL_0128–IL_0178`).

### 7.2 CRC (STM32 hardware CRC)

`JukenDataManagement.ComputeSTM32Checksum` — two overloads (`UInt32[]` and `Byte[]`):

- **init** `0xFFFFFFFF` (`ldc.i4.m1`), **poly** `0x04C11DB7` (`ldc.i4 79764919`).
- Input bytes are rounded up to a multiple of 4 (`Math.Round(len/4)*4`), copied into a `uint32[]` via
  `Buffer.BlockCopy` → **little-endian**.
- Per word: `crc ^= word`; 32 iterations: `crc = (crc<<1) ^ poly` if MSB set, else `crc<<1`.
- No reflection, no final XOR. (C pseudocode: `opcodes.json → crc.pseudo_c`)

Usage (`isGetMapValid`, `IL_0000–IL_008D`):

```
CRC_CAL = crc(GetBytesArray(cal))   → set struct CRC_CAL
CRC_MAP = crc(GetBytesArray(map))   → set struct CRC_MAP
valid = (calCrc == CRC_CAL) && (mapCrc == CRC_MAP)
```

The `9500;<calCRC_hex>;<mapCRC_hex>` reply uses hex without the `0x` prefix
(`Convert.ToUInt32(parts[1],16)` — `SetText IL_00E3–IL_00F6`).

---

## 8. Transport profiles

### 8.1 UART / Serial (USB or virtual BT COM)

`ReliableSerialPort..ctor IL_0001–IL_0057`:

| Parameter | Value |
|---|---|
| Parity / DataBits / StopBits | `None(0)` / `8` / `One(1)` |
| Handshake | `None(0)` |
| DTR | **enabled** (`ldc.i4.1`) |
| NewLine | `Environment.NewLine` |
| ReceivedBytesThreshold | 1024 |
| WriteTimeout | 100 ms (default), 10000 ms for bulk bytes |
| Read buffer / mode | 4096, **continuous async read** (`ContinuousRead IL_0000–IL_0049`) |

Baud rate:

- **57600** — normal baud (strong inference: `ApplicationUpdater.AutoDetectDevice` tries `57600`
  first, then `115200`, then `57600` again — `IL_0004–IL_0098`; `SwitchBaudrate` mode 0 = 57600).
- **460800** — fast logger mode, selected by sending `150d;1` then reopening the port
  (`SwitchBaudrate IL_002C–IL_007A`); revert with `150d;0`.
- Users can change it via the **Set Baud Rate** menu (`setBaudRateToolStripMenuItem_Click IL_008C–IL_0091`).

### 8.2 WiFi

- `Connect_Wifi` (SimpleWifi) joins the **ECU AP** (SSID changed via the ChangeSSID form).
- Default endpoint `192.168.2.6:80` (`WifiCommHandler..ctor IL_0001–IL_000E`;
  `OnConnectedComplete IL_0029–IL_0031` → `Form1.Connect_Handler("192.168.2.6",80,…)`)
  → `connectionType = WIFI(0)` (`Connect_Handler IL_002A`).
- After `GetStream()`: **send 1 byte `0x00`** (probe) then `BeginReceive` with a
  **4095**-byte buffer (`Open() IL_0048–IL_0068`, `BufferSize = 4095` const).
- Timeouts: `commTimeout` 10000 ms, `reconnectTimeOut` 15 s (`..ctor IL_0030–IL_004D`).
- **ECU SSID/password provisioning** (app acts as an already-connected client):
  `#;8080;<SSID>;<PASSWORD>;\r\n` — SSID ≤ 31 chars, password 8..63 chars
  (`ChangeSSID.button3_Click IL_0045–IL_0069`, `ChangeSSIDMain IL_0000–IL_0038`).

### 8.3 Bluetooth (facts & mobile implications)

Evidence that `Juken_5.exe` does **not** use Bluetooth:

1. The complete `AssemblyRef` list has 25 entries — **no** `InTheHand` (`inspect_managed_artifact`,
   evidence `ev_ae30fa63…`).
2. String scan of `Juken_5.exe` (ASCII + UTF-16) for `bluetooth`/`rfcomm` = **0 matches**.
3. Enum `Juken_5.SerialCommHandler+ConnectionType` = **`{WIFI, SERIALCOM}`** only
   (`inspect_managed_members`, evidence `ev_5407f3e4…`).

The Bluetooth user is **`ApplicationUpdater.exe`** (evidence: TypeRef
`InTheHand.Net.Personal v3.5.605.0`):

- `BluetoothComPortFinder.FindAll()` — enumerates COM ports via `SetupDiGetClassDevs(GUID_DEVCLASS_PORTS)`,
  reading the registry `HKLM\SYSTEM\CurrentControlSet\Services\BTHPORT\Parameters\Devices\<addr>`
  for the remote service name (`IL_0000–IL_026C`).
- `SerialCommunication.GetBluetoothPort()` — WMI `Win32_PnPEntity` with
  `ClassGuid {4d36e978-e325-11ce-bfc1-08002be10318}` (Ports) → lists `"… (COMn)(BT)"`.
- `SerialCommunication.connect(com, baud)` — if the label contains `(BT)` → opens a
  **plain `System.IO.Ports.SerialPort`** (`TryConnectSerial`, `NewLine="\r\n"`,
  `ReceivedBytesThreshold`, 8N1) → **meaning: the ECU BT module is a classic SPP (RFCOMM) module
  that appears as a Windows virtual COM port**.
- Discovery only: `scan()` → `BluetoothRadio.PrimaryRadio.Mode=1`,
  `BluetoothClient.DiscoverDevicesInRange()/DiscoverDevices()`.
- `mUUID = 2DA0CC0E-1927-4CE8-A6BF-1B7DAF4BB9D4` is used by `BluetoothUtils()` to build a
  `BluetoothEndPoint` + `BluetoothClient` (no `Connect` call is visible on the normal path) —
  this custom UUID is a fallback candidate, **not** the standard SPP UUID.

**Implications for mobile apps (Android):**

```
Standard SPP UUID : 00001101-0000-1000-8000-00805F9B34FB   (try first)
Fallback UUID     : 2DA0CC0E-1927-4CE8-A6BF-1B7DAF4BB9D4   (ApplicationUpdater evidence)
after connect     : send the §3 protocol bytes exactly (ASCII lines, CRLF)
```

iOS does not provide classic SPP without MFi → the realistic iOS path is WiFi/TCP (§8.2).

---

## 9. Telemetry / status stream — frame `A603`

Received after `ENTER_LIVE` (`160A`) or as the reply to a `4605` status request.
Parsing: `Form1.SetText` `A603` branch (`IL_0854–IL_0DDF`); fields are read from
`newData[]` (split on `;`).

```
A603;<TPS>;<BAT>;<CORE>;<RPM>;<EOT×10>;<AFR>;<MAP×10>;<IAT×10>;<PUMP×1000>;<INJ×1000>
 idx: 1      2      3      4       5        6      7       8       9         10
```

| Idx | Field | Wire type | App scale (IL evidence) | Notes |
|---:|---|---|---|---|
| 0 | header | string | `A603` (last 4 chars of token) | `FEEDBACK_READ_ENGINE_RUN` |
| 1 | TPS | float | `Convert.ToInt32` → gauge; row binning: `1..2 → 1`; `3..94 → floor((floor(tps*2/10)*10/2 + 5)/5)`; `≥95 → 20` | `IL_0A9F–IL_0B47`, `IL_0B87–IL_0B95` |
| 2 | Battery | float (decimal `.`) | displayed as-is; LED `nilai_batery = round(v) − 9` | `IL_0BA1–IL_0BEA` |
| 3 | Core id | int | compared against `mapping_live`; mismatch → `"Different Core"` | `IL_09A2–IL_09FD` |
| 4 | RPM | float | clamp `1000..16000`; columns `posisi_rpm = (q−1000)/250`, `posisi_rpm_ig = (q−1000)/500`, `q = round(rpm*4/1000)*1000/4`; `cek_rpm = (int)` | `IL_0A22–IL_0A9A`, `IL_0BF6–IL_0C02`, `IL_0DB4–IL_0DC1` |
| 5 | EOT | float ×10 | `angka_eot = raw / 10` (°C) | `IL_0C0E–IL_0C24` |
| 6 | AFR | float | `angka_afr = raw token`; band `nilai_afr = (int)raw − 9`: `≤2` blue, `3` green, `4` yellow, `>4` red | `IL_0C30–IL_0C4E`, `jalan_live IL_0404–IL_07F6` |
| 7 | MAP | float ×10 | `nilai_map = map_2d = raw / 10`; token cleaned with `Replace("A603","")` | `IL_0CBE–IL_0D06`, `IL_0D6E–IL_0D9A` |
| 8 | IAT | float ×10 | `nilai_iat = raw / 10` (token cleaned with `Replace("A603","")`) | `IL_0D21–IL_0D46` |
| 9 | Pump | float ×1000 | `angka_ipump = raw / 1000` (A) | `IL_0C59–IL_0C99` |
| 10 | Inj | float ×1000 | `angka_iinj = raw / 1000` (A) | `IL_0C78–IL_0CA6` |

The front-panel variant (branch `assigndataStatus`, `IL_0854–IL_0983`) uses index subset
**1,2,4,5,8** with the same scaling → `angka_tps_depan`, `angka_bat_depan` (`+ "V"`),
`angka_rpm_depan`, `angka_eot_depan = [5]/10`, `nilai_iat_depan = [8]/10`.

Rendering: `jalan_live()` (832 instructions) is run by `live_counter` every **10 ms**
(`live_counter_Tick IL_0001`) → Nevron gauges, VU meters, OxyPlot/Datavis charts, live tables.

---

## 10. Data structure layout (byte-level)

All structs: `StructLayout.Sequential` + `MarshalAs` (U1/I2/U2/ByValArray), serialized with
`Marshal.StructureToPtr`/`PtrToStructure` (`getBytes/fromBytes IL_0000–IL_003D`) →
**little-endian, no special packing**. Offsets below were computed with `Marshal.OffsetOf`;
sizes with `Marshal.SizeOf`.

Point-count constants (`JukenDataStructure`): `FUEL_POINTS=61`, `TPS_POINTS=21`, `CHAR_POINTS=21`,
`ECU_PARAMS_POINTS=128`, `BIKE_FEATURE_POINTS=29`, `APP_SETTING_POINTS=32`,
`TEMP_POINTS=16`, `VBAT_POINTS=16`.

### 10.1 `log_t` — 28 bytes (1 data-logger sample)

| Off | Field | Type |
|---:|---|---|
| 0 | Tps_Idx | u8 |
| 1 | RpmIdx | u8 |
| 2 | Afr | u16 |
| 4 | ETemp | u16 |
| 6 | ATemp | u16 |
| 8 | Map | u16 |
| 10 | VBat | u16 |
| 12 | ERpm | u16 |
| 14 | TPS | u16 |
| 16 | IPump | u16 |
| 18 | IInj | u16 |
| 20 | PW | u16 |
| 22 | ATemp_Ex | u16 |
| 24 | TimerSpeed | u16 |
| 26 | Speed | u16 |

### 10.2 `coreJ6_t` (map) — 10248 bytes

| Off | Field | Type | Count | Bytes |
|---:|---|---|---:|---:|
| 0 | FuelBase | i16[] | 1281 | 2562 |
| 2562 | InjTmg | u16[] | 1281 | 2562 |
| 5124 | IgnTmg | u16[] | 1281 | 2562 |
| 7686 | FuelCor1 | i8[] | 1281 | 1281 |
| 8967 | FuelCorTB1 | i8[] | 1281 | 1281 |

`1281 = 21 TPS rows × 61 RPM columns`; flat index `tpsIdx*61 + rpmIdx`
*(strong inference: the UI uses `posisi_tps` 0..20 and `posisi_rpm` 0..60)*.

### 10.3 `ecu_pt` — 256 bytes (128 × u16)

Offsets `0..118` (words, step 2):

| Off | Field | | Off | Field | | Off | Field |
|---:|---|---|---:|---|---|---:|---|
| 0 | TpsLow | | 40 | BankA_Enable | | 80 | TCS_Status |
| 2 | TpsHigh | | 42 | Lo_Range | | 82 | TCS_DiffSpd |
| 4 | RpmLimit | | 44 | Mid_Range | | 84–94 | Reserve3–Reserve8 |
| 6 | RpmVVA | | 46 | Hi_Range | | 96 | StartSA |
| 8 | Inj2_Enable | | 48 | ST_TpxIdx | | 98 | ImobEn |
| 10 | TpsIdx_Inj2 | | 50 | ST_RpmStart | | 100 | ImobLock |
| 12 | RpmInj2 | | 52 | ST_RpmStop | | 102 | Dwell |
| 14 | TE_Enable | | 54 | ST_Res | | 104–114 | Key1H..Key3L |
| 16 | RpmStartTurbo | | 56 | JJS_TpsRange | | 116 | RUNtime |
| 18 | RpmStopTurbo | | 58 | JJS_RpmRange | | 118 | VBATcal |
| 20 | FanTemp_Set | | 60 | Retard_Temp | | 120–254 | Reserve21–Reserve88 |
| 22 | RpmIdle_Set | | 62 | Retard_Angle | | | |
| 24 | DecTime | | 64–68 | AP_Enable/AP_Temp/AP_Limit | | | |
| 26 | ISMpos_Set | | 70 | MapNo | | | |
| 28 | Rpm_ShiftLight | | 72 | JF_Cal | | | |
| 30 | AirTemp_Set | | 74 | JF_Pos | | | |
| 32 | FuelAir (i16) | | 76 | JF_Rate | | | |
| 34 | ShiftDelay | | 78 | JF_PWidth | | | |
| 36 | WarmUp (i16) | | | | | | |
| 38 | FuelStart (i16) | | | | | | |

### 10.4 `appl_t` — 64 bytes (32 × u16/i16, off 0..62 step 2)

`START_RPM, STALL_RPM, FUEL_PUMP_TOUT, FUEL_ON_MAX, SATURATION_Timeout, CAL_FM_MACCEL,
CAL_FM_MDECEL, MSTART_Decay, MSTART_Decay_Timeout, TIP_IN_VALUE, TIP_IN_Decay,
TIP_IN_Decay_Timeout, TIP_OUT_VALUE, TIP_OUT_Decay, TIP_OUT_Decay_Timeout, FAN_TEMP_OFFSET,
TEMP_WARNING, WARNING_INIT_TOUT, ECO_INIT_TOUT, MIN_SYNC_RATIO, MAX_SYNC_RATIO, AFRtempCal,
AFRmin, AFRmax, AFRminVal(i16), AFRmaxVal(i16), TempAdj, AppResv2..AppResv6`

### 10.5 `Bike_ft` — 29 bytes

Offsets 0..5 u8 enums: `Trans(gear_t), Cylinder(cylinder_t), OBD(obd_t), TACHO(tacho_t),
FTSW(input_t), CHOKE(choke_t)`; offsets 6..28 u8: `MAP_TOOTH_START, MAP_TOOTH_END, MAP_DROPval,
MAP, O2, IAT, MIL_Led, T_IND_Led, ECO_Led, ISS, ABS, BANK_A, ESTOP, STSW, NSW, CLTSW, FAN,
EX_IN, VVA, LAMP, O2H, SPEED, CPS`

### 10.6 `engine_pt` — 20 bytes

Offsets 0..7 u8: `TeethBase, Teeth, GapRatio, Teeth_4C, MissTooth1, MissTooth2, TEETHsync, PeriodPoint`;
offsets 8..18 u16: `Tdc, Spark1RefAngle, Fuel1RefAngle, Spark2RefAngle, Fuel2RefAngle, DeltaTps`

### 10.7 `cal_t` — 1012 bytes

| Off | Field | Type | Bytes |
|---:|---|---|---:|
| 0 | EcuInitCode | u32 | 4 |
| 4 | FuelStart_added | u16[16] | 32 |
| 36 | FuelWarmUp_added | i16[16] | 32 |
| 68 | SparkWarmUp_added | i16[16] | 32 |
| 100 | StepPos | i16[16] | 32 |
| 132 | ChokeDelay | u16[16] | 32 |
| 164 | FuelBat_added | u8[16] | 16 |
| 180 | DwellCal | i16[16] | 32 |
| 212 | Dwell | u16[61] | 122 |
| 334 | Cor1Table | i16[61] | 122 |
| 456 | Cor2Table | i16[61] | 122 |
| 578 | Ecu | `ecu_pt` | 256 |
| 834 | pad | u8 | 1 |
| **835** | **EcuName** | u8[21] | 21 |
| **856** | **EcuType** | u8[21] | 21 |
| **877** | **SoftVer** | u8[21] | 21 |
| 898 | App | `appl_t` | 64 |
| 962 | BikeF | `Bike_ft` | 29 |
| 991 | tcs | u8 | 1 |
| 992 | EngineS | `engine_pt` | 20 |

> **ECU identity** (`EcuName`/`EcuType`/`SoftVer`) is read via the `#B1#` bulk (REQ_PARAM).
> The ECU model also has an enum `ChargerModelComm = {JUKEN5=0, JUKEN5_PLUS=1, JUKEN6=2}`
> and a `Form_Juken6` form.

---

## 11. Example transcripts

**1. Handshake (WiFi/serial):**

```
TX  1B00\r\n
RX  1B01                 (ConnectionState=1)
TX  1B00\r\n  (keepalive every 3 s)
```

**2. Enter live + stream:**

```
TX  1617\r\n
RX  9616;…               (preparation)
TX  160A\r\n             (every 500 ms until the A603 core matches)
RX  A603;12;12.6;0;3450;852;12.5;1013;345;1520;890
      │   │    │   │    │    │    │    │    │     │
      TPS BAT  CORE RPM  EOT  AFR  MAP  IAT  PUMP  INJ
      12% 12.6V core 3450 85.2 12.5 101.3 34.5 1.520A 0.890A
                id   rpm  °C  :1   kPa   °C
TX  1B00\r\n
```

**3. Read CRC + map:**

```
TX  1500\r\n
RX  9500;5A3F;9BC1        (calCRC & mapCRC, hex)
TX  1501\r\n
RX  #B1# <1012-byte cal_t> *eE*\r\n
TX  1502\r\n
RX  #B2# <10248-byte coreJ6_t> *eE*\r\n
     → recompute CRC32-STM32 locally, match against 9500
```

**4. Read one map row (non-bulk path):**

```
TX  1602;0;5\r\n          (READ_FUEL_COR1 core 0 row 5)
RX  9602;…;v0;v1;…;v60   (61 RPM values)
```

**5. Switch baud to logger mode:**

```
TX  150d;1\r\n            (ECU moves to 460800)
     …reopen the COM port at 460800, sleep 200 ms…
```

**6. ECU WiFi provisioning:**

```
TX  #;8080;MyEcuWifi;rahasia123;\r\n
```

---

## 12. Minimal quickstart (live monitor)

```text
1. Transport:
   Android BT  : BluetoothSocket.createRfcommSocketToServiceRecord(SPP UUID)
                 fallback: 2DA0CC0E-1927-4CE8-A6BF-1B7DAF4BB9D4
   TCP/WiFi    : connect 192.168.2.6:80, send 1 byte 0x00
2. Handshake   : send "1B00\r\n" every 300-500 ms until "1B01" arrives
                 (disconnect on timeout if >17 s without RX)
3. Enter live  : send "1617\r\n" (optional), then "160A\r\n" every 500 ms
                 until an "A603;..." frame arrives
4. Parsing     : split ';' → follow the §9 scaling table
5. Keepalive   : "1B00\r\n" every 3 s; reset the watchdog timer on any RX
6. Leave       : "160B\r\n"
7. Don't forget: filter the RX charset (§3.2), all numbers decimal with '.', InvariantCulture
```

---

## 13. Open items & verification methods

| # | Item | How to verify |
|---|---|---|
| 1 | Periodic `A603` streaming vs `4605` replies | serial/WiFi capture while live + while idle |
| 2 | The `8601/8602/8603/8605` & `6601–6605` frame family (remote feature) — server/destination not present in this binary | capture + deeper read of `Send_Map_Remote` |
| 3 | The `7601–7605` reply family (origin: ECU vs server) | capture |
| 4 | Default `Form1._Baudrate` (never seen initialized) | run the app + read runtime `Properties.Settings` |
| 5 | Correlation of `1501/1502/166F` (replies are binary bulk, not text) — how `commandOut` is reset | capture + deeper dump of `Form1.BulkReceiveProcess` |
| 6 | Does the ECU accept lowercase hex (`150d`)? | send `150d;0` vs `150D;0` and compare |
| 7 | ECU BT module: standard SPP vs custom UUID | test from an Android phone with both UUIDs |
| 8 | Absolute sensor scales (EOT×10 confirmed; MAP unit = kPa?) | validate against measuring tools / the official app |

## Appendix A — Quick evidence index

| Claim | Primary evidence |
|---|---|
| No Bluetooth in the main app | `inspect_managed_artifact` `ev_ae30fa63…` (25 AssemblyRefs without InTheHand); string scan 0 matches; enum `ConnectionType={WIFI,SERIALCOM}` (`ev_5407f3e4…`) |
| TX frame = `HEX;param\r\n` | `CommManagement.AddCommMessage IL_0018–IL_0057` |
| RX split on LF, charset filter | `SerialCommHandler.ExtractDataToArray IL_000E–IL_00E8`; `Form1.checkIfValidChar IL_0000–IL_0046` |
| token[0] parsed as hex | `Form1.SetText IL_0029–IL_003A` (`NumberStyles=515` = HexNumber) |
| 13 correlation pairs | `JukenCommProtocol.CreateDictionary IL_0006–IL_00D6` |
| `A603` dispatch etc. | `Form1.SetText IL_053F–IL_076E` (hash switch) + jump table |
| 3 s keepalive / 1.7 s watchdog | `Form1.ulang_echo_Tick IL_0001`; `echo_Tick IL_0002–IL_0065` (intervals from `InitializeComponent`: `ulang_echo=3000`, `echo=1700`) |
| Live entry `1617`→`160A` | `SerialHandler_NewConnection IL_0129–IL_0144`; `timer_live_enter_Tick IL_0028` (interval 500) |
| Bulk markers & sizes | `CommandList..cctor IL_0000–IL_0028`; `GetCalSize/GetMapSize/GetLogSize IL_0000–IL_000F` |
| STM32 CRC32 | `ComputeSTM32Checksum IL_0000–IL_0041/IL_0000–IL_008D`; `isGetMapValid IL_0039` (`79764919`) |
| `A603` live scaling | `Form1.SetText IL_0854–IL_0DDF`; `jalan_live IL_013C–IL_0BDE` |
| TCP `192.168.2.6:80` + `0x00` probe | `WifiCommHandler..ctor/Open IL_0001–IL_0068`; `Connect_Wifi.OnConnectedComplete IL_0029` |
| UART 8N1 + DTR, threshold 1024 | `ReliableSerialPort..ctor IL_002C–IL_0057` |
| 57600/460800 baud via `150d` | `Form1.SwitchBaudrate IL_0007–IL_007A`; `ApplicationUpdater.AutoDetectDevice IL_0004–IL_0098` |
| BT = virtual SPP COM (updater) | `ApplicationUpdater.SerialCommunication.connect/FindSerialPort`; `BluetoothComPortFinder.FindAll IL_0000–IL_026C` |
| Custom BT UUID | `SerialCommunication..ctor IL_000C–IL_0016` (`2DA0CC0E-1927-4CE8-A6BF-1B7DAF4BB9D4`) |
| WiFi provisioning `#;8080;…` | `Form1.ChangeSSIDMain IL_0012–IL_0038` |
| Struct layout | `Marshal.SizeOf/OffsetOf` (see §10; recorded in `opcodes.json → structs`) |

## Appendix B — Related document structure

- [`opcodes.json`](../opcodes.json) — all opcodes (decimal+hex), correlation pairs, dispatch,
  frame literals, bulk markers, CRC parameters, telemetry table, **layout of every struct (per-field offsets)**,
  timing, transport profiles, open items.
