package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL
import java.security.SecureRandom
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
                val id = o.getString("id")
                val name = o.getString("name")
                val seed = o.getString("seed")
                list.add(WalletInfo(id, name, seed))
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
        if (id == null) {
            return getAll().firstOrNull()
        }
        return getAll().find { it.id == id }
    }

    fun create(name: String): WalletInfo {
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
        val seedObj = DeterministicSeed(mnemonic, null, "", System.currentTimeMillis() / 1000)
        val mnemonicStr = seedObj.mnemonicCode!!.joinToString(" ")
        val finalName = if (name.isBlank()) {
            "Ví ${getAll().size + 1}"
        } else {
            name
        }
        val info = WalletInfo(UUID.randomUUID().toString(), finalName, mnemonicStr)
        val list = getAll().toMutableList()
        list.add(info)
        saveAll(list)
        prefs.edit().putString(activeKey, info.id).apply()
        return info
    }

    fun import(name: String, seed: String): WalletInfo? {
        return try {
            val words = seed.trim().split("\\s+".toRegex())
            if (words.size < 12) {
                return null
            }
            DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000)
            val finalName = if (name.isBlank()) {
                "Import ${getAll().size + 1}"
            } else {
                name
            }
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
        val current = getAll()
        val list = current.filter { it.id != id }
        saveAll(list)
        val active = getActive()
        if (active != null && active.id == id) {
            prefs.edit().remove(activeKey).apply()
        }
        try {
            val walletFile = File(ctx.filesDir, "$id.wallet")
            val chainFile = File(ctx.filesDir, "$id.spvchain")
            if (walletFile.exists()) {
                walletFile.delete()
            }
            if (chainFile.exists()) {
                chainFile.delete()
            }
        } catch (e: Exception) {
        }
    }

    fun switchTo(id: String) {
        prefs.edit().putString(activeKey, id).apply()
    }

    fun stop() {
        try {
            if (kit != null) {
                kit!!.stopAsync()
                kit!!.awaitTerminated()
            }
        } catch (e: Exception) {
        } finally {
            kit = null
        }
    }

    fun isReady(): Boolean {
        return try {
            kit != null && kit!!.isRunning
        } catch (e: Exception) {
            false
        }
    }

    fun init() {
        val active = getActive()
        if (active == null) {
            return
        }
        stop()
        val dir = ctx.filesDir
        kit = object : WalletAppKit(params, dir, active.id) {
            override fun onSetupCompleted() {
                if (wallet() != null) {
                    try {
                        wallet().setAcceptRiskyTransactions(true)
                    } catch (e: Exception) {
                    }
                }
            }
        }
        kit!!.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                val p = pct.toInt()
                if (progressCallback != null) {
                    val status = if (p < 100) {
                        "Đang sync $p%"
                    } else {
                        "Đã đồng bộ"
                    }
                    progressCallback!!.invoke(p, status)
                }
            }

            override fun doneDownload() {
                if (progressCallback != null) {
                    progressCallback!!.invoke(100, "Đã đồng bộ")
                }
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
            val wallet = kit?.wallet()
            if (wallet != null) {
                wallet.balance.toBtc().toDouble()
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    fun getAddress(): String {
        return try {
            val wallet = kit?.wallet()
            if (wallet != null) {
                wallet.currentReceiveAddress().toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun getSeed(): String {
        val active = getActive()
        return active?.seed ?: ""
    }

    fun getTransactions(): List<TransactionInfo> {
        val result = mutableListOf<TransactionInfo>()
        try {
            val wallet = kit?.wallet()
            if (wallet != null) {
                val txs = wallet.walletTransactions
                for (wt in txs) {
                    val tx = wt.transaction
                    val value = tx.getValue(wallet).toBtc().toDouble()
                    val absValue = if (value < 0) -value else value
                    val type = if (value >= 0) "Nhận" else "Gửi"
                    val time = tx.updateTime ?: Date()
                    val id = tx.txId.toString()
                    result.add(TransactionInfo(id, absValue, type, time))
                }
            }
        } catch (e: Exception) {
        }
        return result.sortedByDescending { it.time }
    }

    fun send(to: String, amountBtc: Double, feePerKb: Long): String {
        return try {
            val wallet = kit?.wallet()
            if (wallet == null) {
                return "Lỗi: ví chưa sẵn sàng"
            }
            val address = Address.fromString(params, to)
            val amount = Coin.valueOf((amountBtc * 100000000).toLong())
            val req = Wallet.SendRequest.to(address, amount)
            req.feePerKb = Coin.valueOf(feePerKb * 1000)
            val result = wallet.sendCoins(req)
            val tx = result.tx
            "Đã gửi: " + tx.txId.toString()
        } catch (e: Exception) {
            "Lỗi: " + (e.message ?: "không xác định")
        }
    }

    fun price(): Double {
        try {
            val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
            val conn = url.openConnection()
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.getInputStream().bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(text)
            val bitcoin = json.getJSONObject("bitcoin")
            val usd = bitcoin.getDouble("usd")
            prefs.edit().putFloat("last_price", usd.toFloat()).apply()
            return usd
        } catch (e: Exception) {
            val last = prefs.getFloat("last_price", 0f)
            if (last > 0) {
                return last.toDouble()
            }
            return 65000.0
        }
    }

    fun getFeeRates(): FeeRates {
        try {
            val api = getFeeApiUrl()
            val url = URL(api)
            val conn = url.openConnection()
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val text = conn.getInputStream().bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(text)
            val slow = json.optLong("hourFee", 5)
            val normal = json.optLong("halfHourFee", 10)
            val fast = json.optLong("fastestFee", 20)
            return FeeRates(slow, normal, fast)
        } catch (e: Exception) {
            return FeeRates(5, 10, 20)
        }
    }

    fun getFeeApiUrl(): String {
        return prefs.getString("fee_api", "https://mempool.space/api/v1/fees/recommended") ?: "https://mempool.space/api/v1/fees/recommended"
    }

    fun setFeeApiUrl(v: String) {
        prefs.edit().putString("fee_api", v).apply()
    }

    fun getRefreshSec(): Long {
        return prefs.getLong("refresh", 60)
    }

    fun setRefreshSec(v: Long) {
        prefs.edit().putLong("refresh", v).apply()
    }

    fun getDefaultCustomFee(): Long {
        return prefs.getLong("custom_fee", 10)
    }

    fun setDefaultCustomFee(v: Long) {
        prefs.edit().putLong("custom_fee", v).apply()
    }
}