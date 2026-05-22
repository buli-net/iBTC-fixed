package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.security.SecureRandom
import java.util.*

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private val prefs: SharedPreferences = ctx.getSharedPreferences("btc_wallet", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null
    private var seed: String? = null
    private var walletName: String? = null

    fun hasWallets(): Boolean = prefs.getStringSet("wallets", emptySet())?.isNotEmpty() == true
    fun isUnlocked(): Boolean = seed != null
    fun getActiveName(): String = walletName ?: ""

    fun unlock(password: String): Boolean {
        return try {
            val wallets = prefs.getStringSet("wallets", emptySet())!!
            val name = prefs.getString("active", wallets.first())!!
            val enc = prefs.getString("seed_$name", null)!!
            seed = CryptoUtil.decrypt(enc, password)
            walletName = name
            true
        } catch (e: Exception) { false }
    }

    fun create(name: String, password: String) {
        val s = DeterministicSeed(SecureRandom(), 128, "", Utils.currentTimeSeconds())
        val phrase = s.mnemonicCode!!.joinToString(" ")
        val enc = CryptoUtil.encrypt(phrase, password)
        prefs.edit().putString("seed_$name", enc).putStringSet("wallets", prefs.getStringSet("wallets", emptySet())!! + name).putString("active", name).apply()
        seed = phrase; walletName = name
    }

    fun import(name: String, phrase: String, password: String): Boolean {
        return try {
            DeterministicSeed(phrase, null, "", Utils.currentTimeSeconds())
            val enc = CryptoUtil.encrypt(phrase, password)
            prefs.edit().putString("seed_$name", enc).putStringSet("wallets", prefs.getStringSet("wallets", emptySet())!! + name).putString("active", name).apply()
            seed = phrase; walletName = name; true
        } catch (e: Exception) { false }
    }

    fun init() {
        if (seed == null) return
        val dir = File(ctx.filesDir, "wallets"); dir.mkdirs()
        kit = WalletAppKit(params, dir, walletName).apply {
            setAutoSave(true); setBlockingStartup(false)
            if (!File(dir, "$walletName.wallet").exists()) {
                restoreWalletFromSeed(DeterministicSeed(seed, null, "", Utils.currentTimeSeconds()))
            }
            startAsync()
        }
    }

    fun onProgress(cb: (Int, String) -> Unit) {
        kit?.setDownloadListener(object : org.bitcoinj.core.listeners.DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                val p = pct.toInt()
                cb(p, if (p < 100) "Đang đồng bộ $p%" else "Đã đồng bộ")
            }
            override fun doneDownload() { cb(100, "Đã đồng bộ") }
        })
    }

    fun getBalance(): Double = kit?.wallet()?.getBalance(Wallet.BalanceType.ESTIMATED)?.toBtc()?.toDouble() ?: 0.0
    fun getAddress(): String = kit?.wallet()?.currentReceiveAddress()?.toString() ?: ""
    fun getTransactions(): List<TransactionInfo> {
        val w = kit?.wallet() ?: return emptyList()
        return w.getTransactionsByTime().take(20).map {
            val v = it.getValue(w).toBtc().toDouble()
            TransactionInfo(if (v > 0) "Nhận" else "Gửi", kotlin.math.abs(v), it.updateTime, it.txId.toString())
        }
    }

    fun price(): Double {
        return try {
            val txt = java.net.URL("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT").readText()
            Regex("\"price\":\"([0-9.]+)\"").find(txt)?.groupValues?.get(1)?.toDouble() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    fun send(to: String, amount: Double): String {
        return try {
            val w = kit?.wallet() ?: return "Chưa sẵn sàng"
            val req = Wallet.SendRequest.to(Address.fromString(params, to), Coin.valueOf((amount * 1e8).toLong()))
            req.feePerKb = Coin.valueOf(10000)
            w.sendCoins(req).tx.txId.toString()
        } catch (e: Exception) { "Lỗi: ${e.message}" }
    }
}

data class TransactionInfo(val type: String, val amount: Double, val time: Date, val hash: String)