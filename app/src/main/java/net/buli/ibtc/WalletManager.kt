package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener
import java.io.File

class WalletManager(private val context: Context) {

    private val params: NetworkParameters = MainNetParams.get()
    private lateinit var kit: WalletAppKit

    var onBalanceChanged: ((Coin) -> Unit)? = null
    var onTransaction: ((Transaction) -> Unit)? = null

    fun startWallet() {
        try {
            // FIX LỖI CUỐI: tạo Context bitcoinj đúng cách cho version 0.16.2
            org.bitcoinj.core.Context.getOrCreate(params)
            // Cài BouncyCastle để tránh crash trên Android 12+
            java.security.Security.insertProviderAt(org.spongycastle.jce.provider.BouncyCastleProvider(), 1)
        } catch (e: Exception) {}

        val walletDir = File(context.filesDir, "ibtc-wallet")
        if (!walletDir.exists()) walletDir.mkdirs()

        kit = object : WalletAppKit(params, walletDir, "ibtc-wallet") {
            override fun onSetupCompleted() {
                wallet().addCoinsReceivedEventListener(WalletCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
                    onBalanceChanged?.invoke(wallet.balance)
                    onTransaction?.invoke(tx)
                })
                wallet().addCoinsSentEventListener { wallet, tx, prevBalance, newBalance ->
                    onBalanceChanged?.invoke(wallet.balance)
                    onTransaction?.invoke(tx)
                }
                onBalanceChanged?.invoke(wallet().balance)
            }
        }

        kit.setAutoSave(true)
        kit.setBlockingStartup(false)
        kit.startAsync()
    }

    fun getReceiveAddress(): String {
        return try { kit.wallet().currentReceiveAddress().toString() } catch (e: Exception) { "Đang tạo ví..." }
    }

    fun getBalance(): Coin {
        return try { kit.wallet().balance } catch (e: Exception) { Coin.ZERO }
    }

    fun sendCoins(addressStr: String, amountBtc: Double): String {
        val amount = Coin.parseCoin(amountBtc.toString())
        val targetAddress = Address.fromString(params, addressStr)
        val result = kit.wallet().sendCoins(kit.peerGroup(), targetAddress, amount)
        return result.broadcastComplete.get().txId.toString()
    }

    fun getTransactions(): List<Transaction> {
        return try { kit.wallet().getTransactionsByTime().toList() } catch (e: Exception) { emptyList() }
    }

    fun getWallet(): Wallet = kit.wallet()
    fun getTxValue(tx: Transaction): Coin = tx.getValue(kit.wallet())

    fun stopWallet() {
        if (::kit.isInitialized) { kit.stopAsync() }
    }
}