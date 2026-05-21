package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    // Quản lý ví
    private lateinit var wm: WalletManager
    // Callback nhận QR
    private var qrCallback: ((String) -> Unit)? = null
    // Launcher quét QR
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            qrCallback?.invoke(contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var price by remember { mutableStateOf(0.0) }
                var activeWalletId by remember { mutableStateOf(wm.getActive()?.id ?: "") }

                LaunchedEffect(hasWallet) {
                    if (hasWallet && wm.hasWallets()) {
                        withContext(Dispatchers.IO) {
                            wm.init()
                            price = wm.price()
                            val active = wm.getActive()
                            if (active != null) {
                                activeWalletId = active.id
                            }
                        }
                    }
                }

                if (!hasWallet) {
                    Onboarding(
                        onCreate = { name ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                wm.create(name)
                                wm.init()
                                hasWallet = true
                                val active = wm.getActive()
                                if (active != null) {
                                    activeWalletId = active.id
                                }
                            }
                        },
                        onImport = { name, seed ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val imported = wm.import(name, seed)
                                if (imported != null) {
                                    wm.init()
                                    hasWallet = true
                                    val active = wm.getActive()
                                    if (active != null) {
                                        activeWalletId = active.id
                                    }
                                }
                            }
                        }
                    )
                } else {
                    var tab by remember { mutableStateOf(0) }
                    Scaffold(
                        topBar = {
                            TabRow(selectedTabIndex = tab) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }) {
                                    Text("Ví")
                                }
                                Tab(selected = tab == 1, onClick = { tab = 1 }) {
                                    Text("Quản lý")
                                }
                                Tab(selected = tab == 2, onClick = { tab = 2 }) {
                                    Text("Tùy chỉnh")
                                }
                            }
                        }
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            if (!wm.isReady() && wm.hasWallets()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (!wm.hasWallets()) {
                                LaunchedEffect(Unit) {
                                    hasWallet = false
                                }
                            } else {
                                when (tab) {
                                    0 -> {
                                        WalletTab(activeWalletId, price) { newPrice ->
                                            price = newPrice
                                        }
                                    }
                                    1 -> {
                                        ManageTab(
                                            onSwitched = { newId ->
                                                activeWalletId = newId
                                            },
                                            onNoWallets = {
                                                hasWallet = false
                                            }
                                        )
                                    }
                                    2 -> {
                                        SettingsTab()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Onboarding(onCreate: (String) -> Unit, onImport: (String, String) -> Unit) {
        var showCreate by remember { mutableStateOf(false) }
        var showImport by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "TẠO VÍ MỚI")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showImport = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "IMPORT SEED")
            }
        }
        if (showCreate) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreate = false },
                confirmButton = {
                    TextButton(onClick = {
                        showCreate = false
                        onCreate(name)
                    }) {
                        Text(text = "Tạo")
                    }
                },
                title = { Text(text = "Tên ví") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(text = "Tên") }
                    )
                }
            )
        }
        if (showImport) {
            var name by remember { mutableStateOf("") }
            var seed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showImport = false },
                confirmButton = {
                    TextButton(onClick = {
                        showImport = false
                        onImport(name, seed)
                    }) {
                        Text(text = "Import")
                    }
                },
                title = { Text(text = "Import ví") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(text = "Tên") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = seed,
                            onValueChange = { seed = it },
                            label = { Text(text = "12 từ seed") }
                        )
                    }
                }
            )
        }
    }

    @Composable
    fun WalletTab(activeId: String, price: Double, onPriceUpdate: (Double) -> Unit) {
        var balance by remember { mutableStateOf(0.0) }
        var pct by remember { mutableStateOf(0) }
        var txt by remember { mutableStateOf("Chưa sync") }
        var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        LaunchedEffect(activeId) {
            wm.onProgress { p, t ->
                pct = p
                txt = t
            }
            withContext(Dispatchers.IO) {
                balance = wm.getBalance()
                txs = wm.getTransactions()
                val newPrice = wm.price()
                onPriceUpdate(newPrice)
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                val sec = wm.getRefreshSec()
                delay(sec * 1000)
                withContext(Dispatchers.IO) {
                    balance = wm.getBalance()
                    txs = wm.getTransactions()
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Số dư:")
                    Text(
                        text = "%.8f BTC".format(balance),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "≈ $%.2f".format(balance * price))
                    Text(
                        text = "1 BTC = $%,.0f USD".format(price),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = txt, fontSize = 12.sp)
                    if (pct in 1..99) {
                        LinearProgressIndicator(
                            progress = pct / 100f,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        txt = "Đang sync..."
                        balance = wm.getBalance()
                        txs = wm.getTransactions()
                        val newPrice = wm.price()
                        onPriceUpdate(newPrice)
                        txt = "Đã đồng bộ"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "SYNC NGAY")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Lịch sử giao dịch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (txs.isEmpty()) {
                    item {
                        Text(
                            text = "Chưa có giao dịch",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(txs) { tx ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val color = if (tx.type == "Nhận") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    Text(text = tx.type, fontWeight = FontWeight.Bold, color = color)
                                    Text(text = "%.8f BTC".format(tx.amount), fontSize = 14.sp)
                                    Text(text = dateFormat.format(tx.time), fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = tx.txId.take(8) + "...", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ManageTab(onSwitched: (String) -> Unit, onNoWallets: () -> Unit) {
        val ctx = LocalContext.current
        var wallets by remember { mutableStateOf(wm.getAll()) }
        var to by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var feeSel by remember { mutableStateOf(1) }
        var customFee by remember { mutableStateOf(wm.getDefaultCustomFee().toString()) }
        var result by remember { mutableStateOf("") }
        var renameId by remember { mutableStateOf<String?>(null) }
        var detailId by remember { mutableStateOf<String?>(null) }
        var fees by remember { mutableStateOf(FeeRates(5, 10, 20)) }
        var currentAddress by remember { mutableStateOf(wm.getAddress()) }
        var showSeedDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                fees = wm.getFeeRates()
            }
        }

        val feeRate = when (feeSel) {
            0 -> fees.slow
            1 -> fees.normal
            2 -> fees.fast
            else -> customFee.toLongOrNull() ?: fees.normal
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text(text = "Danh sách ví", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(wallets) { w ->
                var expanded by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = w.name, fontWeight = FontWeight.Bold)
                            val status = if (w.id == wm.getActive()?.id) "Đang dùng" else "ID: ${w.id.take(6)}"
                            Text(text = status, fontSize = 12.sp)
                        }
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Text(text = "⋮")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(text = "Chuyển") },
                                    onClick = {
                                        expanded = false
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            wm.switchTo(w.id)
                                            wallets = wm.getAll()
                                            currentAddress = wm.getAddress()
                                            onSwitched(w.id)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "Đổi tên") },
                                    onClick = {
                                        expanded = false
                                        renameId = w.id
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "Chi tiết") },
                                    onClick = {
                                        expanded = false
                                        detailId = w.id
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "Xóa") },
                                    onClick = {
                                        expanded = false
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            wm.delete(w.id)
                                            wallets = wm.getAll()
                                            if (wallets.isEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    onNoWallets()
                                                }
                                            } else {
                                                currentAddress = wm.getAddress()
                                                val active = wm.getActive()
                                                if (active != null) {
                                                    onSwitched(active.id)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            wm.create("Ví ${wallets.size + 1}")
                            wm.init()
                            wallets = wm.getAll()
                            currentAddress = wm.getAddress()
                            val active = wm.getActive()
                            if (active != null) {
                                onSwitched(active.id)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "THÊM VÍ")
                }
            }
        }
    }

    @Composable
    fun SettingsTab() {
        // ... (giữ nguyên như bản trước)
    }

    private fun generateQr(text: String): Bitmap {
        val size = 512
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val color = if (bits.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                bmp.setPixel(x, y, color)
            }
        }
        return bmp
    }
}