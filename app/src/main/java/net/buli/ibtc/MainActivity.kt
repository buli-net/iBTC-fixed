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

        walletManager = WalletManager(this)

        walletManager.onBalanceChanged = { balance ->
            runOnUiThread {
                tvBalance.text = "${balance.toFriendlyString()} BTC"
                tvStatus.text = "Đã đồng bộ"
                progressSync.visibility = ProgressBar.GONE
            }
        }

        walletManager.onTransaction = { tx ->
            runOnUiThread { addTransactionToList(tx) }
        }

        Thread {
            try {
                walletManager.startWallet()
                Thread.sleep(3000)
                runOnUiThread {
                    tvAddress.text = walletManager.getReceiveAddress()
                    walletManager.getTransactions().forEach { addTransactionToList(it) }
                    tvStatus.text = "Đang đồng bộ..."
                    progressSync.visibility = ProgressBar.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Lỗi khởi tạo"
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("BTC", tvAddress.text))
            Toast.makeText(this, "Đã copy", Toast.LENGTH_SHORT).show()
        }
        btnReceive.setOnClickListener { showReceiveDialog() }
        btnSend.setOnClickListener { showSendDialog() }
    }

    private fun showReceiveDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_receive, null)
        view.findViewById<TextView>(R.id.tvReceiveAddr).text = walletManager.getReceiveAddress()
        AlertDialog.Builder(this).setTitle("Nhận BTC").setView(view)
            .setPositiveButton("Copy", null).show()
    }

    private fun showSendDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50,40,50,10) }
        val etAddress = EditText(this).apply { hint = "Địa chỉ BTC" }
        val etAmount = EditText(this).apply { hint = "Số BTC"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        layout.addView(etAddress); layout.addView(etAmount)
        AlertDialog.Builder(this).setTitle("Gửi BTC").setView(layout)
            .setPositiveButton("Gửi") { _, _ ->
                val addr = etAddress.text.toString(); val amt = etAmount.text.toString().toDoubleOrNull()
                if (addr.isNotEmpty() && amt != null) {
                    Thread {
                        try { walletManager.sendCoins(addr, amt) } catch (e: Exception) {}
                    }.start()
                }
            }.setNegativeButton("Hủy", null).show()
    }

    private fun addTransactionToList(tx: Transaction) {
        val date = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(tx.updateTime?.time ?: System.currentTimeMillis()))
        val valueCoin = try { walletManager.getTxValue(tx) } catch (e: Exception) { Coin.ZERO }
        val type = if (valueCoin.isPositive) "Nhận" else "Gửi"
        txList.add(0, "$date - $type ${valueCoin.toFriendlyString()} BTC")
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() { super.onDestroy(); walletManager.stopWallet() }
}