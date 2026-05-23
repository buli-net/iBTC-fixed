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
 * Sử dụng thư viện bitcoinj 0.16.2
 * 
 * Chức năng chính:
 * - Tạo ví mới lần đầu, lưu file wallet
 * - Đồng bộ blockchain theo kiểu SPV (nhẹ, không tải full block)
 * - Nhận/gửi BTC thật trên mạng Bitcoin
 */
class WalletManager(private val context: Context) {

    // 1. THAM SỐ MẠNG BITCOIN THẬT
    // MainNetParams = mạng chính, tiền thật. Đổi thành TestNet3Params nếu muốn test
    private val params: NetworkParameters = MainNetParams.get()

    // 2. WalletAppKit - công cụ tự động của bitcoinj
    // Nó lo hết: kết nối peer, tải header, quản lý wallet file, lưu blockchain
    private lateinit var kit: WalletAppKit

    // 3. CALLBACK để MainActivity cập nhật giao diện
    // Khi số dư thay đổi, gọi hàm này
    var onBalanceChanged: ((Coin) -> Unit)? = null
    // Khi có giao dịch mới, gọi hàm này
    var onTransaction: ((Transaction) -> Unit)? = null

    /**
     * KHỞI TẠO VÍ - chạy lần đầu sẽ tạo file wallet mới
     * Chạy trong thread riêng để không đơ UI
     */
    fun startWallet() {
        // Thư mục lưu dữ liệu ví trong bộ nhớ app
        // Đường dẫn: /data/data/net.buli.ibtc/files/ibtc-wallet/
        val walletDir = File(context.filesDir, "ibtc-wallet")
        if (!walletDir.exists()) walletDir.mkdirs()

        // Tạo WalletAppKit với tên file "ibtc-wallet"
        kit = object : WalletAppKit(params, walletDir, "ibtc-wallet") {
            override fun onSetupCompleted() {
                // Hàm này được gọi khi ví đã sẵn sàng (đã load xong file)
                
                // Lắng nghe sự kiện NHẬN coin
                wallet().addCoinsReceivedEventListener(WalletCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
                    // Gọi callback để MainActivity cập nhật số dư
                    onBalanceChanged?.invoke(wallet.balance)
                    onTransaction?.invoke(tx)
                })
                
                // Lắng nghe sự kiện GỬI coin
                wallet().addCoinsSentEventListener { wallet, tx, prevBalance, newBalance ->
                    onBalanceChanged?.invoke(wallet.balance)
                    onTransaction?.invoke(tx)
                }
                
                // Cập nhật balance lần đầu khi mở app
                onBalanceChanged?.invoke(wallet().balance)
            }
        }

        // CẤU HÌNH KIT
        kit.setAutoSave(true)          // Tự động lưu ví mỗi khi có thay đổi
        kit.setBlockingStartup(false)  // Không block thread chính
        // Mặc định kit sẽ dùng SPV - chỉ tải header block (~50MB), không tải full blockchain
        
        // BẮT ĐẦU CHẠY
        kit.startAsync()    // Chạy bất đồng bộ
        kit.awaitRunning()  // Đợi cho đến khi chạy xong
    }

    /**
     * LẤY ĐỊA CHỈ NHẬN BTC HIỆN TẠI
     * Trả về dạng legacy bắt đầu bằng "1..." (tương thích mọi ví)
     */
    fun getReceiveAddress(): String {
        val address = kit.wallet().currentReceiveAddress()
        return address.toString()
    }

    /**
     * LẤY SỐ DƯ HIỆN TẠI
     * Trả về đối tượng Coin của bitcoinj
     */
    fun getBalance(): Coin {
        return kit.wallet().balance
    }

    /**
     * GỬI BTC - trả về tx hash nếu thành công
     * @param addressStr địa chỉ nhận (1..., 3... hoặc bc1...)
     * @param amountBtc số lượng BTC (vd: 0.001)
     */
    fun sendCoins(addressStr: String, amountBtc: Double): String {
        // Chuyển double sang Coin (đơn vị satoshi)
        val amount = Coin.parseCoin(amountBtc.toString())
        
        // Parse địa chỉ - hỗ trợ cả legacy và segwit
        val targetAddress = Address.fromString(params, addressStr)
        
        // Tạo và broadcast giao dịch
        val result = kit.wallet().sendCoins(kit.peerGroup(), targetAddress, amount)
        
        // Đợi broadcast xong, trả về TXID
        return result.broadcastComplete.get().txId.toString()
    }

    /**
     * LẤY LỊCH SỬ GIAO DỊCH
     * Trả về danh sách sắp xếp theo thời gian
     */
    fun getTransactions(): List<Transaction> {
        return kit.wallet().getTransactionsByTime().toList()
    }

    // ===== 2 HÀM FIX LỖI CHO MainActivity =====
    
    /**
     * LẤY VÍ ĐỂ TÍNH TOÁN - HÀM MỚI THÊM
     * MainActivity cần truy cập wallet để tính giá trị giao dịch
     * Trước đây MainActivity gọi sai kiểu nên bị lỗi compile
     */
    fun getWallet(): Wallet {
        return kit.wallet()
    }

    /**
     * TÍNH GIÁ TRỊ GIAO DỊCH - HÀM MỚI THÊM (FIX LỖI TransactionBag)
     * 
     * LỖI CŨ: MainActivity gọi tx.getValue(Coin) -> sai kiểu
     * ĐÚNG: tx.getValue(Wallet) mới trả về Coin
     * 
     * Hàm này bọc lại để MainActivity không cần truy cập trực tiếp kit.wallet()
     * 
     * @param tx giao dịch cần tính
     * @return Coin dương nếu nhận, âm nếu gửi
     */
    fun getTxValue(tx: Transaction): Coin {
        // Truyền wallet vào getValue() - đây là cách đúng của bitcoinj
        return tx.getValue(kit.wallet())
    }

    /**
     * DỪNG VÍ KHI APP ĐÓNG
     * Giải phóng kết nối peer, lưu dữ liệu
     */
    fun stopWallet() {
        if (::kit.isInitialized) {
            kit.stopAsync()
            kit.awaitTerminated()
        }
    }
}