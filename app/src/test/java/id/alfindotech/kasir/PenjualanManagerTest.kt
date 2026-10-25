package id.alfindotech.kasir


class PenjualanManager {
    fun hitungTotalPenjualan(hargaBarang: Int, kuantitas: Int, diskon: Int, pajakPersen: Double): Int {
        if (hargaBarang < 0 || kuantitas < 0 || diskon < 0) {
            throw IllegalArgumentException("Nilai tidak boleh negatif")
        }
        val totalSebelumPajak = (hargaBarang * kuantitas) - diskon
        val nilaiPajak = totalSebelumPajak * (pajakPersen / 100)
        return (totalSebelumPajak + nilaiPajak).toInt()
    }
}

fun main() {
    println("=== MENJALANKAN UNIT TESTING PENJUALAN ===")
    val manager = PenjualanManager()
    
    // Skenario Uji: Harga 50.000, Beli 2, Diskon 10.000, Pajak 10%
    val totalAkhir = manager.hitungTotalPenjualan(50000, 2, 10000, 10.0)
    val ekspektasi = 99000
    
    println("Hasil Perhitungan Aplikasi : Rp $totalAkhir")
    println("Hasil Ekspektasi      : Rp $ekspektasi")
    
   
    if (totalAkhir == ekspektasi) {
        println("STATUS PENGUJIAN: SUCCESS (PASSED) ✅")
    } else {
        println("STATUS PENGUJIAN: FAILED ❌")
        throw AssertionError("Perhitungan Penjualan Salah!")
    }
}