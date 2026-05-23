package net.buli.ibtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptType
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletProtobufSerializer
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

/**
 * WalletManager - Full version for iBTC v4.1
 * Compatible with bitcoinj-core 0.16.3
 * Features: create, restore, backup, send, receive, SPV sync, transaction history
 */
class WalletManager(private val context: Context, private val appDir: File) {

    companion object {
        private const val TAG = "WalletManager"
        private const val WALLET_FILE_PREFIX = "ibtc-wallet"
        private const val SPV_FILE = "ibtc-spv.blockstore"
    }

    private val params: NetworkParameters = MainNetParams.get()
    private lateinit var kit: WalletAppKit
    private var currentSeed: DeterministicSeed? = null
    private var isSyncing = false

    // Listeners
    var onSyncProgress: ((percent: Double, blocksLeft: Int, date: Date?) -> Unit)? = null
    var onSyncFinished: (() -> Unit)? = null
    var onBalanceChanged: ((Coin) -> Unit)? = null
    var onTransactionReceived: ((Transaction) -> Unit)? = null

    // ==================== WALLET LIFECYCLE ====================

    fun initialize(existingSeed: DeterministicSeed? = null) {
        Log.i(TAG, "Initializing WalletManager")
        currentSeed = existingSeed
        setupWalletKit()
    }

    private fun setupWalletKit() {
        val blockStoreFile = File(appDir, SPV_FILE)
        
        kit = object : WalletAppKit(params, appDir, WALLET_FILE_PREFIX) {
            override fun createWallet(): Wallet {
                return if (currentSeed != null) {
                    Log.i(TAG, "Creating wallet from seed")
                    // BITCOINJ 0.16.3 API - requires ScriptType
                    Wallet.fromSeed(params, currentSeed!!, ScriptType.P2PKH)
                } else {
                    Log.i(TAG, "Creating new random wallet")
                    Wallet.createDeterministic(params, ScriptType.P2PKH)
                }
            }

            override fun onSetupCompleted() {
                Log.i(TAG, "Wallet setup completed")
                // FIX for 0.16.3 - old method removed
                wallet().setAcceptRiskyTransactions(true)
                wallet().allowSpendingUnconfirmedTransactions()
                
                // Add balance listener
                wallet().addCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
                    Log.d(TAG, "Coins received: ${tx.txId}")
                    onBalanceChanged?.invoke(newBalance)
                    onTransactionReceived?.invoke(tx)
                }
                
                wallet().addCoinsSentEventListener { wallet, tx, prevBalance, newBalance ->
                    Log.d(TAG, "Coins sent: ${tx.txId}")
                    onBalanceChanged?.invoke(newBalance)
                }
            }

            override fun provideBlockStore(file: File): SPVBlockStore {
                return SPVBlockStore(params, blockStoreFile)
            }
        }

        // Configure kit
        kit.setAutoSave(true)
        kit.setBlockingStartup(false)
        kit.setDownloadListener(object : DownloadProgressTracker() {
            // FIX 0.16.3 - date is Instant?
            override fun progress(pct: Double, blocksSoFar: Int, date: Instant?) {
                isSyncing = pct < 100.0
                val javaDate = date?.let { Date.from(it) }
                val blocksLeft = kit.peerGroup().mostCommonChainHeight - blocksSoFar
                onSyncProgress?.invoke(pct, blocksLeft.coerceAtLeast(0), javaDate)
            }

            override fun doneDownload() {
                Log.i(TAG, "Blockchain sync complete")
                isSyncing = false
                onSyncFinished?.invoke()
                onSyncProgress?.invoke(100.0, 0, Date())
            }
        })

