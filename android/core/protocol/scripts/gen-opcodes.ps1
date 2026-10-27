# Codegen: docs/opcodes.json -> Opcodes.kt (Kotlin constants)
# Dipanggil Gradle task :core:protocol:generateOpcodes (Exec).
# Catatan: file ini sengaja murni ASCII agar aman dibaca PowerShell 5.1 (ANSI).
param(
    [Parameter(Mandatory = $true)][string]$JsonPath,
    [Parameter(Mandatory = $true)][string]$OutDir
)

$ErrorActionPreference = 'Stop'
$j = Get-Content -Raw -LiteralPath $JsonPath | ConvertFrom-Json

$pkgDir = Join-Path $OutDir "id/arvoid/artunningmonitor/protocol"
New-Item -ItemType Directory -Path $pkgDir -Force | Out-Null
$outFile = Join-Path $pkgDir "Opcodes.kt"

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("// AUTO-GENERATED dari docs/opcodes.json - JANGAN EDIT TANGAN.")
[void]$sb.AppendLine("// Regenerasi: gradlew :core:protocol:generateOpcodes")
[void]$sb.AppendLine("package id.arvoid.artunningmonitor.protocol")
[void]$sb.AppendLine()

# families: object per enum dengan const val (hex), dibungkus object Opcodes
$familyMap = [ordered]@{
    ack           = 'Ack'
    read          = 'Read'
    write         = 'Write'
    test          = 'Test'
    bulk_command  = 'BulkCommand'
    bulk_reply    = 'BulkReply'
    reply         = 'Reply'
    other         = 'Other'
}
[void]$sb.AppendLine("object Opcodes {")
foreach ($prop in $j.command_families.PSObject.Properties) {
    $jsonName = $prop.Name
    if (-not $familyMap.Contains($jsonName)) { throw "family tidak dikenal: $jsonName" }
    $objName = $familyMap[$jsonName]
    [void]$sb.AppendLine("    /** Opcode keluarga $jsonName (dari metadata Juken_5.exe). */")
    [void]$sb.AppendLine("    object $objName {")
    foreach ($it in $prop.Value) {
        $name = $it.name
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw "nama tidak valid: $name" }
        [void]$sb.AppendLine("        const val $name = $($it.hex)")
    }
    [void]$sb.AppendLine("    }")
    [void]$sb.AppendLine()
}
[void]$sb.AppendLine("}")

# command -> expected reply
[void]$sb.AppendLine("/** Pasang korelasi request -> kode balasan yang ditunggu (hex string -> kode). */")
[void]$sb.AppendLine("val COMMAND_TO_REPLY: Map<Int, Int> = mapOf(")
$pairs = @()
foreach ($prop in $j.command_to_expected_reply.PSObject.Properties) {
    $pairs += "    0x$($prop.Name) to 0x$($prop.Value)"
}
$nl = [char]10
[void]$sb.AppendLine(($pairs -join ("," + $nl)))
[void]$sb.AppendLine(")")
[void]$sb.AppendLine()

# bulk markers & telemetry header
$b = $j.bulk
$endRaw = [string]$b.end_marker_raw
[void]$sb.AppendLine("/** Marker bulk transfer (spec bagian 7). */")
[void]$sb.AppendLine("object BulkMarkers {")
[void]$sb.AppendLine("    const val BEGIN_CAL = `"$($b.begin_markers.cal)`"")
[void]$sb.AppendLine("    const val BEGIN_MAP = `"$($b.begin_markers.map)`"")
[void]$sb.AppendLine("    const val BEGIN_LOG = `"$($b.begin_markers.log)`"")
[void]$sb.AppendLine("    const val END_TRIMMED = `"$($b.end_marker_trimmed)`"")
[void]$sb.AppendLine("    const val END_RAW = `"$endRaw`"")
[void]$sb.AppendLine("}")
[void]$sb.AppendLine()
[void]$sb.AppendLine("/** Header frame telemetri/status (spec bagian 9). */")
[void]$sb.AppendLine("const val TELEMETRY_HEADER = `"$($j.telemetry_A603.header)`"")

[System.IO.File]::WriteAllText($outFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output "generated: $outFile"
