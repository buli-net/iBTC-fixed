package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL
import java.security.SecureRandom
import java.util.Date
import kotlin.math.abs

// Dữ liệu cho 1 ví Bitcoin
// id: mã duy nhất để phân biệt nhiều ví
// name: tên hiển thị do người dùng đặt
// seed: chuỗi 12 từ BIP39 để khôi phục ví
data class WalletInfo(val id: String, var name: String, val seed: String)

// Dữ liệu 3 mức phí lấy từ mạng Bitcoin
// slow: xác nhận trong ~1 giờ, normal: ~30 phút, fast: ~10 phút
data class FeeRates(val slow: Long, val normal: Long, val fast: Long)

// Dữ liệu 1 giao dịch trong lịch sử
data class TransactionInfo(
    val txId: String,      // mã giao dịch
    val amount: Double,    // số BTC (dương)
    val time: Date,        // thời gian
    val type: String       // "Gửi" hoặc "Nhận"
)

class WalletManager(private val ctx: Context) {
    // Sử dụng mạng Bitcoin chính thức (mainnet), tiền thật
    private val params = MainNetParams.get()
    
    // SharedPreferences để lưu danh sách ví và cài đặt
    private val prefs = ctx.getSharedPreferences("ibtc_wallets", Context.MODE_PRIVATE)
    
    // WalletAppKit của bitcoinj để đồng bộ blockchain
    private var kit: WalletAppKit? = null
    
    // Đối tượng Wallet hiện tại
    private var wallet: Wallet? = null
    
    // Cờ báo ví đã sẵn sàng để dùng (không bị null)
    @Volatile private var ready = false
    
    // Callback để UI nhận tiến độ sync
    private var progressCb: ((Int, String) -> Unit)? = null

    // ===== PHẦN CÀI ĐẶT TÙY CHỈNH =====
    
    // Lấy URL API phí, mặc định dùng mempool.space
    fun getFeeApiUrl(): String = prefs.getString("fee_url", "https://mempool.space/api/v1/fees/recommended")!!
    
    // Lưu URL API phí do người dùng nhập
    fun setFeeApiUrl(url: String) { prefs.edit().putString("fee_url", url).apply() }
    
    // Lấy thời gian tự động refresh số dư (giây), mặc định 60s
    fun getRefreshSec(): Long = prefs.getLong("refresh_sec", 60)
    
    // Lưu thời gian refresh
    fun setRefreshSec(sec: Long) { prefs.edit().putLong("refresh_sec", sec).apply() }
    
    // Lấy phí tùy chỉnh mặc định
    fun getDefaultCustomFee(): Long = prefs.getLong("custom_fee", 10)
    
    // Lưu phí tùy chỉnh
    fun setDefaultCustomFee(fee: Long) { prefs.edit().putLong("custom_fee", fee).apply() }

    // Đăng ký hàm callback để nhận % sync
    fun onProgress(cb: (Int, String) -> Unit) { progressCb = cb }
    
    // Kiểm tra đã có ví nào chưa
    fun hasWallets(): Boolean = getAll().isNotEmpty()

