# Relay usbmuxd Windows (iTunes) -> WSL2 untuk xtool/libimobiledevice.
# Resep: https://github.com/xtool-org/xtool/issues/19 (issue comment rena2019).
# Idempoten; jalankan ulang bila IP vEthernet WSL berubah (reboot WSL).
$ErrorActionPreference = 'Stop'

$alias = (Get-NetAdapter | Where-Object { $_.InterfaceAlias -match 'WSL' } | Select-Object -First 1).InterfaceAlias
if (-not $alias) { Write-Error "Interface vEthernet WSL tidak ditemukan - WSL belum jalan?"; exit 1 }
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -eq $alias } | Select-Object -First 1).IPAddress
if (-not $ip) { Write-Error "IP vEthernet WSL tidak ditemukan"; exit 1 }
Write-Output "WSL interface: $alias  IP: $ip"

#1) firewall: izinkan inbound dari interface WSL (sekali dibuat, dipakai terus)
$ruleName = "WSL usbmux relay"
if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -InterfaceAlias $alias -Action Allow | Out-Null
    Write-Output "firewall rule dibuat: $ruleName"
} else {
    Write-Output "firewall rule sudah ada"
}

#2) portproxy: WSL:27015 -> Windows loopback:27015 (itunes/Apple Mobile Device)
netsh interface portproxy set v4tov4 listenport=27015 connectaddress=127.0.0.1 connectport=27015 listenaddress=$ip | Out-Null
Write-Output "portproxy: ${ip}:27015 -> 127.0.0.1:27015"
netsh interface portproxy show v4tov4 | Out-String

Write-Output "Selesai. Dari WSL: USBMUXD_SOCKET_ADDRESS=${ip}:27015 (sudah di-set otomatis via ~/.bashrc)"
