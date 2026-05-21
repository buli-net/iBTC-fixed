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

// Lưu thông tin 1 ví
data class WalletInfo(
    val id: String,      // ID duy nhất
    var name: String,    // Tên hiển thị
    val seed: String     // 12 từ mnemonic
)

// Lưu 3 mức phí
data class FeeRates(
    val slow: Long,      // phí chậm
    val normal: Long,    // phí thường
    val fast: Long       // phí nhanh
)

// Lưu 1 giao dịch
data class TransactionInfo(
    val txId: String,    // mã giao dịch
    val amount: Double,  // số BTC
    val time: Date,      // thời gian
    val type: String     // Nhận hoặc Gửi
)

class WalletManager(private val ctx: Context) {
    // Tham số mạng Bitcoin chính
    private val params = MainNetParams.get()
    // Nơi lưu ví
    private val prefs = ctx.getSharedPreferences("ibtc_wallets", Context.MODE_PRIVATE)
    // Ví bitcoinj hiện tại
    private var wallet: Wallet? = null
    // Cờ báo ví đã sẵn sàng
    @Volatile private var ready = false
    // Callback báo tiến độ
    private var progressCb: ((Int, String) -> Unit)? = null

    // Danh sách API lấy số dư (dự phòng)
    private val balanceApis = listOf(
        "https://blockstream.info/api/address/",
        "https://mempool.space/api/address/",
        "https://blockchain.info/q/addressbalance/"
    )
    // Danh sách API lấy lịch sử
    private val txApis = listOf(
        "https://blockstream.info/api/address/",
        "https://mempool.space/api/address/"
    )

