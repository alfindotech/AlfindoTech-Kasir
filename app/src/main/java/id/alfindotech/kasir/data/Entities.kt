package id.alfindotech.kasir.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val password: String,
    val nama: String,
    val role: String
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nama: String
)

@Entity(tableName = "units")
data class Unit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nama: String
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val kode: String,
    val barcode: String = "",
    val nama: String,
    val categoryId: Int = 0,
    val unitId: Int = 0,
    val hargaBeli: Long = 0,
    val hargaJual: Long = 0,
    val stok: Int = 0,
    val imagePath: String? = null
)

@Entity(tableName = "store_settings")
data class StoreSettings(
    @PrimaryKey val id: Int = 1,
    val namaToko: String = "TOKO ALFINDO",
    val alamat: String = "",
    val telepon: String = "",
    val logoPath: String? = null
)


@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nomor: String,
    val tanggal: Long,
    val userId: Int,
    val total: Long
)

@Entity(tableName = "purchase_details")
data class PurchaseDetail(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val purchaseId: Int,
    val productId: Int,
    val hargaBeli: Long,
    val jumlah: Int,
    val subtotal: Long
)


@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nomor: String,
    val tanggal: Long,
    val userId: Int,
    val total: Long,
    val bayar: Long,
    val kembalian: Long
)

@Entity(tableName = "sale_details")
data class SaleDetail(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val saleId: Int,
    val productId: Int,
    val hargaJual: Long,
    val jumlah: Int,
    val subtotal: Long
)
