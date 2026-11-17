# Fyland 1.0

Rilisan pertama Fyland (sebelumnya SCPD Island). ID paket: `id.fyland`.

- Island selalu tampil (pil kecil saat kosong). Layanan latar `KeepAliveService` (notifikasi senyap) menjaga proses tetap hidup.
- Bahasa aplikasi mengikuti bahasa sistem: Inggris (default, `res/values`) dan Indonesia (`res/values-in`). Bahasa lain tinggal menambah `res/values-xx/strings.xml`.
- Tema hijau gelap, ikon adaptive dari logo F, splash video tanpa suara (`res/raw/splash.mp4`).

Build: `./build-local.sh` atau `gradle assembleRelease` (hasil: `Fyland-1.0.apk`).

## Build

Butuh JDK 17, Android SDK 35 (build-tools 35.0.0), Gradle 8.9.

```
gradle assembleRelease      # hasil: app/build/outputs/apk/release/
```

Tanpa konfigurasi tambahan, APK ditandatangani dengan debug keystore Android (cukup untuk testing). Untuk rilis, buat `keystore.properties` di root (sudah di-gitignore):

```
storeFile=/path/ke/kunci-kamu.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Jangan pernah commit file `.jks` / `keystore.properties`.

`build-local.sh` (tanpa Gradle) memakai env `KEYSTORE`, `KS_ALIAS`, `KS_PASS` jika ingin tanda tangan sendiri.

## Privasi

Fyland tidak punya server sendiri, akun, analytics, maupun API key. Koneksi keluar yang ada:

- `lrclib.net` — lirik (judul/artis lagu yang sedang diputar)
- `open-meteo.com`, `ipwho.is`, `nominatim.openstreetmap.org` — cuaca & nama lokasi
- (tidak ada lagi koneksi ke WhatsApp Web; panel WhatsApp membuka aplikasinya langsung)

Izin sensitif (kalender, kontak, telepon, lokasi, mikrofon, kamera, notifikasi, overlay, accessibility) dipakai untuk fitur island dan hanya diminta saat dibutuhkan. Data notifikasi, kontak, dan kalender diproses di perangkat dan tidak dikirim ke server mana pun. Yang keluar dari perangkat hanya data pada daftar koneksi di atas: koordinat lokasi (cuaca dan nama tempat), alamat IP (perkiraan lokasi cadangan), dan judul/artis lagu (lirik).

## Lisensi

Fyland dirilis di bawah [PolyForm Noncommercial License 1.0.0](LICENSE). Kode ini *source-available*, bukan open source menurut definisi OSI, karena penggunaan komersial dilarang.

- Boleh: mengunduh, memakai, mengubah, dan membagikan ulang untuk tujuan non-komersial.
- Tidak boleh: menjual, atau memakainya untuk tujuan komersial.
- Wajib: menyertakan file `LICENSE` dan baris `Required Notice: Copyright 2026 Muhammad Tsaqib Yanfaunnas and SCPD Production` bersama setiap salinan.

## Aset dan merek dagang

Repo ini sengaja tidak menyertakan logo pihak ketiga (WhatsApp, YouTube, TikTok) maupun ikon dan animasi yang asal-usul lisensinya tidak jelas. Aplikasi tetap berjalan: tile terkait memakai huruf atau emoji pengganti, dan animasi Face ID memakai gambar cadangan. Kalau kamu menambahkan aset sendiri (`res/drawable/ic_tile_*`, `res/raw/face_id.json`), pastikan kamu punya haknya.

Lisensi ini hanya berlaku untuk karya milik pemilik proyek. Logo, ikon, dan aset pihak ketiga (misalnya logo WhatsApp) tetap tunduk pada hak cipta pemiliknya masing-masing.
