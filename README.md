# Absensi Siswa GPS v2

Prototype aplikasi Android untuk absensi siswa berbasis geofence.

## Fitur
- Login siswa dengan NIS/NISN dan nama.
- Absensi HADIR dengan selfie dari kamera + GPS.
- Geofence: absensi hanya diterima dalam radius sekolah.
- Deteksi mock location pada Android yang mendukungnya.
- Cegah absensi lebih dari sekali per hari pada perangkat.
- Pengajuan Izin/Sakit dengan alasan.
- Riwayat absensi 90 hari.
- Pengaturan latitude, longitude, dan radius melalui PIN admin.
- Foto selfie disimpan di penyimpanan internal aplikasi.

## Konfigurasi awal
Koordinat contoh: Bandung (-6.9175, 107.6191), radius 100 meter.
Ubah dari menu Pengaturan Admin.
PIN demo: 123456. **Wajib diganti sebelum produksi.**

## Build APK
Buka folder ini di Android Studio. Pastikan Android SDK API 35 tersedia. Lalu pilih:
Build > Build Bundle(s) / APK(s) > Build APK(s)

Output umumnya:
app/build/outputs/apk/debug/app-debug.apk

## Penting untuk produksi
Versi ini menyimpan data secara lokal di perangkat. Untuk sekolah sungguhan, data harus dipindahkan ke backend/server dengan autentikasi guru/siswa, waktu server, database terpusat, audit log, dan kebijakan privasi. Jangan mengandalkan SharedPreferences/PIN lokal sebagai keamanan produksi.
