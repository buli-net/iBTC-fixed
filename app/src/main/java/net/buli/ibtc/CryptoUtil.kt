package net.buli.ibtc

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ============================================================================
// CryptoUtil v4.2 - Mã hóa seed phrase
// - PBKDF2WithHmacSHA256 với 200,000 iterations (tăng từ 100k)
// - AES-GCM 256-bit authenticated encryption
// - Salt 16 bytes + IV 12 bytes random mỗi lần mã hóa
// - Không lưu password, chỉ lưu ciphertext
// ============================================================================

object CryptoUtil {

    // Cấu hình bảo mật
    private const val ITERATIONS = 200000  // Tăng gấp đôi so với v3
    private const val KEY_LENGTH = 256     // AES-256
    private const val SALT_LENGTH = 16     // 128-bit salt
    private const val IV_LENGTH = 12       // 96-bit IV cho GCM (khuyến nghị)
    private const val TAG_LENGTH = 128     // 128-bit auth tag

    /**
     * Mã hóa dữ liệu bằng password
     * Output format: [salt(16)][iv(12)][ciphertext+tag]
     */
    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        // Tạo salt ngẫu nhiên
        val salt = ByteArray(SALT_LENGTH).also { 
            SecureRandom().nextBytes(it) 
        }
        
        // Tạo IV ngẫu nhiên
        val iv = ByteArray(IV_LENGTH).also { 
            SecureRandom().nextBytes(it) 
        }
        
        // Derive key từ password
        val key = deriveKey(password, salt)
        
        // Mã hóa AES-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        
        val encrypted = cipher.doFinal(data)
        
        // Ghép salt + iv + ciphertext
        return salt + iv + encrypted
    }

    /**
     * Giải mã dữ liệu
     */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        // Tách salt, iv, ciphertext
        require(data.size > SALT_LENGTH + IV_LENGTH) { "Dữ liệu mã hóa không hợp lệ" }
        
        val salt = data.copyOfRange(0, SALT_LENGTH)
        val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val cipherText = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)
        
        // Derive key
        val key = deriveKey(password, salt)
        
        // Giải mã
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return cipher.doFinal(cipherText)
    }

    /**
     * PBKDF2 key derivation
     */
    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        
        // Xóa password khỏi spec
        spec.clearPassword()
        
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Tạo hash để verify password (không dùng để mã hóa)
     */
    fun hashPassword(password: CharArray, salt: ByteArray = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }): Pair<ByteArray, ByteArray> {
        val key = deriveKey(password, salt)
        return Pair(salt, key.encoded)
    }

    /**
     * Verify password với hash
     */
    fun verifyPassword(password: CharArray, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val key = deriveKey(password, salt)
        return key.encoded.contentEquals(expectedHash)
    }
}