package net.buli.ibtc

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.InsufficientMoneyException
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WalletInfo(val id: String, val name: String, val seed: String)
data class TransactionInfo(val txId: String, val amount: Double, val type: String, val time: Date)
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private var kit: WalletAppKit? = null
    private var active: WalletInfo? = null
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE)
    private var lastPrice = prefs.getFloat("last_price", 0f).toDouble()

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return if (ks.containsAlias("ibtc_key")) {
            ks.getKey("ibtc_key", null) as SecretKey
        } else {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(
                KeyGenParameterSpec.Builder("ibtc_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            kg.generateKey()
        }
    }

    private fun enc(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = c.iv
        val e = c.doFinal(s.toByteArray())
        return Base64.encodeToString(iv + e, Base64.NO_WRAP)
    }

    private fun dec(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val iv = b.copyOfRange(0, 12)
        val e = b.copyOfRange(12, b.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return String(c.doFinal(e))
    }

    fun hasWallets(): Boolean {
        return prefs.all.keys.any { it.endsWith("_seed") }
    }

    fun getActive(): WalletInfo? {
        if (active != null) return active
        val id = prefs.all.keys.firstOrNull { it.endsWith("_seed") }?.removeSuffix("_seed") ?: return null
        val name = prefs.getString("${id}_name", "") ?: ""
        val seedEnc = prefs.getString("${id}_seed", "") ?: return null
        val seed = try { dec(seedEnc) } catch (_: Exception) { return null }
        active = WalletInfo(id, name, seed)
        return active
    }

    fun create(name: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val seed = DeterministicSeed(SecureRandom(), 128, "")
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val info = WalletInfo(id, if (name.isBlank()) "Ví $id" else name, mnemonic)
        prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", enc(info.seed)).apply()
        active = info
        return info
    }

    fun import(name: String, phrase: String): WalletInfo? {
        try {
            val words = phrase.trim().split("\\s+".toRegex())
            if (words.size < 12) return null
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000)
            val id = System.currentTimeMillis().toString()
            val info = WalletInfo(id, if (name.isBlank()) "Imported" else name, words.joinToString(" "))
            prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", enc(info.seed)).apply()
            active = info
            return info
        } catch (_: Exception) {
            return null
        }
    }

    fun rename(id: String, newName: String) {
        prefs.edit().putString("${id}_name", newName).apply()
        if (active?.id == id) active = active?.copy(name = newName)
    }

    fun delete(id: String) {
        try { stop() } catch (_: Exception) {}
        prefs.edit().remove("${id}_name").remove("${id}_seed").apply()
        File(ctx.filesDir, id).deleteRecursively()
        if (active?.id == id) active = null
    }

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
        try { kit?.stopAsync()?.awaitTerminated(3, TimeUnit.SECONDS) } catch (_: Exception) {}
        kit = null
    }

    fun onProgress(cb: (Int, String) -> Unit) {
        kit?.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                cb(pct.toInt(), if (pct < 100) "Đang sync ${pct.toInt()}%" else "Đã sync")
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

    fun getSeed(): String {
        return active?.seed ?: ""
    }

    fun getTransactions(): List<TransactionInfo> {
        val w = kit?.wallet() ?: return emptyList()
        return w.getTransactionsByTime().map { tx ->
            val v = tx.getValue(w).value.toDouble() / 1e8
            TransactionInfo(tx.txId.toString(), kotlin.math.abs(v), if (v > 0) "Nhận" else "Gửi", tx.updateTime)
        }.reversed()
    }

    fun send(to: String, amountBTC: Double, feeRateSatVb: Int): String {
        try {
            val w = kit?.wallet() ?: return "Lỗi: chưa sync"
            if (amountBTC <= 0) return "Số tiền không hợp lệ"
            val amount = Coin.parseCoin("%.8f".format(amountBTC))
            if (w.balance.isLessThan(amount)) return "Số dư không đủ"
            
            val addr = try { Address.fromString(params, to) } catch (_: Exception) { return "Địa chỉ không hợp lệ" }
            
            val req = SendRequest.to(addr, amount)
            // FIX PHÍ THẬT - bitcoinj dùng sat/kB
            req.feePerKb = Coin.valueOf(feeRateSatVb * 1000L)
            
            // FIX RBF - cho phép thay thế phí
            req.tx.inputs.forEach { input ->
                input.sequenceNumber = TransactionInput.NO_SEQUENCE - 2 // 0xfffffffd
            }
            
            w.completeTx(req)
            w.commitTx(req.tx)
            kit!!.peerGroup().broadcastTransaction(req.tx).future().get()
            return "Đã gửi: ${req.tx.txId}"
        } catch (e: InsufficientMoneyException) {
            return "Số dư không đủ phí"
        } catch (e: Exception) {
            return "Lỗi: ${e.message}"
        }
    }

    private fun httpGet(url: String): String {
        return try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 7000
                readTimeout = 7000
            }.inputStream.bufferedReader().readText()
        } catch (_: Exception) { "" }
    }

    fun price(): Double {
        var t = httpGet("https://api.coinbase.com/v2/prices/BTC-USD/spot")
        var p = Regex("\"amount\":\"([\\d.]+)\"").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
        if (p == null) {
            t = httpGet("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT")
            p = Regex("\"price\":\"([\\d.]+)\"").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        val r = p ?: lastPrice
        if (r != lastPrice && r > 0) {
            lastPrice = r
            prefs.edit().putFloat("last_price", r.toFloat()).apply()
        }
        return r
    }

    fun getFeeRates(): FeeRates {
        val t = httpGet("https://mempool.space/api/v1/fees/recommended")
        val s = Regex("\"hourFee\":(\\d+)").find(t)?.groupValues?.get(1)?.toInt() ?: 4
        val n = Regex("\"halfHourFee\":(\\d+)").find(t)?.groupValues?.get(1)?.toInt() ?: 6
        val f = Regex("\"fastestFee\":(\\d+)").find(t)?.groupValues?.get(1)?.toInt() ?: 8
        return FeeRates(s, n, f)
    }
}