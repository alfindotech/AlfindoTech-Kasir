
package id.alfindotech.kasir.test.steps

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import id.alfindotech.kasir.data.AppDatabase
import id.alfindotech.kasir.data.Product
import id.alfindotech.kasir.data.Sale
import id.alfindotech.kasir.data.SaleDetail
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ProductStockSteps {

    private lateinit var db: AppDatabase
    private var productId: Int = 0
    private var transactionRejected = false
    private var createdSaleId: Int? = null

    @Before
    fun setup() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Given("produk memiliki stok {int}")
    fun produkMemilikiStok(stok: Int) = runBlocking {
        transactionRejected = false
        createdSaleId = null

        db.productDao().insert(
            Product(
                kode = "BDD-001",
                nama = "Produk BDD",
                hargaBeli = 500,
                hargaJual = 1000,
                stok = stok
            )
        )

        productId = db.productDao().getAllNow().single().id
    }

    @When("kasir menjual produk sebanyak {int}")
    fun kasirMenjualProduk(jumlah: Int) = runBlocking {
        try {
            db.withTransaction {
                val affected = db.productDao().reduceStock(
                    productId,
                    jumlah
                )

                if (affected == 0) {
                    throw IllegalStateException(
                        "Stok produk tidak mencukupi"
                    )
                }

                val total = jumlah * 1000L

                val saleId = db.saleDao().insertSale(
                    Sale(
                        nomor = "BDD-TEST-001",
                        tanggal = System.currentTimeMillis(),
                        userId = 1,
                        total = total,
                        bayar = total,
                        kembalian = 0
                    )
                ).toInt()

                db.saleDao().insertDetails(
                    listOf(
                        SaleDetail(
                            saleId = saleId,
                            productId = productId,
                            hargaJual = 1000,
                            jumlah = jumlah,
                            subtotal = total
                        )
                    )
                )

                createdSaleId = saleId
            }
        } catch (e: IllegalStateException) {
            transactionRejected = true
        }
    }

    @Then("stok produk menjadi {int}")
    fun stokProdukMenjadi(stok: Int) = runBlocking {
        val product = db.productDao()
            .getAllNow()
            .single()

        assertEquals(stok, product.stok)
    }

    @Then("transaksi penjualan ditolak")
    fun transaksiPenjualanDitolak() {
        assertTrue(
            "Transaksi seharusnya ditolak",
            transactionRejected
        )
    }

    @Then("stok produk tetap {int}")
    fun stokProdukTetap(stok: Int) = runBlocking {
        val product = db.productDao()
            .getAllNow()
            .single()

        assertEquals(stok, product.stok)
    }

    @And("transaksi penjualan tersimpan")
    fun transaksiPenjualanTersimpan() = runBlocking {
        val sales = db.saleDao().observeAll().first()

        assertEquals(1, sales.size)

        val saleId = createdSaleId
            ?: throw AssertionError("ID transaksi tidak ditemukan")

        val details = db.saleDao().getDetails(saleId)

        assertEquals(1, details.size)
        assertEquals(3, details.single().jumlah)
    }

    @And("tidak ada transaksi penjualan tersimpan")
    fun tidakAdaTransaksiPenjualanTersimpan() = runBlocking {
        val sales = db.saleDao().observeAll().first()

        assertTrue(
            "Seharusnya tidak ada transaksi yang tersimpan",
            sales.isEmpty()
        )

        assertTrue(
            "ID transaksi seharusnya kosong",
            createdSaleId == null
        )
    }
}
