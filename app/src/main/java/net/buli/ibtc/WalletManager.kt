package net.buli.ibtc

import android.util.Log
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.utils.Threading
import java.io.File
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * WalletManager v4.1 - Full version
 * Build date: 2025-05-22
 * Compatible with bitcoinj 0.17
 * 
 * Chức năng:
 * - Tạo ví mới HD (BIP32/BIP44)
 * - Khôi phục ví từ mnemonic 12 từ
 * - Đồng bộ blockchain SPV
 * - Gửi/nhận BTC mainnet
 * - Theo dõi tiến trình sync
 */
class WalletManager(private val appDir: File) {

    private val TAG = "WalletManager_v41"
    private val params: NetworkParameters = MainNetParams.get()
    private lateinit var kit: WalletAppKit
    private var progressListener: ((Double, Int, Date?) -> Unit)? = null
    private var isSyncing = false

    companion object {
        private const val WALLET_FILE_PREFIX = "ibtc-wallet"
        private const val DEFAULT_FEE_PER_KB = 2000L // 2 sat/byte
    }

    /**
     * Tạo ví mới với seed ngẫu nhiên
     * @return List 12 từ mnemonic
     */
    fun createNewWallet(): List<String> {
        Log.d(TAG, "=== createNewWallet START ===")
        try {
            // Tạo seed mới với độ mạnh 128 bit = 12 từ
            val seed = DeterministicSeed(SecureRandom(), 128, "")
            Log.d(TAG, "Seed created, mnemonic size: ${seed.mnemonicCode?.size}")
            
            // Tạo wallet từ seed
            val wallet = Wallet.fromSeed(params, seed, MainNetParams.get())
            Log.d(TAG, "Wallet created from seed")
            
            // Khởi tạo WalletAppKit
            initKit(wallet)
            
            Log.d(TAG, "=== createNewWallet SUCCESS ===")
            return seed.mnemonicCode ?: emptyList()
            
        } catch (e: Exception) {
            Log.e(TAG, "createNewWallet FAILED", e)
            throw RuntimeException("Không thể tạo ví mới: ${e.message}", e)
        }
    }

    /**
     * Khôi phục ví từ mnemonic
     * @param mnemonic List 12 từ
     */
    fun restoreWallet(mnemonic: List<String>) {
        Log.d(TAG, "=== restoreWallet START ===")
        Log.d(TAG, "Mnemonic words: ${mnemonic.size}")
        
        try {
            // Validate mnemonic
            if (mnemonic.size != 12) {
                throw IllegalArgumentException("Mnemonic phải có đúng 12 từ")
            }
            
            // Tạo seed từ mnemonic, thời gian tạo = 0 để sync từ đầu
            val creationTime = System.currentTimeMillis() / 1000
            val seed = DeterministicSeed(mnemonic, null, "", creationTime)
            Log.d(TAG, "Seed restored from mnemonic")
            
            // Tạo wallet
            val wallet = Wallet.fromSeed(params, seed, MainNetParams.get())
            Log.d(TAG, "Wallet restored")
            
            // Khởi tạo kit
            initKit(wallet)
            
            Log.d(TAG, "=== restoreWallet SUCCESS ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "restoreWallet FAILED", e)
            throw RuntimeException("Không thể khôi phục ví: ${e.message}", e)
        }
    }

    /**
     * Khởi tạo WalletAppKit với SPV sync
     */
    private fun initKit(wallet: Wallet) {
        Log.d(TAG, "initKit START")
        
        val tracker = object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                super.progress(pct, blocksSoFar, date)
                isSyncing = pct < 100.0
                progressListener?.invoke(pct, blocksSoFar, date)
                Log.v(TAG, "Sync progress: ${String.format("%.2f", pct)}% - blocks: $blocksSoFar")
            }
            
            override fun doneDownload() {
                super.doneDownload()
                isSyncing = false
                progressListener?.invoke(100.0, 0, null)
                Log.d(TAG, "Blockchain sync COMPLETED")
            }
        }

        kit = object : WalletAppKit(params, appDir, WALLET_FILE_PREFIX) {
            override fun onSetupCompleted() {
                Log.d(TAG, "WalletAppKit onSetupCompleted")
                try {
                    // Cho phép chi tiêu UTXO chưa confirm (bitcoinj 0.17 API)
                    wallet().setAllowSpendingUnconfirmedTransactions(true)
                    Log.d(TAG, "Allow spending unconfirmed: true")
                    
                    // Thêm wallet đã tạo vào kit
                    if (wallet != this.wallet()) {
                        this.wallet().addAllTransactions(wallet)
                        Log.d(TAG, "Transactions merged")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "onSetupCompleted error", e)
                }
            }
        }

        // Cấu hình kit
        kit.setDownloadListener(tracker)
        kit.setAutoSave(true)
        kit.setBlockingStartup(false)
        kit.setUserAgent("iBTC", "4.1")
        
