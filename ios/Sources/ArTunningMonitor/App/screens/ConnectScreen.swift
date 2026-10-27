#if os(iOS)
import Foundation
import SwiftUI

/// Tab Connect — jalur iOS = WiFi ECU (TCP); iOS tidak punya BT SPP.
struct ConnectScreen: View {
    @EnvironmentObject private var vm: AppViewModel
    @Environment(\.colorScheme) private var scheme

    private var dark: Bool { scheme == .dark }
    private var connected: Bool {
        vm.status != .disconnected && vm.status != .error
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                StatusCard()

                if vm.status == .connecting || vm.status == .handshaking {
                    SkeletonBox()
                }

                // ---- WiFi ECU (opsional: auto-join AP) ----
                sectionHeader("WiFi ECU (opsional)")
                VStack(spacing: 8) {
                    TextField("SSID ECU", text: $vm.ssid)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                    SecureField("Password WiFi", text: $vm.wifiPassword)
                        .textFieldStyle(.roundedBorder)
                    Button {
                        vm.joinECUWifi()
                    } label: {
                        HStack {
                            Image(systemName: "wifi")
                            Text(vm.joinedWifi ? "Sudah join — tes lagi" : "Join WiFi ECU")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(vm.ssid.trimmingCharacters(in: .whitespaces).isEmpty)
                }

                // ---- Endpoint TCP ----
                sectionHeader("Endpoint ECU (TCP)")
                HStack(spacing: 8) {
                    TextField("IP", text: $vm.host)
                        .keyboardType(.decimalPad)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                    TextField("Port", text: $vm.portText)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 84)
                }

                // ---- Aksi utama ----
                if connected {
                    Button {
                        vm.disconnect()
                    } label: {
                        Text("Disconnect")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(AppPalette.error(dark))
                } else {
                    Button {
                        vm.connect()
                    } label: {
                        HStack {
                            Image(systemName: "link")
                            Text("Connect")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.sand)
                    .foregroundStyle(Color.onSand)
                }

                Text("iOS tidak mendukung Bluetooth SPP — app memakai WiFi ECU (default TCP 192.168.2.6:80). iPhone harus berada di AP WiFi ECU yang sama.")
                    .font(.footnote)
                    .foregroundStyle(AppPalette.onSurfaceMuted(dark))
            }
            .padding(16)
        }
        .background(AppPalette.background(dark))
    }

    @ViewBuilder
    private func sectionHeader(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundStyle(AppPalette.onSurfaceMuted(dark))
            Spacer()
        }
    }
}
#endif

