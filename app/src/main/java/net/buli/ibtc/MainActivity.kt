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
 */
class MainActivity : AppCompatActivity() {

    // Khai báo view
    private lateinit var tvBalance: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private lateinit var btnCopy: ImageButton
    private lateinit var btnReceive: Button
    private lateinit var btnSend: Button
    private lateinit var lvTransactions: ListView
    private lateinit var progressSync: ProgressBar

    // Wallet manager
    private lateinit var walletManager: WalletManager

    // Adapter cho list giao dịch
    private val txList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Ánh xạ view từ XML
        tvBalance = findViewById(R.id.tvBalance)
        tvStatus = findViewById(R.id.tvStatus)
        tvAddress = findViewById(R.id.tvAddress)
        btnCopy = findViewById(R.id.btnCopy)
        btnReceive = findViewById(R.id.btnReceive)
        btnSend = findViewById(R.id.btnSend)
        lvTransactions = findViewById(R.id.lvTransactions)
        progressSync = findViewById(R.id.progressSync)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, txList)
        lvTransactions.adapter = adapter

        // 2. Khởi tạo ví
        tvStatus.text = "Đang khởi tạo ví..."
        progressSync.visibility = ProgressBar.VISIBLE

        walletManager = WalletManager(this)

        // 3. Thiết lập callback khi balance thay đổi
        walletManager.onBalanceChanged = { balance ->
            runOnUiThread {
                // Hiển thị BTC với 8 số thập phân
                tvBalance.text = "${balance.toFriendlyString()} BTC"
                tvStatus.text = "Đã đồng bộ"
                progressSync.visibility = ProgressBar.GONE
            }
        }

        walletManager.onTransaction = { tx ->
            runOnUiThread {
                addTransactionToList(tx)
            }
        }

        // 4. Start ví trong thread riêng để không lag UI
        Thread {
            walletManager.startWallet()
            runOnUiThread {
                tvAddress.text = walletManager.getReceiveAddress()
                // Load lịch sử cũ
                walletManager.getTransactions().forEach { addTransactionToList(it) }
            }
        }.start()

        // 5. Nút copy địa chỉ
        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BTC Address", tvAddress.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
        }

        // 6. Nút Nhận - hiện dialog
        btnReceive.setOnClickListener {
            showReceiveDialog()
        }

        // 7. Nút Gửi - hiện dialog nhập
        btnSend.setOnClickListener {
            showSendDialog()
        }
    }

    /**
     * Hiện dialog địa chỉ nhận
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
        val etAddress = EditText(this).apply { hint = "Địa chỉ BTC (bắt đầu bằng 1, 3 hoặc bc1)" }
        val etAmount = EditText(this).apply { hint = "Số lượng BTC (vd: 0.001)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
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
     * Thêm giao dịch vào list
     */
    private fun addTransactionToList(tx: Transaction) {
        val date = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(tx.updateTime?.time ?: System.currentTimeMillis()))
        val value = tx.getValue(walletManager.getBalance()).toFriendlyString()
        val type = if (tx.getValue(walletManager.getBalance()).isPositive) "Nhận" else "Gửi"
        txList.add(0, "$date - $type $value BTC")
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        walletManager.stopWallet()
    }
}