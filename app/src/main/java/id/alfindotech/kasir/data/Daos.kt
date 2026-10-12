package id.alfindotech.kasir.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Query("SELECT * FROM users ORDER BY nama")
    fun observeAll(): Flow<List<User>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY nama")
    fun observeAll(): Flow<List<Category>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Category)
    @Delete
    suspend fun delete(item: Category)
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY nama")
    fun observeAll(): Flow<List<Unit>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Unit)
    @Delete
    suspend fun delete(item: Unit)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY nama")
    fun observeAll(): Flow<List<Product>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Product)
    @Delete
    suspend fun delete(item: Product)
    @Query("SELECT * FROM products ORDER BY nama")
    suspend fun getAllNow(): List<Product>
    @Query("UPDATE products SET stok = stok + :qty WHERE id = :productId")
    suspend fun addStock(productId: Int, qty: Int)
    @Query("UPDATE products SET stok = stok - :qty WHERE id = :productId AND stok >= :qty")
    suspend fun reduceStock(productId: Int, qty: Int): Int
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM store_settings WHERE id = 1")
    suspend fun get(): StoreSettings?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: StoreSettings)
}


@Dao
interface PurchaseDao {
    @Insert
    suspend fun insertPurchase(item: Purchase): Long

    @Insert
    suspend fun insertDetails(items: List<PurchaseDetail>)

    @Query("SELECT * FROM purchases ORDER BY id DESC")
    fun observeAll(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchase_details WHERE purchaseId = :purchaseId ORDER BY id")
    suspend fun getDetails(purchaseId: Int): List<PurchaseDetail>
}


@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(item: Sale): Long

    @Insert
    suspend fun insertDetails(items: List<SaleDetail>)

    @Query("SELECT * FROM sales ORDER BY id DESC")
    fun observeAll(): Flow<List<Sale>>

    @Query("SELECT * FROM sale_details WHERE saleId = :saleId ORDER BY id")
    suspend fun getDetails(saleId: Int): List<SaleDetail>
}
