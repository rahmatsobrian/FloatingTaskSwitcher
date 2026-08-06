# Resource Transfer

Aplikasi transfer file peer-to-peer Android dengan 7 metode koneksi
otomatis (fallback chain), dibuat sebagai alternatif fitur *Resource
Transfer* Mobile Legends: Bang Bang — **tanpa aset, kode, atau API MLBB
apa pun**. Murni aplikasi transfer file umum: bisa dipakai untuk game
apa pun, folder apa pun, file apa pun.

## Status proyek ini

Ini adalah **scaffold produksi yang bisa langsung dibuka di Android
Studio**, bukan aplikasi 100% jadi. Membangun seluruh permintaan asli
(termasuk implementasi jaringan penuh per-transport, test suite lengkap,
diagram arsitektur, dan hardening produksi) adalah pekerjaan berminggu-minggu
untuk tim — tidak realistis diselesaikan dalam satu sesi. Yang sudah
solid di scaffold ini:

**Selesai / siap pakai:**
- Struktur project Gradle Kotlin DSL lengkap (KSP, Hilt, Compose Compiler plugin)
- `AndroidManifest.xml` dengan semua permission API 26–16 (termasuk transisi
  Location→NEARBY_WIFI_DEVICES di API 33, BLUETOOTH_SCAN/CONNECT di API 31)
- Arsitektur Clean Architecture + MVVM + Repository pattern (domain/data/ui)
- `TransportType` enum + `TransportManager` — **logic fallback otomatis**
  sesuai urutan prioritas di brief (Wi-Fi Direct → Nearby → Hotspot → LAN →
  QR → Bluetooth → Manual IP), termasuk `reconnectSkipping()` yang
  melanjutkan dari transport berikutnya tanpa mengulang dari awal
- `TransferEngine` — chunking, SHA256 + CRC32 checksum, manifest builder,
  diff manifest (delta transfer), pause/resume/cancel, speed/ETA calculation
- Room database untuk History, DataStore untuk Settings
- Hilt DI lengkap dengan `@IntoMap` multibinding untuk ke-7 Transport
- Foreground Service dengan notification progress + Pause/Resume/Cancel
- 9 layar Compose Material 3 (Splash, Home, Send, Receive, History,
  Settings, About, Permissions, Logs) dengan tema KernelSU Clean Light,
  dynamic color, dark mode, animated navigation transitions
- Adaptive grid layout di Home (otomatis menyesuaikan phone/tablet/foldable)

## Transport yang sudah beneran jalan

**Manual IP** dan **Wi-Fi LAN** sekarang keduanya fungsional dan berbagi
`ServerSocket`/port yang sama (`8988`) — bedanya cuma cara pengirim
menemukan IP tujuan:

- **Manual IP**: user ketik IP secara langsung di layar Kirim.
- **Wi-Fi LAN**: `LanTransport` pakai NSD (`_resourcetransfer._tcp`) —
  begitu penerima menekan "Mulai Menunggu Pengirim", perangkatnya otomatis
  ikut mengumumkan diri lewat mDNS. Di layar Kirim ada tombol "Cari
  Perangkat di Wi-Fi" yang menampilkan daftar perangkat ketemu; tap salah
  satu otomatis isi field IP.

Protokol pengiriman (`TransferEngine.runSenderSession`/`runReceiverSession`)
sama persis untuk keduanya karena sama-sama berujung ke `SocketTransportChannel`
di port yang sama — LAN discovery cuma lapisan "cara nemuin IP", bukan jalur
transfer terpisah.

**Catatan penting**: NSD discovery di beberapa versi Android (13+) bisa
butuh izin `NEARBY_WIFI_DEVICES`. Ini sekarang **sudah ditangani otomatis**
lewat `PermissionsBanner` (di `ui/components/`) yang muncul di layar Kirim
dan Terima kalau ada izin runtime yang belum di-grant — tinggal tekan
"Berikan Izin", dan banner-nya otomatis hilang begitu semua ter-grant
(termasuk kalau user grant manual lewat Settings, bukan cuma lewat dialog
sistem). Daftar izin per Android version ada di `util/RequiredPermissions.kt`.

