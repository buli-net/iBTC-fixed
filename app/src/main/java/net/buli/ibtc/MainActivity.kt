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
                var activeId by remember { mutableStateOf("") }

                // FIX 1: Load tỷ giá ngay khi mở app, retry 3 lần
                LaunchedEffect(hasWallet) {
                    if (hasWallet) {
                        withContext(Dispatchers.IO) {
                            try {
                                wm.init()
                                activeId = wm.getActive()?.id ?: ""
                                // thử lấy giá 3 lần
                                repeat(3) {
                                    price = wm.price()
                                    if (price > 0) return@repeat
                                    delay(2000)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                // FIX 1b: Tự động refresh giá mỗi 30s
                LaunchedEffect(price) {
                    while (hasWallet) {
                        delay(30000)
                        withContext(Dispatchers.IO) {
                            val p = wm.price()
                            if (p > 0) price = p
                        }
                    }
                }

                if (!hasWallet) {
                    // ... màn hình tạo ví giữ nguyên ...
                    var showCreate by remember { mutableStateOf(false) }
                    var showImport by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { showCreate = true }, Modifier.fillMaxWidth()) { Text("TẠO VÍ MỚI") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showImport = true }, Modifier.fillMaxWidth()) { Text("IMPORT SEED") }
                    }
                    if (showCreate) {
                        var name by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showCreate = false },
                            confirmButton = { TextButton(onClick = {
                                showCreate = false
                                lifecycleScope.launch(Dispatchers.IO) {
                                    wm.create(name); wm.init(); hasWallet = true
                                }
                            }) { Text("Tạo") } },
                            title = { Text("Tên ví") },
                            text = { OutlinedTextField(value = name, onValueChange = { name = it }) }
                        )
                    }
                    if (showImport) {
                        var name by remember { mutableStateOf("") }
                        var seed by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showImport = false },
                            confirmButton = { TextButton(onClick = {
                                showImport = false
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (wm.import(name, seed) != null) { wm.init(); hasWallet = true }
                                }
                            }) { Text("Import") } },
                            title = { Text("Import") },
                            text = { Column {
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên") })
                                OutlinedTextField(value = seed, onValueChange = { seed = it }, label = { Text("Seed 12 từ") })
                            }}
                        )
                    }
                } else {
                    var tab by remember { mutableStateOf(0) }
                    Scaffold(topBar = {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Ví") }
                            Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Quản lý") }
                            Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("Tùy chỉnh") }
                        }
                    }) { padding ->
                        Box(Modifier.padding(padding)) {
                            when (tab) {
                                0 -> {
                                    var balance by remember { mutableStateOf(0.0) }
                                    var progress by remember { mutableStateOf(0) }
                                    var status by remember { mutableStateOf("Chưa sync") }
                                    var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
                                    val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

                                    LaunchedEffect(activeId) {
                                        wm.onProgress { p, t -> progress = p; status = t }
                                        withContext(Dispatchers.IO) {
                                            balance = try { wm.getBalance() } catch (_: Exception) { 0.0 }
                                            txs = try { wm.getTransactions() } catch (_: Exception) { emptyList() }
                                            val p = wm.price(); if (p > 0) price = p
                                        }
                                    }

                                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text("Số dư:")
                                                Text("%.8f BTC".format(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                                Text("≈ $%.2f".format(balance * price)) // FIX: luôn nhân với price mới
                                                Text(status, fontSize = 12.sp)
                                                if (progress in 1..99) LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                status = "Đang sync..."
                                                balance = wm.getBalance()
                                                txs = wm.getTransactions()
                                                val p = wm.price(); if (p > 0) price = p
                                                status = "Đã đồng bộ"
                                            }
                                        }, modifier = Modifier.fillMaxWidth()) { Text("SYNC NGAY") }

                                        Spacer(Modifier.height(16.dp))
                                        Text("Lịch sử", fontWeight = FontWeight.Bold)
                                        LazyColumn(Modifier.fillMaxSize()) {
                                            items(txs) { tx ->
                                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                    Row(Modifier.padding(12.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(tx.type, fontWeight = FontWeight.Bold)
                                                            Text("%.8f".format(tx.amount))
                                                            Text(formatter.format(tx.time), fontSize = 11.sp)
                                                        }
                                                        Text(tx.txId.take(8))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    val ctx = LocalContext.current
                                    var wallets by remember { mutableStateOf(wm.getAll()) }
                                    var to by remember { mutableStateOf("") }
                                    var amount by remember { mutableStateOf("") }
                                    var feeSel by remember { mutableStateOf(1) }
                                    var customFee by remember { mutableStateOf(wm.getDefaultCustomFee().toString()) }
                                    var result by remember { mutableStateOf("") }
                                    var fees by remember { mutableStateOf(FeeRates(5,10,20)) }
                                    var currentAddress by remember { mutableStateOf("") }
                                    var showSeed by remember { mutableStateOf(false) }

                                    LaunchedEffect(activeId) {
                                        withContext(Dispatchers.IO) {
                                            fees = wm.getFeeRates()
                                            currentAddress = try { wm.getAddress() } catch (_: Exception) { "" }
                                            val p = wm.price(); if (p > 0) price = p
                                        }
                                    }

                                    val feeRate = when(feeSel) {
                                        0 -> fees.slow; 1 -> fees.normal; 2 -> fees.fast
                                        else -> customFee.toLongOrNull() ?: fees.normal
                                    }

                                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                                        item { Text("Danh sách ví", fontWeight = FontWeight.Bold) }
                                        items(wallets) { w ->
                                            var expanded by remember { mutableStateOf(false) }
                                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(w.name, fontWeight = FontWeight.Bold)
                                                        Text(if (w.id == activeId) "Đang dùng" else w.id.take(6), fontSize = 12.sp)
                                                    }
                                                    Box {
                                                        IconButton(onClick = { expanded = true }) { Text("⋮") }
                                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                            DropdownMenuItem(text = { Text("Chuyển sang ví này") }, onClick = {
                                                                expanded = false
                                                                lifecycleScope.launch(Dispatchers.IO) {
                                                                    wm.switchTo(w.id); wm.init()
                                                                    withContext(Dispatchers.Main) {
                                                                        wallets = wm.getAll()
                                                                        activeId = w.id
                                                                        currentAddress = wm.getAddress()
                                                                    }
                                                                }
                                                            })
                                                            // FIX 2: Xóa ví không văng app
                                                            DropdownMenuItem(text = { Text("Xóa ví") }, onClick = {
                                                                expanded = false
                                                                lifecycleScope.launch(Dispatchers.IO) {
                                                                    try {
                                                                        val wasActive = w.id == activeId
                                                                        wm.delete(w.id)
                                                                        val remaining = wm.getAll()
                                                                        withContext(Dispatchers.Main) {
                                                                            wallets = remaining
                                                                            if (remaining.isEmpty()) {
                                                                                hasWallet = false // về màn tạo ví
                                                                            } else if (wasActive) {
                                                                                // chuyển sang ví đầu tiên còn lại
                                                                                lifecycleScope.launch(Dispatchers.IO) {
                                                                                    wm.switchTo(remaining.first().id); wm.init()
                                                                                    withContext(Dispatchers.Main) {
                                                                                        activeId = remaining.first().id
                                                                                        currentAddress = wm.getAddress()
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        withContext(Dispatchers.Main) {
                                                                            Toast.makeText(ctx, "Lỗi xóa: ${e.message}", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                            })
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        item {
                                            Button(onClick = {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    wm.create("Ví " + (wallets.size + 1)); wm.init()
                                                    withContext(Dispatchers.Main) { wallets = wm.getAll() }
                                                }
                                            }, modifier = Modifier.fillMaxWidth()) { Text("THÊM VÍ MỚI") }
                                            Spacer(Modifier.height(16.dp))
                                            // ... phần gửi/nhận giữ nguyên ...
                                            Text("Gửi BTC", fontWeight = FontWeight.Bold)
                                            OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("Địa chỉ nhận") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { qrCallback = { to = it }; qrLauncher.launch(ScanOptions()) }) { Text("QR") } })
                                            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Số BTC") }, modifier = Modifier.fillMaxWidth())
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = feeSel == 0, onClick = { feeSel = 0 }); Text("Chậm (${fees.slow})")
                                                Spacer(Modifier.width(8.dp)); RadioButton(selected = feeSel == 1, onClick = { feeSel = 1 }); Text("Thường (${fees.normal})")
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = feeSel == 2, onClick = { feeSel = 2 }); Text("Nhanh (${fees.fast})")
                                                Spacer(Modifier.width(8.dp)); RadioButton(selected = feeSel == 3, onClick = { feeSel = 3 }); Text("Tùy chỉnh")
                                            }
                                            if (feeSel == 3) OutlinedTextField(value = customFee, onValueChange = { customFee = it }, label = { Text("sat/vB") })
                                            Button(onClick = { CoroutineScope(Dispatchers.IO).launch { result = wm.send(to, amount.toDoubleOrNull() ?: 0.0, feeRate) } }, modifier = Modifier.fillMaxWidth()) { Text("GỬI") }
                                            if (result.isNotEmpty()) Text(result, fontSize = 12.sp)
                                            Spacer(Modifier.height(16.dp))
                                            Text("Nhận BTC", fontWeight = FontWeight.Bold)
                                            val qr = remember(currentAddress) {
                                                val size = 512
                                                val bits = QRCodeWriter().encode(if (currentAddress.isEmpty()) "empty" else currentAddress, BarcodeFormat.QR_CODE, size, size)
                                                Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                                                    for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (bits.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                                }
                                            }
                                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image(bitmap = qr.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp))
                                                Spacer(Modifier.height(8.dp))
                                                SelectionContainer { Text(text = currentAddress, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Button(onClick = { val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("btc", currentAddress)); Toast.makeText(ctx, "Đã copy", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("COPY ĐỊA CHỈ") }
                                            Spacer(Modifier.height(8.dp))
                                            Button(onClick = { showSeed = true }, modifier = Modifier.fillMaxWidth()) { Text("XUẤT SEED") }
                                        }
                                    }
                                    if (showSeed) {
                                        val seedText = wm.getSeed()
                                        val seedQr = remember(seedText) {
                                            val size = 400
                                            val bits = QRCodeWriter().encode(seedText, BarcodeFormat.QR_CODE, size, size)
                                            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                                                for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (bits.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                            }
                                        }
                                        AlertDialog(onDismissRequest = { showSeed = false },
                                            confirmButton = { TextButton(onClick = { val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("seed", seedText)); Toast.makeText(ctx, "Đã copy", Toast.LENGTH_SHORT).show(); showSeed = false }) { Text("Copy") } },
                                            title = { Text("Seed") },
                                            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image(bitmap = seedQr.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp))
                                                Spacer(Modifier.height(12.dp))
                                                SelectionContainer { Text(seedText, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                                            }}
                                        )
                                    }
                                }
                                2 -> {
                                    var api by remember { mutableStateOf(wm.getFeeApiUrl()) }
                                    var refresh by remember { mutableStateOf(wm.getRefreshSec().toString()) }
                                    var custom by remember { mutableStateOf(wm.getDefaultCustomFee().toString()) }
                                    val ctx = LocalContext.current
                                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                                        Text("Tùy chỉnh", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        Spacer(Modifier.height(16.dp))
                                        OutlinedTextField(value = api, onValueChange = { api = it }, label = { Text("API phí") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(value = refresh, onValueChange = { refresh = it }, label = { Text("Refresh (giây)") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Phí mặc định") }, modifier = Modifier.fillMaxWidth())
                                        Button(onClick = { wm.setFeeApiUrl(api); wm.setRefreshSec(refresh.toLongOrNull() ?: 60); wm.setDefaultCustomFee(custom.toLongOrNull() ?: 10); Toast.makeText(ctx, "Đã lưu", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("LƯU") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}