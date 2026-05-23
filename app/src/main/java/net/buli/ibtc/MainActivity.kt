package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.setMargins
import androidx.core.view.setPadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // ==================== WALLET MANAGER ====================
    private lateinit var walletManager: WalletManager
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ==================== UI COMPONENTS ====================
    private lateinit var toolbar: Toolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var scrollView: ScrollView
    private lateinit var mainContainer: LinearLayout
    
    // Sync Status
    private lateinit var layoutSyncStatus: LinearLayout
    private lateinit var tvSyncStatusText: TextView
    private lateinit var tvSyncBlocks: TextView
    private lateinit var progressBarSync: ProgressBar
    
    // Balance Card
    private lateinit var cardBalance: LinearLayout
    private lateinit var tvBalanceBtcLabel: TextView
    private lateinit var tvBalanceBtc: TextView
    private lateinit var tvBalanceUsd: TextView
    private lateinit var tvBalanceSats: TextView
    private lateinit var tvPriceInfo: TextView
    
    // Address Card
    private lateinit var cardAddress: LinearLayout
    private lateinit var tvAddressLabel: TextView
    private lateinit var tvCurrentAddress: TextView
    private lateinit var imageViewQrCode: ImageView
    private lateinit var layoutAddressButtons: LinearLayout
    private lateinit var btnCopyAddress: Button
    private lateinit var btnShareAddress: Button
    private lateinit var btnNewAddress: Button
    private lateinit var btnShowQr: Button
    
    // Wallet Actions
    private lateinit var layoutWalletActions: LinearLayout
    private lateinit var btnCreateNewWallet: Button
    private lateinit var btnRestoreWallet: Button
    private lateinit var btnBackupWallet: Button
    private lateinit var btnViewSeed: Button
    
    // Send Section
    private lateinit var cardSend: LinearLayout
    private lateinit var tvSendTitle: TextView
    private lateinit var etRecipientAddress: EditText
    private lateinit var etAmountSatoshi: EditText
    private lateinit var etAmountBtc: EditText
    private lateinit var tvFeeEstimate: TextView
    private lateinit var btnPasteAddress: Button
    private lateinit var btnScanQr: Button
    private lateinit var btnMaxAmount: Button
    private lateinit var btnSendBitcoin: Button
    
    // Transactions
    private lateinit var tvTransactionsHeader: TextView
    private lateinit var layoutTransactionsList: LinearLayout
    private lateinit var tvEmptyTransactions: TextView
    private lateinit var btnViewAllTransactions: Button
    
    // Footer Info
    private lateinit var tvWalletInfo: TextView
    private lateinit var tvNetworkStatus: TextView

    // ==================== FORMATTING ====================
    private val btcDecimalFormat = DecimalFormat("0.########")
    private val satsFormat = NumberFormat.getIntegerInstance(Locale.US)
    private val usdCurrencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    
    // ==================== STATE ====================
    private var currentBitcoinPrice = 67850.0
    private var isQrVisible = false
    private var isWalletInitialized = false

    // ==================== LIFECYCLE ====================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createFullUserInterface()
        setupToolbar()
        initializeWalletManager()
        setupClickListeners()
        startPriceUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        ioScope.cancel()
        if (::walletManager.isInitialized) {
            walletManager.stop()
        }
    }

    // ==================== UI CREATION - FULL ====================
    private fun createFullUserInterface() {
        // Root layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        // Toolbar
        toolbar = Toolbar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material)
            )
            setBackgroundColor(Color.parseColor("#1E293B"))
            title = "iBTC Wallet Pro v4.1"
            setTitleTextColor(Color.WHITE)
        }
        rootLayout.addView(toolbar)

        // Swipe Refresh
        swipeRefreshLayout = SwipeRefreshLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setColorSchemeColors(
                Color.parseColor("#F59E0B"),
                Color.parseColor("#10B981"),
                Color.parseColor("#3B82F6")
            )
            setProgressBackgroundColorSchemeColor(Color.parseColor("#1E293B"))
        }

        scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(20, 20, 20, 40)
        }

        // ===== SYNC STATUS SECTION =====
        layoutSyncStatus = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val syncTopRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvSyncStatusText = TextView(this).apply {
            text = "Đang khởi tạo ví..."
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvSyncBlocks = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            gravity = Gravity.END
        }
        syncTopRow.addView(tvSyncStatusText)
        syncTopRow.addView(tvSyncBlocks)

        progressBarSync = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                8
            ).apply { topMargin = 8 }
            max = 100
            progress = 0
            progressDrawable = resources.getDrawable(android.R.drawable.progress_horizontal, theme)
        }

        layoutSyncStatus.addView(syncTopRow)
        layoutSyncStatus.addView(progressBarSync)
        mainContainer.addView(layoutSyncStatus)

        // ===== BALANCE CARD =====
        cardBalance = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(24, 28, 24, 28)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
            elevation = 8f
        }

        tvBalanceBtcLabel = TextView(this).apply {
            text = "Số dư khả dụng"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            gravity = Gravity.CENTER
        }

        tvBalanceBtc = TextView(this).apply {
            text = "0.00000000 BTC"
            setTextColor(Color.WHITE)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }

        tvBalanceUsd = TextView(this).apply {
            text = "$0.00 USD"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 18f
            gravity = Gravity.CENTER
        }

        tvBalanceSats = TextView(this).apply {
            text = "0 sats"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        }

        tvPriceInfo = TextView(this).apply {
            text = "1 BTC = $67,850.00"
            setTextColor(Color.parseColor("#475569"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        cardBalance.addView(tvBalanceBtcLabel)
        cardBalance.addView(tvBalanceBtc)
        cardBalance.addView(tvBalanceUsd)
        cardBalance.addView(tvBalanceSats)
        cardBalance.addView(tvPriceInfo)
        mainContainer.addView(cardBalance)

        // ===== ADDRESS CARD =====
        cardAddress = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        tvAddressLabel = TextView(this).apply {
            text = "Địa chỉ Bitcoin của bạn"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }

        tvCurrentAddress = TextView(this).apply {
            text = "Chưa khởi tạo ví"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(0, 12, 0, 16)
            typeface = Typeface.MONOSPACE
        }

        imageViewQrCode = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(280, 280).apply {
                gravity = Gravity.CENTER
                bottomMargin = 16
                topMargin = 8
            }
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
        }

        layoutAddressButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
        }

        btnCopyAddress = createSmallButton("Copy")
        btnShareAddress = createSmallButton("Chia sẻ")
        btnNewAddress = createSmallButton("Mới")
        btnShowQr = createSmallButton("QR")

        layoutAddressButtons.addView(btnCopyAddress, createButtonParams(1f).apply { marginEnd = 4 })
        layoutAddressButtons.addView(btnShareAddress, createButtonParams(1f).apply { marginStart = 2; marginEnd = 2 })
        layoutAddressButtons.addView(btnNewAddress, createButtonParams(1f).apply { marginStart = 2; marginEnd = 2 })
        layoutAddressButtons.addView(btnShowQr, createButtonParams(1f).apply { marginStart = 4 })

        cardAddress.addView(tvAddressLabel)
        cardAddress.addView(tvCurrentAddress)
        cardAddress.addView(imageViewQrCode)
        cardAddress.addView(layoutAddressButtons)
        mainContainer.addView(cardAddress)

        // ===== WALLET ACTIONS =====
        layoutWalletActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        btnCreateNewWallet = Button(this).apply {
            text = "TẠO VÍ MỚI"
            setBackgroundColor(Color.parseColor("#F59E0B"))
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        btnRestoreWallet = Button(this).apply {
            text = "KHÔI PHỤC"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val leftActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
        }
        val rightActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 6 }
        }

        btnBackupWallet = createActionButton("Backup ví")
        btnViewSeed = createActionButton("Xem seed")

        leftActions.addView(btnCreateNewWallet)
        leftActions.addView(btnBackupWallet)
        rightActions.addView(btnRestoreWallet)
        rightActions.addView(btnViewSeed)

        layoutWalletActions.addView(leftActions)
        layoutWalletActions.addView(rightActions)
        mainContainer.addView(layoutWalletActions)

        // ===== SEND CARD =====
        cardSend = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        tvSendTitle = TextView(this).apply {
            text = "Gửi Bitcoin"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }

        etRecipientAddress = EditText(this).apply {
            hint = "Nhập địa chỉ Bitcoin (bc1q... / 1... / 3...)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val addressActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        btnPasteAddress = createTinyButton("Dán")
        btnScanQr = createTinyButton("Quét QR")
        addressActionRow.addView(btnPasteAddress, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 })
        addressActionRow.addView(btnScanQr, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 6 })

        etAmountSatoshi = EditText(this).apply {
            hint = "Số lượng (satoshi)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        etAmountBtc = EditText(this).apply {
            hint = "≈ 0.00000000 BTC"
            inputType = InputType.TYPE_NUMBER_FLAG_DECIMAL
            setHintTextColor(Color.parseColor("#475569"))
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 8, 16, 8)
            isEnabled = false
        }

        val amountActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        btnMaxAmount = createTinyButton("Dùng tối đa")
        tvFeeEstimate = TextView(this).apply {
            text = "Phí: ~2,000 sats"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        amountActionRow.addView(btnMaxAmount, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        amountActionRow.addView(tvFeeEstimate)

        btnSendBitcoin = Button(this).apply {
            text = "GỬI BITCOIN NGAY"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                56
            ).apply { topMargin = 8 }
        }

        cardSend.addView(tvSendTitle)
        cardSend.addView(etRecipientAddress)
        cardSend.addView(addressActionRow)
        cardSend.addView(etAmountSatoshi)
        cardSend.addView(etAmountBtc)
        cardSend.addView(amountActionRow)
        cardSend.addView(btnSendBitcoin)
        mainContainer.addView(cardSend)

        // ===== TRANSACTIONS =====
        tvTransactionsHeader = TextView(this).apply {
            text = "Lịch sử giao dịch gần đây"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 8, 0, 12)
        }
        mainContainer.addView(tvTransactionsHeader)

        layoutTransactionsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        tvEmptyTransactions = TextView(this).apply {
            text = "Chưa có giao dịch nào\nTạo ví và nhận Bitcoin đầu tiên của bạn"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
            setLineSpacing(4f, 1.2f)
        }
        layoutTransactionsList.addView(tvEmptyTransactions)
        mainContainer.addView(layoutTransactionsList)

        btnViewAllTransactions = Button(this).apply {
            text = "Xem tất cả giao dịch"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#3B82F6"))
            visibility = View.GONE
        }
        mainContainer.addView(btnViewAllTransactions)

        // ===== FOOTER INFO =====
        val footerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 0)
        }

        tvWalletInfo = TextView(this).apply {
            text = "Ví chưa khởi tạo"
            setTextColor(Color.parseColor("#475569"))
            textSize = 10f
            gravity = Gravity.CENTER
        }

        tvNetworkStatus = TextView(this).apply {
            text = "Mạng: Bitcoin Mainnet | SPV Mode"
            setTextColor(Color.parseColor("#475569"))
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        }

        footerLayout.addView(tvWalletInfo)
        footerLayout.addView(tvNetworkStatus)
        mainContainer.addView(footerLayout)

        scrollView.addView(mainContainer)
        swipeRefreshLayout.addView(scrollView)
        rootLayout.addView(swipeRefreshLayout)
        setContentView(rootLayout)
    }

    private fun createSmallButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 11f
            setPadding(4, 8, 4, 8)
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            isAllCaps = false
        }
    }

    private fun createTinyButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 10f
            setPadding(12, 6, 12, 6)
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.parseColor("#E2E8F0"))
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
        }
    }

    private fun createActionButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.parseColor("#94A3B8"))
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
    }

    private fun createButtonParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
    }

    // ==================== SETUP ====================
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
    }

    private fun setupClickListeners() {
        swipeRefreshLayout.setOnRefreshListener { refreshWalletData() }
        
        btnCopyAddress.setOnClickListener { copyCurrentAddress() }
        btnShareAddress.setOnClickListener { shareCurrentAddress() }
        btnNewAddress.setOnClickListener { generateNewAddress() }
        btnShowQr.setOnClickListener { toggleQrCodeVisibility() }
        
        btnCreateNewWallet.setOnClickListener { promptCreateNewWallet() }
        btnRestoreWallet.setOnClickListener { promptRestoreWallet() }
        btnBackupWallet.setOnClickListener { backupWallet() }
        btnViewSeed.setOnClickListener { viewSeedPhrase() }
        
        btnPasteAddress.setOnClickListener { pasteFromClipboard() }
        btnScanQr.setOnClickListener { showQrScannerPlaceholder() }
        btnMaxAmount.setOnClickListener { setMaximumAmount() }
        btnSendBitcoin.setOnClickListener { confirmAndSendBitcoin() }

        etAmountSatoshi.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateBtcAmountPreview()
            }
        })
    }

    // ==================== WALLET INITIALIZATION ====================
    private fun initializeWalletManager() {
        tvSyncStatusText.text = "Đang khởi tạo WalletManager..."
        
        walletManager = WalletManager(this@MainActivity, filesDir)
        
        walletManager.onSyncProgress = { percent, blocksLeft, date ->
            runOnUiThread {
                progressBarSync.visibility = View.VISIBLE
                progressBarSync.progress = percent.toInt()
                tvSyncStatusText.text = "Đang đồng bộ blockchain: ${String.format("%.1f", percent)}%"
                tvSyncBlocks.text = if (blocksLeft > 0) "Còn $blocksLeft blocks" else "Gần xong..."
                
                if (date != null) {
                    tvSyncBlocks.text = "${tvSyncBlocks.text} • ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)}"
                }
            }
        }
        
        walletManager.onSyncFinished = {
            runOnUiThread {
                tvSyncStatusText.text = "✓ Đã đồng bộ hoàn tất"
                tvSyncBlocks.text = "Mạng Bitcoin Mainnet"
                progressBarSync.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                isWalletInitialized = true
                updateCompleteUI()
                enableWalletButtons(true)
            }
        }
        
        walletManager.onBalanceChanged = { newBalance ->
            runOnUiThread {
                updateBalanceDisplay()
                updateTransactionHistory()
            }
        }
        
        walletManager.onTransactionReceived = { tx ->
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Nhận được giao dịch mới!", Toast.LENGTH_SHORT).show()
                updateCompleteUI()
            }
        }

        ioScope.launch {
            try {
                walletManager.initialize()
                walletManager.startAsync()
                walletManager.awaitRunning()
                
                withContext(Dispatchers.Main) {
                    tvSyncStatusText.text = "Ví đã sẵn sàng"
                    isWalletInitialized = true
                    enableWalletButtons(true)
                    updateCompleteUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvSyncStatusText.text = "Lỗi khởi tạo: ${e.message}"
                    Toast.makeText(this@MainActivity, "Không thể khởi tạo ví: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun enableWalletButtons(enabled: Boolean) {
        btnSendBitcoin.isEnabled = enabled
        btnCopyAddress.isEnabled = enabled
        btnShareAddress.isEnabled = enabled
        btnNewAddress.isEnabled = enabled
        btnShowQr.isEnabled = enabled
        btnBackupWallet.isEnabled = enabled
        btnViewSeed.isEnabled = enabled
    }

    // ==================== WALLET ACTIONS ====================
    private fun promptCreateNewWallet() {
        AlertDialog.Builder(this@MainActivity)
            .setTitle("⚠️ Tạo ví Bitcoin mới")
            .setMessage("Hành động này sẽ:\n\n• Tạo ví mới với 12 từ khôi phục\n• Xóa ví hiện tại (nếu có)\n• Bạn PHẢI lưu 12 từ ở nơi an toàn\n\nBạn có chắc chắn?")
            .setPositiveButton("TẠO VÍ MỚI") { _, _ -> executeCreateWallet() }
            .setNegativeButton("Hủy", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun executeCreateWallet() {
        tvSyncStatusText.text = "Đang tạo ví mới..."
        progressBarSync.visibility = View.VISIBLE
        progressBarSync.isIndeterminate = true
        
        ioScope.launch {
            try {
                val result = walletManager.createNewWallet()
                withContext(Dispatchers.Main) {
                    progressBarSync.isIndeterminate = false
                    progressBarSync.visibility = View.GONE
                    showSeedPhraseDialog(result.mnemonic, true, result.address)
                    updateCompleteUI()
                    Toast.makeText(this@MainActivity, "Ví mới đã được tạo thành công!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarSync.isIndeterminate = false
                    progressBarSync.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Lỗi tạo ví: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun promptRestoreWallet() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        
        val inputSeed = EditText(this).apply {
            hint = "Nhập 12 hoặc 24 từ khôi phục, cách nhau bởi dấu cách"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.BLACK)
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP
            setBackgroundColor(Color.parseColor("#F1F5F9"))
            setPadding(16, 16, 16, 16)
        }
        
        val inputPassphrase = EditText(this).apply {
            hint = "Passphrase (tùy chọn, để trống nếu không có)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#F1F5F9"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        
        container.addView(inputSeed)
        container.addView(inputPassphrase)
        
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Khôi phục ví từ Seed")
            .setView(container)
            .setPositiveButton("KHÔI PHỤC") { _, _ ->
                val words = inputSeed.text.toString().trim().lowercase(Locale.US).split("\\s+".toRegex()).filter { it.isNotEmpty() }
                val passphrase = inputPassphrase.text.toString()
                
                if (words.size == 12 || words.size == 24) {
                    executeRestoreWallet(words, passphrase)
                } else {
                    Toast.makeText(this@MainActivity, "Seed phải có 12 hoặc 24 từ (hiện tại: ${words.size})", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun executeRestoreWallet(mnemonic: List<String>, passphrase: String) {
        tvSyncStatusText.text = "Đang khôi phục ví..."
        progressBarSync.visibility = View.VISIBLE
        progressBarSync.isIndeterminate = true
        
        ioScope.launch {
            val success = walletManager.restoreFromMnemonic(mnemonic, passphrase)
            withContext(Dispatchers.Main) {
                progressBarSync.isIndeterminate = false
                progressBarSync.visibility = View.GONE
                
                if (success) {
                    Toast.makeText(this@MainActivity, "Khôi phục ví thành công! Đang đồng bộ...", Toast.LENGTH_LONG).show()
                    updateCompleteUI()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Khôi phục thất bại")
                        .setMessage("Không thể khôi phục ví. Vui lòng kiểm tra lại 12 từ và thử lại.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun showSeedPhraseDialog(mnemonic: List<String>, isNewWallet: Boolean, address: String) {
        val message = buildString {
            if (isNewWallet) {
                append("🔐 LƯU TRỮ AN TOÀN 12 TỪ NÀY 🔐\n\n")
                append("Đây là CHÌA KHÓA duy nhất để khôi phục ví của bạn.\n\n")
            }
            append(mnemonic.joinToString(" ").uppercase(Locale.US))
            append("\n\n")
            append("Địa chỉ ví: $address\n\n")
            if (isNewWallet) {
                append("⚠️ CẢNH BÁO:\n")
                append("• Không chụp màn hình\n")
                append("• Không lưu trên máy tính/điện thoại\n")
                append("• Viết ra giấy và cất nơi an toàn\n")
                append("• Không chia sẻ cho bất kỳ ai")
            }
        }
        
        AlertDialog.Builder(this@MainActivity)
            .setTitle(if (isNewWallet) "BẢO MẬT VÍ CỦA BẠN" else "Seed Phrase")
            .setMessage(message)
            .setPositiveButton("Tôi đã lưu an toàn") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(mnemonic.joinToString(" "))
                Toast.makeText(this@MainActivity, "Đã copy seed phrase", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun viewSeedPhrase() {
        val mnemonic = walletManager.getMnemonic()
        if (mnemonic != null && mnemonic.isNotEmpty()) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("⚠️ Cảnh báo bảo mật")
                .setMessage("Bạn có chắc muốn xem seed phrase? Đảm bảo không có ai nhìn thấy màn hình.")
                .setPositiveButton("Xem") { _, _ ->
                    showSeedPhraseDialog(mnemonic, false, walletManager.getCurrentReceiveAddress())
                }
                .setNegativeButton("Hủy", null)
                .show()
        } else {
            Toast.makeText(this@MainActivity, "Không tìm thấy seed phrase", Toast.LENGTH_SHORT).show()
        }
    }

    private fun backupWallet() {
        try {
            val backupFile = java.io.File(getExternalFilesDir(null), "ibtc-backup-${System.currentTimeMillis()}.wallet")
            val success = walletManager.backupWalletToFile(backupFile)
            if (success) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Backup thành công")
                    .setMessage("Ví đã được sao lưu tại:\n${backupFile.absolutePath}\n\nHãy copy file này ra nơi an toàn!")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Chia sẻ") { _, _ ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                                this@MainActivity,
                                "${packageName}.fileprovider",
                                backupFile
                            ))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Chia sẻ backup"))
                    }
                    .show()
            } else {
                Toast.makeText(this@MainActivity, "Backup thất bại", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Lỗi backup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== ADDRESS ACTIONS ====================
    private fun copyCurrentAddress() {
        val address = tvCurrentAddress.text.toString()
        if (address.isNotEmpty() && address != "Chưa khởi tạo ví") {
            copyToClipboard(address)
            Toast.makeText(this@MainActivity, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCurrentAddress() {
        val address = tvCurrentAddress.text.toString()
        if (address.isNotEmpty() && address != "Chưa khởi tạo ví") {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Địa chỉ Bitcoin của tôi")
                putExtra(Intent.EXTRA_TEXT, "Gửi Bitcoin đến địa chỉ này:\n\n$address\n\nMạng: Bitcoin Mainnet")
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ địa chỉ Bitcoin"))
        }
    }

    private fun generateNewAddress() {
        ioScope.launch {
            val newAddress = walletManager.getFreshReceiveAddress()
            withContext(Dispatchers.Main) {
                tvCurrentAddress.text = newAddress
                if (isQrVisible) {
                    imageViewQrCode.setImageBitmap(generateQrCodeBitmap(newAddress))
                }
                Toast.makeText(this@MainActivity, "Đã tạo địa chỉ mới", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleQrCodeVisibility() {
        val address = tvCurrentAddress.text.toString()
        if (address.isEmpty() || address == "Chưa khởi tạo ví") {
            Toast.makeText(this@MainActivity, "Chưa có địa chỉ ví", Toast.LENGTH_SHORT).show()
            return
        }

        isQrVisible = !isQrVisible
        if (isQrVisible) {
            imageViewQrCode.setImageBitmap(generateQrCodeBitmap(address))
            imageViewQrCode.visibility = View.VISIBLE
            btnShowQr.text = "Ẩn QR"
        } else {
            imageViewQrCode.visibility = View.GONE
            btnShowQr.text = "QR"
        }
    }

    private fun generateQrCodeBitmap(content: String): Bitmap {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("iBTC Address", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pastedText = clip.getItemAt(0).text.toString()
            etRecipientAddress.setText(pastedText)
            Toast.makeText(this@MainActivity, "Đã dán địa chỉ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showQrScannerPlaceholder() {
        Toast.makeText(this@MainActivity, "Tính năng quét QR sẽ có trong bản cập nhật", Toast.LENGTH_SHORT).show()
    }

    // ==================== SEND BITCOIN ====================
    private fun updateBtcAmountPreview() {
        val satsText = etAmountSatoshi.text.toString()
        if (satsText.isNotEmpty()) {
            try {
                val sats = satsText.toLong()
                val btc = sats.toDouble() / 100_000_000.0
                etAmountBtc.setText("≈ ${btcDecimalFormat.format(btc)} BTC")
            } catch (e: NumberFormatException) {
                etAmountBtc.setText("≈ 0.00000000 BTC")
            }
        } else {
            etAmountBtc.setText("≈ 0.00000000 BTC")
        }
    }

    private fun setMaximumAmount() {
        val available = walletManager.getBalanceAvailable().value
        val feeEstimate = 2000L
        val maxSendable = (available - feeEstimate).coerceAtLeast(0)
        etAmountSatoshi.setText(maxSendable.toString())
        Toast.makeText(this@MainActivity, "Đã đặt số tiền tối đa (trừ phí)", Toast.LENGTH_SHORT).show()
    }

    private fun confirmAndSendBitcoin() {
        val recipient = etRecipientAddress.text.toString().trim()
        val amountSatsStr = etAmountSatoshi.text.toString().trim()

        if (recipient.isEmpty()) {
            etRecipientAddress.error = "Nhập địa chỉ nhận"
            return
        }

        if (amountSatsStr.isEmpty()) {
            etAmountSatoshi.error = "Nhập số lượng"
            return
        }

        val amountSats = try {
            amountSatsStr.toLong()
        } catch (e: NumberFormatException) {
            etAmountSatoshi.error = "Số không hợp lệ"
            return
        }

        if (amountSats <= 0) {
            etAmountSatoshi.error = "Số tiền phải lớn hơn 0"
            return
        }

        val availableBalance = walletManager.getBalanceAvailable().value
        if (amountSats > availableBalance) {
            etAmountSatoshi.error = "Số dư không đủ (có ${satsFormat.format(availableBalance)} sats)"
            return
        }

        val btcAmount = amountSats.toDouble() / 100_000_000.0
        val usdAmount = btcAmount * currentBitcoinPrice

        val confirmationMessage = buildString {
            append("Xác nhận gửi Bitcoin:\n\n")
            append("Đến: ${recipient.take(20)}...\n")
            append("Số lượng: ${satsFormat.format(amountSats)} sats\n")
            append("≈ ${btcDecimalFormat.format(btcAmount)} BTC\n")
            append("≈ ${usdCurrencyFormat.format(usdAmount)}\n\n")
            append("Phí mạng: ~2,000 sats\n")
            append("Tổng trừ: ${satsFormat.format(amountSats + 2000)} sats\n\n")
            append("⚠️ Giao dịch không thể hoàn tác!")
        }

        AlertDialog.Builder(this@MainActivity)
            .setTitle("Xác nhận giao dịch")
            .setMessage(confirmationMessage)
            .setPositiveButton("GỬI NGAY") { _, _ -> executeSendBitcoin(recipient, amountSats) }
            .setNegativeButton("Hủy", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun executeSendBitcoin(recipient: String, amountSats: Long) {
        tvSyncStatusText.text = "Đang gửi giao dịch..."
        progressBarSync.visibility = View.VISIBLE
        progressBarSync.isIndeterminate = true
        btnSendBitcoin.isEnabled = false

        ioScope.launch {
            try {
                val result = walletManager.sendCoins(recipient, amountSats)
                
                withContext(Dispatchers.Main) {
                    progressBarSync.isIndeterminate = false
                    progressBarSync.visibility = View.GONE
                    btnSendBitcoin.isEnabled = true

                    if (result.success) {
                        val successMessage = "Giao dịch đã được gửi thành công!\n\nTXID:\n${result.txId}\n\nGiao dịch sẽ được xác nhận trong vài phút."
                        
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("✓ Gửi thành công")
                            .setMessage(successMessage)
                            .setPositiveButton("OK") { _, _ ->
                                etRecipientAddress.text.clear()
                                etAmountSatoshi.text.clear()
                                updateCompleteUI()
                            }
                            .setNeutralButton("Copy TXID") { _, _ ->
                                copyToClipboard(result.txId ?: "")
                                Toast.makeText(this@MainActivity, "Đã copy TXID", Toast.LENGTH_SHORT).show()
                            }
                            .show()
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Gửi thất bại")
                            .setMessage("Lỗi: ${result.error}\n\nVui lòng kiểm tra địa chỉ và số dư rồi thử lại.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarSync.isIndeterminate = false
                    progressBarSync.visibility = View.GONE
                    btnSendBitcoin.isEnabled = true
                    Toast.makeText(this@MainActivity, "Lỗi nghiêm trọng: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==================== UI UPDATES ====================
    private fun refreshWalletData() {
        swipeRefreshLayout.isRefreshing = true
        updateCompleteUI()
        uiScope.launch {
            delay(1000)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun updateCompleteUI() {
        updateBalanceDisplay()
        updateAddressDisplay()
        updateTransactionHistory()
        updateWalletInfo()
    }

    private fun updateBalanceDisplay() {
        if (!::walletManager.isInitialized || !walletManager.isWalletReady()) return
        
        val balanceSats = walletManager.getBalance().value
        val balanceBtc = balanceSats.toDouble() / 100_000_000.0
        val balanceUsd = balanceBtc * currentBitcoinPrice
        
        tvBalanceBtc.text = "${btcDecimalFormat.format(balanceBtc)} BTC"
        tvBalanceUsd.text = usdCurrencyFormat.format(balanceUsd)
        tvBalanceSats.text = "${satsFormat.format(balanceSats)} satoshis"
    }

    private fun updateAddressDisplay() {
        if (!::walletManager.isInitialized || !walletManager.isWalletReady()) return
        
        val address = walletManager.getCurrentReceiveAddress()
        tvCurrentAddress.text = if (address.isNotEmpty()) address else "Đang tạo địa chỉ..."
        
        if (isQrVisible && address.isNotEmpty()) {
            imageViewQrCode.setImageBitmap(generateQrCodeBitmap(address))
        }
    }

    private fun updateTransactionHistory() {
        if (!::walletManager.isInitialized || !walletManager.isWalletReady()) return
        
        layoutTransactionsList.removeAllViews()
        val transactions = walletManager.getTransactionHistory()
        
        if (transactions.isEmpty()) {
            layoutTransactionsList.addView(tvEmptyTransactions)
            btnViewAllTransactions.visibility = View.GONE
            return
        }
        
        btnViewAllTransactions.visibility = if (transactions.size > 5) View.VISIBLE else View.GONE
        
        transactions.take(5).forEach { tx ->
            val txView = createTransactionView(tx)
            layoutTransactionsList.addView(txView)
        }
    }

    private fun createTransactionView(tx: WalletManager.TransactionInfo): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        
        val amountText = TextView(this).apply {
            text = "${if (tx.isSent) "-" else "+"} ${tx.getFormattedAmount()} BTC"
            setTextColor(if (tx.isSent) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val statusText = TextView(this).apply {
            text = if (tx.confirmations >= 6) "✓ Xác nhận" else "${tx.confirmations}/6"
            setTextColor(if (tx.confirmations >= 6) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            textSize = 11f
            gravity = Gravity.END
        }
        
        topRow.addView(amountText)
        topRow.addView(statusText)

        val middleRow = TextView(this).apply {
            text = tx.getFormattedDate()
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 4, 0, 2)
        }

        val bottomRow = TextView(this).apply {
            text = "TXID: ${tx.txId.take(12)}...${tx.txId.takeLast(8)}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }

        container.addView(topRow)
        container.addView(middleRow)
        container.addView(bottomRow)
        
        container.setOnClickListener {
            copyToClipboard(tx.txId)
            Toast.makeText(this@MainActivity, "Đã copy TXID đầy đủ", Toast.LENGTH_SHORT).show()
        }
        
        return container
    }

    private fun updateWalletInfo() {
        if (!::walletManager.isInitialized) return
        
        val info = buildString {
            append("Ví: ${if (walletManager.isWalletReady()) "Hoạt động" else "Đang khởi tạo"}")
            if (walletManager.isWalletReady()) {
                append(" | Tạo: ${dateFormat.format(walletManager.getCreationTime())}")
            }
        }
        tvWalletInfo.text = info
        tvNetworkStatus.text = "Mạng: ${walletManager.getNetwork()} | SPV Sync | bitcoinj 0.16.3"
    }

    private fun startPriceUpdates() {
        uiScope.launch {
            while (true) {
                // In production, fetch from API
                // currentBitcoinPrice = fetchPriceFromAPI()
                tvPriceInfo.text = "1 BTC = ${usdCurrencyFormat.format(currentBitcoinPrice)}"
                delay(60000) // Update every minute
            }
        }
    }

    // ==================== MENU ====================
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1001, 0, "Xem Seed Phrase").setIcon(android.R.drawable.ic_menu_view)
        menu.add(0, 1002, 0, "Backup ví").setIcon(android.R.drawable.ic_menu_save)
        menu.add(0, 1003, 0, "Cài đặt mạng").setIcon(android.R.drawable.ic_menu_preferences)
        menu.add(0, 1004, 0, "Về iBTC").setIcon(android.R.drawable.ic_menu_info_details)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1001 -> { viewSeedPhrase(); true }
            1002 -> { backupWallet(); true }
            1003 -> { showNetworkSettings(); true }
            1004 -> { showAboutDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNetworkSettings() {
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Cài đặt mạng")
            .setMessage("Mạng hiện tại: Bitcoin Mainnet\n\nChế độ: SPV (Simplified Payment Verification)\nPeers: Tự động\n\nTính năng testnet sẽ có trong bản cập nhật.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this@MainActivity)
            .setTitle("iBTC Wallet Pro v4.1")
            .setMessage("Ví Bitcoin không lưu ký mã nguồn mở\n\n• Xây dựng trên bitcoinj 0.16.3\n• SPV full node\n• Hỗ trợ SegWit (bech32)\n• 12/24 từ khôi phục BIP39\n• Mã nguồn: github.com/buli/ibtc\n\n© 2024 iBTC Team")
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Website") { _, _ ->
                // Open website
            }
            .show()
    }
}