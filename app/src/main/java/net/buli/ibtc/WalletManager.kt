package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Date

// Data class lưu thông tin ví
data class WalletInfo(val id: String, val name: String, val seed: String)
data class TransactionInfo(val txId: String, val amount: Double, val type: String, val time: Date)
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get() // mạng Bitcoin chính
    private var kit: WalletAppKit? = null // core bitcoinj
    private var active: WalletInfo? = null
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE)
    // CHỐNG SẬP TỶ GIÁ: đọc giá cache, mặc định 67500
    private var lastPrice = prefs.getFloat("last_price", 67500f).toDouble()
    // SEED GIẢI MÃ TẠM TRONG RAM - chỉ tồn tại khi đã unlock, thoát app là mất
    private var unlockedSeed: String? = null

    // Kiểm tra đã có ví chưa (tìm key _enc thay vì _seed cũ)
    fun hasWallets(): Boolean {
        return prefs.all.keys.any { key -> key.endsWith("_enc") }
    }

    // Kiểm tra ví đã mở khóa chưa
    fun isUnlocked(): Boolean = unlockedSeed != null

    // MỞ KHÓA VÍ BẰNG PASS - giải mã AES
    fun unlock(password: String): Boolean {
        val info = getActive() ?: return false
        val enc = prefs.getString("${info.id}_enc", "") ?: return false
        val seed = CryptoUtil.decrypt(enc, password) ?: return false // sai pass trả null
        unlockedSeed = seed
        return true
    }

    // KHÓA VÍ - xóa seed khỏi RAM và dừng blockchain
    fun lock() {
        unlockedSeed = null
        try { stop() } catch (_: Exception) {}
    }

    // Lấy ví đang active (không chứa seed plain)
    fun getActive(): WalletInfo? {
        if (active != null) return active
        val id = prefs.all.keys.mapNotNull { key ->
            if (key.endsWith("_enc")) key.removeSuffix("_enc") else null
        }.firstOrNull() ?: return null
        val name = prefs.getString("${id}_name", "") ?: ""
        active = WalletInfo(id, name, "")
        return active
    }

    // TẠO VÍ MỚI - BẮT BUỘC NHẬP PASSWORD ĐỂ MÃ HÓA
    fun create(name: String, password: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val seed = DeterministicSeed(SecureRandom(), 128, "")
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        // MÃ HÓA SEED TRƯỚC KHI LƯU VÀO SharedPreferences
        val enc = CryptoUtil.encrypt(mnemonic, password)
        val walletName = if (name.isBlank()) "Ví $id" else name
        val info = WalletInfo(id, walletName, "")
        prefs.edit().putString("${id}_name", info.name).putString("${id}_enc", enc).apply()
        active = info
        unlockedSeed = mnemonic // mở khóa luôn sau khi tạo để dùng ngay
        return info
    }

    // IMPORT VÍ - BẮT BUỘC PASSWORD
    fun import(name: String, phrase: String, password: String): WalletInfo? {
        return try {
            val words = phrase.trim().split("\\s+".toRegex())
            if (words.size < 12) return null
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000) // kiểm tra seed hợp lệ
            val id = System.currentTimeMillis().toString()
            val enc = CryptoUtil.encrypt(words.joinToString(" "), password)
            val walletName = if (name.isBlank()) "Imported" else name
            val info = WalletInfo(id, walletName, "")
            prefs.edit().putString("${id}_name", info.name).putString("${id}_enc", enc).apply()
            active = info
            unlockedSeed = words.joinToString(" ")
            info
        } catch (e: Exception) {
            null
        }
    }

    // ĐỔI MẬT KHẨU - giải mã bằng cũ, mã hóa lại bằng mới
    fun changePassword(oldPass: String, newPass: String): Boolean {
        val info = getActive() ?: return false
        val enc = prefs.getString("${info.id}_enc", "") ?: return false
        val seed = CryptoUtil.decrypt(enc, oldPass) ?: return false
        val newEnc = CryptoUtil.encrypt(seed, newPass)
        prefs.edit().putString("${info.id}_enc", newEnc).apply()
        unlockedSeed = seed
        return true
    }

    // Xóa ví hoàn toàn
    fun delete(id: String) {
        try { stop() } catch (_: Exception) {}
        prefs.edit().remove("${id}_name").remove("${id}_enc").apply()
        File(ctx.filesDir, id).deleteRecursively()
        if (active?.id == id) { active = null; unlockedSeed = null }
    }

    // Khởi tạo blockchain - CHỈ CHẠY KHI ĐÃ UNLOCK
    fun init() {
        val info = getActive() ?: return
        if (kit != null) return
        val seedStr = unlockedSeed ?: return // chưa unlock thì không init
        val seed = DeterministicSeed(seedStr.split(" "), null, "", 0L)
        kit = WalletAppKit(params, File(ctx.filesDir, info.id), "ibtc").apply {
            setBlockingStartup(false)
            restoreWalletFromSeed(seed)
            setDownloadListener(object : DownloadProgressTracker() {})
            startAsync()
            awaitRunning()
        }
    }

    fun stop() {
        try { kit?.stopAsync()?.awaitTerminated() } catch (_: Exception) {}
        kit = null
    }

    // Lắng nghe tiến trình sync blockchain
    fun onProgress(cb: (Int, String) -> Unit) {
        kit?.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                val percent = pct.toInt()
                val text = if (percent < 100) "Đang sync ${percent}%" else "Đã sync"
                cb(percent, text)
            }
            override fun doneDownload() { cb(100, "Đã sync") }
        })
    }

    // Lấy số dư BTC
    fun getBalance(): Double {
        return kit?.wallet()?.balance?.value?.toDouble()?.div(1e8) ?: 0.0
    }

    // Lấy địa chỉ nhận hiện tại
    fun getAddress(): String {
        return kit?.wallet()?.currentReceiveAddress().toString()
    }

    // Lấy seed - CHỈ TRẢ VỀ KHI ĐÃ UNLOCK, dùng để hiện trong Chi tiết ví
    fun getSeed(): String { return unlockedSeed ?: "" }

    // Lấy lịch sử giao dịch
    fun getTransactions(): List<TransactionInfo> {
        val wallet = kit?.wallet() ?: return emptyList()
        return wallet.getTransactionsByTime().map { tx ->
            val value = tx.getValue(wallet).value.toDouble() / 1e8
            TransactionInfo(tx.txId.toString(), kotlin.math.abs(value), if (value > 0) "Nhận" else "Gửi", tx.updateTime)
        }.reversed()
    }

    // Gửi BTC ra ngoài
    fun send(to: String, amountBTC: Double, feeRateSatVb: Int): String {
        return try {
            val wallet = kit!!.wallet()
            val request = SendRequest.to(Address.fromString(params, to), Coin.parseCoin(amountBTC.toString()))
            request.feePerKb = Coin.valueOf(feeRateSatVb.toLong() * 1000)
            wallet.completeTx(request)
            wallet.commitTx(request.tx)
            kit!!.peerGroup().broadcastTransaction(request.tx).future().get()
            request.tx.txId.toString()
        } catch (e: Exception) { "Lỗi: ${e.message}" }
    }

    // Hàm GET HTTP đơn giản
    private fun httpGet(url: String): String {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.inputStream.bufferedReader().readText()
        } catch (_: Exception) { "" }
    }

    // LẤY GIÁ BTC - 3 nguồn fallback + cache chống sập
    fun price(): Double {
        var text = httpGet("https://api.coinbase.com/v2/prices/BTC-USD/spot")
        var price = Regex("\"amount\":\"([\\d.]+)\"").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        if (price == null) {
            text = httpGet("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT")
            price = Regex("\"price\":\"([\\d.]+)\"").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (price == null) {
            text = httpGet("https://blockchain.info/ticker")
            price = Regex("\"USD\"[^}]*\"last\":([\\d.]+)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        val result = price ?: lastPrice
        if (result != lastPrice) {
            lastPrice = result
            prefs.edit().putFloat("last_price", result.toFloat()).apply()
        }
        return result
    }

    // Lấy phí gợi ý từ mempool.space
    fun getFeeRates(): FeeRates {
        val text = httpGet("https://mempool.space/api/v1/fees/recommended")
        val slow = Regex("\"hourFee\":(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 5
        val normal = Regex("\"halfHourFee\":(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 10
        val fast = Regex("\"fastestFee\":(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 20
        return FeeRates(slow, normal, fast)
    }
}