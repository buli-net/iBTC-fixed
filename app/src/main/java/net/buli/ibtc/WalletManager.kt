package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL
import java.security.SecureRandom
import java.util.Date

// 1 ví = id + tên + seed 12 từ
data class WalletInfo(val id: String, var name: String, val seed: String)
// 3 mức phí lấy từ mạng
data class FeeRates(val slow: Long, val normal: Long, val fast: Long)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get() // mainnet thật
    private val prefs = ctx.getSharedPreferences("ibtc_wallets", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null
    private var wallet: Wallet? = null
    @Volatile private var ready = false
    private var progressCb: ((Int, String) -> Unit)? = null

    // ===== CÀI ĐẶT TÙY CHỈNH =====
    fun getFeeApiUrl(): String = prefs.getString("fee_url", "https://mempool.space/api/v1/fees/recommended")!!
    fun setFeeApiUrl(url: String) { prefs.edit().putString("fee_url", url).apply() }
    fun getRefreshSec(): Long = prefs.getLong("refresh_sec", 60) // mặc định 60s
    fun setRefreshSec(sec: Long) { prefs.edit().putLong("refresh_sec", sec).apply() }
    fun getDefaultCustomFee(): Long = prefs.getLong("custom_fee", 10)
    fun setDefaultCustomFee(fee: Long) { prefs.edit().putLong("custom_fee", fee).apply() }

    fun onProgress(cb: (Int, String) -> Unit) { progressCb = cb }
    fun hasWallets(): Boolean = getAll().isNotEmpty()

    fun getAll(): List<WalletInfo> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return ids.map { id -> WalletInfo(id, prefs.getString("n_$id", "Ví")!!, prefs.getString("s_$id", "")!!) }
    }

    private fun saveIds(ids: Set<String>) { prefs.edit().putStringSet("ids", ids).apply() }

    fun create(name: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val entropy = ByteArray(16); SecureRandom().nextBytes(entropy)
        val seed = DeterministicSeed(entropy, "", System.currentTimeMillis() / 1000)
        val mnemonic = seed.mnemonicCode?.joinToString(" ") ?: ""
        val info = WalletInfo(id, if (name.isBlank()) "Ví mới" else name, mnemonic)
        saveIds(getAll().map { it.id }.toMutableSet().apply { add(id) })
        prefs.edit().putString("n_$id", info.name).putString("s_$id", mnemonic).apply()
        setActive(id); return info
    }

    fun import(name: String, mnemonic: String): WalletInfo? = try {
        DeterministicSeed(mnemonic.trim().split("\\s+".toRegex()), null, "", 0)
        val id = System.currentTimeMillis().toString()
        val info = WalletInfo(id, if (name.isBlank()) "Ví import" else name, mnemonic.trim())
        saveIds(getAll().map { it.id }.toMutableSet().apply { add(id) })
        prefs.edit().putString("n_$id", info.name).putString("s_$id", info.seed).apply()
        setActive(id); info
    } catch (_: Exception) { null }

    fun setActive(id: String) { prefs.edit().putString("active", id).apply() }
    fun getActive(): WalletInfo? = getAll().find { it.id == prefs.getString("active", null) }
    fun rename(id: String, n: String) { prefs.edit().putString("n_$id", n).apply() }
    fun delete(id: String) {
        try { kit?.stopAsync() } catch (_: Exception) {}
        saveIds(getAll().map { it.id }.toMutableSet().apply { remove(id) })
        prefs.edit().remove("n_$id").remove("s_$id").apply()
        File(ctx.filesDir, "wallets").listFiles()?.filter { it.name.contains(id) }?.forEach { it.delete() }
        if (prefs.getString("active", null) == id) getAll().firstOrNull()?.let { setActive(it.id) }
        kit = null; wallet = null; ready = false
    }
    fun switchTo(id: String) { setActive(id); init() }
    fun isReady(): Boolean = ready && wallet != null

    fun init() {
        ready = false; val info = getActive() ?: return
        try {
            val seed = DeterministicSeed(info.seed.split(" "), null, "", 0)
            wallet = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH); ready = true
            Thread {
                try {
                    kit?.stopAsync()
                    val dir = File(ctx.filesDir, "wallets").apply { mkdirs() }
                    val walletFile = File(dir, "wallet-${info.id}.wallet")
                    kit = object : WalletAppKit(params, dir, "wallet-${info.id}") {
                        override fun onSetupCompleted() { wallet = this.wallet() }
                    }.apply {
                        if (!walletFile.exists()) restoreWalletFromSeed(seed)
                        setAutoSave(true); setBlockingStartup(false)
                        setDownloadListener(object : DownloadProgressTracker() {
                            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) { progressCb?.invoke(pct.toInt(), "Sync ${pct.toInt()}%") }
                            override fun doneDownload() { progressCb?.invoke(100, "Đã đồng bộ") }
                        })
                        startAsync()
                    }
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}
    }

    fun getAddress(): String = if (isReady()) wallet!!.currentReceiveAddress().toString() else ""
    fun getBalance(): Double = if (isReady()) wallet!!.getBalance(Wallet.BalanceType.ESTIMATED).value / 1e8 else 0.0
    fun getSeed(): String = getActive()?.seed ?: ""
    fun sync() { if (kit?.isRunning == true) Thread { try { kit?.peerGroup()?.downloadBlockChain() } catch (_: Exception) {} }.start() }
    fun price(): Double = try { URL("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT").readText().let { """"price":"([0-9.]+)"""".toRegex().find(it)?.groupValues?.get(1)?.toDouble() ?: 0.0 } } catch (_: Exception) { 0.0 }

    // Lấy phí từ API do người dùng tùy chỉnh
    fun getFeeRates(): FeeRates = try {
        val json = URL(getFeeApiUrl()).readText()
        val fast = """"fastestFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 20L
        val normal = """"halfHourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 10L
        val slow = """"hourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 5L
        FeeRates(slow, normal, fast)
    } catch (_: Exception) { FeeRates(5, 10, 20) }

    fun send(to: String, amount: Double, feeSatVb: Long): String = try {
        if (!isReady()) "Ví chưa sẵn sàng" else {
            val req = SendRequest.to(Address.fromString(params, to), Coin.valueOf((amount * 1e8).toLong()))
            req.feePerKb = Coin.valueOf(feeSatVb * 1000)
            val res = wallet!!.sendCoins(kit!!.peerGroup(), req); res.broadcastComplete.get()
            "Đã gửi! TXID: ${res.tx.txId}"
        }
    } catch (e: Exception) { "Lỗi: ${e.message}" }
}