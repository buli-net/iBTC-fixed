package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Views SafePal
    private lateinit var tvBalance: TextView
    private lateinit var tvBalanceUsd: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private lateinit var btnReceive: LinearLayout
    private lateinit var btnSend: LinearLayout
    private lateinit var lvTokens: ListView

    private lateinit var walletManager: WalletManager
    private val tokenList = mutableListOf<TokenItem>()
    private lateinit var tokenAdapter: TokenAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ánh xạ ID mới
        tvBalance = findViewById(R.id.tvBalance)
        tvBalanceUsd = findViewById(R.id.tvBalanceUsd)
        tvStatus = findViewById(R.id.tvStatus)
        tvAddress = findViewById(R.id.tvAddress)
        btnReceive = findViewById(R.id.btnReceive)
        btnSend = findViewById(R.id.btnSend)
        lvTokens = findViewById(R.id.lvTokens)

        // Adapter cho list token
        tokenAdapter = TokenAdapter(this, tokenList)
        lvTokens.adapter = tokenAdapter

        tvStatus.text = "Đang khởi tạo..."
        tvAddress.text = "Đang tạo ví..."

        walletManager = WalletManager(this)

        // Cập nhật số dư - chuẩn Bitcoin 8 số
        walletManager.onBalanceChanged = { balance ->
            runOnUiThread {
                tvBalance.text = formatBTC(balance)
                tvBalanceUsd.text = "≈ $0.00" // sau này thêm giá live
                tvStatus.text = "Sẵn sàng"
                updateTokenList(balance)
            }
        }

        walletManager.onTransaction = { tx ->
            runOnUiThread {
                // có thể refresh list ở đây
            }
        }

        // Khởi động ví
        Thread {
            try {
                walletManager.startWallet()
                Thread.sleep(5000)
                runOnUiThread {
                    val addr = walletManager.getReceiveAddress()
                    tvAddress.text = addr
                    tvAddress.setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("BTC", addr))
                        Toast.makeText(this, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Lỗi khởi tạo"
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        btnReceive.setOnClickListener { showReceiveDialog() }
        btnSend.setOnClickListener { showSendDialog() }
    }

    // Format chuẩn Bitcoin: tối đa 8 số, bỏ 0 thừa
    private fun formatBTC(coin: Coin): String {
        return "${coin.toPlainString()} BTC"
    }

    private fun updateTokenList(balance: Coin) {
        tokenList.clear()
        tokenList.add(
            TokenItem(
                name = "Bitcoin",
                symbol = "BTC",
                amount = formatBTC(balance),
                usd = "$0.00"
            )
        )
        tokenAdapter.notifyDataSetChanged()
    }

    private fun showReceiveDialog() {
        try {
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
        } catch (e: Exception) {
            Toast.makeText(this, "Ví chưa sẵn sàng", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSendDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val etAddress = EditText(this).apply { hint = "Địa chỉ BTC" }
        val etAmount = EditText(this).apply {
            hint = "Số BTC"
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
                if (addr.isNotEmpty() && amt!= null && amt > 0) {
                    tvStatus.text = "Đang gửi..."
                    Thread {
                        try {
                            walletManager.sendCoins(addr, amt)
                            runOnUiThread { tvStatus.text = "Đã gửi" }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                                tvStatus.text = "Lỗi"
                            }
                        }
                    }.start()
                }
            }
           .setNegativeButton("Hủy", null)
           .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { walletManager.stopWallet() } catch (_: Exception) {}
    }

    // Data class cho token
    data class TokenItem(val name: String, val symbol: String, val amount: String, val usd: String)

    // Adapter cho ListView SafePal
    inner class TokenAdapter(private val context: Context, private val items: List<TokenItem>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView?: LayoutInflater.from(context).inflate(R.layout.item_token, parent, false)
            val item = items[position]
            view.findViewById<TextView>(R.id.tvName).text = item.name
            view.findViewById<TextView>(R.id.tvSymbol).text = item.symbol
            view.findViewById<TextView>(R.id.tvAmount).text = item.amount
            view.findViewById<TextView>(R.id.tvUsd).text = item.usd
            return view
        }
    }
}