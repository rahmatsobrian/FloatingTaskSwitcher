# Floating Task Switcher

Floating overlay multitasking assistant for Android 10 (API 29) – Android 16.
Kotlin, 100% Jetpack Compose, Material 3 + Material You (Dynamic Color), MVVM,
Hilt, Coroutines/Flow, DataStore, Room-ready.

## Cara membuka

1. Buka folder ini di Android Studio (Koala/Ladybug atau lebih baru, dengan AGP 8.6+ dan JDK 17).
2. Biarkan Gradle sync mengunduh dependency (Compose BOM, Hilt, libsu, Shizuku API).
3. Jalankan konfigurasi `app` di emulator/device Android 10+.

## Yang sudah diimplementasikan (fungsional, bukan mock)

- **Deteksi mode otomatis** Root (Magisk/KernelSU/APatch via libsu) → Shizuku → Standard,
  dengan fallback otomatis tanpa restart (`OperatingModeManager`).
- **Root tidak pernah diminta otomatis** — prompt `su` hanya muncul setelah user menekan
  tombol di Permission Manager.
- **Floating overlay service** (`OverlayService`): bubble draggable, snap ke tepi layar,
  expand/collapse ke panel horizontal dock, notifikasi foreground dengan aksi
  Pause/Resume/Exit.
- **Recent apps** diambil dari `UsageStatsManager`; saat Root Mode aktif, diperkaya dengan
  parsing `dumpsys activity recents` (best-effort, fallback aman jika format berubah).
- **Switch app**: Standard/Shizuku via launch intent resmi; Root via `am start` melalui `su`.
- **Permission Manager**: status real-time untuk Overlay, Accessibility, Usage Access,
  Notifikasi, Battery Optimization, Shizuku, dan Root — masing-masing dengan alasan
  dan tombol aktivasi langsung ke halaman sistem terkait.
- **Settings**: panel style, opacity, corner radius, auto-hide, dynamic color, dark mode
  (Light/Dark/AMOLED/System), gaming mode toggle — semua persisten via DataStore.
- **Auto-restart setelah reboot** hanya jika overlay memang pernah diaktifkan pengguna
  (dicek dari DataStore + izin overlay masih ada), lewat `BootCompletedReceiver`.

## Batasan yang diketahui (didokumentasikan, bukan diklaim berfungsi)

- **Shizuku Mode** saat ini memakai jalur switch-app yang sama dengan Standard Mode.
  Integrasi penuh ke `ActivityTaskManager` lewat Shizuku `UserService`/AIDL — yang akan
  memberi keunggulan nyata dibanding Standard Mode — belum diimplementasikan. Ini
  ditandai eksplisit di kode (`RecentAppsRepositoryImpl.switchToApp`), bukan fitur palsu.
- **Auto Start (OEM)** ditandai `UNSUPPORTED` karena tidak ada API publik resmi untuk
  mendeteksi/meminta izin ini di semua OEM (MIUI, ColorOS, One UI, dll punya halaman
  masing-masing yang tidak seragam).
- Fitur berikut dari spesifikasi awal **belum dibangun** pada iterasi ini: gesture custom
  per-arah, quick actions long-press lengkap (Pin/Lock/Force Stop UI/Split Screen/Floating
  Window), search realtime di panel (state sudah ada di `OverlayUiState.searchQuery`,
  UI input belum dipasang), statistik penggunaan, homescreen widget, Quick Settings Tile,
  app exclusion/blacklist, backup & restore, Room-based history, unit/UI test, dan
  lokalisasi selain ID/EN untuk seluruh string.
- `dumpsys activity recents` parsing bersifat best-effort karena formatnya bukan API
  publik yang stabil lintas versi Android/OEM.

## Struktur modul

```
core/permission/   -> RootController, ShizukuController, OperatingModeManager
data/local/        -> SettingsDataStore (DataStore Preferences)
data/repository/   -> implementasi Repository (UsageStats, PackageManager, root shell)
domain/            -> model, repository interface, use case (murni Kotlin, tanpa Android)
service/           -> OverlayService, TaskAccessibilityService, BootCompletedReceiver
ui/                -> Compose screens per fitur (home, permission, settings, overlay) + navigasi
```
