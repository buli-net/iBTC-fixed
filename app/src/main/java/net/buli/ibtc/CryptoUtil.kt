package net.buli.ibtc

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * CryptoUtil - Mã hóa AES-256-GCM cho seed phrase
 * 
 * Chi tiết thuật toán:
 * - PBKDF2WithHmacSHA256: 10,000 vòng lặp để tạo key từ mật khẩu
 * - AES/GCM/NoPadding: mã hóa xác thực, chống chỉnh sửa dữ liệu
 * - Salt 16 bytes + IV 12 bytes được tạo ngẫu nhiên và lưu cùng ciphertext
 * - Key length: 256 bit
 */
object CryptoUtil {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_LEN = 128

    /**
     * Mã hóa văn bản bằng mật khẩu
     * @return chuỗi Base64 chứa salt + iv + ciphertext
     */
    fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN, iv))
        
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        
        // Ghép salt + iv + encrypted để lưu
        val combined = salt + iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Giải mã văn bản bằng mật khẩu
     * @throws Exception nếu sai mật khẩu hoặc dữ liệu hỏng
     */
    fun decrypt(encryptedBase64: String, password: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        
        val salt = combined.copyOfRange(0, SALT_LEN)
        val iv = combined.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val encrypted = combined.copyOfRange(SALT_LEN + IV_LEN, combined.size)
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN, iv))
        
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}