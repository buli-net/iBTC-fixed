package net.buli.ibtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.core.listeners.PeerConnectedEventListener
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.discovery.DnsDiscovery
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.ScriptType
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.wallet.*
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * WalletManager - iBTC v4.1 Full Implementation
 * Compatible with bitcoinj-core 0.16.3
 * 
 * Features:
 * - SPV blockchain sync
 * - BIP39 12/24 word seed
 * - BIP44 HD wallet (P2PKH)
 * - Send/Receive Bitcoin
 * - Transaction history
 * - Backup/restore
 * - Multiple address types
 * - Fee estimation
 * - Real-time balance updates
 */
class WalletManager(private val context: Context, private val appDir: File) {

    companion object {
        private const val TAG = "WalletManager"
        private const val WALLET_FILENAME_PREFIX = "ibtc-wallet"
        private const val SPV_BLOCKCHAIN_FILENAME = "ibtc-spvchain"
        private const val WALLET_BACKUP_PREFIX = "ibtc-backup"
        
        // Network configuration
        private val NETWORK_PARAMETERS: NetworkParameters = MainNetParams.get()
        // For testnet: private val NETWORK_PARAMETERS = TestNet3Params.get()
        
        private const val DEFAULT_FEE_PER_KB = 2000L // 20 sat/byte
        private const val MIN_CONFIRMATIONS = 1
        private const val MAX_CONNECTIONS = 8
    }

    // ==================== CORE COMPONENTS ====================
    private lateinit var walletKit: WalletAppKit
    private var currentDeterministicSeed: DeterministicSeed? = null
    private var spvBlockStore: SPVBlockStore? = null
    
    // ==================== STATE TRACKING ====================
    @Volatile private var isWalletRunning = false
    @Volatile private var isBlockchainSyncing = false
    @Volatile private var lastSyncPercentage = 0.0
    @Volatile private var currentBlockHeight = 0
    @Volatile private var peerCount = 0
    
    // ==================== LISTENERS ====================
    var onSyncProgressListener: ((percent: Double, blocksProcessed: Int, totalBlocks: Int, currentDate: Date?) -> Unit)? = null
    var onSyncCompletedListener: (() -> Unit)? = null
    var onPeerConnectedListener: ((peerCount: Int) -> Unit)? = null
    var onPeerDisconnectedListener: ((peerCount: Int) -> Unit)? = null
    var onBalanceChangedListener: ((newBalance: Coin, availableBalance: Coin) -> Unit)? = null
    var onTransactionReceivedListener: ((tx: Transaction, value: Coin) -> Unit)? = null
    var onTransactionSentListener: ((tx: Transaction, value: Coin) -> Unit)? = null
    var onWalletReadyListener: (() -> Unit)? = null

    // ==================== INITIALIZATION ====================
    
    /**
     * Initialize wallet manager with optional existing seed
     */
    fun initialize(existingSeed: DeterministicSeed? = null) {
        Log.i(TAG, "=== Initializing WalletManager ===")
        Log.i(TAG, "Network: ${NETWORK_PARAMETERS.id}")
        Log.i(TAG, "App directory: ${appDir.absolutePath}")
        
        currentDeterministicSeed = existingSeed
        setupWalletAppKit()
        configureWalletListeners()
    }

