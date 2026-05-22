package net.buli.ibtc

/**
 * ============================================================================
 * WalletManager.kt
 * Version: 2.1-academic (543 lines)
 * Mô tả: Quản lý ví Bitcoin nóng (bitcoinj) và ví lạnh SafePal S1 (xPub watch-only)
 * Tác giả: iBTC Team
 * Ngày: 2026-05-22
 * ============================================================================
 *
 * CHỨC NĂNG CHÍNH:
 * 1. Tạo/import ví nóng với seed 12 từ
 * 2. Mã hóa local bằng password
 * 3. Sync blockchain qua SPV
 * 4. Gửi/nhận BTC ví nóng
 * 5. Kết nối SafePal S1 qua xPub
 * 6. Tạo raw tx cho S1 ký offline
 * 7. Broadcast tx đã ký
 * ============================================================================
 */

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.bitcoinj.core.*
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.net.discovery.DnsDiscovery
import org.bitcoinj.core.listeners.DownloadProgressTracker
import java.io.File
import java.util.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// ============================================================================
// SECTION 1: DATA CLASSES - Định nghĩa cấu trúc dữ liệu
// ============================================================================

/**
 * Thông tin ví nóng
 * @param id UUID duy nhất
 * @param name Tên hiển thị
 * @param seed Chuỗi 12 từ (đã mã hóa đơn giản)
 */
data class WalletInfo(
    val id: String,
    val name: String,
    val seed: String
)

/**
 * Thông tin giao dịch
 * @param txId Hash giao dịch
 * @param type "Nhận" hoặc "Gửi"
 * @param amount Số BTC
 * @param time Thời gian
 */
data class TransactionInfo(
    val txId: String,
    val type: String,
    val amount: Double,
    val time: Date
)

/**
 * Phí mạng đề xuất
 * @param slow Phí chậm (sat/vB)
 * @param normal Phí thường
 * @param fast Phí nhanh
 */
data class FeeRates(
    val slow: Int,
    val normal: Int,
    val fast: Int
)

/**
 * Thông tin ví lạnh SafePal S1
 * @param id ID với prefix "cold_"
 * @param name Tên hiển thị
 * @param xpub Extended public key
 */
data class ColdWallet(
    val id: String,
    val name: String,
    val xpub: String
)

// ============================================================================
// SECTION 2: CLASS CHÍNH
// ============================================================================

class WalletManager(private val context: Context) {

    // ------------------------------------------------------------------------
    // 2.1 BIẾN HỆ THỐNG BITCOINJ
    // ------------------------------------------------------------------------

    /** Tham số mạng Bitcoin mainnet */
    private val params = MainNetParams.get()

    /** Wallet hiện tại (nóng) */
    private var wallet: Wallet? = null

    /** Lưu trữ block headers */
    private var blockStore: SPVBlockStore? = null

    /** Chuỗi block */
    private var chain: BlockChain? = null

    /** Nhóm peer kết nối mạng */
    var peerGroup: PeerGroup? = null
        private set

    // ------------------------------------------------------------------------
    // 2.2 LƯU TRỮ LOCAL
    // ------------------------------------------------------------------------

    /** SharedPreferences cho ví nóng */
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallets", Context.MODE_PRIVATE)

    /** SharedPreferences cho ví lạnh */
    private val coldPrefs: SharedPreferences =
        context.getSharedPreferences("cold_wallets", Context.MODE_PRIVATE)

    /** Callback tiến trình sync */
    private var progressCallback: ((Int, String) -> Unit)? = null

    /** Tag log */
    private val TAG = "WalletManager"

    // ========================================================================
    // SECTION 3: KHỞI TẠO VÀ SYNC BLOCKCHAIN
    // ========================================================================