        kit.setUserAgent("iBTC", "4.1")
        kit.setMaxConnections(8)
    }

    fun startAsync() {
        if (!::kit.isInitialized) setupWalletKit()
        Log.i(TAG, "Starting WalletAppKit")
        kit.startAsync()
    }

    fun awaitRunning() {
        kit.awaitRunning()
        Log.i(TAG, "Wallet is running")
    }

    fun stop() {
        if (::kit.isInitialized) {
            Log.i(TAG, "Stopping wallet")
            kit.stopAsync()
            try {
                kit.awaitTerminated()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping kit", e)
            }
        }
    }

    // ==================== WALLET CREATION ====================

    fun createNewWallet(): WalletCreationResult {
        Log.i(TAG, "Creating new wallet with 12 words")
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val seed = DeterministicSeed(entropy, "", System.currentTimeMillis() / 1000)
        currentSeed = seed
        
        // Stop old kit if running
        if (::kit.isInitialized && kit.isRunning) stop()
        
        setupWalletKit()
        startAsync()
        awaitRunning()
        
        return WalletCreationResult(
            mnemonic = seed.mnemonicCode ?: emptyList(),
            creationTime = Date(seed.creationTimeSeconds * 1000),
            address = getCurrentReceiveAddress()
        )
    }

    fun restoreFromMnemonic(mnemonic: List<String>, passphrase: String = "", birthday: Long = 0): Boolean {
        return try {
            Log.i(TAG, "Restoring wallet from mnemonic")
            val seed = DeterministicSeed(mnemonic, null, passphrase, birthday)
            currentSeed = seed
            
            if (::kit.isInitialized && kit.isRunning) stop()
            
            // Delete old wallet files for clean restore
            deleteWalletFiles()
            
            setupWalletKit()
            startAsync()
            awaitRunning()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore wallet", e)
            false
        }
    }

    private fun deleteWalletFiles() {
        try {
            File(appDir, "$WALLET_FILE_PREFIX.wallet").delete()
            File(appDir, "$WALLET_FILE_PREFIX.wallet.protobuf").delete()
            File(appDir, SPV_FILE).delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete old wallet files", e)
        }
    }

    // ==================== WALLET INFO ====================

    fun isWalletReady(): Boolean = ::kit.isInitialized && kit.isRunning && kit.wallet() != null

    fun getWallet(): Wallet? = if (isWalletReady()) kit.wallet() else null

    fun getBalance(): Coin = getWallet()?.balance ?: Coin.ZERO

    fun getBalanceAvailable(): Coin = getWallet()?.getBalance(Wallet.BalanceType.AVAILABLE) ?: Coin.ZERO

    fun getBalanceEstimated(): Coin = getWallet()?.getBalance(Wallet.BalanceType.ESTIMATED) ?: Coin.ZERO

    fun getBalanceBTC(): Double = getBalance().value.toDouble() / 100_000_000.0

    fun getCurrentReceiveAddress(): String {
        return getWallet()?.currentReceiveAddress()?.toString() ?: ""
    }

    fun getFreshReceiveAddress(): String {
        return getWallet()?.freshReceiveAddress()?.toString() ?: ""
    }

    fun getMnemonic(): List<String>? {
        return currentSeed?.mnemonicCode ?: getWallet()?.keyChainSeed?.mnemonicCode
    }

    fun getCreationTime(): Date {
        val seconds = getWallet()?.keyChainSeed?.creationTimeSeconds ?: 0
        return Date(seconds * 1000)
    }

    fun getNetwork(): String = params.id

    fun isSyncing(): Boolean = isSyncing

    // ==================== TRANSACTIONS ====================

    fun getTransactionHistory(): List<TransactionInfo> {
        val wallet = getWallet() ?: return emptyList()
        return wallet.getTransactionsByTime().map { tx ->
            val value = tx.getValue(wallet)
            val isSent = value.isNegative
            val timestamp = tx.updateTime ?: Date(0)
            
            TransactionInfo(
                txId = tx.txId.toString(),
                amount = value,
                isSent = isSent,
                timestamp = timestamp,
                confirmations = tx.confidence?.depthInBlocks ?: 0,
                fee = tx.fee,
                memo = tx.memo
            )
        }.sortedByDescending { it.timestamp }
    }

    fun sendCoins(toAddress: String, amountSatoshis: Long, feePerKb: Long = 2000): SendResult {
        return try {
            val wallet = getWallet() ?: throw IllegalStateException("Wallet not ready")
            val target = Address.fromString(params, toAddress.trim())
            val amount = Coin.valueOf(amountSatoshis)
            
            if (amount.isGreaterThan(getBalanceAvailable())) {
                return SendResult(false, null, "Insufficient funds")
            }
            
            val request = SendRequest.to(target, amount)
            request.feePerKb = Coin.valueOf(feePerKb)
            request.ensureMinRequiredFee = true
            
            val result = wallet.sendCoins(kit.peerGroup(), request)
            val txId = result.tx.txId.toString()
            
            Log.i(TAG, "Transaction sent: $txId")
            SendResult(true, txId, null)
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
            SendResult(false, null, e.message ?: "Unknown error")
        }
    }

    fun estimateFee(amountSatoshis: Long, toAddress: String): Coin? {
        return try {
            val wallet = getWallet() ?: return null
            val target = Address.fromString(params, toAddress)
            val request = SendRequest.to(target, Coin.valueOf(amountSatoshis))
            request.feePerKb = Coin.valueOf(1000)
            wallet.completeTx(request)
            request.tx.fee
        } catch (e: Exception) {
            null
        }
    }

    // ==================== BACKUP & RESTORE ====================

    fun backupWalletToFile(destination: File): Boolean {
        return try {
            val wallet = getWallet() ?: return false
            wallet.saveToFile(destination)
            Log.i(TAG, "Wallet backed up to ${destination.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            false
        }
    }

    fun restoreWalletFromFile(backupFile: File): Boolean {
        return try {
            if (::kit.isInitialized) stop()
            deleteWalletFiles()
            
            val wallet = Wallet.loadFromFile(backupFile)
            val dest = File(appDir, "$WALLET_FILE_PREFIX.wallet")
            wallet.saveToFile(dest)
            
            setupWalletKit()
            startAsync()
            awaitRunning()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore from file failed", e)
            false
        }
    }

    // ==================== DATA CLASSES ====================

    data class WalletCreationResult(
        val mnemonic: List<String>,
        val creationTime: Date,
        val address: String
    )

    data class TransactionInfo(
        val txId: String,
        val amount: Coin,
        val isSent: Boolean,
        val timestamp: Date,
        val confirmations: Int,
        val fee: Coin?,
        val memo: String?
    ) {
        fun getFormattedAmount(): String {
            val btc = amount.value.toDouble() / 1e8
            return String.format(Locale.US, "%.8f", kotlin.math.abs(btc))
        }
        
        fun getFormattedDate(): String {
            return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(timestamp)
        }
    }

    data class SendResult(
        val success: Boolean,
        val txId: String?,
        val error: String?
    )
}