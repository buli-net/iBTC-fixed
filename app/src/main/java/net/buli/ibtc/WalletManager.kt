package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener
import java.io.File

/**
 * WalletManager - Quản lý toàn bộ ví Bitcoin mainnet thật
 * Dùng thư viện bitcoinj 0.16.2
 */
class WalletManager(private val context: Context) {

    // 1. Tham số mạng Bitcoin thật (mainnet)
    private val params: NetworkParameters = MainNetParams.get()

    // 2. Kit tự động quản lý blockchain, peer, wallet file
    private lateinit var kit: WalletAppKit

    // 3. Callback để MainActivity cập nhật UI
    var onBalanceChanged: ((Coin) -> Unit)? = null
    var onTransaction: ((Transaction) -> Unit)? = null

    /**
     * Khởi tạo ví - chạy lần đầu sẽ tạo file wallet
     */
    fun startWallet() {
        // Thư mục lưu blockchain + wallet
        val walletDir = File(context.filesDir, "ibtc-wallet")
        if (!walletDir.exists()) walletDir.mkdirs()

        // Tạo WalletAppKit - tự động download header, sync SPV
        kit = object : WalletAppKit(params, walletDir, "ibtc-wallet") {
            override fun onSetupCompleted() {
                // Được gọi khi ví đã sẵn sàng
                wallet().addCoinsReceivedEventListener(WalletCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
                    onBalanceChanged?.invoke(wallet.balance)
                    onTransaction?.invoke(tx)
                })
                wallet().addCoinsSentEventListener { wallet, tx, prevBalance, newBalance ->
                    onBalanceChanged?.invoke(wallet.balance)
                    onTransaction?.invoke(tx)
                }
                // Cập nhật balance lần đầu
                onBalanceChanged?.invoke(wallet().balance)
            }
        }

        // Cấu hình: chỉ dùng SPV, không download full block (tiết kiệm data)
        kit.setAutoSave(true)
        kit.setBlockingStartup(false) // Không block UI
        kit.startAsync()
        kit.awaitRunning()
    }

    /**
     * Lấy địa chỉ nhận BTC hiện tại (dạng legacy 1...)
     */
    fun getReceiveAddress(): String {
        val address = kit.wallet().currentReceiveAddress()
        return address.toString()
    }

    /**
     * Lấy số dư hiện tại
     */
    fun getBalance(): Coin {
        return kit.wallet().balance
    }

    /**
     * Gửi BTC - trả về tx hash nếu thành công
     */
    fun sendCoins(addressStr: String, amountBtc: Double): String {
        val amount = Coin.parseCoin(amountBtc.toString())
        val targetAddress = LegacyAddress.fromBase58(params, addressStr)
        val result = kit.wallet().sendCoins(kit.peerGroup(), targetAddress, amount)
        return result.broadcastComplete.get().txId.toString()
    }

    /**
     * Lấy lịch sử giao dịch
     */
    fun getTransactions(): List<Transaction> {
        return kit.wallet().getTransactionsByTime().toList()
    }


    /**
     * Lấy ví để tính toán (dùng nội bộ)
     */
    fun getWallet(): Wallet {
        return kit.wallet()
    }

    /**
     * Tính giá trị giao dịch so với ví của mình
     * Dương = nhận, Âm = gửi
     */
    fun getTxValue(tx: Transaction): Coin {
        return tx.getValue(kit.wallet())
    }

    /**
     * Dừng ví khi app đóng
     */
    fun stopWallet() {
        if (::kit.isInitialized) {
            kit.stopAsync()
            kit.awaitTerminated()
        }
    }
}