package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.net.discovery.DnsDiscovery
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

data class WalletInfo(val id: String, val name: String, val createdAt: Long)
data class TxInfo(val txId: String, val type: String, val amount: Double, val time: Date, val confirmations: Int)
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {

    private val params = MainNetParams.get()
    private val prefs: SharedPreferences = ctx.getSharedPreferences("ibtc_wallets_v4", Context.MODE_PRIVATE)
    
    private var wallet: Wallet? = null
    private var blockStore: SPVBlockStore? = null
    private var blockChain: BlockChain? = null
    private var peerGroup: PeerGroup? = null
    
    private var activeId: String? = null
    private var cachedSeed: String? = null
    private var cachedPassword: CharArray? = null
    private var progressListener: ((Int, String) -> Unit)? = null

    fun hasWallets(): Boolean {
        return prefs.all.keys.any { it.endsWith("_name") }
    }

    fun getActiveId(): String? {
        if (activeId != null) return activeId
        val ids = prefs.all.keys.filter { it.endsWith("_name") }.map { it.removeSuffix("_name") }
        return ids.firstOrNull()?.also { activeId = it }
    }

    fun getActive(): WalletInfo? {
        val id = getActiveId() ?: return null
        val name = prefs.getString("${id}_name", "Ví") ?: "Ví"
        val created = prefs.getLong("${id}_created", System.currentTimeMillis())
        return WalletInfo(id, name, created)
    }

    fun getAllWallets(): List<WalletInfo> {
        return prefs.all.keys.filter { it.endsWith("_name") }.map { key ->
            val id = key.removeSuffix("_name")
            WalletInfo(id, prefs.getString(key, "Ví") ?: "Ví", prefs.getLong("${id}_created", 0))
        }.sortedByDescending { it.createdAt }
    }

    fun create(name: String, password: String): WalletInfo {
        val id = UUID.randomUUID().toString()
        val entropy = ByteArray(16)
        Random().nextBytes(entropy)
        val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
        val seedStr = mnemonic.joinToString(" ")
        val encrypted = CryptoUtil.encrypt(seedStr.toByteArray(Charsets.UTF_8), password.toCharArray())
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit()
            .putString("${id}_seed", encoded)
            .putString("${id}_name", if (name.isBlank()) "Ví ${id.take(4).uppercase()}" else name)
            .putLong("${id}_created", System.currentTimeMillis())
            .putInt("${id}_attempts", 0)
            .apply()
        activeId = id
        cachedSeed = seedStr
        cachedPassword = password.toCharArray()
        return getActive()!!
    }

    fun import(name: String, seedPhrase: String, password: String): WalletInfo? {
        return try {
            val cleanSeed = seedPhrase.trim().lowercase(Locale.US)
            val words = cleanSeed.split("\\s+".toRegex())
            if (words.size !in listOf(12, 15, 18, 21, 24)) return null
            MnemonicCode.INSTANCE.check(words)
            val id = UUID.randomUUID().toString()
            val encrypted = CryptoUtil.encrypt(cleanSeed.toByteArray(Charsets.UTF_8), password.toCharArray())
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit()
                .putString("${id}_seed", encoded)
                .putString("${id}_name", if (name.isBlank()) "Ví Import" else name)
                .putLong("${id}_created", System.currentTimeMillis())
                .putInt("${id}_attempts", 0)
                .apply()
            activeId = id
            cachedSeed = cleanSeed
            cachedPassword = password.toCharArray()
            getActive()
        } catch (e: Exception) {
            null
        }
    }

    fun unlock(id: String, password: String): Boolean {
        val attempts = prefs.getInt("${id}_attempts", 0)
        if (attempts >= 5) return false
        val enc = prefs.getString("${id}_seed", null) ?: return false
        return try {
            val decoded = Base64.decode(enc, Base64.NO_WRAP)
            val decrypted = CryptoUtil.decrypt(decoded, password.toCharArray())
            val seedStr = String(decrypted, Charsets.UTF_8)
            MnemonicCode.INSTANCE.check(seedStr.split(" "))
            cachedSeed = seedStr
            cachedPassword = password.toCharArray()
            activeId = id
            prefs.edit().putInt("${id}_attempts", 0).apply()
            true
        } catch (e: Exception) {
            prefs.edit().putInt("${id}_attempts", attempts + 1).apply()
            false
        }
    }

    fun lock() {
        cachedPassword?.fill('0')
        cachedPassword = null
        cachedSeed = null
        try {
            peerGroup?.stopAsync()
            peerGroup?.awaitTerminated()
        } catch (_: Exception) {}
        try {
            blockStore?.close()
        } catch (_: Exception) {}
        wallet = null
        blockChain = null
        peerGroup = null
        blockStore = null
        try {
            val id = activeId ?: return
            File(ctx.filesDir, "$id.spvchain").delete()
            File(ctx.filesDir, "$id.spvchain.lock").delete()
        } catch (_: Exception) {}
    }

    fun init() {
        val seedStr = cachedSeed ?: throw IllegalStateException("Chưa mở khóa ví")
        val id = activeId ?: "default"
        
        val seed = DeterministicSeed(seedStr, null, "", System.currentTimeMillis() / 1000)
        wallet = Wallet.fromSeed(params, seed, Script.ScriptType.P2WPKH)
        
        val chainFile = File(ctx.filesDir, "$id.spvchain")
        blockStore = SPVBlockStore(params, chainFile)
        blockChain = BlockChain(params, wallet, blockStore)
        
        peerGroup = PeerGroup(params, blockChain).apply {
            setUserAgent("iBTC", "4.3")
            addPeerDiscovery(DnsDiscovery(params))
            setMaxConnections(8)
            setConnectTimeoutMillis(10000)
        }

        val tracker = object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                super.progress(pct, blocksSoFar, date)
                val percent = pct.toInt()
                val peers = peerGroup?.connectedPeers?.size ?: 0
                val text = when {
                    pct < 1 -> "Đang kết nối mạng... ($peers peers)"
                    pct < 5 -> "Đang tìm peers... ($peers)"
                    pct < 30 -> "Đang tải headers... $percent%"
                    pct < 99 -> "Đang đồng bộ blocks... $blocksSoFar"
                    else -> "Đã đồng bộ"
                }
                progressListener?.invoke(percent, text)
            }

            override fun doneDownload() {
                super.doneDownload()
                val peers = peerGroup?.connectedPeers?.size ?: 0
                val height = blockChain?.bestChainHeight ?: 0
                progressListener?.invoke(100, "Đã đồng bộ - Block $height - $peers peers")
            }
        }

        Thread {
            try {
                progressListener?.invoke(0, "Đang khởi tạo SPV...")
                peerGroup?.startAsync()
                peerGroup?.awaitRunning()
                peerGroup?.startBlockChainDownload(tracker)
                wallet?.let { w ->
                    cachedPassword?.let { pwd ->
                        if (!w.isEncrypted) {
                            w.encrypt(String(pwd))
                        }
                    }
                }
            } catch (e: Exception) {
                progressListener?.invoke(0, "Lỗi kết nối: ${e.message}")
            }
        }.start()
    }

    fun onProgress(listener: (Int, String) -> Unit) {
        progressListener = listener
    }

    fun getBalance(): Double {
        val w = wallet ?: return 0.0
        return try {
            w.getBalance(Wallet.BalanceType.ESTIMATED).value.toDouble() / 1e8
        } catch (_: Exception) {
            0.0
        }
    }

    fun getAddress(): String {
        val w = wallet ?: return ""
        return try {
            w.currentReceiveAddress().toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun getFreshAddress(): String {
        val w = wallet ?: return ""
        return try {
            w.freshReceiveAddress().toString()
        } catch (_: Exception) {
            getAddress()
        }
    }

    fun getTransactions(): List<TxInfo> {
        val w = wallet ?: return emptyList()
        return try {
            w.transactions.map { tx ->
                val value = tx.getValue(w).value
                val type = if (value > 0) "Nhận" else "Gửi"
                TxInfo(
                    txId = tx.txId.toString(),
                    type = type,
                    amount = abs(value.toDouble()) / 1e8,
                    time = tx.updateTime ?: Date(),
                    confirmations = tx.confidence.depthInBlocks
                )
            }.sortedByDescending { it.time }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSeed(): String {
        return cachedSeed ?: ""
    }

    fun send(toAddress: String, amountBtc: Double, feeRate: Int): String {
        val w = wallet ?: return "Lỗi: Ví chưa mở"
        val pg = peerGroup ?: return "Lỗi: Chưa kết nối mạng"
        return try {
            val target = Address.fromString(params, toAddress)
            val amount = Coin.valueOf((amountBtc * 1e8).roundToLong())
            val req = Wallet.SendRequest.to(target, amount)
            req.feePerKb = Coin.valueOf(feeRate * 1000L)
            req.ensureMinRequiredFee = true
            
            val pwd = cachedPassword?.let { String(it) }
            val wasEncrypted = w.isEncrypted
            if (wasEncrypted && pwd != null) {
                w.decrypt(pwd)
            }
            
            w.completeTx(req)
            w.commitTx(req.tx)
            
            val broadcast = pg.broadcastTransaction(req.tx)
            val future = broadcast.broadcast()
            future.get()
            
            if (wasEncrypted && pwd != null) {
                w.encrypt(pwd)
            }
            
            req.tx.txId.toString()
        } catch (e: InsufficientMoneyException) {
            "Lỗi: Không đủ số dư (cần thêm phí)"
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }

    fun estimateFee(to: String, amount: Double, feeRate: Int): Double {
        val vbytes = 140
        return (vbytes * feeRate) / 1e8
    }

    fun price(): Double {
        return try {
            val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.inputStream.bufferedReader().readText()
            val regex = """"usd":([0-9.]+)""".toRegex()
            regex.find(text)?.groupValues?.get(1)?.toDouble() ?: 65000.0
        } catch (_: Exception) {
            65000.0
        }
    }

    fun getFeeRates(): FeeRates {
        return try {
            val url = URL("https://mempool.space/api/v1/fees/recommended")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.inputStream.bufferedReader().readText()
            val slow = """"hourFee":(\d+)""".toRegex().find(text)?.groupValues?.get(1)?.toInt() ?: 5
            val normal = """"halfHourFee":(\d+)""".toRegex().find(text)?.groupValues?.get(1)?.toInt() ?: 10
            val fast = """"fastestFee":(\d+)""".toRegex().find(text)?.groupValues?.get(1)?.toInt() ?: 20
            FeeRates(slow, normal, fast)
        } catch (_: Exception) {
            FeeRates(5, 10, 20)
        }
    }

    fun changePassword(id: String, oldPass: String, newPass: String): Boolean {
        if (!unlock(id, oldPass)) return false
        val seed = cachedSeed ?: return false
        return try {
            val encrypted = CryptoUtil.encrypt(seed.toByteArray(Charsets.UTF_8), newPass.toCharArray())
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit()
                .putString("${id}_seed", encoded)
                .putInt("${id}_attempts", 0)
                .apply()
            cachedPassword = newPass.toCharArray()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun rename(id: String, newName: String) {
        prefs.edit().putString("${id}_name", newName).apply()
    }

    fun delete(id: String) {
        lock()
        prefs.edit()
            .remove("${id}_seed")
            .remove("${id}_name")
            .remove("${id}_created")
            .remove("${id}_attempts")
            .apply()
        try {
            File(ctx.filesDir, "$id.spvchain").delete()
            File(ctx.filesDir, "$id.spvchain.lock").delete()
        } catch (_: Exception) {}
        if (activeId == id) {
            activeId = null
        }
    }

    fun stop() {
        lock()
        progressListener = null
    }

    fun isLocked(): Boolean {
        return cachedSeed == null
    }

    fun getPeerCount(): Int {
        return peerGroup?.connectedPeers?.size ?: 0
    }

    fun getBlockHeight(): Int {
        return blockChain?.bestChainHeight ?: 0
    }
}