        // Start async
        Log.d(TAG, "Starting WalletAppKit...")
        kit.startAsync()
        
        try {
            kit.awaitRunning(30, TimeUnit.SECONDS)
            Log.d(TAG, "WalletAppKit is RUNNING")
        } catch (e: Exception) {
            Log.w(TAG, "awaitRunning timeout, continuing async", e)
        }
    }

    fun setProgressListener(listener: (Double, Int, Date?) -> Unit) {
        this.progressListener = listener
    }

    fun isWalletReady(): Boolean {
        return try {
            ::kit.isInitialized && kit.isRunning
        } catch (e: Exception) {
            false
        }
    }

    fun isSyncing(): Boolean = isSyncing

    fun getBalance(): Coin {
        return try {
            if (isWalletReady()) {
                val balance = kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED)
                Log.v(TAG, "Balance: $balance")
                balance
            } else {
                Coin.ZERO
            }
        } catch (e: Exception) {
            Log.e(TAG, "getBalance error", e)
            Coin.ZERO
        }
    }

    fun getAvailableBalance(): Coin {
        return try {
            if (isWalletReady()) kit.wallet().getBalance(Wallet.BalanceType.AVAILABLE) else Coin.ZERO
        } catch (e: Exception) {
            Coin.ZERO
        }
    }

    fun getAddress(): String {
        return try {
            val address: Address = if (isWalletReady()) {
                kit.wallet().currentReceiveAddress()
            } else {
                Wallet(params).currentReceiveAddress()
            }
            val addrStr = address.toString()
            Log.d(TAG, "Current address: $addrStr")
            addrStr
        } catch (e: Exception) {
            Log.e(TAG, "getAddress error", e)
            ""
        }
    }

    fun getFreshAddress(): String {
        return try {
            if (isWalletReady()) {
                val addr = kit.wallet().freshReceiveAddress()
                addr.toString()
            } else getAddress()
        } catch (e: Exception) {
            getAddress()
        }
    }

    fun getMnemonic(): List<String>? {
        return try {
            if (isWalletReady()) {
                kit.wallet().keyChainSeed?.mnemonicCode
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "getMnemonic error", e)
            null
        }
    }

    /**
     * Gửi BTC
     * @param toAddressStr Địa chỉ nhận
     * @param amountSat Số satoshi
     */
    fun send(toAddressStr: String, amountSat: Long): String {
        Log.d(TAG, "=== send START ===")
        Log.d(TAG, "To: $toAddressStr, Amount: $amountSat sat")
        
        try {
            // Làm sạch địa chỉ - FIX lỗi Illegal escape '\.'
            val cleanAddress = toAddressStr.replace("\\s".toRegex(), "").trim()
            Log.d(TAG, "Clean address: $cleanAddress")
            
            if (cleanAddress.isEmpty()) {
                throw IllegalArgumentException("Địa chỉ nhận trống")
            }
            
            // Kiểm tra địa chỉ có dấu chấm (test)
            if (cleanAddress.matches(".*\\..*".toRegex())) {
                Log.w(TAG, "Address contains dot character")
            }

            val toAddress = Address.fromString(params, cleanAddress)
            val amount = Coin.valueOf(amountSat)
            
            val wallet = kit.wallet()
            val available = wallet.getBalance(Wallet.BalanceType.AVAILABLE)
            
            Log.d(TAG, "Available balance: $available")
            
            if (available.isLessThan(amount)) {
                throw IllegalArgumentException("Số dư không đủ. Có: $available, cần: $amount")
            }

            val request = SendRequest.to(toAddress, amount)
            request.feePerKb = Coin.valueOf(DEFAULT_FEE_PER_KB)
            
            Log.d(TAG, "Sending transaction...")
            val sendResult = wallet.sendCoins(kit.peerGroup(), request)
            val tx = sendResult.tx
            
            Log.d(TAG, "=== send SUCCESS ===")
            Log.d(TAG, "TxID: ${tx.txId}")
            
            return tx.txId.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "=== send FAILED ===", e)
            throw e
        }
    }

    fun getTransactions(): List<String> {
        return try {
            if (!isWalletReady()) return emptyList()
            kit.wallet().getTransactionsByTime().map { tx ->
                val time = tx.updateTime ?: Date()
                "${tx.txId} | ${time}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTransactions error", e)
            emptyList()
        }
    }

    fun getProgress(): Double {
        return try {
            if (::kit.isInitialized) {
                val height = kit.peerGroup().mostCommonChainHeight
                height.toDouble()
            } else 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping WalletManager...")
        try {
            if (::kit.isInitialized && kit.isRunning) {
                kit.stopAsync()
                kit.awaitTerminated(10, TimeUnit.SECONDS)
                Log.d(TAG, "WalletManager stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
        }
    }
}