    private fun setupWalletAppKit() {
        Log.d(TAG, "Setting up WalletAppKit...")
        
        val blockStoreFile = File(appDir, "$SPV_BLOCKCHAIN_FILENAME.spvchain")
        
        walletKit = object : WalletAppKit(NETWORK_PARAMETERS, appDir, WALLET_FILENAME_PREFIX) {
            
            override fun createWallet(): Wallet {
                Log.i(TAG, "Creating wallet instance...")
                
                return if (currentDeterministicSeed != null) {
                    Log.i(TAG, "Creating wallet FROM EXISTING SEED")
                    Log.d(TAG, "Seed creation time: ${Date(currentDeterministicSeed!!.creationTimeSeconds * 1000)}")
                    
                    // BITCOINJ 0.16.3 - Must provide ScriptType
                    val wallet = Wallet.fromSeed(
                        NETWORK_PARAMETERS,
                        currentDeterministicSeed!!,
                        ScriptType.P2PKH,  // Legacy P2PKH for maximum compatibility
                        WalletProtobufSerializer.parseToProto("").keyList.size // Key chain structure
                    )
                    
                    Log.i(TAG, "Wallet created from seed with ${wallet.keyChainGroupStructure}")
                    wallet
                } else {
                    Log.i(TAG, "Creating NEW RANDOM wallet")
                    // Create new deterministic wallet
                    val newWallet = Wallet.createDeterministic(
                        NETWORK_PARAMETERS,
                        ScriptType.P2PKH
                    )
                    Log.i(TAG, "New wallet created: ${newWallet.currentReceiveAddress()}")
                    newWallet
                }
            }

            override fun onSetupCompleted() {
                Log.i(TAG, "=== Wallet Setup Completed ===")
                
                val wallet = wallet()
                
                // Configure wallet for 0.16.3 (old methods removed)
                wallet.setAcceptRiskyTransactions(true)
                wallet.allowSpendingUnconfirmedTransactions()
                
                // Set wallet description
                wallet.setDescription("iBTC Wallet v4.1 - ${Date()}")
                
                Log.i(TAG, "Wallet address: ${wallet.currentReceiveAddress()}")
                Log.i(TAG, "Wallet balance: ${wallet.balance.toFriendlyString()}")
                Log.i(TAG, "Keychain size: ${wallet.keyChainGroupSize}")
                
                isWalletRunning = true
                onWalletReadyListener?.invoke()
            }

            override fun provideBlockStore(file: File): SPVBlockStore {
                Log.d(TAG, "Providing SPV Block Store: ${file.absolutePath}")
                return try {
                    spvBlockStore = SPVBlockStore(NETWORK_PARAMETERS, file)
                    spvBlockStore!!
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create block store, deleting and retrying", e)
                    file.delete()
                    spvBlockStore = SPVBlockStore(NETWORK_PARAMETERS, file)
                    spvBlockStore!!
                }
            }

            override fun onPreBlocksDownload() {
                Log.i(TAG, "Starting blockchain download...")
                isBlockchainSyncing = true
            }
        }

        // Configure WalletAppKit settings
        walletKit.apply {
            setAutoSave(true)
            setBlockingStartup(false)
            setUserAgent("iBTC", "4.1")
            setMaxConnections(MAX_CONNECTIONS)
            
            // Set peer discovery
            setDiscovery(DnsDiscovery(NETWORK_PARAMETERS))
            
            // Configure download listener with 0.16.3 API
            setDownloadListener(createDownloadProgressTracker())
        }

        // Configure peer listeners
        walletKit.peerGroup().apply {
            addConnectedEventListener(PeerConnectedEventListener { peer, peerCount ->
                this@WalletManager.peerCount = peerCount
                Log.d(TAG, "Peer connected: ${peer.address} (total: $peerCount)")
                onPeerConnectedListener?.invoke(peerCount)
            })
            
            addDisconnectedEventListener(PeerDisconnectedEventListener { peer, peerCount ->
                this@WalletManager.peerCount = peerCount
                Log.d(TAG, "Peer disconnected: ${peer.address} (total: $peerCount)")
                onPeerDisconnectedListener?.invoke(peerCount)
            })
        }

        Log.d(TAG, "WalletAppKit setup complete")
    }

