package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Date
import kotlin.math.abs

// Dữ liệu 1 ví
data class WalletInfo(val id: String, var name: String, val seed: String)
// 3 mức phí
data class FeeRates(val slow: Long, val normal: Long, val fast: Long)
// 1 giao dịch
data class TransactionInfo(val txId: String, val amount: Double, val time: Date, val type: String)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get() // mạng Bitcoin mainnet
    private val prefs = ctx.getSharedPreferences("ibtc_wallets", Context.MODE_PRIVATE)
    private var wallet: Wallet? = null
    @Volatile private var ready = false
    private var progressCb: ((Int, String) -> Unit)? = null

    // API dự phòng lấy số dư
    private val balanceApis = listOf(
        "https://blockstream.info/api/address/",
        "https://mempool.space/api/address/",
        "https://blockchain.info/q/addressbalance/"
    )
    // API dự phòng lấy lịch sử
    private val txApis = listOf(
        "https://blockstream.info/api/address/",
        "https://mempool.space/api/address/"
    )

    // ===== CÀI ĐẶT =====
    fun getFeeApiUrl(): String = prefs.getString("fee_url", "https://mempool.space/api/v1/fees/recommended")!!
    fun setFeeApiUrl(url: String) { prefs.edit().putString("fee_url", url).apply() }
    fun getRefreshSec(): Long = prefs.getLong("refresh_sec", 60)
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
        val ids = getAll().map { it.id }.toMutableSet().apply { add(id) }
        saveIds(ids)
        prefs.edit().putString("n_$id", info.name).putString("s_$id", mnemonic).apply()
        setActive(id); return info
    }

    fun import(name: String, mnemonic: String): WalletInfo? = try {
        DeterministicSeed(mnemonic.trim().split("\\s+".toRegex()), null, "", 0)
        val id = System.currentTimeMillis().toString()
        val info = WalletInfo(id, if (name.isBlank()) "Ví import" else name, mnemonic.trim())
        val ids = getAll().map { it.id }.toMutableSet().apply { add(id) }
        saveIds(ids)
        prefs.edit().putString("n_$id", info.name).putString("s_$id", info.seed).apply()
        setActive(id); info
    } catch (e: Exception) { null }

    fun setActive(id: String) { prefs.edit().putString("active", id).apply() }
    fun getActive(): WalletInfo? = getAll().find { it.id == prefs.getString("active", null) }
    fun rename(id: String, n: String) { prefs.edit().putString("n_$id", n).apply() }

    // FIX XÓA VÍ: xóa active, chuyển ví, không để load mãi
    fun delete(id: String) {
        val ids = getAll().map { it.id }.toMutableSet().apply { remove(id) }
        saveIds(ids)
        prefs.edit().remove("n_$id").remove("s_$id").apply()
        if (prefs.getString("active", null) == id) {
            prefs.edit().remove("active").apply() // xóa active cũ
            if (ids.isNotEmpty()) { setActive(ids.first()) } // chọn ví khác nếu còn
        }
        wallet = null; ready = false
    }

    fun switchTo(id: String) { setActive(id); init() }
    fun isReady(): Boolean = ready && wallet != null

    fun init() {
        ready = false; val info = getActive() ?: return // nếu không có ví thì thoát
        try {
            val seed = DeterministicSeed(info.seed.split(" "), null, "", 0)
            wallet = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
            ready = true; progressCb?.invoke(100, "Đã đồng bộ")
        } catch (_: Exception) {}
    }

    fun getAddress(): String = if (isReady()) wallet!!.currentReceiveAddress().toString() else ""

    fun getBalance(): Double {
        val addr = getAddress(); if (addr.isEmpty()) return 0.0
        for (base in balanceApis) {
            try {
                val response = URL(base + addr).readText()
                val balance = if (response.startsWith("{")) {
                    val funded = """"funded_txo_sum":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    val spent = """"spent_txo_sum":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    (funded - spent) / 1e8
                } else { response.toLong() / 1e8 }
                prefs.edit().putString("cache_balance_$addr", balance.toString()).putLong("cache_time", System.currentTimeMillis()).apply()
                progressCb?.invoke(100, "Đã đồng bộ"); return balance
            } catch (_: Exception) { continue }
        }
        val cached = prefs.getString("cache_balance_$addr", "0")?.toDouble() ?: 0.0
        val ageMin = (System.currentTimeMillis() - prefs.getLong("cache_time", 0)) / 60000
        progressCb?.invoke(100, "Offline (cache ${ageMin} phút)"); return cached
    }

    fun getSeed(): String = getActive()?.seed ?: ""
    fun sync() { progressCb?.invoke(100, "Đã đồng bộ") }

    // Lấy giá BTC với 4 API dự phòng
    fun price(): Double {
        val apis = listOf(
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd",
            "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT",
            "https://blockchain.info/ticker",
            "https://api.coinbase.com/v2/prices/BTC-USD/spot"
        )
        for (url in apis) {
            try {
                val json = URL(url).readText()
                val price = when {
                    url.contains("coingecko") -> """"usd":([0-9.]+)""".toRegex().find(json)?.groupValues?.get(1)?.toDouble()
                    url.contains("binance") -> """"price":"([0-9.]+)"""".toRegex().find(json)?.groupValues?.get(1)?.toDouble()
                    url.contains("blockchain") -> """"USD".*?"last":([0-9.]+)""".toRegex().find(json)?.groupValues?.get(1)?.toDouble()
                    else -> """"amount":"([0-9.]+)"""".toRegex().find(json)?.groupValues?.get(1)?.toDouble()
                }
                if (price != null && price > 100) {
                    prefs.edit().putString("cache_price", price.toString()).apply()
                    return price
                }
            } catch (_: Exception) { continue }
        }
        return prefs.getString("cache_price", "65000")?.toDouble() ?: 65000.0
    }

    fun getFeeRates(): FeeRates = try {
        val json = URL(getFeeApiUrl()).readText()
        val fast = """"fastestFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 20L
        val normal = """"halfHourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 10L
        val slow = """"hourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 5L
        FeeRates(slow, normal, fast)
    } catch (_: Exception) { FeeRates(5, 10, 20) }

    fun getTransactions(): List<TransactionInfo> {
        if (!isReady()) return emptyList(); val addr = getAddress()
        for (base in txApis) {
            try {
                val json = URL(base + addr + "/txs").readText()
                val list = mutableListOf<TransactionInfo>()
                val txBlocks = json.split("\"txid\":\"").drop(1)
                for (block in txBlocks.take(25)) {
                    val txid = block.substringBefore("\"")
                    val time = """"block_time":(\d+)""".toRegex().find(block)?.groupValues?.get(1)?.toLong() ?: 0L
                    var received = 0L
                    """"address":"$addr","value":(\d+)""".toRegex().findAll(block).forEach { received += it.groupValues[1].toLong() }
                    val type = if (received > 0) "Nhận" else "Gửi"
                    val amount = if (received > 0) received else { """"value":(\d+)""".toRegex().find(block)?.groupValues?.get(1)?.toLong() ?: 0L }
                    list.add(TransactionInfo(txid, abs(amount) / 1e8, Date(time * 1000), type))
                }
                return list.sortedByDescending { it.time }
            } catch (_: Exception) { continue }
        }
        return emptyList()
    }

    fun send(to: String, amount: Double, feeSatVb: Long): String {
        try {
            if (!isReady()) return "Ví chưa sẵn sàng"
            val req = SendRequest.to(Address.fromString(params, to), Coin.valueOf((amount * 1e8).toLong()))
            req.feePerKb = Coin.valueOf(feeSatVb * 1000)
            wallet!!.completeTx(req)
            val tx = req.tx
            val hex = tx.bitcoinSerialize().joinToString("") { "%02x".format(it) }
            val broadcastApis = listOf("https://blockstream.info/api/tx", "https://mempool.space/api/tx")
            for (api in broadcastApis) {
                try {
                    val conn = URL(api).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"; conn.doOutput = true
                    conn.outputStream.write(hex.toByteArray())
                    val txid = conn.inputStream.bufferedReader().readText()
                    return "Đã gửi! TXID: $txid"
                } catch (_: Exception) { continue }
            }
            return "Lỗi broadcast - thử lại sau"
        } catch (e: Exception) { return "Lỗi: ${e.message}" }
    }
}