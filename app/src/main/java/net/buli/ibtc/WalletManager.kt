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
import java.net.URL
import java.security.SecureRandom
import java.util.Date

data class WalletInfo(val id: String, val name: String, val seed: String)
data class TransactionInfo(val txId: String, val amount: Double, val type: String, val time: Date)
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private var kit: WalletAppKit? = null
    private var active: WalletInfo? = null
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE)

    fun hasWallets(): Boolean {
        return prefs.all.isNotEmpty()
    }

    fun getActive(): WalletInfo? {
        if (active != null) return active
        val id = prefs.all.keys.firstOrNull() ?: return null
        val name = prefs.getString("${id}_name", "") ?: ""
        val seed = prefs.getString("${id}_seed", "") ?: ""
        active = WalletInfo(id, name, seed)
        return active
    }

    fun create(name: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val seed = DeterministicSeed(SecureRandom(), 128, "")
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val info = WalletInfo(id, if (name.isBlank()) "Ví $id" else name, mnemonic)
        prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", info.seed).apply()
        active = info
        return info
    }

    fun import(name: String, phrase: String): WalletInfo? {
        return try {
            val words = phrase.trim().split("\\s+".toRegex())
            if (words.size < 12) return null
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000)
            val id = System.currentTimeMillis().toString()
            val info = WalletInfo(id, if (name.isBlank()) "Imported" else name, words.joinToString(" "))
            prefs.edit().putString("${id}_name", info.name).putString("${id}_seed", info.seed).apply()
            active = info
            info
        } catch (e: Exception) {
            null
        }
    }

    fun delete(id: String) {
        prefs.edit().remove("${id}_name").remove("${id}_seed").apply()
        val dir = File(ctx.filesDir, id)
        dir.deleteRecursively()
        if (active?.id == id) active = null
    }

    fun init() {
        val info = getActive() ?: return
        val seed = DeterministicSeed(info.seed.split(" "), null, "", 0L)
        kit = WalletAppKit(params, File(ctx.filesDir, info.id), "ibtc").apply {
            setBlockingStartup(false)
            setDownloadListener(object : DownloadProgressTracker() {})
            restoreWalletFromSeed(seed)
            startAsync()
            awaitRunning()
        }
    }

    fun stop() {
        try {
            kit?.stopAsync()
            kit?.awaitTerminated()
        } catch (_: Exception) {}
        kit = null
    }

    fun onProgress(cb: (Int, String) -> Unit) {
        kit?.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                cb(pct.toInt(), if (pct < 100) "Đang sync ${pct.toInt()}%" else "Đã sync")
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
        val w = kit?.wallet() ?: return emptyList()
        return w.getTransactionsByTime().map { tx ->
            val v = tx.getValue(w).value.toDouble() / 1e8
            TransactionInfo(tx.txId.toString(), kotlin.math.abs(v), if (v > 0) "Nhận" else "Gửi", tx.updateTime)
        }.reversed()
    }

    fun send(to: String, amountBTC: Double, feeRateSatVb: Int): String {
        return try {
            val w = kit!!.wallet()
            val req = SendRequest.to(Address.fromString(params, to), Coin.parseCoin(amountBTC.toString()))
            req.allowUnconfirmed()
            req.feePerKb = Coin.valueOf(feeRateSatVb.toLong() * 1000)
            w.completeTx(req)
            w.commitTx(req.tx)
            kit!!.peerGroup().broadcastTransaction(req.tx).future().get()
            req.tx.txId.toString()
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }

    fun getFeeRates(): FeeRates {
        return try {
            val txt = URL("https://mempool.space/api/v1/fees/recommended").readText()
            val slow = Regex("\"hourFee\":(\\d+)").find(txt)?.groupValues?.get(1)?.toInt() ?: 5
            val normal = Regex("\"halfHourFee\":(\\d+)").find(txt)?.groupValues?.get(1)?.toInt() ?: 10
            val fast = Regex("\"fastestFee\":(\\d+)").find(txt)?.groupValues?.get(1)?.toInt() ?: 20
            FeeRates(slow, normal, fast)
        } catch (_: Exception) {
            FeeRates(5, 10, 20)
        }
    }

    fun price(): Double {
        return try {
            val txt = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd").readText()
            Regex("\"usd\":([\\d.]+)").find(txt)?.groupValues?.get(1)?.toDouble() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }
}