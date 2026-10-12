# Aplikasi Kasir Android - AlfindoTech

## Tahap 8 - Printer Thermal Bluetooth
- Izin Bluetooth Android 12+
- Membaca printer Bluetooth yang sudah dipasangkan
- Pilih printer thermal dari halaman struk
- Bluetooth Classic SPP
- Cetak format ESC/POS 32 kolom yang cocok untuk printer thermal 58/80 mm
- Nama toko, alamat, telepon, nomor transaksi, kasir, item, total, bayar, kembalian
- Penanganan kegagalan koneksi

## Catatan
Printer thermal harus dipasangkan terlebih dahulu melalui Settings > Bluetooth Android.
Implementasi menggunakan UUID SPP umum:
00001101-0000-1000-8000-00805F9B34FB

## Tahap berikutnya
Final audit, build APK debug/release, dan pengujian pada printer thermal yang digunakan.
