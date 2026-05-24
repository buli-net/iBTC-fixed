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

class MainActivity : AppCompatActivity() {

    private lateinit var tvBalance: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private lateinit var btnCopy: ImageButton
    private lateinit var btnReceive: Button
    private lateinit var btnSend: Button
    private lateinit var lvTransactions: ListView
    private lateinit var progressSync: ProgressBar

    private lateinit var walletManager: WalletManager
    private val txList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        tvStatus.text = "Đang khởi tạo ví..."
        progressSync.visibility = ProgressBar.VISIBLE
        tvAddress.text = "Đang tạo..."

        walletManager = WalletManager(this)

        walletManager.onBalanceChanged = { balance ->
            runOnUiThread {
                tvBalance.text = formatBTC(balance) // CHUẨN 8 SỐ
                tvStatus.text = "Hoạt động"
                progressSync.visibility = ProgressBar.GONE
            }
        }

        walletManager.onTransaction = { tx ->
            runOnUiThread { addTransactionToList(tx) }
        }

        Thread {
            try {
                walletManager.startWallet()
                Thread.sleep(6000)
                runOnUiThread {
                    val addr = walletManager.getReceiveAddress()
                    tvAddress.text = addr
                    tvStatus.text = if (addr.startsWith("1") || addr.startsWith("3") || addr.startsWith("bc1")) "Sẵn sàng" else "Đang tạo ví..."
                    progressSync.visibility = ProgressBar.GONE
                    try { walletManager.getTransactions().forEach { addTransactionToList(it) } } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Lỗi khởi tạo"
                    tvAddress.text = "Lỗi"
                    progressSync.visibility = ProgressBar.GONE
                }
            }
        }.start()

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("BTC", tvAddress.text))
            Toast.makeText(this, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
        }

        btnReceive.setOnClickListener { showReceiveDialog() }
        btnSend.setOnClickListener { showSendDialog() }
    }

    // HÀM CHUẨN BITCOIN - tối đa 8 số, bỏ 0 thừa
    private fun formatBTC(coin: Coin): String {
        // toPlainString() của bitcoinj đã trả về đúng chuẩn (vd: "0.00000001")
        return "${coin.toPlainString()} BTC"
    }

    private fun showReceiveDialog() { /* giữ nguyên */ 
        try {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_receive, null)
            val tvAddr = view.findViewById<TextView>(R.id.tvReceiveAddr)
            tvAddr.text = walletManager.getReceiveAddress()
            AlertDialog.Builder(this).setTitle("Nhận BTC").setView(view)
                .setPositiveButton("Copy") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BTC", tvAddr.text))
                }.show()
        } catch (_: Exception) {}
    }

    private fun showSendDialog() { /* giữ nguyên */ 
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50,40,50,10) }
        val etAddress = EditText(this).apply { hint = "Địa chỉ BTC" }
        val etAmount = EditText(this).apply { hint = "Số BTC"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        layout.addView(etAddress); layout.addView(etAmount)
        AlertDialog.Builder(this).setTitle("Gửi BTC").setView(layout)
            .setPositiveButton("Gửi") { _, _ ->
                val addr = etAddress.text.toString().trim()
                val amt = etAmount.text.toString().toDoubleOrNull()
                if (addr.isNotEmpty() && amt != null) {
                    Thread { try { walletManager.sendCoins(addr, amt) } catch (_: Exception) {} }.start()
                }
            }.setNegativeButton("Hủy", null).show()
    }

    private fun addTransactionToList(tx: Transaction) {
        try {
            val date = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(tx.updateTime?.time ?: System.currentTimeMillis()))
            val valueCoin = try { walletManager.getTxValue(tx) } catch (_: Exception) { Coin.ZERO }
            val valueStr = valueCoin.toPlainString() // CHUẨN
            val type = if (valueCoin.isPositive) "Nhận" else "Gửi"
            txList.add(0, "$date - $type $valueStr BTC")
            adapter.notifyDataSetChanged()
        } catch (_: Exception) {}
    }

    override fun onDestroy() { super.onDestroy(); try { walletManager.stopWallet() } catch (_: Exception) {} }
}