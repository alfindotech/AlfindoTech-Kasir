package id.alfindotech.kasir.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "license")
data class LicenseEntity(
    @PrimaryKey val id:Int = 1,
    val deviceId:String,
    val installDate:Long,
    val activated:Boolean = false,
    val activationKey:String = ""
)