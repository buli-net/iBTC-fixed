package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.util.*

/**
 * WalletManager - Quản lý ví Bitcoin với mã hóa mật khẩu
 * 
 * NGUYÊN LÝ BẢO MẬT:
 * 1. Seed phrase được mã hóa bằng AES-256-GCM với key từ mật khẩu (PBKDF2 10,000 vòng)
 * 2. Mật khẩu KHÔNG bao giờ được lưu trữ - chỉ dùng tạm thời để giải mã
 * 3. Khi lock, seed bị xóa khỏi RAM hoàn toàn
 * 4. Quên mật khẩu = mất quyền truy cập (phải dùng backup seed)
 */
class WalletManager(private val context: Context) {
    private val params = MainNetParams.get()
    private val prefs: SharedPreferences = context.getSharedPreferences("btc_wallet", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null
    private var decryptedSeed: String? = null
    private var currentWalletName: String? = null
    private var progressListener: ((Int, String) -> Unit)? = null

    // ========== KIỂM TRA TRẠNG THÁI ==========
    fun hasWallets(): Boolean = prefs.getStringSet("wallets", emptySet())?.isNotEmpty() == true
    
    fun isUnlocked(): Boolean = decryptedSeed != null
    
    fun getActive(): WalletInfo? {
        val name = currentWalletName ?: prefs.getString("active_wallet", null) ?: return null
        return WalletInfo(name)
    }

    // ========== MỞ KHÓA VÀ KHÓA VÍ ==========
    fun unlock(password: String): Boolean {
        return try {
            val wallets = prefs.getStringSet("wallets", emptySet()) ?: return false
            val name = prefs.getString("active_wallet", wallets.first()) ?: return false
            
            val encryptedSeed = prefs.getString("seed_$name", null) ?: return false
            val seed = CryptoUtil.decrypt(encryptedSeed, password)
            
            decryptedSeed = seed
            currentWalletName = name
            true
        } catch (e: Exception) {
            false
        }
    }

    fun lock() {
        decryptedSeed = null
        kit?.stopAsync()
        kit = null
        currentWalletName = null
    }

    // ========== TẠO VÍ MỚI ==========
    fun create(name: String, password: String) {
        val seed = DeterministicSeed(System.currentTimeMillis() / 1000, 12)
        val seedPhrase = seed.mnemonicCode.joinToString(" ")
        
        // Mã hóa seed trước khi lưu vào SharedPreferences
        val encrypted = CryptoUtil.encrypt(seedPhrase, password)
        
        prefs.edit().apply {
            putString("seed_$name", encrypted)
            putStringSet("wallets", (prefs.getStringSet("wallets", emptySet()) ?: emptySet()) + name)
            putString("active_wallet", name)
            apply()
        }
        
        decryptedSeed = seedPhrase
        currentWalletName = name
    }

    // ========== IMPORT VÍ TỪ SEED ==========
    fun import(name: String, phrase: String, password: String): Boolean {
        return try {
            // Kiểm tra seed hợp lệ trước khi lưu
            DeterministicSeed(phrase, null, "", 0L)
            
            val encrypted = CryptoUtil.encrypt(phrase, password)
            prefs.edit().apply {
                putString("seed_$name", encrypted)
                putStringSet("wallets", (prefs.getStringSet("wallets", emptySet()) ?: emptySet()) + name)
                putString("active_wallet", name)
                apply()
            }
            decryptedSeed = phrase
            currentWalletName = name
            true
        } catch (e: Exception) {
            false
        }
    }

    // ========== KHỞI TẠO BITCOINJ KIT ==========
    fun init() {
        if (decryptedSeed == null) return
        
        val walletDir = File(context.filesDir, "wallets")
        walletDir.mkdirs()
        
        kit = object : WalletAppKit(params, walletDir, currentWalletName) {
            override fun onSetupCompleted() {
                wallet().addCoinsReceivedEventListener { _, _, _, _ -> }
            }
        }.apply {
            setAutoSave(true)
            setBlockingStartup(false)
            
            // Restore từ seed nếu ví chưa tồn tại
            if (!File(walletDir, "$currentWalletName.wallet").exists()) {
                restoreWalletFromSeed(DeterministicSeed(decryptedSeed, null, "", 0L))
            }
            
            startAsync()
        }
    }

    fun onProgress(callback: (Int, String) -> Unit) {
        progressListener = callback
        kit?.peerGroup()?.addBlocksDownloadedEventListener { _, _, blocksLeft, _ ->
            val progress = if (blocksLeft <= 0) 100 else (100 - blocksLeft.coerceAtMost(100))
            callback(progress, if (progress < 100) "Đang sync... $progress%" else "Đã đồng bộ")
        }
    }

    // ========== CÁC THAO TÁC VÍ ==========
    fun getBalance(): Double = kit?.wallet()?.balance?.toBtc()?.toDouble() ?: 0.0
    
    fun getAddress(): String = kit?.wallet()?.currentReceiveAddress()?.toString() ?: ""
    
    fun price(): Double = 67500.0 // TODO: gọi API thật
    
    fun getTransactions(): List<TransactionInfo> {
        val wallet = kit?.wallet() ?: return emptyList()
        return wallet.getTransactionsByTime().take(20).map { tx ->
            val value = tx.getValue(wallet).toBtc().toDouble()
            TransactionInfo(
                type = if (value > 0) "Nhận" else "Gửi",
                amount = kotlin.math.abs(value),
                time = tx.updateTime,
                hash = tx.txId.toString()
            )
        }
    }

    fun getFeeRates(): FeeRates = FeeRates(5, 10, 20)

    fun send(toAddress: String, amountBtc: Double, feePerKb: Int): String {
        return try {
            val wallet = kit?.wallet() ?: return "Ví chưa sẵn sàng"
            val address = Address.fromString(params, toAddress)
            val amount = Coin.valueOf((amountBtc * 1e8).toLong())
            
            val sendReq = Wallet.SendRequest.to(address, amount)
            sendReq.feePerKb = Coin.valueOf(feePerKb * 1000L)
            
            val tx = wallet.sendCoins(sendReq).tx
            tx.txId.toString()
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }

    // ========== BẢO MẬT NÂNG CAO ==========
    fun getDecryptedSeed(password: String): String {
        val name = currentWalletName ?: throw Exception("No wallet")
        val encrypted = prefs.getString("seed_$name", null) ?: throw Exception("No seed")
        return CryptoUtil.decrypt(encrypted, password)
    }

    fun changePassword(oldPass: String, newPass: String): Boolean {
        return try {
            val name = currentWalletName ?: return false
            val seed = getDecryptedSeed(oldPass)
            val newEncrypted = CryptoUtil.encrypt(seed, newPass)
            prefs.edit().putString("seed_$name", newEncrypted).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}

data class WalletInfo(val name: String)
data class TransactionInfo(val type: String, val amount: Double, val time: Date, val hash: String)