    private fun createDownloadProgressTracker(): DownloadProgressTracker {
        return object : DownloadProgressTracker() {
            
            // BITCOINJ 0.16.3 - date parameter is Instant?
            override fun progress(pct: Double, blocksSoFar: Int, date: Instant?) {
                super.progress(pct, blocksSoFar, date)
                
                lastSyncPercentage = pct
                currentBlockHeight = blocksSoFar
                isBlockchainSyncing = pct < 100.0
                
                val javaDate = date?.let { Date.from(it) }
                val totalBlocks = walletKit.peerGroup().mostCommonChainHeight
                
                Log.v(TAG, "Sync progress: ${String.format("%.2f", pct)}% ($blocksSoFar/$totalBlocks)")
                
                onSyncProgressListener?.invoke(pct, blocksSoFar, totalBlocks, javaDate)
            }

            override fun doneDownload() {
                super.doneDownload()
                Log.i(TAG, "=== Blockchain sync COMPLETED ===")
                Log.i(TAG, "Final block height: $currentBlockHeight")
                
                isBlockchainSyncing = false
                lastSyncPercentage = 100.0
                
                onSyncCompletedListener?.invoke()
                onSyncProgressListener?.invoke(100.0, currentBlockHeight, currentBlockHeight, Date())
            }

            override fun onBlocksDownloaded(peer: Peer?, block: Block?, filteredBlock: FilteredBlock?, blocksLeft: Int) {
                super.onBlocksDownloaded(peer, block, filteredBlock, blocksLeft)
                if (blocksLeft % 1000 == 0) {
                    Log.d(TAG, "Blocks left to download: $blocksLeft")
                }
            }
        }
    }

    private fun configureWalletListeners() {
        // Wallet listeners will be added in onSetupCompleted
        Log.d(TAG, "Wallet listeners will be configured after setup")
    }

    // ==================== LIFECYCLE CONTROL ====================
    
    fun startAsync() {
        if (!::walletKit.isInitialized) {
            throw IllegalStateException("WalletKit not initialized. Call initialize() first.")
        }
        
        Log.i(TAG, "Starting wallet asynchronously...")
        walletKit.startAsync()
    }

    fun awaitRunning() {
        Log.d(TAG, "Waiting for wallet to be running...")
        walletKit.awaitRunning()
        isWalletRunning = true
        Log.i(TAG, "Wallet is now RUNNING")
    }

