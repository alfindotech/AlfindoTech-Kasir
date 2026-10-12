package id.alfindotech.kasir.license

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object DeviceIdentifier {
    fun getDeviceId(context:Context):String {
        val id = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return sha(id).substring(0,16).uppercase()
    }

    private fun sha(v:String):String =
        MessageDigest.getInstance("SHA-256")
            .digest(v.toByteArray())
            .joinToString("") { "%02x".format(it) }
}