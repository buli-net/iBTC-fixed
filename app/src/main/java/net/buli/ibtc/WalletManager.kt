package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
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
            override fun onSetupCompleted() { wallet = wallet() }
        }
        kit.setAutoSave(true)
        kit.setBlockingStartup(false)
        kit.startAsync()
        kit.awaitRunning()
        wallet = kit.wallet()
    }

    fun getReceiveAddress() = wallet.currentReceiveAddress().toString()

    fun getBalance(): String {
        val bal = wallet.getBalance(Wallet.BalanceType.ESTIMATED).value
        return String.format("%.8f", bal / 1e8)
    }

    fun sync(cb: (Int) -> Unit) {
        try { kit.peerGroup().downloadBlockChain(); cb(100) } catch (_: Exception) {}
    }

    fun getSeed(): String {
        return wallet.keyChainSeed?.mnemonicCode?.joinToString(" ") ?: "Không tìm thấy seed"
    }

    fun sendCoins(to: String, amountBtc: String): String {
        return try {
            val amount = Coin.parseCoin(amountBtc)
            val target = Address.fromString(params, to)
            val res = wallet.sendCoins(kit.peerGroup(), target, amount)
            res.broadcastComplete.get()
            "Đã gửi! TXID: ${res.tx.txId}"
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }
}