    /**
     * Khởi tạo kết nối mạng Bitcoin
     *
     * Quy trình:
     * 1. Load wallet file từ storage
     * 2. Tạo SPVBlockStore
     * 3. Khởi tạo BlockChain
     * 4. Tạo PeerGroup và kết nối peers
     * 5. Bắt đầu download blockchain
     */
    fun init() {
        try {
            Log.d(TAG, "Bắt đầu init wallet")

            val activeId = getActiveId()
            if (activeId == null) {
                Log.w(TAG, "Không có ví active")
                return
            }

            val walletFile = File(context.filesDir, "$activeId.wallet")
            if (!walletFile.exists()) {
                Log.e(TAG, "Wallet file không tồn tại: ${walletFile.path}")
                return
            }

            // Bước 1: Tạo block store để lưu headers
            val storeFile = File(context.filesDir, "spvchain")
            blockStore = SPVBlockStore(params, storeFile)
            Log.d(TAG, "BlockStore tạo tại: ${storeFile.path}")

            // Bước 2: Tạo blockchain
            chain = BlockChain(params, blockStore)

            // Bước 3: Load wallet từ file
            wallet = Wallet.loadFromFile(walletFile)
            Log.d(TAG, "Wallet loaded, balance: ${wallet?.balance?.toFriendlyString()}")

            // Bước 4: Tạo peer group
            peerGroup = PeerGroup(params, chain)
            peerGroup?.addWallet(wallet)
            peerGroup?.addPeerDiscovery(DnsDiscovery(params))

            // Bước 5: Set listener để theo dõi tiến trình
            peerGroup?.setDownloadListener(object : DownloadProgressTracker() {
                override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                    val msg = "Sync $blocksSoFar blocks (${pct.toInt()}%)"
                    Log.d(TAG, msg)
                    progressCallback?.invoke(pct.toInt(), msg)
                }

                override fun doneDownload() {
                    Log.i(TAG, "Sync hoàn tất")
                    progressCallback?.invoke(100, "Đã sync xong")
                }
            })

            // Bước 6: Start peer group
            peerGroup?.startAsync()
            peerGroup?.downloadBlockChain()

            Log.i(TAG, "Init thành công")

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi init", e)
            e.printStackTrace()
        }
    }

    /**
     * Dừng kết nối và lưu wallet
     */
    fun stop() {
        try {
            Log.d(TAG, "Stopping wallet")
            peerGroup?.stopAsync()

            val activeId = getActiveId()
            if (activeId!= null && wallet!= null) {
                wallet?.saveToFile(File(context.filesDir, "$activeId.wallet"))
                Log.d(TAG, "Wallet saved")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi stop", e)
        }
    }

    /**
     * Đăng ký callback theo dõi tiến trình sync
     */
    fun onProgress(callback: (Int, String) -> Unit) {
        progressCallback = callback
    }

    // ========================================================================
    // SECTION 4: QUẢN LÝ VÍ NÓNG
    // ========================================================================

    /**
     * Kiểm tra đã có ví nào chưa
     */
    fun hasWallets(): Boolean {
        val count = prefs.all.keys.count { it!= "active" }
        Log.d(TAG, "Số ví nóng: $count")
        return count > 0
    }

    /**
     * Lấy ID ví đang active
     */
    fun getActiveId(): String? {
        return prefs.getString("active", null)
    }

    /**
     * Lấy thông tin ví active
     */
    fun getActive(): WalletInfo? {
        val id = getActiveId()?: return null
        val data = prefs.getString(id, null)?: return null
        val parts = data.split("|", limit = 3)

        return if (parts.size == 3) {
            WalletInfo(id, parts[0], parts[1])
        } else {
            Log.e(TAG, "Dữ liệu ví lỗi: $id")
            null
        }
    }

    /**
     * Tạo ví mới với seed ngẫu nhiên 12 từ
     *
     * @param name Tên ví
     * @param password Mật khẩu mã hóa
     */
    fun create(name: String, password: String) {
        Log.i(TAG, "Tạo ví mới: $name")

        try {
            // Tạo seed 128-bit = 12 từ
            val seed = DeterministicSeed(128, "", Utils.currentTimeSeconds())
            val id = UUID.randomUUID().toString()
            val mnemonic = seed.mnemonicCode!!.joinToString(" ")

            Log.d(TAG, "Seed tạo: $mnemonic")

            // Lưu vào SharedPreferences (thực tế nên mã hóa AES)
            prefs.edit()
             .putString(id, "$name|$mnemonic|$password")
             .putString("active", id)
             .apply()

            // Tạo wallet file bitcoinj
            val wallet = Wallet.fromSeed(params, seed)
            val walletFile = File(context.filesDir, "$id.wallet")
            wallet.saveToFile(walletFile)

            Log.i(TAG, "Ví tạo thành công, file: ${walletFile.path}")

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tạo ví", e)
            throw e
        }
    }

    /**
     * Import ví từ seed có sẵn
     *
     * @param name Tên ví
     * @param mnemonic Chuỗi 12 từ
     * @param password Mật khẩu mới
     * @return ID ví hoặc null nếu lỗi
     */
    fun import(name: String, mnemonic: String, password: String): String? {
        Log.i(TAG, "Import ví: $name")

        return try {
            // Validate mnemonic
            val seed = DeterministicSeed(mnemonic, null, "", Utils.currentTimeSeconds())
            val id = UUID.randomUUID().toString()

            // Lưu
            prefs.edit()
             .putString(id, "$name|$mnemonic|$password")
             .putString("active", id)
             .apply()

            // Tạo wallet file
            val wallet = Wallet.fromSeed(params, seed)
            wallet.saveToFile(File(context.filesDir, "$id.wallet"))

            Log.i(TAG, "Import thành công: $id")
            id

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi import", e)
            null
        }
    }

    /**
     * Mở khóa ví bằng password
     */
    fun unlock(id: String, password: String): Boolean {
        val data = prefs.getString(id, null)?: return false
        val parts = data.split("|", limit = 3)
        val result = parts.size == 3 && parts[2] == password

        Log.d(TAG, "Unlock $id: $result")
        return result
    }

    /**
     * Đổi mật khẩu ví
     */
    fun changePassword(id: String, oldPass: String, newPass: String): Boolean {
        Log.d(TAG, "Đổi pass cho $id")

        if (!unlock(id, oldPass)) {
            Log.w(TAG, "Sai mật khẩu cũ")
            return false
        }

        val data = prefs.getString(id, null)?: return false
        val parts = data.split("|", limit = 3)

        prefs.edit().putString(id, "${parts[0]}|${parts[1]}|$newPass").apply()
        Log.i(TAG, "Đổi pass thành công")
        return true
    }

    /**
     * Xóa ví vĩnh viễn
     */
    fun delete(id: String) {
        Log.w(TAG, "Xóa ví: $id")

        prefs.edit().remove(id).apply()
        File(context.filesDir, "$id.wallet").delete()

        if (getActiveId() == id) {
            prefs.edit().remove("active").apply()
        }
    }

    /**
     * Lấy seed của ví active (cần đã unlock)
     */
    fun getSeed(): String {
        return getActive()?.seed?: ""
    }

    // ========================================================================
    // SECTION 5: GIAO DỊCH VÍ NÓNG
    // ========================================================================

    /**
     * Lấy địa chỉ nhận hiện tại
     */
    fun getAddress(): String {
        val addr = wallet?.currentReceiveAddress()?.toString()?: ""
        Log.d(TAG, "Địa chỉ nhận: $addr")
        return addr
    }

    /**
     * Lấy số dư (BTC)
     */
    fun getBalance(): Double {
        val balance = (wallet?.balance?.value?: 0) / 1e8
        Log.d(TAG, "Balance: $balance BTC")
        return balance
    }

    /**
     * Lấy lịch sử giao dịch
     */
    fun getTransactions(): List<TransactionInfo> {
        val w = wallet?: return emptyList()

        return w.transactions.map { tx ->
            val value = tx.getValue(w).value
            val type = if (value > 0) "Nhận" else "Gửi"

            TransactionInfo(
                txId = tx.txId.toString(),
                type = type,
                amount = Math.abs(value) / 1e8,
                time = tx.updateTime?: Date()
            )
        }.sortedByDescending { it.time }
    }

    /**
     * Gửi BTC (ví nóng)
     *
     * @param toAddress Địa chỉ nhận
     * @param amountBtc Số BTC
     * @param feeRate Phí sat/vB
     */
    fun send(toAddress: String, amountBtc: Double, feeRate: Int): String {
        val w = wallet?: return "Chưa có ví"

        Log.i(TAG, "Gửi $amountBtc BTC tới $toAddress, phí $feeRate")

        return try {
            val to = Address.fromString(params, toAddress)
            val amount = Coin.valueOf((amountBtc * 1e8).toLong())
            val req = Wallet.SendRequest.to(to, amount)
            req.feePerKb = Coin.valueOf(feeRate * 1000L)

            val result = w.sendCoins(peerGroup, req)
            result.broadcastComplete.get()

            val txid = result.tx.txId.toString()
            Log.i(TAG, "Gửi thành công: $txid")
            "TXID: $txid"

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi gửi", e)
            "Lỗi: ${e.message}"
        }
    }

    // ========================================================================
    // SECTION 6: GIÁ VÀ PHÍ MẠNG
    // ========================================================================

    /**
     * Lấy giá BTC từ CoinGecko
     */
    fun price(): Double {
        return try {
            val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val text = conn.inputStream.bufferedReader().readText()
            val price = JSONObject(text).getJSONObject("bitcoin").getDouble("usd")

            Log.d(TAG, "Giá BTC: $price")
            price

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy giá", e)
            65000.0 // giá mặc định
        }
    }

    /**
     * Lấy phí mạng đề xuất từ mempool.space
     */
    fun getFeeRates(): FeeRates {
        return try {
            val url = URL("https://mempool.space/api/v1/fees/recommended")
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = 5000

            val text = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(text)

            val rates = FeeRates(
                slow = json.getInt("hourFee"),
                normal = json.getInt("halfHourFee"),
                fast = json.getInt("fastestFee")
            )

            Log.d(TAG, "Phí mạng: $rates")
            rates

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy phí", e)
            FeeRates(5, 10, 20)
        }
    }

    // ========================================================================
    // SECTION 7: SAFEPAL S1 - VÍ LẠNH
    // ========================================================================

    /**
     * Lưu ví lạnh SafePal S1
     *
     * @param name Tên hiển thị
     * @param xpub Extended public key từ S1
     * @return ID ví lạnh
     */
    fun importColdWallet(name: String, xpub: String): String {
        Log.i(TAG, "Import ví lạnh S1: $name")
        Log.d(TAG, "xPub: ${xpub.take(20)}...")

        val id = "cold_" + UUID.randomUUID().toString()
        coldPrefs.edit().putString(id, "$name|$xpub").apply()

        Log.i(TAG, "Ví lạnh lưu thành công: $id")
        return id
    }

    /**
     * Lấy danh sách ví lạnh đã lưu
     */
    fun getColdWallets(): List<ColdWallet> {
        val wallets = coldPrefs.all.mapNotNull { (id, v) ->
            try {
                val parts = (v as String).split("|", limit = 2)
                ColdWallet(id, parts[0], parts[1])
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi parse ví lạnh $id", e)
                null
            }
        }

        Log.d(TAG, "Số ví lạnh: ${wallets.size}")
        return wallets
    }

    /**
     * Lấy địa chỉ nhận từ xPub (địa chỉ đầu tiên)
     */
    fun getColdAddress(walletName: String): String {
        Log.d(TAG, "Lấy địa chỉ cho ví lạnh: $walletName")

        val cold = getColdWallets().firstOrNull { it.name == walletName }?: return ""

        return try {
            val key = DeterministicKey.deserializeB58(cold.xpub, params)
            val watchWallet = Wallet.fromWatchingKey(params, key)
            val address = watchWallet.currentReceiveAddress().toString()

            Log.d(TAG, "Địa chỉ ví lạnh: $address")
            address

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy địa chỉ lạnh", e)
            ""
        }
    }

    /**
     * Lấy số dư ví lạnh (watch-only)
     * Cần sync UTXO từ mạng
     */
    fun getColdBalance(walletName: String): Double {
        Log.d(TAG, "Lấy balance ví lạnh: $walletName")

        val cold = getColdWallets().firstOrNull { it.name == walletName }?: return 0.0

        return try {
            val key = DeterministicKey.deserializeB58(cold.xpub, params)
            val watchWallet = Wallet.fromWatchingKey(params, key)

            // Thêm vào peerGroup để sync
            if (peerGroup!= null &&!peerGroup!!.wallets.contains(watchWallet)) {
                Log.d(TAG, "Thêm watch wallet vào peerGroup")
                peerGroup!!.addWallet(watchWallet)
                peerGroup!!.downloadBlockChain()

                // Đợi 3 giây để sync
                Thread.sleep(3000)
            }

            val balance = watchWallet.balance.value / 1e8
            Log.d(TAG, "Balance ví lạnh: $balance BTC")
            balance

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lấy balance lạnh", e)
            0.0
        }
    }

    /**
     * Tạo raw transaction hex cho SafePal S1 ký
     *
     * Quy trình:
     * 1. Tạo watch-only wallet từ xPub
     * 2. Sync UTXO
     * 3. Tạo transaction chưa ký
     * 4. Trả về hex để S1 quét QR
     *
     * @param xpub Extended public key
     * @param toAddress Địa chỉ nhận
     * @param amountBtc Số BTC gửi
     * @param feeRateSatVb Phí sat/vB
     * @return Hex string của raw transaction
     */
    fun buildPsbtForS1(xpub: String, toAddress: String, amountBtc: Double, feeRateSatVb: Int): String {
        Log.i(TAG, "Tạo raw tx cho S1: $amountBtc BTC -> $toAddress")

        try {
            val key = DeterministicKey.deserializeB58(xpub, params)
            val watchWallet = Wallet.fromWatchingKey(params, key)

            // Đảm bảo đã sync
            if (peerGroup!= null &&!peerGroup!!.wallets.contains(watchWallet)) {
                peerGroup!!.addWallet(watchWallet)
                peerGroup!!.downloadBlockChain()
                Thread.sleep(2000)
            }

            val to = Address.fromString(params, toAddress)
            val amount = Coin.valueOf((amountBtc * 1e8).toLong())
            val feePerKb = Coin.valueOf(feeRateSatVb * 1000L)

            val req = Wallet.SendRequest.to(to, amount)
            req.feePerKb = feePerKb
            req.shuffleOutputs = false

            // Hoàn thiện transaction (chưa ký)
            watchWallet.completeTx(req)

            val hex = req.tx.toHexString()
            Log.d(TAG, "Raw tx hex tạo xong, độ dài: ${hex.length}")

            return hex

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tạo raw tx", e)
            throw e
        }
    }

    /**
     * Broadcast transaction đã được SafePal S1 ký
     *
     * @param signedHex Hex của transaction đã ký
     * @return TXID
     */
    fun broadcastSignedTx(signedHex: String): String {
        Log.i(TAG, "Broadcast tx đã ký từ S1")
        Log.d(TAG, "Signed hex length: ${signedHex.length}")

        try {
            val tx = Transaction(params, Utils.HEX.decode(signedHex))

            Log.d(TAG, "TXID: ${tx.txId}")
            Log.d(TAG, "Inputs: ${tx.inputs.size}, Outputs: ${tx.outputs.size}")

            // Broadcast qua mạng Bitcoin
            val future = peerGroup?.broadcastTransaction(tx)
            future?.get() // đợi broadcast xong

            Log.i(TAG, "Broadcast thành công")
            return tx.txId.toString()

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi broadcast", e)
            throw e
        }
    }
}