## Transport yang masih belum diimplementasikan (stub/`NotImplementedError` dengan komentar arah
implementasi di tiap fungsi):**
- Socket/callback layer nyata untuk Wi-Fi Direct (`WifiP2pManager`),
  Nearby Connections (`NearbyConnectionsClient`), Local Hotspot, Bluetooth
  RFCOMM — kerangka interface dan struktur kelas sudah lengkap, tinggal
  isi implementasi socket per platform API (contoh polanya sudah ada di
  `ManualIpTransport`/`LanTransport`)
- QR code generation/scanning (library ZXing sudah ada di dependencies)
- Enkripsi AES-256 payload
- Unit test, instrumentation test, UI test
- Diagram arsitektur/sequence/class (disarankan dibuat dengan Mermaid
  setelah socket layer selesai, supaya diagram mencerminkan implementasi asli)

## Background survival (Foreground Service)

`TransferForegroundService` sekarang **beneran menjalankan** proses kirim/terima
— bukan cuma nampilin notifikasi. `SendViewModel`/`ReceiveViewModel` cuma
memicu service lewat `Intent` (folder sumber/tujuan + IP tujuan sebagai
extras), lalu observe `TransferEngine.progress` dan `TransferSessionStatus`
(dua Singleton yang sama-sama diakses Service maupun ViewModel). Efeknya:
kalau Activity di-destroy saat app di-background (mis. Android reclaim
memori), transfer tetap jalan karena coroutine kerjanya hidup di
`serviceScope` milik Service, bukan `viewModelScope` yang ikut mati saat
ViewModel di-clear. Tombol Jeda/Lanjut/Batal di UI maupun di notifikasi
sama-sama ngirim `Intent` action ke service yang sama.

## Folder picker & Shizuku

`SendScreen` sekarang benar-benar membuka SAF folder picker
(`ActivityResultContracts.OpenDocumentTree`). Path yang dipilih di-resolve
lewat 3 tingkat:

1. **Direct path** — kalau folder ada di penyimpanan internal utama, tree
   Uri-nya diterjemahkan langsung ke path asli (`/storage/emulated/0/...`)
   tanpa akses privileged apa pun. Ini jalur paling umum dan cukup untuk
   sebagian besar kasus.
2. **Shizuku** — kalau (1) gagal (misalnya folder ada di SD card/OTG) dan
   Shizuku terpasang + izin diberikan, `ShizukuHelper` dipakai untuk
   verifikasi & akses path lewat shell privileged (`dev.rikka.shizuku:api`).
   Berguna khusus untuk kasus custom ROM/root seperti yang biasa kamu
   kerjakan — bisa juga dipakai nanti untuk `find`/`sha256sum`/`cp` di sisi
   shell yang jauh lebih cepat daripada baca-tulis stream Java biasa.
3. **Fallback nama saja** — kalau keduanya gagal, folder cuma tersimpan
   sebagai display name; `TransferEngine.buildManifest()` butuh
   `java.io.File`, jadi tombol "Mulai & Tunggu Penerima" tetap disabled
   sampai ada path yang valid. Langkah berikutnya yang disarankan: bikin
   varian `buildManifest` berbasis `DocumentFile`/`ContentResolver` supaya
   kasus ini juga bisa jalan tanpa Shizuku.

Tombol "Aktifkan Shizuku" di `SendScreen` cuma muncul kalau Shizuku belum
`READY`, dan disembunyikan otomatis begitu status jadi `READY`. Tidak ada
dependency keras ke Shizuku — semua fungsi inti tetap jalan tanpa Shizuku
terpasang sama sekali.

## Struktur folder

```
app/src/main/java/com/siroha/resourcetransfer/
├── domain/
│   ├── model/          # TransportType, DeviceInfo, TransferManifest, TransferProgress
│   ├── transport/       # Transport interface + 7 implementasi + TransportManager
│   └── engine/          # TransferEngine (chunking, checksum, resume)
├── data/
│   ├── local/           # Room: AppDatabase, TransferHistoryEntity/Dao
│   └── datastore/       # SettingsDataStore
├── di/                  # Hilt modules (DatabaseModule, TransportModule)
├── service/             # TransferForegroundService
├── ui/
│   ├── theme/           # Material 3 theme, KernelSU Clean Light palette
│   ├── navigation/      # NavGraph, Destination
│   └── screens/         # 9 screens, masing-masing dengan ViewModel
└── util/                # AppLogger
```