    // Lấy danh sách tất cả ví đã lưu
    fun getAll(): List<WalletInfo> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return ids.map { id ->
            WalletInfo(
                id,
                prefs.getString("n_$id", "Ví")!!,
                prefs.getString("s_$id", "")!!
            )
        }
    }

    // Lưu danh sách ID ví
    private fun saveIds(ids: Set<String>) {
        prefs.edit().putStringSet("ids", ids).apply()
    }

    // Tạo ví mới hoàn toàn
    fun create(name: String): WalletInfo {
        // Tạo ID duy nhất bằng timestamp
        val id = System.currentTimeMillis().toString()
        
        // Tạo entropy 16 byte ngẫu nhiên (128 bit)
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        
        // Tạo seed BIP39 từ entropy
        val seed = DeterministicSeed(entropy, "", System.currentTimeMillis() / 1000)
        
        // Lấy 12 từ mnemonic
        val mnemonic = seed.mnemonicCode?.joinToString(" ") ?: ""
        
        // Tạo thông tin ví
        val info = WalletInfo(id, if (name.isBlank()) "Ví mới" else name, mnemonic)
        
        // Lưu vào danh sách
        val ids = getAll().map { it.id }.toMutableSet().apply { add(id) }
        saveIds(ids)
        prefs.edit().putString("n_$id", info.name).putString("s_$id", mnemonic).apply()
        
        // Đặt làm ví đang dùng
        setActive(id)
        return info
    }

    // Import ví từ seed có sẵn
    fun import(name: String, mnemonic: String): WalletInfo? {
        return try {
            // Kiểm tra seed hợp lệ bằng cách tạo DeterministicSeed
            DeterministicSeed(mnemonic.trim().split("\\s+".toRegex()), null, "", 0)
            
            val id = System.currentTimeMillis().toString()
            val info = WalletInfo(id, if (name.isBlank()) "Ví import" else name, mnemonic.trim())
            
            val ids = getAll().map { it.id }.toMutableSet().apply { add(id) }
            saveIds(ids)
            prefs.edit().putString("n_$id", info.name).putString("s_$id", info.seed).apply()
            
            setActive(id)
            info
        } catch (e: Exception) {
            null // Seed không hợp lệ
        }
    }

    // Đặt ví đang hoạt động
    fun setActive(id: String) {
        prefs.edit().putString("active", id).apply()
    }

    // Lấy ví đang hoạt động
    fun getActive(): WalletInfo? {
        val id = prefs.getString("active", null) ?: return null
        return getAll().find { it.id == id }
    }

    // Đổi tên ví
    fun rename(id: String, newName: String) {
        prefs.edit().putString("n_$id", newName).apply()
    }

    // Xóa ví hoàn toàn
    fun delete(id: String) {
        try { kit?.stopAsync() } catch (_: Exception) {}
        
        val ids = getAll().map { it.id }.toMutableSet().apply { remove(id) }
        saveIds(ids)
        
        // Xóa dữ liệu ví
        prefs.edit().remove("n_$id").remove("s_$id").apply()
        
        // Xóa file wallet trên đĩa
        File(ctx.filesDir, "wallets").listFiles()?.filter { it.name.contains(id) }?.forEach { it.delete() }
        
        // Nếu xóa ví đang dùng, chuyển sang ví khác
        if (prefs.getString("active", null) == id) {
            ids.firstOrNull()?.let { setActive(it) }
        }
        
        kit = null
        wallet = null
        ready = false
    }

    // Chuyển sang ví khác
    fun switchTo(id: String) {
        setActive(id)
        init()
    }

    // Kiểm tra ví đã sẵn sàng chưa
    fun isReady(): Boolean = ready && wallet != null

    // Khởi tạo ví (load seed và bắt đầu sync)
    fun init() {
        ready = false
        val info = getActive() ?: return
        
        try {
            // Tạo wallet từ seed
            val seed = DeterministicSeed(info.seed.split(" "), null, "", 0)
            wallet = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
            ready = true
            
            // Chạy sync blockchain ở thread riêng để không block UI
            Thread {
                try {
                    kit?.stopAsync()
                    
                    val dir = File(ctx.filesDir, "wallets").apply { mkdirs() }
                    val walletFile = File(dir, "wallet-${info.id}.wallet")
                    
                    kit = object : WalletAppKit(params, dir, "wallet-${info.id}") {
                        override fun onSetupCompleted() {
                            // Khi setup xong, thay wallet offline bằng wallet đã sync
                            wallet = this.wallet()
                        }
                    }.apply {
                        // Nếu chưa có file wallet, khôi phục từ seed
                        if (!walletFile.exists()) {
                            restoreWalletFromSeed(seed)
                        }
                        
                        setAutoSave(true)
                        setBlockingStartup(false)
                        
                        // Listener để báo tiến độ download blockchain
                        setDownloadListener(object : DownloadProgressTracker() {
                            override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                                progressCb?.invoke(pct.toInt(), "Sync ${pct.toInt()}%")
                            }
                            
                            override fun doneDownload() {
                                progressCb?.invoke(100, "Đã đồng bộ")
                            }
                        })
                        
                        startAsync()
                    }
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}
    }

    // Lấy địa chỉ nhận BTC hiện tại
    fun getAddress(): String = if (isReady()) wallet!!.currentReceiveAddress().toString() else ""

    // Lấy số dư (BTC)
    fun getBalance(): Double = if (isReady()) wallet!!.getBalance(Wallet.BalanceType.ESTIMATED).value / 1e8 else 0.0

    // Lấy seed của ví đang dùng
    fun getSeed(): String = getActive()?.seed ?: ""

    // Bắt đầu sync thủ công
    fun sync() {
        if (kit?.isRunning == true) {
            Thread {
                try {
                    kit?.peerGroup()?.downloadBlockChain()
                } catch (_: Exception) {}
            }.start()
        }
    }

    // Lấy giá BTC từ Binance
    fun price(): Double = try {
        URL("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT").readText().let {
            """"price":"([0-9.]+)"""".toRegex().find(it)?.groupValues?.get(1)?.toDouble() ?: 0.0
        }
    } catch (_: Exception) { 0.0 }

    // Lấy phí giao dịch từ API (mạng thật)
    fun getFeeRates(): FeeRates = try {
        val json = URL(getFeeApiUrl()).readText()
        val fast = """"fastestFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 20L
        val normal = """"halfHourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 10L
        val slow = """"hourFee":(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLong() ?: 5L
        FeeRates(slow, normal, fast)
    } catch (_: Exception) {
        FeeRates(5, 10, 20) // Giá trị mặc định nếu không lấy được
    }

    // LẤY LỊCH SỬ GIAO DỊCH
    fun getTransactions(): List<TransactionInfo> {
        if (!isReady()) return emptyList()
        return try {
            wallet!!.getTransactionsByTime().map { tx ->
                val value = tx.getValue(wallet).value / 1e8
                val type = if (value > 0) "Nhận" else "Gửi"
                TransactionInfo(
                    txId = tx.txId.toString(),
                    amount = abs(value),
                    time = tx.updateTime ?: Date(),
                    type = type
                )
            }.sortedByDescending { it.time }
        } catch (_: Exception) { emptyList() }
    }

    // Gửi BTC
    fun send(to: String, amount: Double, feeSatVb: Long): String = try {
        if (!isReady()) "Ví chưa sẵn sàng" else {
            // Tạo yêu cầu gửi
            val req = SendRequest.to(Address.fromString(params, to), Coin.valueOf((amount * 1e8).toLong()))
            // Đặt phí (chuyển từ sat/vB sang sat/kB)
            req.feePerKb = Coin.valueOf(feeSatVb * 1000)
            
            // Gửi giao dịch
            val res = wallet!!.sendCoins(kit!!.peerGroup(), req)
            res.broadcastComplete.get()
            
            "Đã gửi! TXID: ${res.tx.txId}"
        }
    } catch (e: Exception) {
        "Lỗi: ${e.message}"
    }
}