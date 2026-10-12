package id.alfindotech.kasir.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LicenseDao {
    @Query("SELECT * FROM license WHERE id = 1 LIMIT 1")
    suspend fun getLicense(): LicenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLicense(license: LicenseEntity)

    @Query("UPDATE license SET activated = :activated, activationKey = :activationKey WHERE id = 1")
    suspend fun activate(activated: Boolean, activationKey: String)
}