    // Lấy URL API phí
    fun getFeeApiUrl(): String {
        return prefs.getString("fee_url", "https://mempool.space/api/v1/fees/recommended")!!
    }
    // Lưu URL API phí
    fun setFeeApiUrl(url: String) {
        prefs.edit().putString("fee_url", url).apply()
    }
    // Lấy thời gian tự động refresh
    fun getRefreshSec(): Long {
        return prefs.getLong("refresh_sec", 60)
    }
    // Lưu thời gian refresh
    fun setRefreshSec(sec: Long) {
        prefs.edit().putLong("refresh_sec", sec).apply()
    }
    // Lấy phí tùy chỉnh mặc định
    fun getDefaultCustomFee(): Long {
        return prefs.getLong("custom_fee", 10)
    }
    // Lưu phí tùy chỉnh
    fun setDefaultCustomFee(fee: Long) {
        prefs.edit().putLong("custom_fee", fee).apply()
    }
    // Đăng ký callback tiến độ
    fun onProgress(cb: (Int, String) -> Unit) {
        progressCb = cb
    }
    // Kiểm tra có ví nào không
    fun hasWallets(): Boolean {
        return getAll().isNotEmpty()
    }
    // Lấy danh sách tất cả ví
    fun getAll(): List<WalletInfo> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return ids.map { id ->
            val name = prefs.getString("n_$id", "Ví")!!
            val seed = prefs.getString("s_$id", "")!!
            WalletInfo(id, name, seed)
        }
    }
    // Lưu danh sách ID
    private fun saveIds(ids: Set<String>) {
        prefs.edit().putStringSet("ids", ids).apply()
    }
    // Tạo ví mới
    fun create(name: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val seed = DeterministicSeed(entropy, "", System.currentTimeMillis() / 1000)
        val mnemonic = seed.mnemonicCode?.joinToString(" ") ?: ""
        val finalName = if (name.isBlank()) "Ví mới" else name
        val info = WalletInfo(id, finalName, mnemonic)
        val ids = getAll().map { it.id }.toMutableSet()
        ids.add(id)
        saveIds(ids)
        prefs.edit().putString("n_$id", info.name).apply()
        prefs.edit().putString("s_$id", mnemonic).apply()
        setActive(id)
        return info
    }
    // Import ví từ seed
    fun import(name: String, mnemonic: String): WalletInfo? {
        return try {
            DeterministicSeed(mnemonic.trim().split("\\s+".toRegex()), null, "", 0)
            val id = System.currentTimeMillis().toString()
            val finalName = if (name.isBlank()) "Ví import" else name
            val info = WalletInfo(id, finalName, mnemonic.trim())
            val ids = getAll().map { it.id }.toMutableSet()
            ids.add(id)
            saveIds(ids)
            prefs.edit().putString("n_$id", info.name).apply()
            prefs.edit().putString("s_$id", info.seed).apply()
            setActive(id)
            info
        } catch (e: Exception) {
            null
        }
    }
    // Đặt ví đang dùng
    fun setActive(id: String) {
        prefs.edit().putString("active", id).apply()
    }
    // Lấy ví đang dùng
    fun getActive(): WalletInfo? {
        val activeId = prefs.getString("active", null)
        return getAll().find { it.id == activeId }
    }
    // Đổi tên ví
    fun rename(id: String, n: String) {
        prefs.edit().putString("n_$id", n).apply()
    }
    // Xóa ví - ĐÃ FIX
    fun delete(id: String) {
        val ids = getAll().map { it.id }.toMutableSet()
        ids.remove(id)
        saveIds(ids)
        prefs.edit().remove("n_$id").apply()
        prefs.edit().remove("s_$id").apply()
        val active = prefs.getString("active", null)
        if (active == id) {
            prefs.edit().remove("active").apply()
            if (ids.isNotEmpty()) {
                setActive(ids.first())
            }
        }
        wallet = null
        ready = false
    }
    // Chuyển sang ví khác
    fun switchTo(id: String) {
        setActive(id)
        init()
    }
    // Kiểm tra ví sẵn sàng
    fun isReady(): Boolean {
        return ready && wallet != null
    }
    // Khởi tạo ví
    fun init() {
        ready = false
        val info = getActive()
        if (info == null) return
        try {
            val seed = DeterministicSeed(info.seed.split(" "), null, "", 0)
            wallet = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
            ready = true
            progressCb?.invoke(100, "Đã đồng bộ")
        } catch (_: Exception) {
        }
    }
    // Lấy địa chỉ nhận
    fun getAddress(): String {
        if (!isReady()) return ""
        return wallet!!.currentReceiveAddress().toString()
    }
    // Lấy số dư
    fun getBalance(): Double {
        val addr = getAddress()
        if (addr.isEmpty()) return 0.0
        for (base in balanceApis) {
            try {
                val response = URL(base + addr).readText()
                val balance = if (response.startsWith("{")) {
                    val funded = """"funded_txo_sum":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    val spent = """"spent_txo_sum":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    (funded - spent) / 1e8
                } else {
                    response.toLong() / 1e8
                }
                prefs.edit().putString("cache_balance_$addr", balance.toString()).apply()
                prefs.edit().putLong("cache_time", System.currentTimeMillis()).apply()
                progressCb?.invoke(100, "Đã đồng bộ")
                return balance
            } catch (_: Exception) {
                continue
            }
        }
        val cached = prefs.getString("cache_balance_$addr", "0")?.toDouble() ?: 0.0
        val ageMin = (System.currentTimeMillis() - prefs.getLong("cache_time", 0)) / 60000
        progressCb?.invoke(100, "Offline (cache ${ageMin} phút)")
        return cached
    }
    // Lấy seed
    fun getSeed(): String {
        return getActive()?.seed ?: ""
    }
    // Đồng bộ
    fun sync() {
        progressCb?.invoke(100, "Đã đồng bộ")
    }
    // Lấy giá BTC
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
            } catch (_: Exception) {
                continue
            }
        }
        return prefs.getString("cache_price", "65000")?.toDouble() ?: 65000.0
    }
    // Lấy phí
    fun getFeeRates(): FeeRates {
        return try {
            val json = URL(getFeeApiUrl()).readText()
            val fast = """"fastestFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 20L
            val normal = """"halfHourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 10L
            val slow = """"hourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 5L
            FeeRates(slow, normal, fast)
        } catch (_: Exception) {
            FeeRates(5, 10, 20)
        }
    }
    // Lấy lịch sử
    fun getTransactions(): List<TransactionInfo> {
        if (!isReady()) return emptyList()
        val addr = getAddress()
        for (base in txApis) {
            try {
                val json = URL(base + addr + "/txs").readText()
                val list = mutableListOf<TransactionInfo>()
                val txBlocks = json.split("\"txid\":\"").drop(1)
                for (block in txBlocks.take(25)) {
                    val txid = block.substringBefore("\"")
                    val time = """"block_time":(\d+)""".toRegex().find(block)?.groupValues?.get(1)?.toLong() ?: 0L
                    var received = 0L
                    val matches = """"address":"$addr","value":(\d+)""".toRegex().findAll(block)
                    for (m in matches) {
                        received += m.groupValues[1].toLong()
                    }
                    val type = if (received > 0) "Nhận" else "Gửi"
                    val amount = if (received > 0) received else {
                        """"value":(\d+)""".toRegex().find(block)?.groupValues?.get(1)?.toLong() ?: 0L
                    }
                    list.add(TransactionInfo(txid, abs(amount) / 1e8, Date(time * 1000), type))
                }
                return list.sortedByDescending { it.time }
            } catch (_: Exception) {
                continue
            }
        }
        return emptyList()
    }
    // Gửi BTC
    fun send(to: String, amount: Double, feeSatVb: Long): String {
        try {
            if (!isReady()) return "Ví chưa sẵn sàng"
            val target = Address.fromString(params, to)
            val coin = Coin.valueOf((amount * 1e8).toLong())
            val req = SendRequest.to(target, coin)
            req.feePerKb = Coin.valueOf(feeSatVb * 1000)
            wallet!!.completeTx(req)
            val tx = req.tx
            val hex = tx.bitcoinSerialize().joinToString("") { "%02x".format(it) }
            val broadcastApis = listOf("https://blockstream.info/api/tx", "https://mempool.space/api/tx")
            for (api in broadcastApis) {
                try {
                    val conn = URL(api).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.outputStream.write(hex.toByteArray())
                    val txid = conn.inputStream.bufferedReader().readText()
                    return "Đã gửi! TXID: $txid"
                } catch (_: Exception) {
                    continue
                }
            }
            return "Lỗi broadcast - thử lại sau"
        } catch (e: Exception) {
            return "Lỗi: ${e.message}"
        }
    }
}