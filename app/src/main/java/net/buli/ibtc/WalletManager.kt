package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.bitcoinj.core.Address
import org.bitcoinj.core.BlockChain
import org.bitcoinj.core.Coin
import org.bitcoinj.core.PeerGroup
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
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.UUID
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
        if (activeId!= null) {
            return activeId
        }
        val ids = prefs.all.keys.filter { it.endsWith("_name") }.map { it.removeSuffix("_name") }
        val first = ids.firstOrNull()
        if (first!= null) {
            activeId = first
        }
        return first
    }

    fun getActive(): WalletInfo? {
        val id = getActiveId()?: return null
        val name = prefs.getString("${id}_name", "Ví")?: "Ví"
        val created = prefs.getLong("${id}_created", System.currentTimeMillis())
        return WalletInfo(id, name, created)
    }

    fun getAllWallets(): List<WalletInfo> {
        val result = mutableListOf<WalletInfo>()
        for (key in prefs.all.keys) {
            if (key.endsWith("_name")) {
                val id = key.removeSuffix("_name")
                val name = prefs.getString(key, "Ví")?: "Ví"
                val created = prefs.getLong("${id}_created", 0)
                result.add(WalletInfo(id, name, created))
            }
        }
        return result.sortedByDescending { it.createdAt }
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
           .putString("${id}_name", if (name.isBlank()) "Ví ${id.take(4).uppercase(Locale.US)}" else name)
           .putLong("${id}_created", System.currentTimeMillis())
           .putInt("${id}_attempts", 0)
           .apply()
        activeId = id
        cachedSeed = seedStr
        cachedPassword = password.toCharArray()
        return getActive()!!
    }

    fun import(name: String, seedPhrase: String, password: String): WalletInfo? {
        try {
            val cleanSeed = seedPhrase.trim().lowercase(Locale.US).replace("\\s+".toRegex(), " ")
            val words = cleanSeed.split(" ")
            if (words.size!in listOf(12, 15, 18, 21, 24)) {
                return null
            }
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
            return getActive()
        } catch (e: Exception) {
            return null
        }
    }

    fun unlock(id: String, password: String): Boolean {
        val attempts = prefs.getInt("${id}_attempts", 0)
        if (attempts >= 5) {
            return false
        }
        val enc = prefs.getString("${id}_seed", null)?: return false
        try {
            val decoded = Base64.decode(enc, Base64.NO_WRAP)
            val decrypted = CryptoUtil.decrypt(decoded, password.toCharArray())
            val seedStr = String(decrypted, Charsets.UTF_8)
            MnemonicCode.INSTANCE.check(seedStr.split(" "))
            cachedSeed = seedStr
            cachedPassword = password.toCharArray()
            activeId = id
            prefs.edit().putInt("${id}_attempts", 0).apply()
            return true
        } catch (e: Exception) {
            prefs.edit().putInt("${id}_attempts", attempts + 1).apply()
            return false
        }
    }

    fun lock() {
        if (cachedPassword!= null) {
            cachedPassword!!.fill('0')
        }
        cachedPassword = null
        cachedSeed = null
        try {
            peerGroup?.stop()
        } catch (e: Exception) {
        }
        try {
            blockStore?.close()
        } catch (e: Exception) {
        }
        wallet = null
        blockChain = null
        peerGroup = null
        blockStore = null
    }

    fun init() {
        val seedStr = cachedSeed?: throw IllegalStateException("Chưa mở khóa")
        val id = activeId?: "default"
        val seed = DeterministicSeed(seedStr, null, "", System.currentTimeMillis() / 1000)
        wallet = Wallet.fromSeed(params, seed, Script.ScriptType.P2WPKH)
        val chainFile = File(ctx.filesDir, "$id.spvchain")
        blockStore = SPVBlockStore(params, chainFile)
        blockChain = BlockChain(params, wallet, blockStore)
        peerGroup = PeerGroup(params, blockChain)
        peerGroup!!.setUserAgent("iBTC", "4.3")
        peerGroup!!.addPeerDiscovery(DnsDiscovery(params))
        peerGroup!!.maxConnections = 8

        val tracker = object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                super.progress(pct, blocksSoFar, date)
                val percent = pct.toInt()
                val text = when {
                    pct < 1.0 -> "Đang kết nối peers..."
                    pct < 10.0 -> "Đang tải headers... $percent%"
                    pct < 99.0 -> "Đang đồng bộ blocks... $blocksSoFar"
                    else -> "Đã đồng bộ"
                }
                if (progressListener!= null) {
                    progressListener!!.invoke(percent, text)
                }
            }

            override fun doneDownload() {
                super.doneDownload()
                if (progressListener!= null) {
                    val height = blockChain?.bestChainHeight?: 0
                    progressListener!!.invoke(100, "Đã đồng bộ - Block $height")
                }
            }
        }

        Thread {
            try {
                if (progressListener!= null) {
                    progressListener!!.invoke(0, "Đang khởi tạo SPV...")
                }
                peerGroup!!.start()
                peerGroup!!.startBlockChainDownload(tracker)
            } catch (e: Exception) {
                if (progressListener!= null) {
                    progressListener!!.invoke(0, "Lỗi kết nối: ${e.message}")
                }
            }
        }.start()
    }

    fun onProgress(listener: (Int, String) -> Unit) {
        progressListener = listener
    }

    fun getBalance(): Double {
        val w = wallet
        if (w == null) {
            return 0.0
        }
        try {
            val balance = w.getBalance(Wallet.BalanceType.ESTIMATED)
            return balance.value.toDouble() / 100000000.0
        } catch (e: Exception) {
            return 0.0
        }
    }

    fun getAddress(): String {
        val w = wallet
        if (w == null) {
            return ""
        }
        try {
            return w.currentReceiveAddress().toString()
        } catch (e: Exception) {
            return ""
        }
    }

    fun getTransactions(): List<TxInfo> {
        val w = wallet
        if (w == null) {
            return emptyList()
        }
        try {
            val txs = w.getTransactions(true)
            val list = mutableListOf<TxInfo>()
            for (tx in txs) {
                val value = tx.getValue(w).value
                val type = if (value > 0) "Nhận" else "Gửi"
                val amount = abs(value.toDouble()) / 100000000.0
                val time = tx.updateTime?: Date()
                val conf = tx.confidence?.depthInBlocks?: 0
                val id = tx.txId.toString()
                list.add(TxInfo(id, type, amount, time, conf))
            }
            return list.sortedByDescending { it.time }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun getSeed(): String {
        return cachedSeed?: ""
    }

    fun send(toAddress: String, amountBtc: Double, feeRate: Int): String {
        val w = wallet
        val pg = peerGroup
        if (w == null) {
            return "Lỗi: Ví chưa mở"
        }
        if (pg == null) {
            return "Lỗi: Chưa kết nối mạng"
        }
        try {
            val target = Address.fromString(params, toAddress)
            val amount = Coin.valueOf((amountBtc * 100000000.0).roundToLong())
            val req = Wallet.SendRequest.to(target, amount)
            req.feePerKb = Coin.valueOf((feeRate * 1000).toLong())
            w.completeTx(req)
            w.commitTx(req.tx)
            val broadcast = pg.broadcastTransaction(req.tx)
            broadcast.broadcast().get()
            return req.tx.txId.toString()
        } catch (e: Exception) {
            return "Lỗi: ${e.message}"
        }
    }

    fun estimateFee(to: String, amount: Double, feeRate: Int): Double {
        val vbytes = 140
        return (vbytes * feeRate) / 100000000.0
    }

    fun price(): Double {
        try {
            val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.inputStream.bufferedReader().readText()
            val match = """"usd":([0-9.]+)""".toRegex().find(text)
            if (match!= null) {
                return match.groupValues[1].toDouble()
            }
            return 65000.0
        } catch (e: Exception) {
            return 65000.0
        }
    }

    fun getFeeRates(): FeeRates {
        try {
            val url = URL("https://mempool.space/api/v1/fees/recommended")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.inputStream.bufferedReader().readText()
            val slow = """"hourFee":(\d+)""".toRegex().find(text)?.groupValues?.get(1)?.toInt()?: 5
            val normal = """"halfHourFee":(\d+)""".toRegex().find(text)?.groupValues?.get(1)?.toInt()?: 10
            val fast = """"fastestFee":(\d+)""".toRegex().find(text)?.groupValues?.get(1)?.toInt()?: 20
            return FeeRates(slow, normal, fast)
        } catch (e: Exception) {
            return FeeRates(5, 10, 20)
        }
    }

    fun changePassword(id: String, oldPass: String, newPass: String): Boolean {
        val ok = unlock(id, oldPass)
        if (!ok) {
            return false
        }
        val seed = cachedSeed
        if (seed == null) {
            return false
        }
        try {
            val encrypted = CryptoUtil.encrypt(seed.toByteArray(Charsets.UTF_8), newPass.toCharArray())
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString("${id}_seed", encoded).putInt("${id}_attempts", 0).apply()
            cachedPassword = newPass.toCharArray()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun rename(id: String, newName: String) {
        prefs.edit().putString("${id}_name", newName).apply()
    }

    fun delete(id: String) {
        lock()
        prefs.edit().remove("${id}_seed").remove("${id}_name").remove("${id}_created").remove("${id}_attempts").apply()
        try {
            val f = File(ctx.filesDir, "$id.spvchain")
            if (f.exists()) {
                f.delete()
            }
        } catch (e: Exception) {
        }
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
}