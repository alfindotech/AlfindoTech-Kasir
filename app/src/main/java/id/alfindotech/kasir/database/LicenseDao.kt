package id.alfindotech.kasir.database

import androidx.room.*
import id.alfindotech.kasir.license.LicenseEntity

@Dao
interface LicenseDao {
    @Query("SELECT * FROM license WHERE id=1")
    suspend fun getLicense(): LicenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLicense(data:LicenseEntity)

    @Query("UPDATE license SET activated=:status, activationKey=:key WHERE id=1")
    suspend fun activate(status:Boolean,key:String)
}