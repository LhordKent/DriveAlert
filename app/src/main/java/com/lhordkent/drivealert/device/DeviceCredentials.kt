package com.lhordkent.drivealert.device

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DeviceCredential(val deviceId: String, val accountId: String, val secret: String,
    val owner: Boolean, val serial: Long = 0, val certificate: String = "") {
    override fun toString(): String = "DeviceCredential(deviceId=$deviceId, owner=$owner, serial=$serial, secrets=REDACTED)"
}

/** Shared, versioned wire contract with device_authorization.cpp. */
object DeviceCredentialCrypto {
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
    fun bytes(hex: String): ByteArray {
        require(hex.matches(Regex("[0-9A-F]{64}")))
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    fun accountId(uid: String): String = hex(MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))).take(32)
    fun randomSecret(): String = hex(ByteArray(32).also(SecureRandom()::nextBytes))
    fun hmac(key: String, message: String): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(bytes(key), "HmacSHA256")); hex(doFinal(message.toByteArray(Charsets.UTF_8)))
    }
    fun proof(credential: DeviceCredential, nonce: String): String {
        require(nonce.matches(Regex("[0-9A-F]{32}")))
        return hmac(credential.secret, "AUTH|${credential.deviceId}|${credential.accountId}|$nonce")
    }
    fun delegate(owner: DeviceCredential, uid: String, serial: Long, operationId: String): DeviceCredential {
        require(owner.owner && serial in 1..Int.MAX_VALUE)
        val account = accountId(uid)
        val secret = hmac(owner.secret, "DRIVER|${owner.deviceId}|$account|$operationId")
        val certificate = hmac(owner.secret, "CERT|${owner.deviceId}|$account|$serial|$secret")
        return DeviceCredential(owner.deviceId, account, secret, false, serial, certificate)
    }
}

/** UID-scoped secrets encrypted at rest with a non-exportable Android Keystore key. */
class DeviceCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("device_authorization_v1", Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    @Synchronized private fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "Could not save offline device authorization." }
    }
    private fun get(name: String): String? = runCatching {
        val encrypted = preferences.getString(name, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        require(encrypted.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(0, 12))) }
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)), Charsets.UTF_8)
    }.getOrNull()

    fun save(uid: String, credential: DeviceCredential) = put("credential/$uid/${credential.deviceId}", JSONObject()
        .put("id", credential.deviceId).put("account", credential.accountId).put("secret", credential.secret)
        .put("owner", credential.owner).put("serial", credential.serial).put("certificate", credential.certificate).toString())
    fun load(uid: String, deviceId: String): DeviceCredential? = runCatching {
        val json = JSONObject(get("credential/$uid/$deviceId") ?: return null)
        DeviceCredential(json.getString("id"), json.getString("account"), json.getString("secret"), json.getBoolean("owner"), json.getLong("serial"), json.getString("certificate"))
    }.getOrNull()
    fun saveSession(uid: String, deviceId: String, token: String) {
        require(token.matches(Regex("[0-9A-F]{64}")))
        put("session/$uid/$deviceId", token)
    }
    fun session(uid: String, deviceId: String): String? = get("session/$uid/$deviceId")
    @Synchronized fun setRevocation(uid: String, deviceId: String, account: String, serial: Long) {
        require(account.matches(Regex("[0-9A-F]{32}")) && serial in 0..Int.MAX_VALUE)
        val name = "revocations/$uid/$deviceId"
        val values = JSONObject(get(name) ?: "{}")
        values.put(account, maxOf(values.optLong(account, 0), serial))
        put(name, values.toString())
    }
    fun revocations(uid: String, deviceId: String): List<Pair<String, Long>> {
        val json = JSONObject(get("revocations/$uid/$deviceId") ?: "{}")
        return json.keys().asSequence().map { it to json.getLong(it) }.filter { it.second > 0 }.toList()
    }
    companion object { private const val KEY_ALIAS = "drivealert-device-secrets-v1" }
}

internal fun java.net.HttpURLConnection.authorizeDevice(token: String?) {
    if (token != null) setRequestProperty("Authorization", "Bearer $token")
}

internal fun java.net.HttpURLConnection.requireDeviceResponse(deviceId: String, token: String?) {
    if (token != null) check(getHeaderField("X-DriveAlert-Device") == deviceId && getHeaderField("X-DriveAlert-Auth") == "1") {
        "The camera did not confirm the registered device identity. Reconnect through Bluetooth setup with the updated firmware."
    }
}
