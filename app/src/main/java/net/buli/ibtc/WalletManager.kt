package net.buli.ibtc

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.net.discovery.DnsDiscovery
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
            setUserAgent("iBTC", "4.2")
            setDiscovery(DnsDiscovery(params)) // FIX cho 0.16.2
            restoreWalletFromSeed(seed)
            try { setCheckpoints(ctx.assets.open("bitcoin-checkpoints.txt")) } catch (_: Exception) {}
            setDownloadListener(object : org.bitcoinj.core.listeners.DownloadProgressTracker() {
                override fun progress(p: Double, b: Int, d: Date?) { onProg(p.toInt(), "Sync ${p.toInt()}%") }
                override fun doneDownload() { onProg(100, "Đã đồng bộ") }
            })
            startAsync(); awaitRunning()
            peerGroup().maxConnections = 6
            // FIX signature 4 tham số
            wallet().addCoinsReceivedEventListener { _, _, _, _ -> }
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
    fun price() = try { Regex("\"USD\":\\{\"last\":([0-9.]+)").find(URL("https://blockchain.info/ticker").readText())?.groupValues?.get(1)?.toDouble() ?: 0.0 } catch (_:Exception){0.0}
    fun send(to: String, amt: Double): String {
        val tx = kit!!.wallet().createSend(Address.fromString(params, to), Coin.parseCoin("%.8f".format(amt)))
        kit!!.wallet().commitTx(tx); kit!!.peerGroup().broadcastTransaction(tx); return tx.txId.toString()
    }
    fun qrBitmap(data: String, size: Int = 512): Bitmap {
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (bits[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        return bmp
    }
}