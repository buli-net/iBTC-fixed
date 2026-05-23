package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL
import java.security.SecureRandom
import java.util.*

data class TxInfo(val id: String, val amt: Double, val time: Date, val type: String)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private val prefs = ctx.getSharedPreferences("w", 0)
    private var kit: WalletAppKit? = null
    var onProg: (Int, String) -> Unit = { _, _ -> }

    fun init() {
        val s = prefs.getString("seed", null) ?: return
        if (kit != null) return
        val seed = DeterministicSeed(null, s.split(" "), 0L)
        kit = WalletAppKit(params, File(ctx.filesDir, "w"), "ibtc").apply {
            setBlockingStartup(false)
            restoreWalletFromSeed(seed)
            try { setCheckpoints(ctx.assets.open("bitcoin-checkpoints.txt")) } catch (_: Exception) {}
            setDownloadListener(object : org.bitcoinj.core.listeners.DownloadProgressTracker() {
                override fun progress(p: Double, b: Int, d: Date?) { onProg(p.toInt(), "Sync ${p.toInt()}%") }
                override fun doneDownload() { onProg(100, "Đã sync") }
            })
            startAsync(); awaitRunning()
        }
    }

    fun newWallet(): String {
        val seed = DeterministicSeed(SecureRandom(), 128, "")
        val m = seed.mnemonicCode!!.joinToString(" ")
        prefs.edit().putString("seed", m).apply()
        kit?.stopAsync(); kit = null; init(); return m
    }

    fun hasWallet() = prefs.contains("seed")
    fun balance() = kit?.wallet()?.getBalance(Wallet.BalanceType.ESTIMATED)?.toBtc()?.toDouble() ?: 0.0
    fun address() = kit?.wallet()?.currentReceiveAddress().toString() ?: ""
    fun txs() = kit?.wallet()?.getTransactionsByTime()?.map {
        val v = it.getValue(kit!!.wallet())
        TxInfo(it.txId.toString(), v.toBtc().toDouble(), it.updateTime, if (v.isPositive) "Nhận" else "Gửi")
    }?.reversed() ?: emptyList()
    fun price() = try { Regex("[0-9]{5,}\\.[0-9]+").find(URL("https://blockchain.info/ticker").readText())?.value?.toDouble() ?: 0.0 } catch (_:Exception){0.0}
}