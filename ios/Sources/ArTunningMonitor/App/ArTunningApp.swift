#if os(iOS)
import SwiftUI

@main
struct ArTunningApp: App {
    @StateObject private var vm = AppViewModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(vm)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var vm: AppViewModel
    @State private var tab = 0
    @State private var wasStreaming = false

    var body: some View {
        TabView(selection: $tab) {
            ConnectScreen()
                .tabItem {
                    Label("Connect", systemImage: "wifi")
                }
                .tag(0)
            LiveScreen()
                .tabItem {
                    Label("Live", systemImage: "speedometer")
                }
                .tag(1)
            ConsoleScreen()
                .tabItem {
                    Label("Console", systemImage: "terminal")
                }
                .tag(2)
        }
        .tint(Color.sand) // aksi utama dominan Sand (NomadKit Do)
        // Auto-pindah ke tab Live saat streaming MULAI (hanya transisi masuk) —
        // gauge langsung terlihat tanpa klik.
        .onChange(of: vm.status) { newStatus in
            let streaming = newStatus == .streaming
            if streaming && !wasStreaming { tab = 1 }
            wasStreaming = streaming
        }
    }
}
#endif

