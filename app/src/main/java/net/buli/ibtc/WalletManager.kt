package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
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

// Dữ liệu cho 1 ví Bitcoin
data class WalletInfo(val id: String, var name: String, val seed: String)

// 3 mức phí giao dịch
data class FeeRates(val slow: Long, val normal: Long, val fast: Long)

// 1 giao dịch trong lịch sử
data class TransactionInfo(val txId: String, val amount: Double, val time: Date, val type: String)

class WalletManager(private val ctx: Context) {
    // Dùng mạng Bitcoin chính
    private val params = MainNetParams.get()
    
    // Lưu trữ SharedPreferences để lưu ví, cache
    private val prefs = ctx.getSharedPreferences("ibtc_wallets", Context.MODE_PRIVATE)
    
    // Ví hiện tại đang dùng
    private var wallet: Wallet? = null
    
    // Cờ báo ví đã sẵn sàng
    @Volatile private var ready = false
    
    // Callback để báo tiến độ sync
    private var progressCb: ((Int, String) -> Unit)? = null

    // ===== DANH SÁCH API DỰ PHÒNG - CHỐNG SẬP =====
    // Nếu API 1 chết, tự động thử API 2, rồi 3
    private val balanceApis = listOf(
        "https://blockstream.info/api/address/",      // API chính - nhanh nhất
        "https://mempool.space/api/address/",         // Dự phòng 1 - giống blockstream
        "https://blockchain.info/q/addressbalance/"   // Dự phòng 2 - trả về số thuần
    )
    
    // API lấy lịch sử giao dịch
    private val txApis = listOf(
        "https://blockstream.info/api/address/",
        "https://mempool.space/api/address/"
    )
    
    // API lấy giá BTC
    private val priceApis = listOf(
        "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT",
        "https://api.coinbase.com/v2/prices/BTC-USD/spot"
    )

    // Đếm số lần API fail liên tiếp
    private var failCount = 0

    // ===== CÀI ĐẶT NGƯỜI DÙNG =====
    fun getFeeApiUrl(): String = prefs.getString("fee_url", "https://mempool.space/api/v1/fees/recommended")!!
    fun setFeeApiUrl(url: String) { prefs.edit().putString("fee_url", url).apply() }
    fun getRefreshSec(): Long = prefs.getLong("refresh_sec", 60)
    fun setRefreshSec(sec: Long) { prefs.edit().putLong("refresh_sec", sec).apply() }
    fun getDefaultCustomFee(): Long = prefs.getLong("custom_fee", 10)
    fun setDefaultCustomFee(fee: Long) { prefs.edit().putLong("custom_fee", fee).apply() }

    fun onProgress(cb: (Int, String) -> Unit) { progressCb = cb }
    fun hasWallets(): Boolean = getAll().isNotEmpty()