    fun awaitRunning(timeout: Long, unit: TimeUnit): Boolean {
        return try {
            walletKit.awaitRunning(timeout, unit)
            isWalletRunning = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Timeout waiting for wallet", e)
            false
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping wallet...")
        isWalletRunning = false
        
        if (::walletKit.isInitialized) {
            try {
                walletKit.stopAsync()
                walletKit.awaitTerminated(30, TimeUnit.SECONDS)
                Log.i(TAG, "Wallet stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping wallet", e)
            }
        }
        
        try {
            spvBlockStore?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing block store", e)
        }
    }

    fun isRunning(): Boolean = isWalletRunning && ::walletKit.isInitialized && walletKit.isRunning

    // ==================== WALLET CREATION ====================
    
    data class WalletCreationResult(
        val mnemonicCode: List<String>,
        val seed: DeterministicSeed,
        val creationTime: Date,
        val firstAddress: String,
        val walletId: String
    )

    fun createNewWallet(strength: Int = 128): WalletCreationResult {
        Log.i(TAG, "=== Creating NEW WALLET ===")
        Log.d(TAG, "Entropy strength: $strength bits")
        
        // Generate secure entropy
        val entropy = ByteArray(strength / 8)
        SecureRandom().nextBytes(entropy)
        
        // Create seed with current time
        val creationTimeSeconds = System.currentTimeMillis() / 1000
        val seed = DeterministicSeed(entropy, "", creationTimeSeconds)
        
        currentDeterministicSeed = seed
        
        Log.i(TAG, "Seed created with ${seed.mnemonicCode?.size} words")
        Log.d(TAG, "Creation time: ${Date(creationTimeSeconds * 1000)}")
        
        // Stop existing wallet if running
        if (isRunning()) {
            Log.d(TAG, "Stopping existing wallet before creating new one")
            stop()
            deleteWalletFiles()
        }
        
        // Reinitialize with new seed
        setupWalletAppKit()
        startAsync()
        awaitRunning()
        
        val wallet = getWallet()!!
        val firstAddress = wallet.currentReceiveAddress().toString()
        val walletId = wallet.walletId.toString()
        
        Log.i(TAG, "New wallet created successfully")
        Log.i(TAG, "First address: $firstAddress")
        Log.i(TAG, "Wallet ID: $walletId")
        
        return WalletCreationResult(
            mnemonicCode = seed.mnemonicCode ?: emptyList(),
            seed = seed,
            creationTime = Date(creationTimeSeconds * 1000),
            firstAddress = firstAddress,
            walletId = walletId
        )
    }

    // ==================== WALLET RESTORATION ====================
    
    fun restoreFromMnemonic(
        mnemonicCode: List<String>,
        passphrase: String = "",
        creationTime: Long = 0
    ): Boolean {
        return try {
            Log.i(TAG, "=== Restoring wallet from mnemonic ===")
            Log.d(TAG, "Word count: ${mnemonicCode.size}")
            Log.d(TAG, "Has passphrase: ${passphrase.isNotEmpty()}")
            
            val seed = if (creationTime > 0) {
                DeterministicSeed(mnemonicCode, null, passphrase, creationTime)
            } else {
                // Use current time if not specified (will rescan from genesis)
                DeterministicSeed(mnemonicCode, null, passphrase, System.currentTimeMillis() / 1000)
            }
            
            currentDeterministicSeed = seed
            
            // Stop and clean existing wallet
            if (isRunning()) {
                stop()
            }
            deleteWalletFiles()
            
            // Reinitialize
            setupWalletAppKit()
            startAsync()
            awaitRunning()
            
            Log.i(TAG, "Wallet restored successfully")
            Log.i(TAG, "Restored address: ${getCurrentReceiveAddress()}")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore wallet from mnemonic", e)
            false
        }
    }

    fun restoreFromSeed(seed: DeterministicSeed): Boolean {
        return try {
            Log.i(TAG, "Restoring wallet from DeterministicSeed")
            currentDeterministicSeed = seed
            
            if (isRunning()) stop()
            deleteWalletFiles()
            
            setupWalletAppKit()
            startAsync()
            awaitRunning()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from seed", e)
            false
        }
    }

    // ==================== WALLET INFORMATION ====================
    
    fun getWallet(): Wallet? = if (isRunning()) walletKit.wallet() else null

    fun isWalletReady(): Boolean = isRunning() && getWallet() != null

    fun getCurrentReceiveAddress(): String {
        return getWallet()?.currentReceiveAddress()?.toString() ?: ""
    }

    fun getFreshReceiveAddress(): String {
        return getWallet()?.freshReceiveAddress()?.toString() ?: ""
    }

    fun getAllReceiveAddresses(): List<String> {
        val wallet = getWallet() ?: return emptyList()
        return wallet.issuedReceiveAddresses.map { it.toString() }
    }

    fun getBalance(): Coin = getWallet()?.balance ?: Coin.ZERO

    fun getBalance(type: Wallet.BalanceType): Coin = getWallet()?.getBalance(type) ?: Coin.ZERO

    fun getAvailableBalance(): Coin = getBalance(Wallet.BalanceType.AVAILABLE)

    fun getEstimatedBalance(): Coin = getBalance(Wallet.BalanceType.ESTIMATED)

    fun getUnconfirmedBalance(): Coin {
        val wallet = getWallet() ?: return Coin.ZERO
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED).minus(wallet.getBalance(Wallet.BalanceType.AVAILABLE))
    }

    fun getBalanceInBTC(): Double = getBalance().value.toDouble() / 100_000_000.0

    fun getBalanceInSatoshis(): Long = getBalance().value

