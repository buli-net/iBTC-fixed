package net.buli.ibtc

import android.content.Context
import android.util.Log
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.Wallet
import java.io.File

class WalletManager(private val context: Context) {

    private val params: NetworkParameters = MainNetParams.get()
    private var kit: WalletAppKit? = null

    var onBalanceChanged: ((Coin) -> Unit)? = null
    var onTransaction: ((Transaction) -> Unit)? = null

    fun startWallet() {
        try {
            val walletDir = File(context.filesDir, "ibtc-wallet")
            if (!walletDir.exists()) walletDir.mkdirs()

            kit = object : WalletAppKit(params, walletDir, "ibtc-wallet") {
                override fun onSetupCompleted() {
                    try {
                        wallet().addCoinsReceivedEventListener { wallet, tx, _, _ ->
                            onBalanceChanged?.invoke(wallet.balance)
                            onTransaction?.invoke(tx)
                        }
                        wallet().addCoinsSentEventListener { wallet, tx, _, _ ->
                            onBalanceChanged?.invoke(wallet.balance)
                            onTransaction?.invoke(tx)
                        }
                        onBalanceChanged?.invoke(wallet().balance)
                    } catch (e: Exception) {
                        Log.e("Wallet", "setup error", e)
                    }
                }
            }
            kit?.setAutoSave(true)
            kit?.setBlockingStartup(false)
            kit?.startAsync()
            Log.i("Wallet", "Kit started")
        } catch (e: Exception) {
            Log.e("Wallet", "startWallet crash", e)
        }
    }

    fun getReceiveAddress(): String {
        return try {
            kit?.wallet()?.currentReceiveAddress()?.toString() ?: "Đang khởi tạo..."
        } catch (e: Exception) {
            "Lỗi ví"
        }
    }

    fun getBalance(): Coin {
        return try { kit?.wallet()?.balance ?: Coin.ZERO } catch (e: Exception) { Coin.ZERO }
    }

    fun sendCoins(addressStr: String, amountBtc: Double): String {
        val k = kit ?: throw Exception("Ví chưa sẵn sàng")
        val amount = Coin.parseCoin(amountBtc.toString())
        val target = Address.fromString(params, addressStr)
        val result = k.wallet().sendCoins(k.peerGroup(), target, amount)
        return result.broadcastComplete.get().txId.toString()
    }

    fun getTransactions(): List<Transaction> {
        return try { kit?.wallet()?.getTransactionsByTime()?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    fun getWallet(): Wallet = kit!!.wallet()
    fun getTxValue(tx: Transaction): Coin = try { tx.getValue(kit!!.wallet()) } catch (e: Exception) { Coin.ZERO }

    fun stopWallet() {
        try { kit?.stopAsync() } catch (_: Exception) {}
    }
}