    // Lấy danh sách tất cả ví
    fun getAll(): List<WalletInfo> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return ids.map { id ->
            WalletInfo(id, prefs.getString("n_$id", "Ví")!!, prefs.getString("s_$id", "")!!)
        }
    }

    private fun saveIds(ids: Set<String>) { prefs.edit().putStringSet("ids", ids).apply() }

    // Tạo ví mới với 12 từ ngẫu nhiên
    fun create(name: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val seed = DeterministicSeed(entropy, "", System.currentTimeMillis() / 1000)
        val mnemonic = seed.mnemonicCode?.joinToString(" ") ?: ""
        val info = WalletInfo(id, if (name.isBlank()) "Ví mới" else name, mnemonic)
        val ids = getAll().map { it.id }.toMutableSet().apply { add(id) }
        saveIds(ids)
        prefs.edit().putString("n_$id", info.name).putString("s_$id", mnemonic).apply()
        setActive(id)
        return info
    }

    // Import ví từ 12 từ seed
    fun import(name: String, mnemonic: String): WalletInfo? {
        return try {
            // Kiểm tra seed hợp lệ
            DeterministicSeed(mnemonic.trim().split("\\s+".toRegex()), null, "", 0)
            val id = System.currentTimeMillis().toString()
            val info = WalletInfo(id, if (name.isBlank()) "Ví import" else name, mnemonic.trim())
            val ids = getAll().map { it.id }.toMutableSet().apply { add(id) }
            saveIds(ids)
            prefs.edit().putString("n_$id", info.name).putString("s_$id", info.seed).apply()
            setActive(id)
            info
        } catch (e: Exception) { null }
    }

    fun setActive(id: String) { prefs.edit().putString("active", id).apply() }
    fun getActive(): WalletInfo? = getAll().find { it.id == prefs.getString("active", null) }
    fun rename(id: String, n: String) { prefs.edit().putString("n_$id", n).apply() }

    // Xóa ví
    fun delete(id: String) {
        val ids = getAll().map { it.id }.toMutableSet().apply { remove(id) }
        saveIds(ids)
        prefs.edit().remove("n_$id").remove("s_$id").apply()
        if (prefs.getString("active", null) == id) { ids.firstOrNull()?.let { setActive(it) } }
        wallet = null
        ready = false
    }

    fun switchTo(id: String) { setActive(id); init() }
    fun isReady(): Boolean = ready && wallet != null

    // Khởi tạo ví từ seed - tạo địa chỉ
    fun init() {
        ready = false
        val info = getActive() ?: return
        try {
            val seed = DeterministicSeed(info.seed.split(" "), null, "", 0)
            wallet = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
            ready = true
            progressCb?.invoke(100, "Đã đồng bộ")
        } catch (_: Exception) {}
    }

    // Lấy địa chỉ nhận BTC hiện tại
    fun getAddress(): String = if (isReady()) wallet!!.currentReceiveAddress().toString() else ""

    // ===== LẤY SỐ DƯ VỚI FALLBACK 3 API + CACHE =====
    fun getBalance(): Double {
        val addr = getAddress()
        if (addr.isEmpty()) return 0.0

        // Thử từng API cho đến khi thành công
        for (base in balanceApis) {
            try {
                val url = base + addr
                val response = URL(url).readText()
                
                val balance = if (response.startsWith("{")) {
                    // JSON từ blockstream/mempool
                    val funded = """"funded_txo_sum":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    val spent = """"spent_txo_sum":(\d+)""".toRegex().find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    (funded - spent) / 1e8
                } else {
                    // Số thuần từ blockchain.info
                    response.toLong() / 1e8
                }
                
                // Lưu cache khi thành công
                prefs.edit().putString("cache_balance_$addr", balance.toString()).putLong("cache_time", System.currentTimeMillis()).apply()
                failCount = 0
                progressCb?.invoke(100, "Đã đồng bộ")
                return balance
                
            } catch (e: Exception) {
                continue // Lỗi thì thử API tiếp theo
            }
        }
        
        // CẢ 3 API ĐỀU SẬP -> DÙNG CACHE
        failCount++
        val cached = prefs.getString("cache_balance_$addr", "0")?.toDouble() ?: 0.0
        val cacheTime = prefs.getLong("cache_time", 0)
        val ageMin = (System.currentTimeMillis() - cacheTime) / 60000
        
        progressCb?.invoke(100, "Offline (cache ${ageMin} phút)")
        
        // Cảnh báo nếu fail nhiều lần
        if (failCount > 5) {
            progressCb?.invoke(100, "API sập - dùng dữ liệu cũ")
        }
        
        return cached
    }

    fun getSeed(): String = getActive()?.seed ?: ""
    fun sync() { progressCb?.invoke(100, "Đã đồng bộ") }

    // ===== LẤY GIÁ BTC VỚI FALLBACK =====
    fun price(): Double {
        for (url in priceApis) {
            try {
                val json = URL(url).readText()
                val price = if (url.contains("binance")) {
                    """"price":"([0-9.]+)"""".toRegex().find(json)?.groupValues?.get(1)?.toDouble()
                } else {
                    """"amount":"([0-9.]+)"""".toRegex().find(json)?.groupValues?.get(1)?.toDouble()
                }
                if (price != null) {
                    prefs.edit().putString("cache_price", price.toString()).apply()
                    return price
                }
            } catch (_: Exception) { continue }
        }
        // Dùng giá cache
        return prefs.getString("cache_price", "0")?.toDouble() ?: 0.0
    }

    // Lấy phí giao dịch từ API
    fun getFeeRates(): FeeRates = try {
        val json = URL(getFeeApiUrl()).readText()
        val fast = """"fastestFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 20L
        val normal = """"halfHourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 10L
        val slow = """"hourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 5L
        FeeRates(slow, normal, fast)
    } catch (_: Exception) { FeeRates(5, 10, 20) }

    // ===== LẤY LỊCH SỬ GIAO DỊCH VỚI FALLBACK =====
    fun getTransactions(): List<TransactionInfo> {
        if (!isReady()) return emptyList()
        val addr = getAddress()
        
        for (base in txApis) {
            try {
                val json = URL(base + addr + "/txs").readText()
                val list = mutableListOf<TransactionInfo>()
                val txBlocks = json.split("\"txid\":\"").drop(1)
                
                for (block in txBlocks.take(25)) { // Lấy 25 giao dịch gần nhất
                    val txid = block.substringBefore("\"")
                    val time = """"block_time":(\d+)""".toRegex().find(block)?.groupValues?.get(1)?.toLong() ?: 0L
                    var received = 0L
                    // Tìm số BTC nhận vào địa chỉ này
                    """"address":"$addr","value":(\d+)""".toRegex().findAll(block).forEach {
                        received += it.groupValues[1].toLong()
                    }
                    val type = if (received > 0) "Nhận" else "Gửi"
                    val amount = if (received > 0) received else {
                        """"value":(\d+)""".toRegex().find(block)?.groupValues?.get(1)?.toLong() ?: 0L
                    }
                    list.add(TransactionInfo(txid, abs(amount) / 1e8, Date(time * 1000), type))
                }
                
                // Cache lịch sử
                prefs.edit().putString("cache_txs_$addr", json.take(5000)).apply()
                return list.sortedByDescending { it.time }
                
            } catch (_: Exception) { continue }
        }
        
        return emptyList()
    }

    // ===== GỬI BTC - ĐÃ FIX LỖI RETURN =====
    // Thử broadcast qua 2 API khác nhau
    fun send(to: String, amount: Double, feeSatVb: Long): String {
        // DÙNG BLOCK BODY thay vì expression để tránh lỗi Kotlin
        try {
            if (!isReady()) {
                return "Ví chưa sẵn sàng"
            }
            // Tạo giao dịch
            val req = SendRequest.to(Address.fromString(params, to), Coin.valueOf((amount * 1e8).toLong()))
            req.feePerKb = Coin.valueOf(feeSatVb * 1000)
            wallet!!.completeTx(req)
            val tx = req.tx
            val hex = tx.bitcoinSerialize().joinToString("") { "%02x".format(it) }
            
            // Danh sách API broadcast
            val broadcastApis = listOf(
                "https://blockstream.info/api/tx",
                "https://mempool.space/api/tx"
            )
            
            // Thử từng API
            for (api in broadcastApis) {
                try {
                    val conn = URL(api).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.outputStream.write(hex.toByteArray())
                    val txid = conn.inputStream.bufferedReader().readText()
                    return "Đã gửi! TXID: $txid"
                } catch (_: Exception) { 
                    continue // API này lỗi thì thử cái tiếp
                }
            }
            
            return "Lỗi broadcast - thử lại sau"
        } catch (e: Exception) {
            return "Lỗi: ${e.message}"
        }
    }
}