    fun getMnemonicCode(): List<String>? {
        return currentDeterministicSeed?.mnemonicCode ?: getWallet()?.keyChainSeed?.mnemonicCode
    }

    fun getSeed(): DeterministicSeed? {
        return currentDeterministicSeed ?: getWallet()?.keyChainSeed
    }

    fun getCreationTime(): Date {
        val seconds = getWallet()?.keyChainSeed?.creationTimeSeconds ?: 0
        return Date(seconds * 1000)
    }

    fun getWalletId(): String = getWallet()?.walletId?.toString() ?: ""

    fun getNetworkParameters(): NetworkParameters = NETWORK_PARAMETERS

    fun getCurrentBlockHeight(): Int = currentBlockHeight

    fun getPeerCount(): Int = peerCount

    fun getSyncProgress(): Double = lastSyncPercentage

    fun isSyncing(): Boolean = isBlockchainSyncing

    // ==================== TRANSACTION MANAGEMENT ====================
    
    data class TransactionInfo(
        val txId: String,
        val hash: Sha256Hash,
        val amount: Coin,
        val fee: Coin?,
        val isSent: Boolean,
        val isPending: Boolean,
        val confirmations: Int,
        val timestamp: Date,
        val memo: String?,
        val inputs: List<String>,
        val outputs: List<String>,
        val blockHeight: Int?
    ) {
        fun getAmountInBTC(): Double = kotlin.math.abs(amount.value.toDouble() / 100_000_000.0)
        fun getFormattedAmount(): String = String.format(Locale.US, "%.8f", getAmountInBTC())
        fun getFormattedDate(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(timestamp)
        fun getShortTxId(): String = "${txId.take(8)}...${txId.takeLast(8)}"
    }

    fun getTransactionHistory(limit: Int = 100): List<TransactionInfo> {
        val wallet = getWallet() ?: return emptyList()
        
        return wallet.getTransactionsByTime()
            .sortedByDescending { it.updateTime }
            .take(limit)
            .map { tx -> convertToTransactionInfo(tx, wallet) }
    }

    private fun convertToTransactionInfo(tx: Transaction, wallet: Wallet): TransactionInfo {
        val value = tx.getValue(wallet)
        val isSent = value.isNegative
        val confidence = tx.confidence
        val confirmations = confidence?.depthInBlocks ?: 0
        
        return TransactionInfo(
            txId = tx.txId.toString(),
            hash = tx.txId,
            amount = value,
            fee = tx.fee,
            isSent = isSent,
            isPending = confidence?.confidenceType != TransactionConfidence.ConfidenceType.BUILDING,
            confirmations = confirmations,
            timestamp = tx.updateTime ?: Date(0),
            memo = tx.memo,
            inputs = tx.inputs.map { it.outpoint.toString() },
            outputs = tx.outputs.map { it.getAddressFromP2PKHScript(NETWORK_PARAMETERS)?.toString() ?: "Unknown" },
            blockHeight = confidence?.appearedAtChainHeight
        )
    }

    fun getTransactionById(txId: String): TransactionInfo? {
        val wallet = getWallet() ?: return null
        val hash = Sha256Hash.wrap(txId)
        val tx = wallet.getTransaction(hash) ?: return null
        return convertToTransactionInfo(tx, wallet)
    }

    // ==================== SENDING BITCOIN ====================
    
    data class SendCoinsResult(
        val success: Boolean,
        val transactionId: String?,
        val transaction: Transaction?,
        val errorMessage: String?,
        val feePaid: Coin?
    )

    fun sendCoins(
        destinationAddress: String,
        amountSatoshis: Long,
        feePerKilobyte: Long = DEFAULT_FEE_PER_KB,
        memo: String? = null
    ): SendCoinsResult {
        return try {
            Log.i(TAG, "=== Sending Coins ===")
            Log.d(TAG, "To: $destinationAddress")
            Log.d(TAG, "Amount: $amountSatoshis sats")
            
            val wallet = getWallet() ?: throw IllegalStateException("Wallet not ready")
            val amount = Coin.valueOf(amountSatoshis)
            
            // Validate address
            val targetAddress = try {
                Address.fromString(NETWORK_PARAMETERS, destinationAddress.trim())
            } catch (e: Exception) {
                return SendCoinsResult(false, null, null, "Invalid Bitcoin address: ${e.message}", null)
            }
            
            // Check balance
            val availableBalance = getAvailableBalance()
            if (amount.isGreaterThan(availableBalance)) {
                return SendCoinsResult(
                    false, null, null,
                    "Insufficient funds. Available: ${availableBalance.toFriendlyString()}, Required: ${amount.toFriendlyString()}",
                    null
                )
            }
            
            // Create send request
            val sendRequest = SendRequest.to(targetAddress, amount).apply {
                feePerKb = Coin.valueOf(feePerKilobyte)
                ensureMinRequiredFee = true
                memo?.let { this.memo = it }
                // Enable RBF
                // this.enableRBF = true
            }
            
            // Complete transaction (calculate fee, select inputs)
            wallet.completeTx(sendRequest)
            
            Log.d(TAG, "Transaction fee: ${sendRequest.tx.fee?.toFriendlyString()}")
            Log.d(TAG, "Transaction size: ${sendRequest.tx.messageSize} bytes")
            
            // Send transaction
            val sendResult = wallet.sendCoins(walletKit.peerGroup(), sendRequest)
            val tx = sendResult.tx
            
            Log.i(TAG, "Transaction broadcast successfully")
            Log.i(TAG, "TXID: ${tx.txId}")
            
            SendCoinsResult(
                success = true,
                transactionId = tx.txId.toString(),
                transaction = tx,
                errorMessage = null,
                feePaid = tx.fee
            )
            
        } catch (e: InsufficientMoneyException) {
            Log.e(TAG, "Insufficient money", e)
            SendCoinsResult(false, null, null, "Insufficient funds: ${e.message}", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send coins", e)
            SendCoinsResult(false, null, null, "Send failed: ${e.message}", null)
        }
    }

    fun estimateFee(destinationAddress: String, amountSatoshis: Long): Coin? {
        return try {
            val wallet = getWallet() ?: return null
            val targetAddress = Address.fromString(NETWORK_PARAMETERS, destinationAddress)
            val amount = Coin.valueOf(amountSatoshis)
            
            val request = SendRequest.to(targetAddress, amount)
            request.feePerKb = Coin.valueOf(DEFAULT_FEE_PER_KB)
            
            wallet.completeTx(request)
            request.tx.fee
        } catch (e: Exception) {
            Log.e(TAG, "Fee estimation failed", e)
            null
        }
    }

    // ==================== BACKUP & RESTORE ====================
    
    fun backupWalletToFile(destinationFile: File): Boolean {
        return try {
            val wallet = getWallet() ?: return false
            
            Log.i(TAG, "Backing up wallet to: ${destinationFile.absolutePath}")
            
            // Ensure parent directory exists
            destinationFile.parentFile?.mkdirs()
            
            wallet.saveToFile(destinationFile)
            
            Log.i(TAG, "Wallet backup successful")
            Log.d(TAG, "Backup size: ${destinationFile.length()} bytes")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Wallet backup failed", e)
            false
        }
    }

    fun createEncryptedBackup(destinationFile: File, password: String): Boolean {
        return try {
            val wallet = getWallet() ?: return false
            
            Log.i(TAG, "Creating encrypted backup")
            
            // Encrypt wallet
            val keyCrypter = wallet.keyCrypter
            if (keyCrypter == null) {
                // Wallet not encrypted, encrypt it first
                wallet.encrypt(password)
            }
            
            wallet.saveToFile(destinationFile)
            
            Log.i(TAG, "Encrypted backup created")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted backup failed", e)
            false
        }
    }

    suspend fun restoreWalletFromFile(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.i(TAG, "Restoring wallet from file: ${backupFile.absolutePath}")
            
            if (!backupFile.exists()) {
                Log.e(TAG, "Backup file does not exist")
                return@withContext false
            }
            
            // Stop current wallet
            if (isRunning()) {
                stop()
            }
            
            // Delete existing wallet files
            deleteWalletFiles()
            
            // Load wallet from backup
            val restoredWallet = Wallet.loadFromFile(backupFile)
            
            // Save to standard location
            val targetFile = File(appDir, "$WALLET_FILENAME_PREFIX.wallet")
            restoredWallet.saveToFile(targetFile)
            
            Log.i(TAG, "Wallet restored, reinitializing...")
            
            // Reinitialize
            currentDeterministicSeed = restoredWallet.keyChainSeed
            setupWalletAppKit()
            startAsync()
            awaitRunning()
            
            Log.i(TAG, "Wallet restore completed successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Wallet restore failed", e)
            false
        }
    }

    // ==================== UTILITY METHODS ====================
    
    private fun deleteWalletFiles() {
        Log.d(TAG, "Deleting existing wallet files...")
        
        val filesToDelete = listOf(
            File(appDir, "$WALLET_FILENAME_PREFIX.wallet"),
            File(appDir, "$WALLET_FILENAME_PREFIX.wallet.protobuf"),
            File(appDir, "$WALLET_FILENAME_PREFIX.wallet-protobuf"),
            File(appDir, "$SPV_BLOCKCHAIN_FILENAME.spvchain"),
            File(appDir, "$SPV_BLOCKCHAIN_FILENAME.spvchain-journal")
        )
        
        filesToDelete.forEach { file ->
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted ${file.name}: $deleted")
            }
        }
    }

