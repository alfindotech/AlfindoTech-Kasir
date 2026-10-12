package id.alfindotech.kasir.license

import android.content.Context
import java.util.concurrent.TimeUnit

class LicenseManager(private val context:Context){

    fun deviceCode():String =
        DeviceIdentifier.getDeviceId(context)

    fun isExpired(date:Long):Boolean {
        val days=TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis()-date
        )
        return days>30
    }

    fun validate(device:String,key:String):Boolean =
        LicenseKeyGenerator.generate(device)
            .equals(key,true)
}