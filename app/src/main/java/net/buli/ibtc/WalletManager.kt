package net.buli.ibtc

import android.content.Context
import android.util.Log
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.*
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener
import org.bitcoinj.wallet.listeners.WalletChangeEventListener
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * WalletManager - Quản lý ví Bitcoin thực sự với bitcoinj 0.16.3
 * Đã fix cho API 0.16.3: bỏ provideBlockStore, onPreBlocksDownload, allowSpendingUnconfirmedTransactions
 */
class WalletManager(
    private val context: Context,
    private val walletDir: File
) {

    companion object {
        private const val TAG = "WalletManager"
        private const val WALLET_FILES_PREFIX = "ibtc-wallet"
        private const val CHECKPOINTS_FILE = "checkpoints.txt"
    }

    // ==================== NETWORK CONFIGURATION ====================
    private val networkParams: NetworkParameters = MainNetParams.get()
    
    // ==================== CORE COMPONENTS ====================
    private var kit: WalletAppKit? = null
    private var downloadTracker: SyncProgressTracker? = null
    private var spvBlockStore: org.bitcoinj.store.SPVBlockStore? = null

    // ==================== LISTENERS ====================
    var onSyncProgressListener: ((percent: Double, blocksProcessed: Int, totalBlocks: Int, currentDate: Date?) -> Unit)? = null
    var onSyncCompletedListener: (() -> Unit)? = null
    var onBalanceChangedListener: ((newBalance: Coin, availableBalance: Coin) -> Unit)? = null
    var onTransactionReceivedListener: ((transaction: Transaction, value: Coin) -> Unit)? = null
    var onTransactionSentListener: ((transaction: Transaction, value: Coin) -> Unit)? = null

    // ==================== WALLET STATE ====================
    data class WalletCreationResult(
        val success: Boolean,
        val mnemonicCode: List<String>,
        val firstAddress: String,
        val seedCreationTime: Long
    )

    data class SendCoinsResult(
        val success: Boolean,
        val transactionId: String?,
        val transaction: Transaction?,
        val errorMessage: String?,
        val feePaid: Coin?
    )

    data class TransactionInfo(
        val txId: String,
        val amount: Coin,
        val fee: Coin?,
        val timestamp: Date,
        val confirmations: Int,
        val isSent: Boolean,
        val memo: String?,
        val involvedAddresses: List<String>
    ) {
        fun getFormattedAmount(): String {
            return String.format("%.8f", amount.value.toDouble() / 100_000_000.0)
        }
        fun getFormattedDate(): String {
            return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(timestamp)
        }
    }

    // ==================== INITIALIZATION ====================
    fun initialize() {
        Log.i(TAG, "Initializing WalletManager with bitcoinj 0.16.3")
        
        if (!walletDir.exists()) {
            walletDir.mkdirs()
        }

        downloadTracker = SyncProgressTracker()

        kit = object : WalletAppKit(networkParams, walletDir, WALLET_FILES_PREFIX) {
            override fun onSetupCompleted() {
                Log.i(TAG, "WalletAppKit setup completed")
                
                wallet().apply {
                    // Listen for incoming transactions
                    addCoinsReceivedEventListener(WalletCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
                        Log.i(TAG, "Coins received: ${tx.txId} - Amount: ${newBalance.minus(prevBalance).toFriendlyString()}")
                        onTransactionReceivedListener?.invoke(tx, newBalance.minus(prevBalance))
                        onBalanceChangedListener?.invoke(newBalance, getBalance(Wallet.BalanceType.AVAILABLE))
                    })

                    // Listen for outgoing transactions
                    addCoinsSentEventListener(WalletCoinsSentEventListener { wallet, tx, prevBalance, newBalance ->
                        Log.i(TAG, "Coins sent: ${tx.txId} - Amount: ${prevBalance.minus(newBalance).toFriendlyString()}")
                        onTransactionSentListener?.invoke(tx, prevBalance.minus(newBalance))
                        onBalanceChangedListener?.invoke(newBalance, getBalance(Wallet.BalanceType.AVAILABLE))
                    })

                    // Listen for balance changes
                    addChangeEventListener(WalletChangeEventListener {
                        val newBalance = getBalance()
                        val availableBalance = getBalance(Wallet.BalanceType.AVAILABLE)
                        onBalanceChangedListener?.invoke(newBalance, availableBalance)
                    })

                    // Allow spending unconfirmed - 0.16.3 method
                    setAcceptRiskyTransactions(true)
                }

                // Configure peer group
                peerGroup().apply {
                    setDownloadTxDependencies(0)
                    maxConnections = 8
                    setFastCatchupTimeSecs(wallet().earliestKeyCreationTime)
                }
            }
        }.apply {
            setBlockingStartup(false)
            setDownloadListener(downloadTracker)
            setAutoSave(true)
            setUserAgent("iBTC", "4.1")
            
            // Load checkpoints if available
            try {
                val checkpointsStream: InputStream? = context.assets.open(CHECKPOINTS_FILE)
                if (checkpointsStream != null) {
                    setCheckpoints(checkpointsStream)
                    Log.i(TAG, "Checkpoints loaded")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No checkpoints file found, will sync from genesis")
            }
        }

        Log.i(TAG, "WalletAppKit configured for ${networkParams.id}")
    }

    // ==================== LIFECYCLE MANAGEMENT ====================
    fun startAsync() {
        Log.i(TAG, "Starting WalletAppKit asynchronously")
        try {
            kit?.startAsync()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Kit already started or starting: ${e.message}")
        }
    }

    fun awaitRunning() {
        Log.i(TAG, "Waiting for WalletAppKit to be running")
        try {
            kit?.awaitRunning(60, TimeUnit.SECONDS)
            Log.i(TAG, "WalletAppKit is now running")
        } catch (e: Exception) {
            Log.e(TAG, "Error awaiting kit running", e)
            throw e
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping WalletAppKit")
        try {
            kit?.stopAsync()
            kit?.awaitTerminated(30, TimeUnit.SECONDS)
            spvBlockStore?.close()
            Log.i(TAG, "WalletAppKit stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping kit", e)
        }
    }

    fun isWalletReady(): Boolean {
        return kit?.isRunning == true && kit?.wallet() != null
    }

    // ==================== WALLET CREATION & RESTORATION ====================
    fun createNewWallet(): WalletCreationResult {
        Log.i(TAG, "Creating new wallet with fresh seed")
        
        try {
            stop()
            deleteWalletFiles()

            val seed = DeterministicSeed(System.currentTimeMillis() / 1000)
            val mnemonicCode = seed.mnemonicCode ?: emptyList()
            
            val newKit = WalletAppKit(networkParams, walletDir, WALLET_FILES_PREFIX)
            newKit.restoreWalletFromSeed(seed)
            newKit.setBlockingStartup(false)
            newKit.setDownloadListener(downloadTracker)
            newKit.setAutoSave(true)
            
            newKit.startAsync()
            newKit.awaitRunning()
            
            kit = newKit
            
            val wallet = kit!!.wallet()
            val firstAddress = wallet.currentReceiveAddress().toString()
            val creationTime = seed.creationTimeSeconds
            
            Log.i(TAG, "New wallet created successfully")
            Log.i(TAG, "First address: $firstAddress")
            Log.i(TAG, "Mnemonic: ${mnemonicCode.joinToString(" ")}")
            
            return WalletCreationResult(
                success = true,
                mnemonicCode = mnemonicCode,
                firstAddress = firstAddress,
                seedCreationTime = creationTime
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating new wallet", e)
            return WalletCreationResult(
                success = false,
                mnemonicCode = emptyList(),
                firstAddress = "",
                seedCreationTime = 0
            )
        }
    }

    fun restoreFromMnemonic(mnemonicCode: List<String>, passphrase: String = ""): Boolean {
        Log.i(TAG, "Restoring wallet from mnemonic (${mnemonicCode.size} words)")
        
        return try {
            stop()
            deleteWalletFiles()

            val seed = DeterministicSeed(mnemonicCode, null, passphrase, System.currentTimeMillis() / 1000)
            
            val restoreKit = WalletAppKit(networkParams, walletDir, WALLET_FILES_PREFIX)
            restoreKit.restoreWalletFromSeed(seed)
            restoreKit.setBlockingStartup(false)
            restoreKit.setDownloadListener(downloadTracker)
            restoreKit.setAutoSave(true)
            
            restoreKit.startAsync()
            restoreKit.awaitRunning()
            
            kit = restoreKit
            
            Log.i(TAG, "Wallet restored successfully from mnemonic")
            Log.i(TAG, "Current address: ${getCurrentReceiveAddress()}")
            Log.i(TAG, "Balance: ${getBalance().toFriendlyString()}")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring wallet from mnemonic", e)
            false
        }
    }

    // ==================== BALANCE & ADDRESS MANAGEMENT ====================
    fun getBalance(): Coin {
        return kit?.wallet()?.getBalance(Wallet.BalanceType.ESTIMATED) ?: Coin.ZERO
    }

    fun getAvailableBalance(): Coin {
        return kit?.wallet()?.getBalance(Wallet.BalanceType.AVAILABLE) ?: Coin.ZERO
    }

    fun getEstimatedBalance(): Coin {
        return kit?.wallet()?.getBalance(Wallet.BalanceType.ESTIMATED) ?: Coin.ZERO
    }

    fun getCurrentReceiveAddress(): String {
        return try {
            kit?.wallet()?.currentReceiveAddress()?.toString() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current address", e)
            ""
        }
    }

    fun getFreshReceiveAddress(): String {
        return try {
            val address = kit?.wallet()?.freshReceiveAddress()
            Log.i(TAG, "Generated fresh address: $address")
            address?.toString() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fresh address", e)
            ""
        }
    }

    fun getAllReceiveAddresses(): List<String> {
        return try {
            kit?.wallet()?.issuedReceiveAddresses?.map { it.toString() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all addresses", e)
            emptyList()
        }
    }

    // ==================== SENDING BITCOIN ====================
    fun sendCoins(recipientAddress: String, amountSatoshis: Long, feePerKb: Long = 2000): SendCoinsResult {
        Log.i(TAG, "Sending $amountSatoshis satoshis to $recipientAddress")
        
        val wallet = kit?.wallet() ?: return SendCoinsResult(
            success = false,
            transactionId = null,
            transaction = null,
            errorMessage = "Wallet not initialized",
            feePaid = null
        )

        return try {
            val amount = Coin.valueOf(amountSatoshis)
            val targetAddress = Address.fromString(networkParams, recipientAddress)
            
            val sendRequest = SendRequest.to(targetAddress, amount)
            sendRequest.feePerKb = Coin.valueOf(feePerKb)
            sendRequest.ensureMinRequiredFee = true
            sendRequest.signInputs = true
            
            val result = wallet.sendCoins(kit?.peerGroup(), sendRequest)
            val transaction = result.tx
            
            Log.i(TAG, "Transaction created successfully: ${transaction.txId}")
            Log.i(TAG, "Fee paid: ${transaction.fee?.toFriendlyString()}")
            
            SendCoinsResult(
                success = true,
                transactionId = transaction.txId.toString(),
                transaction = transaction,
                errorMessage = null,
                feePaid = transaction.fee
            )
            
        } catch (e: InsufficientMoneyException) {
            Log.e(TAG, "Insufficient funds", e)
            SendCoinsResult(
                success = false,
                transactionId = null,
                transaction = null,
                errorMessage = "Insufficient funds. Need ${e.missing?.toFriendlyString()} more",
                feePaid = null
            )
        } catch (e: AddressFormatException) {
            Log.e(TAG, "Invalid address format", e)
            SendCoinsResult(
                success = false,
                transactionId = null,
                transaction = null,
                errorMessage = "Invalid Bitcoin address: ${e.message}",
                feePaid = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending coins", e)
            SendCoinsResult(
                success = false,
                transactionId = null,
                transaction = null,
                errorMessage = "Send failed: ${e.message}",
                feePaid = null
            )
        }
    }

    // ==================== TRANSACTION HISTORY ====================
    fun getTransactionHistory(): List<TransactionInfo> {
        val wallet = kit?.wallet() ?: return emptyList()
        
        return try {
            wallet.getTransactionsByTime().mapNotNull { tx ->
                try {
                    val value = tx.getValue(wallet)
                    val fee = tx.fee
                    val isSent = value.isNegative
                    val confirmations = tx.confidence.depthInBlocks
                    val timestamp = tx.updateTime ?: Date(0)
                    
                    val involvedAddresses = mutableListOf<String>()
                    
                    tx.outputs.forEach { output ->
                        if (output.isMine(wallet)) {
                            try {
                                val address = output.scriptPubKey.getToAddress(networkParams)
                                involvedAddresses.add(address.toString())
                            } catch (e: Exception) { }
                        }
                    }
                    
                    tx.inputs.forEach { input ->
                        try {
                            val connectedOutput = input.connectedOutput
                            if (connectedOutput != null && connectedOutput.isMine(wallet)) {
                                val address = connectedOutput.scriptPubKey.getToAddress(networkParams)
                                involvedAddresses.add(address.toString())
                            }
                        } catch (e: Exception) { }
                    }
                    
                    TransactionInfo(
                        txId = tx.txId.toString(),
                        amount = if (isSent) value.negate() else value,
                        fee = fee,
                        timestamp = timestamp,
                        confirmations = confirmations,
                        isSent = isSent,
                        memo = tx.memo,
                        involvedAddresses = involvedAddresses.distinct()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing transaction ${tx.txId}", e)
                    null
                }
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transaction history", e)
            emptyList()
        }
    }

    // ==================== WALLET INFO ====================
    fun getMnemonicCode(): List<String>? {
        return try {
            kit?.wallet()?.keyChainSeed?.mnemonicCode
        } catch (e: Exception) {
            Log.e(TAG, "Error getting mnemonic", e)
            null
        }
    }

    fun getSeedCreationTime(): Long {
        return kit?.wallet()?.keyChainSeed?.creationTimeSeconds ?: 0
    }

    fun getCreationTime(): Date {
        val timeSeconds = kit?.wallet()?.earliestKeyCreationTime ?: 0
        return Date(timeSeconds * 1000)
    }

    fun getWalletId(): String {
        return try {
            val seed = kit?.wallet()?.keyChainSeed
            if (seed != null) {
                seed.mnemonicCode?.joinToString(" ")?.hashCode().toString()
            } else {
                "unknown"
            }
        } catch (e: Exception) {
            "error"
        }
    }

    // ==================== BACKUP & RESTORE ====================
    fun backupWalletToFile(backupFile: File): Boolean {
        return try {
            kit?.wallet()?.saveToFile(backupFile)
            Log.i(TAG, "Wallet backed up to: ${backupFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up wallet", e)
            false
        }
    }

    fun getNetworkParameters(): NetworkParameters = networkParams
    fun getPeerCount(): Int = kit?.peerGroup()?.numConnectedPeers ?: 0
    fun getChainHeight(): Int = kit?.chain()?.bestChainHeight ?: 0

    // ==================== PRIVATE HELPERS ====================
    private fun deleteWalletFiles() {
        try {
            val walletFile = File(walletDir, "$WALLET_FILES_PREFIX.wallet")
            val chainFile = File(walletDir, "$WALLET_FILES_PREFIX.spvchain")
            
            if (walletFile.exists()) walletFile.delete()
            if (chainFile.exists()) chainFile.delete()
            
            Log.i(TAG, "Old wallet files deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting wallet files", e)
        }
    }

    // ==================== SYNC PROGRESS TRACKER ====================
    private inner class SyncProgressTracker : DownloadProgressTracker() {
        
        override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
            super.progress(pct, blocksSoFar, date)
            onSyncProgressListener?.invoke(pct, blocksSoFar, blocksSoFar, date)
            if (blocksSoFar % 1000 == 0) {
                Log.d(TAG, "Sync progress: ${String.format("%.2f", pct)}% ($blocksSoFar blocks)")
            }
        }

        override fun doneDownload() {
            super.doneDownload()
            Log.i(TAG, "Blockchain download completed")
            val currentBlockHeight = kit?.chain()?.bestChainHeight ?: 0
            onSyncProgressListener?.invoke(100.0, currentBlockHeight, Date())
            onSyncCompletedListener?.invoke()
        }
    }
}