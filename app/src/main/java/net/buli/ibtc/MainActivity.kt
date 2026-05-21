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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    private var qrCallback: ((String) -> Unit)? = null
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { qrCallback?.invoke(it) }
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
                    if (hasWallet) {
                        withContext(Dispatchers.IO) {
                            wm.init()
                            price = wm.price()
                            activeWalletId = wm.getActive()?.id ?: ""
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
                                activeWalletId = wm.getActive()?.id ?: ""
                            }
                        },
                        onImport = { name, seed ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (wm.import(name, seed) != null) {
                                    wm.init()
                                    hasWallet = true
                                    activeWalletId = wm.getActive()?.id ?: ""
                                }
                            }
                        }
                    )
                } else {
                    var tab by remember { mutableStateOf(0) }
                    Scaffold(
                        topBar = {
                            TabRow(selectedTabIndex = tab) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Ví") }
                                Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Quản lý") }
                                Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("Tùy chỉnh") }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            if (!wm.isReady()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                when (tab) {
                                    0 -> WalletTab(activeWalletId, price) { price = it }
                                    1 -> ManageTab(
                                        onSwitched = { activeWalletId = it },
                                        onNoWallets = { hasWallet = false }
                                    )
                                    2 -> SettingsTab()
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
        Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("TẠO VÍ MỚI") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) { Text("IMPORT SEED") }
        }
        if (showCreate) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreate = false },
                confirmButton = { TextButton(onClick = { showCreate = false; onCreate(name) }) { Text("Tạo") } },
                title = { Text("Tên ví") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên") }) }
            )
        }
        if (showImport) {
            var name by remember { mutableStateOf("") }
            var seed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showImport = false },
                confirmButton = { TextButton(onClick = { showImport = false; onImport(name, seed) }) { Text("Import") } },
                title = { Text("Import ví") },
                text = {
                    Column {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên") })
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = seed, onValueChange = { seed = it }, label = { Text("12 từ seed") })
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
            wm.onProgress { p, t -> pct = p; txt = t }
            withContext(Dispatchers.IO) {
                balance = wm.getBalance()
                txs = wm.getTransactions()
                onPriceUpdate(wm.price())
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(wm.getRefreshSec() * 1000)
                withContext(Dispatchers.IO) {
                    balance = wm.getBalance()
                    txs = wm.getTransactions()
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Số dư:")
                    Text("%.8f BTC".format(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("≈ $%.2f".format(balance * price))
                    Spacer(Modifier.height(4.dp))
                    Text(txt, fontSize = 12.sp)
                    if (pct in 1..99) LinearProgressIndicator(progress = pct / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    txt = "Đang sync..."
                    balance = wm.getBalance()
                    txs = wm.getTransactions()
                    onPriceUpdate(wm.price())
                    txt = "Đã đồng bộ"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("SYNC NGAY") }
            Spacer(Modifier.height(16.dp))
            Text("Lịch sử giao dịch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (txs.isEmpty()) {
                    item { Text("Chưa có giao dịch", fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
                } else {
                    items(txs) { tx ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.type, fontWeight = FontWeight.Bold, color = if (tx.type == "Nhận") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    Text("%.8f BTC".format(tx.amount), fontSize = 14.sp)
                                    Text(dateFormat.format(tx.time), fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(tx.txId.take(8) + "...", fontSize = 10.sp)
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

        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { fees = wm.getFeeRates() } }
        val feeRate = when (feeSel) { 0 -> fees.slow; 1 -> fees.normal; 2 -> fees.fast; else -> customFee.toLongOrNull() ?: fees.normal }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item { Text("Danh sách ví", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
            items(wallets) { w ->
                var expanded by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(w.name, fontWeight = FontWeight.Bold)
                            Text(if (w.id == wm.getActive()?.id) "Đang dùng" else "ID: ${w.id.take(6)}", fontSize = 12.sp)
                        }
                        Box {
                            IconButton(onClick = { expanded = true }) { Text("⋮") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Chuyển") }, onClick = {
                                    expanded = false
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        wm.switchTo(w.id); wallets = wm.getAll(); currentAddress = wm.getAddress(); onSwitched(w.id)
                                    }
                                })
                                DropdownMenuItem(text = { Text("Đổi tên") }, onClick = { expanded = false; renameId = w.id })
                                DropdownMenuItem(text = { Text("Chi tiết") }, onClick = { expanded = false; detailId = w.id })
                                DropdownMenuItem(text = { Text("Xóa") }, onClick = {
                                    expanded = false
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        wm.delete(w.id); wallets = wm.getAll()
                                        if (wallets.isEmpty()) { withContext(Dispatchers.Main) { onNoWallets() } }
                                        else { currentAddress = wm.getAddress(); onSwitched(wm.getActive()?.id ?: "") }
                                    }
                                })
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { lifecycleScope.launch(Dispatchers.IO) { wm.create("Ví ${wallets.size + 1}"); wm.init(); wallets = wm.getAll(); currentAddress = wm.getAddress(); onSwitched(wm.getActive()?.id ?: "") } }, modifier = Modifier.fillMaxWidth()) { Text("THÊM VÍ") }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Gửi BTC", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("Địa chỉ") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { qrCallback = { to = it }; qrLauncher.launch(ScanOptions()) }) { Text("QR") } })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Số BTC") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Phí giao dịch:", fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = feeSel == 0, onClick = { feeSel = 0 }); Text("Chậm (${fees.slow})"); Spacer(Modifier.width(8.dp)); RadioButton(selected = feeSel == 1, onClick = { feeSel = 1 }); Text("Thường (${fees.normal})") }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = feeSel == 2, onClick = { feeSel = 2 }); Text("Nhanh (${fees.fast})"); Spacer(Modifier.width(8.dp)); RadioButton(selected = feeSel == 3, onClick = { feeSel = 3 }); Text("Tùy") }
                if (feeSel == 3) OutlinedTextField(value = customFee, onValueChange = { customFee = it }, label = { Text("sat/vB tùy chỉnh") }, modifier = Modifier.fillMaxWidth())
                val feeBtc = 250.0 * feeRate / 100000000.0
                Text("Phí ước tính: %.8f BTC".format(feeBtc), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                Button(onClick = { CoroutineScope(Dispatchers.IO).launch { result = wm.send(to, amount.toDoubleOrNull() ?: 0.0, feeRate); wm.setDefaultCustomFee(feeRate) } }, modifier = Modifier.fillMaxWidth()) { Text("GỬI") }
                if (result.isNotEmpty()) Text(result, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Nhận BTC", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
            }
            item {
                val qr = remember(currentAddress) { generateQr(currentAddress) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(bitmap = qr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(200.dp))
                    Spacer(Modifier.height(8.dp)); Text(currentAddress, fontSize = 12.sp); Spacer(Modifier.height(8.dp))
                    Button(onClick = { val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("btc", currentAddress)); Toast.makeText(ctx, "Đã copy", Toast.LENGTH_SHORT).show() }) { Text("COPY") }
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = { showSeedDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("XUẤT 12 TỪ SEED") }
            }
        }
        if (showSeedDialog) {
            val seed = wm.getSeed(); val seedQr = remember(seed) { generateQr(seed) }
            AlertDialog(onDismissRequest = { showSeedDialog = false }, confirmButton = { TextButton(onClick = { showSeedDialog = false }) { Text("Đóng") } }, title = { Text("Sao lưu seed") }, text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(bitmap = seedQr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(240.dp))
                    Spacer(Modifier.height(16.dp))
                    SelectionContainer { Text(seed, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp) }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("seed", seed)); Toast.makeText(ctx, "Đã copy seed", Toast.LENGTH_SHORT).show() }) { Text("COPY SEED") }
                }
            })
        }
        renameId?.let