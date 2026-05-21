package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL
import java.util.*

data class WalletInfo(
    val id: String,
    val name: String,
    val seed: String
)

data class TransactionInfo(
    val txId: String,
    val amount: Double,
    val type: String,
    val time: Date
)

data class FeeRates(
    val slow: Long,
    val normal: Long,
    val fast: Long
)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private var kit: WalletAppKit? = null
    private val prefs: SharedPreferences = ctx.getSharedPreferences("ibtc", Context.MODE_PRIVATE)
    private val walletsKey = "wallets"
    private val activeKey = "active"
    private var progressCallback: ((Int, String) -> Unit)? = null

    fun hasWallets(): Boolean {
        return getAll().isNotEmpty()
    }

    fun getAll(): List<WalletInfo> {
        val json = prefs.getString(walletsKey, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<WalletInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    WalletInfo(
                        o.getString("id"),
                        o.getString("name"),
                        o.getString("seed")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(list: List<WalletInfo>) {
        val arr = org.json.JSONArray()
        for (w in list) {
            val o = org.json.JSONObject()
            o.put("id", w.id)
            o.put("name", w.name)
            o.put("seed", w.seed)
            arr.put(o)
        }
        prefs.edit().putString(walletsKey, arr.toString()).apply()
    }

    fun getActive(): WalletInfo? {
        val id = prefs.getString(activeKey, null)
        return if (id == null) {
            getAll().firstOrNull()
        } else {
            getAll().find { it.id == id }
        }
    }

    fun create(name: String): WalletInfo {
        val seed = DeterministicSeed(128, null, "", System.currentTimeMillis() / 1000)
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val finalName = if (name.isBlank()) "Ví ${getAll().size + 1}" else name
        val info = WalletInfo(UUID.randomUUID().toString(), finalName, mnemonic)
        val list = getAll().toMutableList()
        list.add(info)
        saveAll(list)
        prefs.edit().putString(activeKey, info.id).apply()
        return info
    }

    fun import(name: String, seed: String): WalletInfo? {
        return try {
            val words = seed.trim().split("\\s+".toRegex())
            if (words.size < 12) return null
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000)
            val finalName = if (name.isBlank()) "Import ${getAll().size + 1}" else name
            val info = WalletInfo(UUID.randomUUID().toString(), finalName, seed.trim())
            val list = getAll().toMutableList()
            list.add(info)
            saveAll(list)
            prefs.edit().putString(activeKey, info.id).apply()
            info
        } catch (e: Exception) {
            null
        }
    }

    fun delete(id: String) {
        saveAll(getAll().filter { it.id != id })
        if (getActive()?.id == id) {
            prefs.edit().remove(activeKey).apply()
        }
        try {
            File(ctx.filesDir, "$id.wallet").delete()
            File(ctx.filesDir, "$id.spvchain").delete()
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            kit?.stopAsync()
            kit?.awaitTerminated()
        } catch (e: Exception) {
        }
        kit = null
    }

    fun isReady(): Boolean {
        return try {
            kit != null && kit!!.isRunning
        } catch (e: Exception) {
            false
        }
    }

    fun init() {
        val active = getActive() ?: return
        stop()
        kit = object : WalletAppKit(params, ctx.filesDir, active.id) {
            override fun onSetupCompleted() {
                wallet().allowSpendingUnconfirmedTransactions()
            }
        }
        kit!!.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                progressCallback?.invoke(
                    pct.toInt(),
                    if (pct < 100) "Đang sync ${pct.toInt()}%" else "Đã đồng bộ"
                )
            }

            override fun doneDownload() {
                progressCallback?.invoke(100, "Đã đồng bộ")
            }
        })
        kit!!.setBlockingStartup(false)
        kit!!.setAutoSave(true)
        kit!!.startAsync()
        kit!!.awaitRunning()
    }

    fun onProgress(cb: (Int, String) -> Unit) {
        progressCallback = cb
    }

    fun getBalance(): Double {
        return try {
            kit?.wallet()?.balance?.toBtc()?.toDouble() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    fun getAddress(): String {
        return try {
            kit?.wallet()?.currentReceiveAddress().toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun getSeed(): String {
        return getActive()?.seed ?: ""
    }

    fun getTransactions(): List<TransactionInfo> {
        return try {
            kit?.wallet()?.walletTransactions?.map {
                val tx = it.transaction
                val v = tx.getValue(kit!!.wallet()).toBtc().toDouble()
                TransactionInfo(
                    tx.txId.toString(),
                    kotlin.math.abs(v),
                    if (v >= 0) "Nhận" else "Gửi",
                    tx.updateTime ?: Date()
                )
            }?.sortedByDescending { it.time } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun send(to: String, amountBtc: Double, feePerKb: Long): String {
        return try {
            val w = kit?.wallet() ?: return "Lỗi: ví chưa sẵn sàng"
            val addr = Address.fromString(params, to)
            val amount = Coin.valueOf((amountBtc * 100000000).toLong())
            val req = Wallet.SendRequest.to(addr, amount)
            req.feePerKb = Coin.valueOf(feePerKb * 1000)
            val tx = w.sendCoins(req).tx
            "Đã gửi: ${tx.txId}"
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }

    fun price(): Double {
        return try {
            val text = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd").readText()
            val p = org.json.JSONObject(text).getJSONObject("bitcoin").getDouble("usd")
            prefs.edit().putFloat("last_price", p.toFloat()).apply()
            p
        } catch (e: Exception) {
            prefs.getFloat("last_price", 65000f).toDouble()
        }
    }

    fun getFeeRates(): FeeRates {
        return try {
            val j = org.json.JSONObject(URL("https://mempool.space/api/v1/fees/recommended").readText())
            FeeRates(
                j.optLong("hourFee", 5),
                j.optLong("halfHourFee", 10),
                j.optLong("fastestFee", 20)
            )
        } catch (e: Exception) {
            FeeRates(5, 10, 20)
        }
    }
}