package net.buli.ibtc

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoUtil – mã hóa AES-256 seed bằng mật khẩu
 * Dùng PBKDF2 để tạo key từ pass, lưu dưới dạng Base64
 */
object CryptoUtil {
    private const val ITERATIONS = 65536 // số vòng băm, càng cao càng khó brute force
    private const val KEY_LENGTH = 256 // AES-256

    // Mã hóa chuỗi plain thành Base64
    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(16) { 0 } // salt cố định cho đơn giản (ví cá nhân)
        val iv = ByteArray(16) { 1 }   // vector khởi tạo
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val enc = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    // Giải mã Base64 về plain, trả null nếu sai pass
    fun decrypt(encBase64: String, password: String): String? {
        return try {
            val salt = ByteArray(16) { 0 }
            val iv = ByteArray(16) { 1 }
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decoded = Base64.decode(encBase64, Base64.NO_WRAP)
            String(cipher.doFinal(decoded))
        } catch (_: Exception) { null } // sai pass sẽ throw
    }
}