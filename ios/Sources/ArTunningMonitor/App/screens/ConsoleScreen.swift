#if os(iOS)
import Foundation
import SwiftUI

private let timeFormatter: DateFormatter = {
    let f = DateFormatter()
    f.dateFormat = "HH:mm:ss.SSS"
    return f
}()

/// Tab Console — log RX/TX mentah + kirim frame manual (alat validasi ke ECU).
struct ConsoleScreen: View {
    @EnvironmentObject private var vm: AppViewModel
    @Environment(\.colorScheme) private var scheme
    @State private var input = ""

    private var dark: Bool { scheme == .dark }
    private var connected: Bool {
        vm.status != .disconnected && vm.status != .error
    }

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Log protokol")
                    .font(.headline)
                    .foregroundStyle(AppPalette.onSurface(dark))
                Spacer()
                Button("Clear") { vm.clearLog() }
                    .buttonStyle(.bordered)
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 2) {
                        ForEach(vm.log) { line in
                            logRow(line)
                        }
                        Color.clear
                            .frame(height: 1)
                            .id("bottom")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
                }
                .onChange(of: vm.log.count) { _ in
                    proxy.scrollTo("bottom", anchor: .bottom)
                }
            }

            HStack(spacing: 8) {
                TextField("contoh: 1B00", text: $input)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.characters)
                    .disabled(!connected)
                Button {
                    vm.sendRaw(input)
                    input = ""
                } label: {
                    Image(systemName: "paperplane.fill")
                        .frame(width: 44, height: 34)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.sand)
                .foregroundStyle(Color.onSand)
                .disabled(!connected || input.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(16)
        .background(AppPalette.background(dark))
    }

    @ViewBuilder
    private func logRow(_ line: IosLogLine) -> some View {
        // Prefiks teks (TX/RX/--) selalu ada — warna hanya pelengkap (aturan NomadKit).
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(timeFormatter.string(from: Date(timeIntervalSince1970: Double(line.timeMs) / 1000)))
                .foregroundStyle(AppPalette.onSurfaceMuted(dark))
            Text(line.prefixTag)
                .fontWeight(.semibold)
                .foregroundStyle(colorFor(line.dir))
            Text(line.text)
                .foregroundStyle(colorFor(line.dir))
        }
        .font(.system(.caption, design: .monospaced))
        .lineLimit(2)
    }

    private func colorFor(_ dir: IosLogDir) -> Color {
        switch dir {
        case .tx: return AppPalette.info(dark)
        case .rx: return AppPalette.onSurface(dark)
        case .info: return AppPalette.onSurfaceMuted(dark)
        }
    }
}

private extension IosLogLine {
    var prefixTag: String {
        switch dir {
        case .tx: return "TX"
        case .rx: return "RX"
        case .info: return "--"
        }
    }
}
#endif

