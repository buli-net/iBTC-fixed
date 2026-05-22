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

// Lưu thông tin ví
data class WalletInfo(val id: String, val name: String, val seed: String)
// Lưu giao dịch
data class TransactionInfo(val txId: String, val amount: Double, val type: String, val time: Date)
// Phí mạng
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get() // Bitcoin mainnet
    private var kit: WalletAppKit? = null
    private var active: WalletInfo? = null
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE)
    private var lastPrice = prefs.getFloat("last_price", 67500f).toDouble()

    // Có ví chưa
    fun hasWallets(): Boolean {
        return prefs.all.keys.any { key -> key.endsWith("_seed") }
    }

    fun getActive(): WalletInfo? {
        return active
    }

    fun getActiveId(): String? {
        return prefs.all.keys.mapNotNull { key ->
            if (key.endsWith("_seed")) key.removeSuffix("_seed") else null
        }.firstOrNull()
    }

    // Mở khóa ví
    fun unlock(id: String, password: String): Boolean {
        return try {
            val enc = prefs.getString("${id}_seed", "") ?: return false
            val seed = CryptoUtil.decrypt(enc, password)
            val name = prefs.getString("${id}_name", "") ?: ""
            active = WalletInfo(id, name, seed)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ĐỔI MẬT KHẨU - dùng cho menu
    fun changePassword(id: String, oldPass: String, newPass: String): Boolean {
        return try {
            val enc = prefs.getString("${id}_seed", "") ?: return false
            val seed = CryptoUtil.decrypt(enc, oldPass) // kiểm tra pass cũ
            val newEnc = CryptoUtil.encrypt(seed, newPass) // mã hóa lại
            prefs.edit().putString("${id}_seed", newEnc).apply()
            active?.let { if (it.id == id) active = it.copy(seed = seed) }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ĐỔI TÊN - fix không reset pass
    fun rename(id: String, newName: String): Boolean {
        return try {
            prefs.edit().putString("${id}_name", newName).apply()
            if (active?.id == id) active = active?.copy(name = newName)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Tạo ví mới
    fun create(name: String, password: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val seed = DeterministicSeed(SecureRandom(), 128, "")
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val info = WalletInfo(id, if (name.isBlank()) "Ví $id" else name, mnemonic)
        val enc = CryptoUtil.encrypt(mnemonic, password)
        prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", enc).apply()
        active = info
        return info
    }

    // Import ví
    fun import(name: String, phrase: String, password: String): WalletInfo? {
        return try {
            val words = phrase.trim().split("\\s+".toRegex())
            if (words.size < 12) return null
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000)
            val id = System.currentTimeMillis().toString()
            val info = WalletInfo(id, if (name.isBlank()) "Imported" else name, words.joinToString(" "))
            val enc = CryptoUtil.encrypt(info.seed, password)
            prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", enc).apply()
            active = info
            info
        } catch (e: Exception) {
            null
        }
    }

    // XÓA VÍ - FIX VÍ MA DÙNG commit()
    fun delete(id: String) {
        try {
            stop()
        } catch (_: Exception) {}
        // commit() ghi ngay, tránh ví quay lại khi thoát app
        prefs.edit().remove("${id}_name").remove("${id}_seed").commit()
        try {
            File(ctx.filesDir, id).deleteRecursively()
        } catch (_: Exception) {}
        if (active?.id == id) {
            active = null
        }
    }

    // Khởi tạo sync
    fun init() {
        val info = getActive() ?: return
        if (kit != null) return
        val seed = DeterministicSeed(info.seed.split(" "), null, "", 0L)
        kit = WalletAppKit(params, File(ctx.filesDir, info.id), "ibtc").apply {
            setBlockingStartup(false)
            restoreWalletFromSeed(seed)
            setDownloadListener(object : DownloadProgressTracker() {})
            startAsync()
            awaitRunning()
        }
    }

    fun stop() {
        try {
            kit?.stopAsync()?.awaitTerminated()
        } catch (_: Exception) {}
        kit = null
    }

    fun onProgress(cb: (Int, String) -> Unit) {
        kit?.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                val percent = pct.toInt()
                val text = if (percent < 100) "Đang sync ${percent}%" else "Đã sync"
                cb(percent, text)
            }
            override fun doneDownload() {
                cb(100, "Đã sync")
            }
        })
    }

    fun getBalance(): Double {
        return kit?.wallet()?.balance?.value?.toDouble()?.div(1e8) ?: 0.0
    }

    fun getAddress(): String {
        return kit?.wallet()?.currentReceiveAddress().toString()
    }

    fun getSeed(): String {
        return active?.seed ?: ""
    }

    fun getTransactions(): List<TransactionInfo> {
        val wallet = kit?.wallet() ?: return emptyList()
        return wallet.getTransactionsByTime().map { tx ->
            val value = tx.getValue(wallet).value.toDouble() / 1e8
            val fee = tx.fee?.value?.toDouble()?.div(1e8) ?: 0.0
            val amount = if (value < 0) kotlin.math.abs(value) - fee else value
            TransactionInfo(tx.txId.toString(), amount, if (value > 0) "Nhận" else "Gửi", tx.updateTime)
        }.reversed()
    }

    fun send(to: String, amountBTC: Double, feeRateSatVb: Int): String {
        return try {
            val wallet = kit!!.wallet()
            val request = SendRequest.to(Address.fromString(params, to), Coin.parseCoin(amountBTC.toString()))
            request.feePerKb = Coin.valueOf(feeRateSatVb.toLong() * 1000)
            wallet.completeTx(request)
            wallet.commitTx(request.tx)
            kit!!.peerGroup().broadcastTransaction(request.tx).future().get()
            request.txId.toString()
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }

    fun estimateFee(to: String, amountBTC: Double, feeRateSatVb: Int): Double {
        return try {
            val wallet = kit?.wallet() ?: return feeRateSatVb * 250.0 / 1e8
            val request = SendRequest.to(Address.fromString(params, to), Coin.parseCoin(amountBTC.toString()))
            request.feePerKb = Coin.valueOf(feeRateSatVb.toLong() * 1000)
            wallet.completeTx(request)
            request.tx.fee?.value?.toDouble()?.div(1e8) ?: feeRateSatVb * 250.0 / 1e8
        } catch (e: Exception) {
            feeRateSatVb * 250.0 / 1e8
        }
    }

    private fun httpGet(url: String): String {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            ""
        }
    }

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

    fun getFeeRates(): FeeRates {
        val text = httpGet("https://mempool.space/api/v1/fees/recommended")
        val slow = Regex("\"hourFee\":(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 5
        val normal = Regex("\"halfHourFee\":(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 10
        val fast = Regex("\"fastestFee\":(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 20
        return FeeRates(slow, normal, fast)
    }
}