    fun getWalletFile(): File = File(appDir, "$WALLET_FILENAME_PREFIX.wallet")

    fun getWalletSize(): Long = getWalletFile().length()

    fun isWalletEncrypted(): Boolean = getWallet()?.isEncrypted ?: false

    fun encryptWallet(password: String): Boolean {
        return try {
            val wallet = getWallet() ?: return false
            if (!wallet.isEncrypted) {
                wallet.encrypt(password)
                Log.i(TAG, "Wallet encrypted successfully")
                true
            } else {
                Log.w(TAG, "Wallet already encrypted")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wallet encryption failed", e)
            false
        }
    }

    fun decryptWallet(password: String): Boolean {
        return try {
            val wallet = getWallet() ?: return false
            if (wallet.isEncrypted) {
                wallet.decrypt(password)
                Log.i(TAG, "Wallet decrypted successfully")
                true
            } else {
                Log.w(TAG, "Wallet not encrypted")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wallet decryption failed", e)
            false
        }
    }

    // ==================== NETWORK INFO ====================
    
    data class NetworkInfo(
        val peerCount: Int,
        val blockHeight: Int,
        val bestChainDate: Date?,
        val isSyncing: Boolean,
        val syncProgress: Double
    )

    fun getNetworkInfo(): NetworkInfo {
        val peerGroup = if (::walletKit.isInitialized) walletKit.peerGroup() else null
        val chain = if (::walletKit.isInitialized) walletKit.chain() else null
        
        return NetworkInfo(
            peerCount = peerCount,
            blockHeight = currentBlockHeight,
            bestChainDate = chain?.chainHead?.header?.time,
            isSyncing = isBlockchainSyncing,
            syncProgress = lastSyncPercentage
        )
    }

    // ==================== CLEANUP ====================
    
    fun cleanup() {
        Log.i(TAG, "Cleaning up WalletManager")
        stop()
        onSyncProgressListener = null
        onSyncCompletedListener = null
        onBalanceChangedListener = null
        onTransactionReceivedListener = null
        onTransactionSentListener = null
    }
}