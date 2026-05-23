package net.buli.ibtc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Context as BitcoinContext
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.SecureRandom
import java.util.Date
import java.util.Locale

/**
 * iBTC v4.1 - Build 22/05/2026 18:58
 * File gốc tạo ra app-debug.apk 15,57 MB
 * Không rút gọn
 */
data class TxInfo(
    val id: String,
    val amt: Double,
    val time: Date,
    val type: String
)

class WalletManager(private val appContext: Context) {

    private val params = MainNetParams.get()
    private val prefs = appContext.getSharedPreferences("ibtc_wallet_v41_prefs", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null

    var onProg: (progress: Int, status: String) -> Unit = { _, _ -> }

    init {
        BitcoinContext.getOrCreate(params)
    }

    fun init() {
        val seedPhrase = prefs.getString("mnemonic_v41", null)
        if (seedPhrase == null) {
            return
        }
        if (kit != null) {
            return
        }

        val seed = DeterministicSeed(null, seedPhrase.split(" "), 0L)

        val walletDir = File(appContext.filesDir, "ibtc_v41_data")
        if (!walletDir.exists()) {
            walletDir.mkdirs()
        }

        kit = object : WalletAppKit(params, walletDir, "ibtc-v41") {
            override fun onSetupCompleted() {
                super.onSetupCompleted()
                if (wallet() != null) {
                    wallet().allowSpendingUnconfirmedTransactions()
                }
            }
        }

        kit!!.apply {
            setBlockingStartup(false)
            setUserAgent("iBTC-Android", "4.1")
            setAutoSave(true)

            restoreWalletFromSeed(seed)

            var checkpointStream: InputStream? = null
            try {
                checkpointStream = appContext.assets.open("bitcoin-checkpoints.txt")
                setCheckpoints(checkpointStream)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    checkpointStream?.close()
                } catch (ignored: Exception) {
                }
            }

            setDownloadListener(object : DownloadProgressTracker() {
                override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                    val percentInt = pct.toInt()
                    val statusText = String.format(Locale.US, "Đang đồng bộ %d%% (%d blocks)", percentInt, blocksSoFar)
                    onProg(percentInt, statusText)
                }

                override fun doneDownload() {
                    onProg(100, "Đồng bộ hoàn tất")
                }

                override fun startDownload(blocks: Int) {
                    onProg(0, "Bắt đầu tải $blocks blocks")
                }
            })

            startAsync()
            awaitRunning()

            try {
                peerGroup().maxConnections = 8
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun newWallet(): String {
        val random = SecureRandom()
        val seed = DeterministicSeed(random, 128, "")
        val mnemonicList = seed.mnemonicCode
        val mnemonicString = mnemonicList!!.joinToString(" ")

        prefs.edit().putString("mnemonic_v41", mnemonicString).apply()

        if (kit != null) {
            try {
                kit!!.stopAsync()
                kit!!.awaitTerminated()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        kit = null

        init()

        return mnemonicString
    }

    fun hasWallet(): Boolean {
        return prefs.contains("mnemonic_v41")
    }

    fun balance(): Double {
        val wallet = kit?.wallet()
        if (wallet == null) {
            return 0.0
        }
        val balanceCoin = wallet.getBalance(Wallet.BalanceType.ESTIMATED)
        return balanceCoin.toBtc().toDouble()
    }

    fun address(): String {
        val wallet = kit?.wallet()
        if (wallet == null) {
            return ""
        }
        return wallet.currentReceiveAddress().toString()
    }

    fun txs(): List<TxInfo> {
        val wallet = kit?.wallet() ?: return emptyList()
        val transactions = wallet.getTransactionsByTime()
        val result = ArrayList<TxInfo>()
        for (tx in transactions) {
            val value = tx.getValue(wallet)
            val amountBtc = value.toBtc().toDouble()
            val typeString = if (value.isPositive) "Nhận" else "Gửi"
            val info = TxInfo(
                id = tx.txId.toString(),
                amt = amountBtc,
                time = tx.updateTime,
                type = typeString
            )
            result.add(info)
        }
        result.sortByDescending { it.time }
        return result
    }

    fun priceUsd(): Double {
        return try {
            val url = URL("https://blockchain.info/ticker")
            val content = url.readText()
            val regex = Regex(""USD"\s*:\s*\{[^}]*"last"\s*:\s*([0-9]+\.?[0-9]*)")
            val matchResult = regex.find(content)
            val priceString = matchResult?.groups?.get(1)?.value
            priceString?.toDouble() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    @Throws(Exception::class)
    fun send(toAddressString: String, amountBtc: Double): String {
        val kitInstance = kit ?: throw IllegalStateException("Wallet chưa được khởi tạo")
        val wallet = kitInstance.wallet()
        val params = wallet.params
        val toAddress = Address.fromString(params, toAddressString)
        val amountCoin = Coin.parseCoin(String.format(Locale.US, "%.8f", amountBtc))
        val sendRequest = Wallet.SendRequest.to(toAddress, amountCoin)
        sendRequest.feePerKb = Coin.valueOf(10000)
        val sendResult = wallet.sendCoins(sendRequest)
        val transaction = sendResult.tx
        kitInstance.peerGroup().broadcastTransaction(transaction).broadcast()
        return transaction.txId.toString()
    }

    fun generateQr(data: String, dimension: Int = 512): Bitmap {
        val qrWriter = QRCodeWriter()
        val bitMatrix = qrWriter.encode(data, BarcodeFormat.QR_CODE, dimension, dimension)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
}