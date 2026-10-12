package id.alfindotech.kasir.license

import java.security.MessageDigest

/**
 * Generator/verifier aktivasi. Harus menggunakan SECRET yang sama dengan
 * AlfindoTech_Kasir_Keygen.py. Format keluaran hanya XXXX-XXXX-XXXX.
 */
object LicenseKeyGenerator {
    private const val SECRET = "APP-KASIR-ALFINDO"

    fun generate(deviceId: String): String {
        val normalizedDeviceId = deviceId.trim().uppercase()
        require(normalizedDeviceId.isNotEmpty()) { "Device ID tidak boleh kosong" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((normalizedDeviceId + SECRET).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
        return "${digest.substring(0, 4)}-${digest.substring(4, 8)}-${digest.substring(8, 12)}"
    }

    fun verify(deviceId: String, activationCode: String): Boolean =
        generate(deviceId).equals(activationCode.trim().uppercase(), ignoreCase = true)
}
