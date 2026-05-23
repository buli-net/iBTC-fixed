package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import java.text.SimpleDateFormat
import java.util.*

/**
 * MainActivity - Giao diện chính của ví IBTC
 * Đây là Activity đầu tiên chạy khi mở app
 */
class MainActivity : AppCompatActivity() {

    // ===== KHAI BÁO VIEW =====
    private lateinit var tvBalance: TextView      // Hiển thị số dư BTC
    private lateinit var tvStatus: TextView       // Trạng thái đồng bộ
    private lateinit var tvAddress: TextView      // Địa chỉ ví
    private lateinit var btnCopy: ImageButton     // Nút copy địa chỉ
    private lateinit var btnReceive: Button       // Nút nhận
    private lateinit var btnSend: Button          // Nút gửi
    private lateinit var lvTransactions: ListView // Danh sách giao dịch
    private lateinit var progressSync: ProgressBar // Thanh tiến trình sync

    // ===== QUẢN LÝ VÍ =====
    private lateinit var walletManager: WalletManager

    // Adapter cho ListView lịch sử
    private val txList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. ÁNH XẠ VIEW TỪ XML
        tvBalance = findViewById(R.id.tvBalance)
        tvStatus = findViewById(R.id.tvStatus)
        tvAddress = findViewById(R.id.tvAddress)
        btnCopy = findViewById(R.id.btnCopy)
        btnReceive = findViewById(R.id.btnReceive)
        btnSend = findViewById(R.id.btnSend)
        lvTransactions = findViewById(R.id.lvTransactions)
        progressSync = findViewById(R.id.progressSync)

        // Thiết lập adapter cho danh sách giao dịch
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, txList)
        lvTransactions.adapter = adapter

        // 2. KHỞI TẠO VÍ
        tvStatus.text = "Đang khởi tạo ví..."
        progressSync.visibility = ProgressBar.VISIBLE

        walletManager = WalletManager(this)

        // 3. CALLBACK KHI SỐ DƯ THAY ĐỔI
        // Được gọi từ WalletManager khi có coin đến hoặc đi
        walletManager.onBalanceChanged = { balance ->
            runOnUiThread {
                // toFriendlyString() trả về dạng "0.00123456"
                tvBalance.text = "${balance.toFriendlyString()} BTC"
                tvStatus.text = "Đã đồng bộ"
                progressSync.visibility = ProgressBar.GONE
            }
        }

        // 4. CALLBACK KHI CÓ GIAO DỊCH MỚI
        walletManager.onTransaction = { tx ->
            runOnUiThread {
                addTransactionToList(tx)
            }
        }

        // 5. START VÍ TRONG THREAD RIÊNG (tránh lag UI)
        Thread {
            walletManager.startWallet()
            runOnUiThread {
                // Lấy địa chỉ nhận sau khi ví sẵn sàng
                tvAddress.text = walletManager.getReceiveAddress()
                // Load lịch sử cũ
                walletManager.getTransactions().forEach { addTransactionToList(it) }
            }
        }.start()

        // 6. NÚT COPY ĐỊA CHỈ
        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BTC Address", tvAddress.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
        }

        // 7. NÚT NHẬN - HIỆN DIALOG
        btnReceive.setOnClickListener {
            showReceiveDialog()
        }

        // 8. NÚT GỬI - HIỆN DIALOG
        btnSend.setOnClickListener {
            showSendDialog()
        }
    }

    /**
     * Hiện dialog địa chỉ nhận BTC
     */
    private fun showReceiveDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_receive, null)
        val tvAddr = view.findViewById<TextView>(R.id.tvReceiveAddr)
        tvAddr.text = walletManager.getReceiveAddress()

        AlertDialog.Builder(this)
            .setTitle("Nhận BTC")
            .setView(view)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("BTC", tvAddr.text))
                Toast.makeText(this, "Đã copy", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /**
     * Hiện dialog gửi BTC
     */
    private fun showSendDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val etAddress = EditText(this).apply { 
            hint = "Địa chỉ BTC (1..., 3... hoặc bc1...)" 
        }
        val etAmount = EditText(this).apply { 
            hint = "Số lượng BTC (vd: 0.001)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL 
        }
        layout.addView(etAddress)
        layout.addView(etAmount)

        AlertDialog.Builder(this)
            .setTitle("Gửi BTC")
            .setView(layout)
            .setPositiveButton("Gửi") { _, _ ->
                val addr = etAddress.text.toString().trim()
                val amt = etAmount.text.toString().toDoubleOrNull()
                if (addr.isNotEmpty() && amt != null && amt > 0) {
                    Thread {
                        try {
                            val txId = walletManager.sendCoins(addr, amt)
                            runOnUiThread {
                                Toast.makeText(this, "Đã gửi! TXID: $txId", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                } else {
                    Toast.makeText(this, "Nhập sai địa chỉ hoặc số lượng", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * THÊM GIAO DỊCH VÀO LIST - ĐÃ FIX LỖI TransactionBag
     * 
     * LỖI CŨ (dòng 170-171): 
     *   tx.getValue(walletManager.getBalance()) -> sai kiểu, getValue() cần Wallet, không phải Coin
     * 
     * FIX MỚI:
     *   Dùng walletManager.getTxValue(tx) - hàm này bên trong đã truyền đúng Wallet vào tx.getValue()
     */
    private fun addTransactionToList(tx: Transaction) {
        // Format thời gian
        val date = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            .format(Date(tx.updateTime?.time ?: System.currentTimeMillis()))
        
        // FIX QUAN TRỌNG: lấy giá trị giao dịch đúng cách
        // getTxValue() trả về Coin (dương = nhận, âm = gửi)
        val valueCoin: Coin = walletManager.getTxValue(tx)
        val value = valueCoin.toFriendlyString()
        
        // Xác định loại giao dịch
        val type = if (valueCoin.isPositive) "Nhận" else "Gửi"
        
        // Thêm vào đầu danh sách
        txList.add(0, "$date - $type $value BTC")
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dừng ví khi đóng app để không tốn pin
        walletManager.stopWallet()
    }
}