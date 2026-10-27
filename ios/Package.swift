// swift-tools-version:6.0
// Build iOS via xtool (lihat ios/README.md); buka file ini langsung di Xcode
// bila kelak punya Mac. Sumber kebenaran protokol tetap docs/opcodes.json.
import PackageDescription

let package = Package(
    name: "ArTunningMonitor",
    platforms: [
        .iOS(.v15),
        .macOS(.v14), // hanya agar eksplisit; `swift test` di Linux host tidak butuh ini
    ],
    products: [
        .library(
            name: "ArTunningMonitor",
            targets: ["ArTunningMonitor"]
        ),
    ],
    targets: [
        // Satu module: Shared (protokol, murni) + App (SwiftUI, di-guard #if os(iOS)
        // agar target tetap compile di Linux host utk `swift test`).
        .target(
            name: "ArTunningMonitor",
            path: "Sources/ArTunningMonitor"
        ),
        .testTarget(
            name: "ArTunningMonitorTests",
            dependencies: ["ArTunningMonitor"],
            path: "Tests/ArTunningMonitorTests"
        ),
    ]
)
