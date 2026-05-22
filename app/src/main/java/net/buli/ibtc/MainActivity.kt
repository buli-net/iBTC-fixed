package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.security.SecureRandom
import java.util.*

class WalletManager(private val context: Context) {
    private val params = MainNetParams.get()
    private val prefs: SharedPreferences = context.getSharedPreferences("btc_wallet", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null
    private var decryptedSeed: String? = null
    private var currentWalletName: String? = null

    fun hasWallets(): Boolean = prefs.getStringSet("wallets", emptySet())?.isNotEmpty() == true
    fun isUnlocked(): Boolean = decryptedSeed != null
    fun getActive(): WalletInfo? = currentWalletName?.let { WalletInfo(it) }

    fun unlock(password: String): Boolean = try {
        val name = prefs.getString("active_wallet", prefs.getStringSet("wallets", emptySet())?.first())!!
        val enc = prefs.getString("seed_$name", null)!!
        decryptedSeed = CryptoUtil.decrypt(enc, password)
        currentWalletName = name
        true
    } catch (e: Exception) { false }

    fun lock() { decryptedSeed = null; kit?.stopAsync(); kit = null }

    // FIX 1: tạo seed đúng constructor
    fun create(name: String, password: String) {
        val seed = DeterministicSeed(SecureRandom(), 128, "", Utils.currentTimeSeconds())
        val phrase = seed.mnemonicCode!!.joinToString(" ")
        val encrypted = CryptoUtil.encrypt(phrase, password)
        prefs.edit().putString("seed_$name", encrypted)
            .putStringSet("wallets", (prefs.getStringSet("wallets", emptySet()) ?: emptySet()) + name)
            .putString("active_wallet", name).apply()
        decryptedSeed = phrase
        currentWalletName = name
    }

    fun import(name: String, phrase: String, password: String): Boolean = try {
        DeterministicSeed(phrase, null, "", Utils.currentTimeSeconds()) // check hợp lệ
        val encrypted = CryptoUtil.encrypt(phrase, password)
        prefs.edit().putString("seed_$name", encrypted)
            .putStringSet("wallets", (prefs.getStringSet("wallets", emptySet()) ?: emptySet()) + name)
            .putString("active_wallet", name).apply()
        decryptedSeed = phrase
        currentWalletName = name
        true
    } catch (e: Exception) { false }

    fun init() {
        if (decryptedSeed == null) return
        val dir = File(context.filesDir, "wallets").apply { mkdirs() }
        kit = object : WalletAppKit(params, dir, currentWalletName) {
            override fun onSetupCompleted() {}
        }.apply {
            setAutoSave(true)
            setBlockingStartup(false)
            if (!File(dir, "$currentWalletName.wallet").exists()) {
                val seed = DeterministicSeed(decryptedSeed, null, "", Utils.currentTimeSeconds())
                restoreWalletFromSeed(seed)
            }
            startAsync()
        }
    }

    // FIX 2: coerceAtMost
    fun onProgress(cb: (Int, String) -> Unit) {
        kit?.peerGroup()?.addBlocksDownloadedEventListener(Threading.SAME_THREAD) { _, _, left, _ ->
            val progress = if (left <= 0) 100 else (100 - left.coerceAtMost(100))
            cb(progress, if (progress < 100) "Sync $progress%" else "Đã sync")
        }
    }

    fun getBalance() = kit?.wallet()?.getBalance(Wallet.BalanceType.ESTIMATED)?.toBtc()?.toDouble() ?: 0.0
    fun getAddress() = kit?.wallet()?.currentReceiveAddress()?.toString() ?: ""
    fun price() = 67500.0
    fun getTransactions() = kit?.wallet()?.getTransactionsByTime()?.take(20)?.map {
        val v = it.getValue(kit!!.wallet()).toBtc().toDouble()
        TransactionInfo(if (v > 0) "Nhận" else "Gửi", kotlin.math.abs(v), it.updateTime, it.txId.toString())
    } ?: emptyList()
    fun getFeeRates() = FeeRates(5, 10, 20)

    // FIX 3: SendRequest
    fun send(to: String, amountBtc: Double, feePerKb: Int): String = try {
        val w = kit!!.wallet()
        val req = Wallet.SendRequest.to(Address.fromString(params, to), Coin.valueOf((amountBtc * 1e8).toLong()))
        req.feePerKb = Coin.valueOf(feePerKb * 1000L)
        w.sendCoins(req).tx.txId.toString()
    } catch (e: Exception) { "Lỗi: ${e.message}" }

    fun getDecryptedSeed(password: String): String {
        val enc = prefs.getString("seed_$currentWalletName", "")!!
        return CryptoUtil.decrypt(enc, password)
    }

    fun changePassword(old: String, newPass: String) = try {
        val seed = getDecryptedSeed(old)
        prefs.edit().putString("seed_$currentWalletName", CryptoUtil.encrypt(seed, newPass)).apply()
        true
    } catch (e: Exception) { false }
}

data class WalletInfo(val name: String)
data class TransactionInfo(val type: String, val amount: Double, val time: Date, val hash: String)