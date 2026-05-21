package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.Wallet.SendRequest
import java.io.File
import java.net.URL
import java.util.Date

data class WalletInfo(val id: String, val name: String, val seed: String)
data class TransactionInfo(val txId: String, val amount: Double, val type: String, val time: Date)
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private var kit: WalletAppKit? = null
    private var active: WalletInfo? = null
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE)

    fun hasWallets() = prefs.all.isNotEmpty()

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
        // bitcoinj 0.15: constructor (entropyBits, passphrase, creationTime)
        val seed = DeterministicSeed(128, "", System.currentTimeMillis() / 1000)
        val mnemonic = seed.mnemonicCode?.joinToString(" ") ?: ""
        val info = WalletInfo(id, name.ifBlank { "Ví $id" }, mnemonic)
        save(info)
        active = info
        return info
    }

    fun import(name: String, seedPhrase: String): WalletInfo? {
        return try {
            val words = seedPhrase.trim().split("\\s+".toRegex())
            if (words.size < 12) return null
            // constructor (List<String>, entropy, passphrase, time)
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000)
            val id = System.currentTimeMillis().toString()
            val info = WalletInfo(id, name.ifBlank { "Imported" }, words.joinToString(" "))
            save(info)
            active = info
            info
        } catch (e: Exception) {
            null
        }
    }

    private fun save(info: WalletInfo) {
        prefs.edit()
            .putString("${info.id}_name", info.name)
            .putString("${info.id}_seed", info.seed)
            .apply()
    }

    fun delete(id: String) {
        prefs.edit().remove("${id}_name").remove("${id}_seed").apply()
        File(ctx.filesDir, id).deleteRecursively()
        if (active?.id == id) active = null
    }

    fun init() {
        val info = getActive() ?: return
        val words = info.seed.split(" ")
        val seed = DeterministicSeed(words, null, "", 0L)
        kit = object : WalletAppKit(params, Script.ScriptType.P2WPKH,
            KeyChainGroup.builder(params).fromSeed(seed).build(),
            File(ctx.filesDir, info.id), "ibtc") {
            override fun onSetupCompleted() {
                // fix line 152
                wallet().setAllowSpendingUnconfirmedTransactions(true)
            }
        }.apply {
            setBlockingStartup(false)
            startAsync()
            awaitRunning()
        }
    }

    fun stop() {
        kit?.stopAsync()
        kit?.awaitTerminated()
        kit = null
    }

    fun onProgress(cb: (Int, String) -> Unit) {
        kit?.setDownloadListener { pct, _ ->
            cb(pct, if (pct < 100) "Đang sync $pct%" else "Đã sync")
        }
    }

    fun getBalance(): Double =
        kit?.wallet()?.balance?.value?.toDouble()?.div(1e8) ?: 0.0

    fun getAddress(): String =
        kit?.wallet()?.currentReceiveAddress().toString()

    fun getSeed(): String = active?.seed ?: ""

    fun getTransactions(): List<TransactionInfo> {
        val w = kit?.wallet() ?: return emptyList()
        return w.getTransactionsByTime().map { tx ->
            val v = tx.getValue(w).value.toDouble() / 1e8
            TransactionInfo(
                tx.txId.toString(),
                kotlin.math.abs(v),
                if (v > 0) "Nhận" else "Gửi",
                tx.updateTime
            )
        }.reversed()
    }

    fun send(to: String, amountBTC: Double, feeRateSatVb: Int): String {
        return try {
            val w = kit!!.wallet()
            val addr = Address.fromString(params, to)
            val req = SendRequest.to(addr, Coin.parseCoin(amountBTC.toString()))
            // fix line 219-220
            req.feePerKb = Coin.valueOf(feeRateSatVb.toLong() * 1000)
            w.completeTx(req)
            w.commitTx(req.tx)
            kit!!.peerGroup().broadcastTransaction(req.tx).future().get()
            req.tx.txId.toString()
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }

    fun getFeeRates(): FeeRates = try {
        val txt = URL("https://mempool.space/api/v1/fees/recommended").readText()
        val slow = Regex("\"hourFee\":(\\d+)").find(txt)?.groupValues?.get(1)?.toInt() ?: 5
        val normal = Regex("\"halfHourFee\":(\\d+)").find(txt)?.groupValues?.get(1)?.toInt() ?: 10
        val fast = Regex("\"fastestFee\":(\\d+)").find(txt)?.groupValues?.get(1)?.toInt() ?: 20
        FeeRates(slow, normal, fast)
    } catch (_: Exception) { FeeRates(5, 10, 20) }

    fun price(): Double = try {
        val txt = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd").readText()
        Regex("\"usd\":([\\d.]+)").find(txt)?.groupValues?.get(1)?.toDouble() ?: 0.0
    } catch (_: Exception) { 0.0 }
}