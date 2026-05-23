package net.buli.ibtc

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.core.Coin
import java.text.DecimalFormat

/**
 * MainActivity v4.1 - Full version
 * Build: 2025-05-22
 * Features: Create/Restore wallet, Send/Receive BTC, SPV sync
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity_v41"
    private lateinit var walletManager: WalletManager
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L // 5 giây

    // UI Components
    private lateinit var tvBalance: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvSyncProgress: TextView
    private lateinit var btnCreateWallet: Button
    private lateinit var btnRestoreWallet: Button
    private lateinit var btnSend: Button
    private lateinit var btnReceive: Button
    private lateinit var btnCopyAddress: Button
    private lateinit var btnRefresh: Button
    private lateinit var etSendAddress: EditText
    private lateinit var etSendAmount: EditText
    private lateinit var etMnemonic: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutWallet: LinearLayout
    private lateinit var layoutNoWallet: LinearLayout
    private lateinit var layoutSend: LinearLayout

    private val satoshiFormat = DecimalFormat("#,###")
    private val btcFormat = DecimalFormat("0.########")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate START")
        
        // Tạo UI bằng code (không dùng XML)
        createUI()
        
        // Khởi tạo WalletManager
        walletManager = WalletManager(filesDir)
        
        // Setup listeners
        setupListeners()
        
        // Kiểm tra ví đã tồn tại chưa
        checkWalletExists()
        
        // Bắt đầu update định kỳ
        startPeriodicUpdate()
        
        Log.d(TAG, "onCreate COMPLETED")
    }

    private fun createUI() {
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val tvTitle = TextView(this).apply {
            text = "iBTC Wallet v4.1"
            textSize = 24f
            setTextColor(0xFFFF9800.toInt())
            setPadding(0, 0, 24)
        }
        mainLayout.addView(tvTitle)

        // Status
        tvStatus = TextView(this).apply {
            text = "Đang khởi tạo..."
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        mainLayout.addView(tvStatus)

        // Sync Progress
        tvSyncProgress = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(0xFF757575.toInt())
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(tvSyncProgress)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        mainLayout.addView(progressBar)

        // Layout No Wallet
        layoutNoWallet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }

        btnCreateWallet = Button(this).apply { text = "Tạo ví mới" }
        layoutNoWallet.addView(btnCreateWallet)

        val tvOr = TextView(this).apply {
            text = "HOẶC"
            textSize = 12f
            setPadding(0, 16, 0, 16)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        layoutNoWallet.addView(tvOr)

        etMnemonic = EditText(this).apply {
            hint = "Nhập 12 từ khôi phục (cách nhau bởi dấu cách)"
            minLines = 3
            setPadding(16, 16, 16, 16)
        }
        layoutNoWallet.addView(etMnemonic)

        btnRestoreWallet = Button(this).apply { text = "Khôi phục ví" }
        layoutNoWallet.addView(btnRestoreWallet)

        mainLayout.addView(layoutNoWallet)

        // Layout Wallet
        layoutWallet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        // Balance
        val tvBalanceLabel = TextView(this).apply {
            text = "Số dư:"
            textSize = 14f
            setTextColor(0xFF757575.toInt())
        }
        layoutWallet.addView(tvBalanceLabel)

        tvBalance = TextView(this).apply {
            text = "0.00000000 BTC"
            textSize = 28f
            setTextColor(0xFF4CAF50.toInt())
            setPadding(0, 0, 0, 16)
        }
        layoutWallet.addView(tvBalance)

        // Address
        val tvAddressLabel = TextView(this).apply {
            text = "Địa chỉ nhận:"
            textSize = 14f
            setTextColor(0xFF757575.toInt())
        }
        layoutWallet.addView(tvAddressLabel)

        tvAddress = TextView(this).apply {
            text = ""
            textSize = 12f
            setPadding(0, 0, 0, 8)
            setTextIsSelectable(true)
        }
        layoutWallet.addView(tvAddress)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        btnCopyAddress = Button(this).apply {
            text = "Copy"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnRefresh = Button(this).apply {
            text = "Làm mới"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        buttonLayout.addView(btnCopyAddress)
        buttonLayout.addView(btnRefresh)
        layoutWallet.addView(buttonLayout)

        // Send/Receive buttons
        val actionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }
        btnSend = Button(this).apply {
            text = "Gửi BTC"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnReceive = Button(this).apply {
            text = "Nhận BTC"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        actionLayout.addView(btnSend)
        actionLayout.addView(btnReceive)
        layoutWallet.addView(actionLayout)

        // Layout Send
        layoutSend = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 24, 0, 0)
        }

        val tvSendLabel = TextView(this).apply {
            text = "Gửi Bitcoin"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        layoutSend.addView(tvSendLabel)

        etSendAddress = EditText(this).apply {
            hint = "Địa chỉ nhận BTC"
            setPadding(16, 16, 16, 16)
        }
        layoutSend.addView(etSendAddress)

        etSendAmount = EditText(this).apply {
            hint = "Số lượng (satoshi)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(16, 16, 16, 16)
        }
        layoutSend.addView(etSendAmount)

        val btnConfirmSend = Button(this).apply {
            text = "Xác nhận gửi"
            setOnClickListener { confirmSend() }
        }
        layoutSend.addView(btnConfirmSend)

        layoutWallet.addView(layoutSend)
        mainLayout.addView(layoutWallet)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun setupListeners() {
        btnCreateWallet.setOnClickListener { createWallet() }
        btnRestoreWallet.setOnClickListener { restoreWallet() }
        btnCopyAddress.setOnClickListener { copyAddress() }
        btnRefresh.setOnClickListener { updateUI() }
        btnSend.setOnClickListener { toggleSendLayout() }
        btnReceive.setOnClickListener { showReceiveDialog() }

        walletManager.setProgressListener { pct, blocks, date ->
            runOnUiThread {
                tvSyncProgress.text = "Đồng bộ: ${String.format("%.1f", pct)}% ($blocks blocks)"
                progressBar.progress = pct.toInt()
                progressBar.visibility = if (pct < 100) View.VISIBLE else View.GONE
                tvStatus.text = if (pct < 100) "Đang đồng bộ..." else "Đã đồng bộ"
            }
        }
    }

    private fun checkWalletExists() {
        // Đơn giản: luôn hiển thị tạo ví lần đầu
        // Trong thực tế sẽ check file wallet
        layoutNoWallet.visibility = View.VISIBLE
        layoutWallet.visibility = View.GONE
        tvStatus.text = "Chưa có ví"
    }

    private fun createWallet() {
        Log.d(TAG, "createWallet clicked")
        try {
            btnCreateWallet.isEnabled = false
            tvStatus.text = "Đang tạo ví..."
            
            Thread {
                try {
                    val mnemonic = walletManager.createNewWallet()
                    runOnUiThread {
                        showMnemonicDialog(mnemonic)
                        layoutNoWallet.visibility = View.GONE
                        layoutWallet.visibility = View.VISIBLE
                        updateUI()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showError("Lỗi tạo ví: ${e.message}")
                        btnCreateWallet.isEnabled = true
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            showError("Lỗi: ${e.message}")
            btnCreateWallet.isEnabled = true
        }
    }

    private fun restoreWallet() {
        val mnemonicStr = etMnemonic.text.toString().trim()
        if (mnemonicStr.isEmpty()) {
            showError("Vui lòng nhập 12 từ khôi phục")
            return
        }
        
        val words = mnemonicStr.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.size != 12) {
            showError("Phải nhập đúng 12 từ (hiện tại: ${words.size})")
            return
        }

        try {
            btnRestoreWallet.isEnabled = false
            tvStatus.text = "Đang khôi phục..."
            
            Thread {
                try {
                    walletManager.restoreWallet(words)
                    runOnUiThread {
                        layoutNoWallet.visibility = View.GONE
                        layoutWallet.visibility = View.VISIBLE
                        updateUI()
                        Toast.makeText(this, "Khôi phục thành công", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showError("Lỗi khôi phục: ${e.message}")
                        btnRestoreWallet.isEnabled = true
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            showError("Lỗi: ${e.message}")
            btnRestoreWallet.isEnabled = true
        }
    }

    private fun showMnemonicDialog(mnemonic: List<String>) {
        val mnemonicStr = mnemonic.joinToString(" ")
        AlertDialog.Builder(this)
            .setTitle("LƯU LẠI 12 TỪ NÀY")
            .setMessage("Đây là chìa khóa ví của bạn. Mất là mất hết!\n\n$mnemonicStr")
            .setPositiveButton("Đã lưu") { _, _ ->
                copyToClipboard("Mnemonic", mnemonicStr)
                Toast.makeText(this, "Đã copy vào clipboard", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateUI() {
        if (!walletManager.isWalletReady()) return
        
        try {
            val balance = walletManager.getBalance()
            val balanceSatoshi = balance.value
            val balanceBTC = balanceSatoshi / 100_000_000.0
            
            tvBalance.text = "${btcFormat.format(balanceBTC)} BTC\n(${satoshiFormat.format(balanceSatoshi)} sat)"
            
            val address = walletManager.getAddress()
            tvAddress.text = address
            
            tvStatus.text = if (walletManager.isSyncing()) "Đang đồng bộ" else "Sẵn sàng"
            
        } catch (e: Exception) {
            Log.e(TAG, "updateUI error", e)
        }
    }

    private fun toggleSendLayout() {
        layoutSend.visibility = if (layoutSend.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun confirmSend() {
        val address = etSendAddress.text.toString().trim()
        val amountStr = etSendAmount.text.toString().trim()
        
        if (address.isEmpty() || amountStr.isEmpty()) {
            showError("Nhập đầy đủ địa chỉ và số lượng")
            return
        }
        
        val amount = amountStr.toLongOrNull()
        if (amount == null || amount <= 0) {
            showError("Số lượng không hợp lệ")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Xác nhận gửi")
            .setMessage("Gửi $amount satoshi\nĐến: $address")
            .setPositiveButton("Gửi") { _, _ -> performSend(address, amount) }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun performSend(address: String, amount: Long) {
        Thread {
            try {
                runOnUiThread { tvStatus.text = "Đang gửi..." }
                val txId = walletManager.send(address, amount)
                runOnUiThread {
                    showSuccess("Đã gửi!\nTxID: $txId")
                    etSendAddress.text.clear()
                    etSendAmount.text.clear()
                    layoutSend.visibility = View.GONE
                    updateUI()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("Gửi thất bại: ${e.message}")
                }
            }
        }.start()
    }

    private fun showReceiveDialog() {
        val address = walletManager.getAddress()
        AlertDialog.Builder(this)
            .setTitle("Địa chỉ nhận BTC")
            .setMessage(address)
            .setPositiveButton("Copy") { _, _ -> copyToClipboard("Address", address) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun copyAddress() {
        val address = tvAddress.text.toString()
        if (address.isNotEmpty()) {
            copyToClipboard("Address", address)
            Toast.makeText(this, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Lỗi")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSuccess(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Thành công")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startPeriodicUpdate() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (walletManager.isWalletReady()) {
                    updateUI()
                }
                handler.postDelayed(this, updateInterval)
            }
        }, updateInterval)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        walletManager.stop()
        Log.d(TAG, "onDestroy")
    }
}