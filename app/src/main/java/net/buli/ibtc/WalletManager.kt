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

// ===== DATA CLASS =====
// Lưu thông tin cơ bản của ví (id để phân biệt, name để hiển thị, seed 12 từ để khôi phục)
data class WalletInfo(val id: String, val name: String, val seed: String)
// Lưu thông tin giao dịch để hiển thị lịch sử
data class TransactionInfo(val txId: String, val amount: Double, val type: String, val time: Date)
// Lưu phí mạng gợi ý (đơn vị satoshi/vByte)
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get() // Sử dụng mạng chính Bitcoin mainnet (tiền thật)
    private var kit: WalletAppKit? = null // Đối tượng của bitcoinj để sync blockchain
    private var active: WalletInfo? = null // Ví đang được mở hiện tại
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE) // Lưu seed đã mã hóa
    // Đọc giá cache lần cuối, mặc định 67,500 USD nếu chưa có mạng
    private var lastPrice = prefs.getFloat("last_price", 67500f).toDouble()

    // Kiểm tra xem máy đã có ví nào chưa (tìm key kết thúc bằng _seed)
    fun hasWallets(): Boolean {
        return prefs.all.keys.any { key -> key.endsWith("_seed") }
    }

    // Trả về ví đang active
    fun getActive(): WalletInfo? {
        return active
    }

    // Lấy ID ví đầu tiên trong SharedPreferences (dùng để mở khóa)
    fun getActiveId(): String? {
        return prefs.all.keys.mapNotNull { key ->
            if (key.endsWith("_seed")) key.removeSuffix("_seed") else null
        }.firstOrNull()
    }

    // MỞ KHÓA VÍ: giải mã seed bằng mật khẩu người dùng nhập
    fun unlock(id: String, password: String): Boolean {
        return try {
            val enc = prefs.getString("${id}_seed", "") ?: return false // lấy chuỗi đã mã hóa
            val seed = CryptoUtil.decrypt(enc, password) // giải mã, sai pass sẽ throw
            val name = prefs.getString("${id}_name", "") ?: ""
            active = WalletInfo(id, name, seed) // gán vào active
            true
        } catch (e: Exception) {
            false // sai mật khẩu
        }
    }

    // ĐỔI MẬT KHẨU: giải mã bằng pass cũ, mã hóa lại bằng pass mới
    fun changePassword(id: String, oldPass: String, newPass: String): Boolean {
        return try {
            val enc = prefs.getString("${id}_seed", "") ?: return false
            val seed = CryptoUtil.decrypt(enc, oldPass) // bước 1: kiểm tra pass cũ đúng
            val newEnc = CryptoUtil.encrypt(seed, newPass) // bước 2: mã hóa lại
            prefs.edit().putString("${id}_seed", newEnc).apply() // bước 3: lưu đè
            // Nếu ví đang mở, cập nhật lại active để không bị lệch
            active?.let { if (it.id == id) active = it.copy(seed = seed) }
            true
        } catch (e: Exception) {
            false // pass cũ sai
        }
    }

    // TẠO VÍ MỚI: sinh seed 12 từ ngẫu nhiên, mã hóa rồi lưu
    fun create(name: String, password: String): WalletInfo {
        val id = System.currentTimeMillis().toString() // dùng timestamp làm ID duy nhất
        val seed = DeterministicSeed(SecureRandom(), 128, "") // 128 bit = 12 từ
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val walletName = if (name.isBlank()) "Ví $id" else name
        val info = WalletInfo(id, walletName, mnemonic)
        val enc = CryptoUtil.encrypt(mnemonic, password) // MÃ HÓA trước khi lưu
        prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", enc).apply()
        active = info
        return info
    }

    // IMPORT VÍ: nhập seed có sẵn, kiểm tra hợp lệ rồi mã hóa lưu
    fun import(name: String, phrase: String, password: String): WalletInfo? {
        return try {
            val words = phrase.trim().split("\\s+".toRegex())
            if (words.size < 12) return null // seed phải ít nhất 12 từ
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000) // kiểm tra hợp lệ
            val id = System.currentTimeMillis().toString()
            val walletName = if (name.isBlank()) "Imported" else name
            val mnemonic = words.joinToString(" ")
            val info = WalletInfo(id, walletName, mnemonic)
            val enc = CryptoUtil.encrypt(mnemonic, password)
            prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", enc).apply()
            active = info
            info
        } catch (e: Exception) {
            null
        }
    }

    // XÓA VÍ: dừng sync, xóa SharedPreferences và xóa thư mục blockchain
    fun delete(id: String) {
        try { stop() } catch (_: Exception) {}
        prefs.edit().remove("${id}_name").remove("${id}_seed").apply()
        File(ctx.filesDir, id).deleteRecursively()
        if (active?.id == id) { active = null }
    }

    // KHỞI TẠO WalletAppKit để sync blockchain
    fun init() {
        val info = getActive() ?: return
        if (kit != null) return
        val seed = DeterministicSeed(info.seed.split(" "), null, "", 0L)
        kit = WalletAppKit(params, File(ctx.filesDir, info.id), "ibtc").apply {
            setBlockingStartup(false) // không chặn UI
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

    // Lắng nghe tiến trình sync để cập nhật %
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

    fun getBalance(): Double {
        return kit?.wallet()?.balance?.value?.toDouble()?.div(1e8) ?: 0.0
    }

    fun getAddress(): String {
        return kit?.wallet()?.currentReceiveAddress().toString()
    }

    fun getSeed(): String { return active?.seed ?: "" }

    fun getTransactions(): List<TransactionInfo> {
        val wallet = kit?.wallet() ?: return emptyList()
        return wallet.getTransactionsByTime().map { tx ->
            val value = tx.getValue(wallet).value.toDouble() / 1e8
            TransactionInfo(tx.txId.toString(), kotlin.math.abs(value), if (value > 0) "Nhận" else "Gửi", tx.updateTime)
        }.reversed()
    }

    // GỬI BTC với phí tùy chọn
    fun send(to: String, amountBTC: Double, feeRateSatVb: Int): String {
        return try {
            val wallet = kit!!.wallet()
            val request = SendRequest.to(Address.fromString(params, to), Coin.parseCoin(amountBTC.toString()))
            request.feePerKb = Coin.valueOf(feeRateSatVb.toLong() * 1000) // đổi sat/vB sang sat/kB
            wallet.completeTx(request)
            wallet.commitTx(request.tx)
            kit!!.peerGroup().broadcastTransaction(request.tx).future().get()
            request.tx.txId.toString()
        } catch (e: Exception) { "Lỗi: ${e.message}" }
    }

    // Hàm GET HTTP đơn giản, timeout 7s
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

    // LẤY GIÁ BTC: thử 3 API, fail thì dùng cache
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