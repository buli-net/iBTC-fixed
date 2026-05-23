package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File

class WalletManager(private val context: Context) {

    private val params: NetworkParameters = MainNetParams.get()
    private var kit: WalletAppKit? = null

    val wallet: Wallet?
        get() = kit?.wallet()

    fun start() {
        val walletDir = File(context.filesDir, "ibtc-wallet")
        if (!walletDir.exists()) walletDir.mkdirs()
        
        kit = object : WalletAppKit(params, walletDir, "ibtc") {
            override fun onSetupCompleted() {
                wallet()?.allowSpendingUnconfirmedTransactions()
            }
        }.setBlockingStartup(false).setAutoSave(true)
        
        kit?.startAsync()
        kit?.awaitRunning()
    }

    fun createNewWallet(): List<String> {
        start()
        val seed = wallet?.keyChainSeed ?: return emptyList()
        return seed.mnemonicCode ?: emptyList()
    }

    fun restoreWallet(mnemonic: List<String>) {
        val walletDir = File(context.filesDir, "ibtc-wallet")
        walletDir.deleteRecursively()
        
        kit = object : WalletAppKit(params, walletDir, "ibtc") {
            override fun onSetupCompleted() {
                wallet()?.allowSpendingUnconfirmedTransactions()
            }
        }
        val seed = DeterministicSeed(mnemonic, null, "", System.currentTimeMillis() / 1000)
        kit?.restoreWalletFromSeed(seed)
        kit?.setBlockingStartup(false)
        kit?.startAsync()
        kit?.awaitRunning()
    }

    fun getBalance(): Coin {
        return wallet?.balance ?: Coin.ZERO
    }

    fun getReceiveAddress(): String {
        return wallet?.currentReceiveAddress()?.toString() ?: ""
    }

    // GỬI THẬT - phí real
    fun send(toAddress: String, amountSatoshi: Long, satPerVb: Long): String {
        val wallet = wallet ?: throw IllegalStateException("Wallet not ready")
        val to = Address.fromString(params, toAddress)
        val req = Wallet.SendRequest.to(to, Coin.valueOf(amountSatoshi))
        
        // phí thật: 1 sat/vB = 1000 sat/kB
        req.feePerKb = Coin.valueOf(satPerVb * 1000)
        req.ensureMinRequiredFee = false
        
        val result = wallet.sendCoins(req)
        return result.tx.txId.toString()
    }

    fun getTransactions(): List<Transaction> {
        return wallet?.getTransactionsByTime()?.toList() ?: emptyList()
    }

    fun stop() {
        kit?.stopAsync()
    }
}