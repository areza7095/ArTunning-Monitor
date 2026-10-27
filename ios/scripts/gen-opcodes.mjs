// Codegen: docs/opcodes.json -> Shared/Opcodes.swift
// Dipakai manual / di CI: node scripts/gen-opcodes.mjs [jsonPath] [outPath]
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const jsonPath = process.argv[2] ?? resolve(here, "../../docs/opcodes.json");
const outPath = process.argv[3] ?? resolve(here, "../Sources/ArTunningMonitor/Shared/Opcodes.swift");

const j = JSON.parse(readFileSync(jsonPath, "utf8"));

const familyMap = {
  ack: "Ack",
  read: "Read",
  write: "Write",
  test: "Test",
  bulk_command: "BulkCommand",
  bulk_reply: "BulkReply",
  reply: "Reply",
  other: "Other",
};

const escSwift = (s) => String(s).replace(/\\/g, "\\\\").replace(/"/g, '\\"');

const L = [];
L.push("// AUTO-GENERATED dari docs/opcodes.json - JANGAN EDIT TANGAN.");
L.push("// Regenerasi: node scripts/gen-opcodes.mjs");
L.push("");

let total = 0;
L.push("public enum Opcodes {");
for (const [jsonName, objName] of Object.entries(familyMap)) {
  const fam = j.command_families?.[jsonName];
  if (!fam) throw new Error("family tidak dikenal: " + jsonName);
  L.push(`    // Opcode keluarga ${jsonName} (dari metadata Juken_5.exe).`);
  L.push(`    public enum ${objName} {`);
  for (const it of fam) {
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(it.name)) {
      throw new Error("nama identifikasi tidak valid: " + it.name);
    }
    L.push(`        public static let ${it.name}: Int = ${it.hex}`);
    total += 1;
  }
  L.push("    }");
  L.push("");
}
L.push("}");
L.push("");

L.push("/// Pasang korelasi request -> kode balasan yang ditunggu (sumber: JukenCommProtocol.CreateDictionary).");
L.push("public let COMMAND_TO_REPLY: [Int: Int] = [");
const pairs = Object.entries(j.command_to_expected_reply ?? {}).map(
  ([k, v]) => `    0x${k}: 0x${v}`,
);
L.push(pairs.join(",\n"));
L.push("]");
L.push("");

L.push("/// Marker bulk transfer (spec bagian 7).");
L.push("public enum BulkMarkers {");
L.push(`    public static let beginCal = "${escSwift(j.bulk.begin_markers.cal)}"`);
L.push(`    public static let beginMap = "${escSwift(j.bulk.begin_markers.map)}"`);
L.push(`    public static let beginLog = "${escSwift(j.bulk.begin_markers.log)}"`);
L.push(`    public static let endTrimmed = "${escSwift(j.bulk.end_marker_trimmed)}"`);
L.push(`    public static let endRaw = "${escSwift(j.bulk.end_marker_raw)}"`);
L.push("}");
L.push("");

L.push("/// Header frame telemetri/status (spec bagian 9).");
L.push(`public let TELEMETRY_HEADER = "${escSwift(j.telemetry_A603.header)}"`);

mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, L.join("\n") + "\n", "utf8");

const familyCount = Object.values(j.command_families).reduce((n, a) => n + a.length, 0);
console.log(`generated: ${outPath}`);
console.log(`opcodes: ${total} (checksum json families: ${familyCount}, pairs: ${pairs.length})`);
if (total !== familyCount) {
  console.error("MISMATCH antara total generasi dan isi json");
  process.exit(1);
}
