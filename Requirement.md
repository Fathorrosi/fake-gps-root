# Fake Traveler (Root + AppOps Spoofing)

## Tujuan
Aplikasi Android dengan package name `cl.coders.faketraveler` yang dapat melakukan spoof lokasi ke seluruh sistem:
- Tidak perlu dijadikan **Mock Location App** di Developer Options.
- Menggunakan **root + AppOps** untuk memberikan izin mock location.
- Lokasi palsu harus dikenali oleh aplikasi pihak ketiga seperti Google Maps, Grab, Gojek, dll.

## Lingkungan
- Device: Xiaomi Redmi (Android 10, MIUI 12.0.5)
- Root: ✅ (Magisk/SU tersedia)
- Bahasa: Java
- Minimum SDK: 29 (Android Q)

## Persyaratan Teknis
1. **Pemberian Izin Mock Location**
   - Gunakan perintah:
     ```bash
     su -c "appops set cl.coders.faketraveler android:mock_location allow"
     ```
   - Bisa dijalankan manual oleh user, atau otomatis lewat app menggunakan `Runtime.getRuntime().exec`.

2. **Class MockedLocationProvider**
   - Nama file: `MockedLocationProvider.java`
   - Fungsi:
     - `startup()`:
       - Tambahkan test provider dengan `addTestProvider("gps", ...)`
       - Aktifkan dengan `setTestProviderEnabled("gps", true)`
     - `pushLocation(double lat, double lon)`:
       - Buat objek `Location` dengan lat/lon
       - Set akurasi, waktu (`System.currentTimeMillis`), dan `elapsedRealtimeNanos`
       - Injeksi lokasi dengan `setTestProviderLocation("gps", location)`
       - Logging jika berhasil/gagal
     - `shutdown()`:
       - Hapus provider dengan `removeTestProvider("gps")`

3. **Keluaran**
   - Saat `pushLocation` dipanggil, koordinat palsu diterapkan ke sistem.
   - Aplikasi lain (misalnya Google Maps) menerima koordinat spoof ini.

## Acceptance Criteria
- Setelah perintah AppOps dijalankan, aplikasi dapat langsung spoof lokasi tanpa Developer Options.
- Menjalankan `pushLocation(lat, lon)` mengubah posisi di Google Maps sesuai koordinat.
- Tidak ada lagi penggunaan `service call location ...` dalam kode.
- Logging tersedia untuk debugging.

