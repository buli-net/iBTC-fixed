package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File

class WalletManager(private val context: Context) {
    private val params: NetworkParameters = MainNetParams.get()
    private lateinit var kit: WalletAppKit
    private lateinit var wallet: Wallet

    fun initWallet() {
        val walletDir = File(context.filesDir, "wallets")
        if (!walletDir.exists()) walletDir.mkdirs()

        kit = object : WalletAppKit(params, walletDir, "ibtc-spv") {
            override fun onSetupCompleted() {
                wallet = wallet()
            }
        }
        
        kit.setAutoSave(true)
        kit.setBlockingStartup(false)
        kit.startAsync()
        kit.awaitRunning()
        wallet = kit.wallet()
    }

    fun getReceiveAddress(): String {
        return wallet.currentReceiveAddress().toString()
    }

    fun getBalance(): String {
        val balance = wallet.getBalance(Wallet.BalanceType.ESTIMATED).value
        return String.format("%.8f", balance / 1e8)
    }

    fun sync(progressCallback: (Int) -> Unit) {
        try {
            kit.peerGroup().downloadBlockChain()
            progressCallback(100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createWallet(): String {
        val seed = DeterministicSeed(System.currentTimeMillis(), "", "", 0)
        return seed.mnemonicCode?.joinToString(" ") ?: ""
    }
}