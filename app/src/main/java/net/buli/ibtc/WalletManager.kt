package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.security.SecureRandom

class WalletManager(private val context: Context) {
    private val params: NetworkParameters = MainNetParams.get()
    private var kit: WalletAppKit? = null
    val wallet: Wallet? get() = kit?.wallet()

    fun walletExists(name: String): Boolean = File(context.filesDir, "$name.wallet").exists()

    fun createWallet(walletName: String, password: String): String {
        val seed = DeterministicSeed(SecureRandom(), 128, "", System.currentTimeMillis()/1000)
        kit = WalletAppKit(params, context.filesDir, walletName).apply {
            setAutoSave(true)
            setBlockingStartup(false)
            startAsync()
            awaitRunning()
        }
        kit?.wallet()?.let { w ->
            w.keyChainSeed = seed
            w.encrypt(password)
        }
        return seed.mnemonicCode!!.joinToString(" ")
    }

    fun importWallet(walletName: String, seedPhrase: String, password: String) {
        val seed = DeterministicSeed(seedPhrase, null, "", System.currentTimeMillis()/1000)
        kit = WalletAppKit(params, context.filesDir, walletName).apply {
            setAutoSave(true)
            restoreWalletFromSeed(seed)
            startAsync()
            awaitRunning()
        }
        kit?.wallet()?.encrypt(password)
    }

    fun unlock(walletName: String, password: String): Boolean {
        return try {
            kit = WalletAppKit(params, context.filesDir, walletName).apply {
                setAutoSave(true)
                startAsync()
                awaitRunning()
            }
            kit?.wallet()?.decrypt(password)
            true
        } catch (e: Exception) { false }
    }

    fun getBalance(): String = kit?.wallet()?.balance?.toFriendlyString() ?: "0.00 BTC"

    // Trả về địa chỉ Legacy 1... giống ảnh
    fun getReceiveAddress(): String {
        val w = kit?.wallet() ?: return ""
        return w.currentReceiveAddress().toString()
    }

    fun getTransactionHistory(): List<Transaction> = kit?.wallet()?.getTransactionsByTime()?.toList() ?: emptyList()

    fun startSync(onProgress: (Int) -> Unit) {
        kit?.setDownloadListener { blocksLeft, _, _, _ -> onProgress(blocksLeft) }
    }

    fun sendCoins(toAddress: String, amount: String, feeSatPerVb: Int): String {
        val w = kit?.wallet() ?: throw IllegalStateException("Wallet not loaded")
        val to = Address.fromString(params, toAddress)
        val coins = Coin.parseCoin(amount)
        val req = Wallet.SendRequest.to(to, coins)
        req.feePerKb = Coin.valueOf(feeSatPerVb * 1000L)
        w.completeTx(req)
        w.commitTx(req.tx)
        kit?.peerGroup()?.broadcastTransaction(req.tx)
        return req.tx.txId.toString()
    }

    fun changePassword(oldPass: String, newPass: String): Boolean {
        return try {
            wallet?.decrypt(oldPass)
            wallet?.encrypt(newPass)
            true
        } catch (e: Exception) { false }
    }

    fun deleteWallet(name: String) {
        kit?.stopAsync()
        File(context.filesDir, "$name.wallet").delete()
        File(context.filesDir, "$name.spvchain").delete()
    }
}