## Langkah berikutnya yang disarankan

1. Buka project di Android Studio (Koala+), sync Gradle
2. Isi implementasi socket nyata untuk `LanTransport` dan `ManualIpTransport`
   dulu (paling sederhana, tidak butuh P2P negotiation) — jadikan ini
   baseline yang berfungsi sebelum mengerjakan `WifiDirectTransport`/`NearbyConnectionsTransport`
   yang lebih kompleks
3. Tambahkan SAF folder picker di `SendScreen`
4. Baru kerjakan transport lain satu per satu — arsitektur `TransportManager`
   tidak perlu diubah saat menambah implementasi baru

## Prinsip desain kunci

- **100% offline** — tidak ada permission INTERNET (`tools:node="remove"`
  di manifest), semua transfer device-to-device
- **Tidak ada kode/aset MLBB** — hanya konsep umum "kirim file yang belum
  dimiliki penerima" via manifest diff, diimplementasikan dari nol
- **Fallback tidak mengulang dari awal** — `TransferEngine` menyimpan
  offset chunk terakhir terlepas dari `Transport` mana yang sedang aktif

## Sumber kirim yang didukung (Send modes)

Layar Kirim sekarang punya 5 mode, dipilih lewat chip di atas:

- **Folder** — seperti sebelumnya, resolusi path langsung (tanpa copy) karena
  folder bisa berukuran GB. Sekarang mempertahankan nama foldernya sendiri
  di sisi penerima (`rootLabel`) — kirim folder "Garena" akan muncul sebagai
  folder "Garena" juga di perangkat penerima, bukan isinya dilempar rata ke
  root folder tujuan.
- **File** — `ActivityResultContracts.OpenMultipleDocuments()`, bisa pilih
  banyak file sekaligus atau satu saja, tipe apa pun.
- **Media** — Android Photo Picker (`PickMultipleVisualMedia`), **tanpa**
  buka Documents UI, khusus foto/video.
- **Teks** — ketik langsung, atau tombol "Tempel Clipboard" buat ambil isi
  clipboard saat ini. Dikirim sebagai file `Teks.txt`.
- **Aplikasi** — daftar aplikasi terinstal (lewat `<queries>` block, bukan
  `QUERY_ALL_PACKAGES`), pilih satu untuk mengekspor APK-nya. Kalau app-nya
  App Bundle (ada split APK), otomatis di-zip jadi satu file `.apks`
  (format yang dipakai installer seperti SAI), bukan dikirim sebagai folder
  berisi banyak potongan APK.

Empat mode terakhir (File/Media/Teks/Aplikasi) melakukan staging: konten
disalin ke folder cache sementara (`util/SendStagingHelper.kt`) supaya bisa
lewat pipeline `buildManifest`/`runSenderSession` yang sama tanpa perubahan
protokol. Mode Folder tetap tidak melakukan copy demi menghindari duplikasi
data untuk folder besar.

## Folder tujuan opsional di penerima

`ReceiveViewModel` sekarang otomatis membuat & memakai folder
`ResourceTransfer/` di penyimpanan utama begitu layar Terima dibuka — tombol
"Mulai Menunggu Pengirim" langsung aktif tanpa perlu pilih folder dulu.
Tombol "Pilih Folder Lain" tetap ada buat yang mau folder custom, dan
"Pakai Default" buat balik ke folder otomatis tadi.

### Known limitations pada fitur baru ini

- Mode Aplikasi: ekspor APK memakai `ApplicationInfo.sourceDir`/`splitSourceDirs`,
  yang umumnya world-readable di Android tanpa root — tapi beberapa ROM yang
  di-harden bisa saja membatasi ini lebih ketat; kalau ekspor gagal, cek Logs.
- Mode File/Media/Teks men-copy konten dulu ke cache sebelum kirim (bukan
  streaming langsung dari Uri asal) — untuk file/media yang sangat besar
  ini menambah waktu tunggu sebelum transfer benar-benar mulai.
