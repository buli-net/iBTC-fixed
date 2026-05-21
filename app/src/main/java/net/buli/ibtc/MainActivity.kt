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

@OptIn(ExperimentalMaterial3Api::class)
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
                var walletName by remember { mutableStateOf(wm.getActive()?.name ?: "") }

                LaunchedEffect(hasWallet) {
                    if (hasWallet) {
                        withContext(Dispatchers.IO) {
                            try {
                                wm.init()
                                walletName = wm.getActive()?.name ?: ""
                                price = wm.price()
                            } catch (_: Exception) {}
                        }
                    }
                }

                if (!hasWallet) {
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
                        AlertDialog(
                            onDismissRequest = { showCreate = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    showCreate = false
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        wm.create(name)
                                        wm.init()
                                        withContext(Dispatchers.Main) { hasWallet = true; walletName = wm.getActive()?.name ?: "" }
                                    }
                                }) { Text("Tạo") }
                            },
                            title = { Text("Tên ví") },
                            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) }
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
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        if (wm.import(name, seed) != null) {
                                            wm.init()
                                            withContext(Dispatchers.Main) { hasWallet = true; walletName = wm.getActive()?.name ?: "" }
                                        }
                                    }
                                }) { Text("Import") }
                            },
                            title = { Text("Import") },
                            text = {
                                Column {
                                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên") }, singleLine = true)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(value = seed, onValueChange = { seed = it }, label = { Text("Seed 12 từ") })
                                }
                            }
                        )
                    }
                } else {
                    var tab by remember { mutableStateOf(0) }
                    val ctx = LocalContext.current
                    var showMenu by remember { mutableStateOf(false) }
                    var showRename by remember { mutableStateOf(false) }
                    var showDetails by remember { mutableStateOf(false) }

                    Scaffold(topBar = {
                        TopAppBar(
                            title = { Text(walletName) },
                            actions = {
                                IconButton(onClick = { showMenu = true }) { Text("⋮", fontSize = 20.sp) }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Đổi tên") }, onClick = { showMenu = false; showRename = true })
                                    DropdownMenuItem(text = { Text("Chi tiết ví") }, onClick = { showMenu = false; showDetails = true })
                                    DropdownMenuItem(text = { Text("Xóa ví") }, onClick = {
                                        showMenu = false
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            wm.getActive()?.let { wm.delete(it.id) }
                                            wm.stop()
                                            withContext(Dispatchers.Main) { hasWallet = false }
                                        }
                                    })
                                }
                            }
                        )
                    }) { padding ->
                        Box(Modifier.padding(padding)) {
                            Column(Modifier.fillMaxSize()) {
                                TabRow(selectedTabIndex = tab) {
                                    Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Ví", Modifier.padding(12.dp)) }
                                    Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Gửi/Nhận", Modifier.padding(12.dp)) }
                                }
                                if (tab == 0) {
                                    var balance by remember { mutableStateOf(0.0) }
                                    var progress by remember { mutableStateOf(0) }
                                    var status by remember { mutableStateOf("Chưa sync") }
                                    var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
                                    val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

                                    LaunchedEffect(Unit) {
                                        wm.onProgress { p, t -> progress = p; status = t }
                                        withContext(Dispatchers.IO) { balance = wm.getBalance(); txs = wm.getTransactions(); price = wm.price() }
                                    }

                                    Column(Modifier.padding(16.dp)) {
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text("Số dư:")
                                                Text("%.8f BTC".format(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                                Text("≈ $%.2f".format(balance * price))
                                                Text(status, fontSize = 12.sp)
                                                if (progress in 1..99) LinearProgressIndicator(progress = progress/100f, Modifier.fillMaxWidth().padding(top = 8.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = { CoroutineScope(Dispatchers.IO).launch { balance = wm.getBalance(); txs = wm.getTransactions(); price = wm.price() } }, Modifier.fillMaxWidth()) { Text("SYNC") }
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
                                                        Text(tx.txId.take(8), fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    var to by remember { mutableStateOf("") }
                                    var amount by remember { mutableStateOf("") }
                                    var result by remember { mutableStateOf("") }
                                    var address by remember { mutableStateOf("") }
                                    var fees by remember { mutableStateOf(FeeRates(5, 10, 20)) }
                                    var feeSel by remember { mutableStateOf(1) }

                                    LaunchedEffect(Unit) {
                                        withContext(Dispatchers.IO) { address = wm.getAddress(); fees = wm.getFeeRates() }
                                    }

                                    val feeRate = when (feeSel) { 0 -> fees.slow; 1 -> fees.normal; 2 -> fees.fast; else -> 10 }

                                    LazyColumn(Modifier.padding(16.dp)) {
                                        item {
                                            Text("Gửi BTC", fontWeight = FontWeight.Bold)
                                            OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("Địa chỉ") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { qrCallback = { to = it }; qrLauncher.launch(ScanOptions()) }) { Text("QR") } })
                                            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("BTC") }, modifier = Modifier.fillMaxWidth())
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = feeSel == 0, onClick = { feeSel = 0 }); Text("Chậm"); Spacer(Modifier.width(8.dp))
                                                RadioButton(selected = feeSel == 1, onClick = { feeSel = 1 }); Text("Thường"); Spacer(Modifier.width(8.dp))
                                                RadioButton(selected = feeSel == 2, onClick = { feeSel = 2 }); Text("Nhanh")
                                            }
                                            Button(onClick = { CoroutineScope(Dispatchers.IO).launch { result = wm.send(to, amount.toDoubleOrNull() ?: 0.0, feeRate) } }, Modifier.fillMaxWidth()) { Text("GỬI") }
                                            if (result.isNotEmpty()) Text(result, fontSize = 12.sp)
                                            Spacer(Modifier.height(24.dp))
                                            Text("Nhận BTC", fontWeight = FontWeight.Bold)
                                            val qr = remember(address) {
                                                val s = 512; val b = QRCodeWriter().encode(address.ifEmpty { "bitcoin:" }, BarcodeFormat.QR_CODE, s, s)
                                                Bitmap.createBitmap(s, s, Bitmap.Config.RGB_565).apply { for (x in 0 until s) for (y in 0 until s) setPixel(x, y, if (b.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE) }
                                            }
                                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image(qr.asImageBitmap(), null, Modifier.size(220.dp)); Spacer(Modifier.height(8.dp)); SelectionContainer { Text(address) }
                                            }
                                            Button(onClick = { val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("btc", address)); Toast.makeText(ctx, "Đã copy", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()) { Text("COPY") }
                                        }
                                    }
                                }
                            }
                        }

                        if (showRename) {
                            var newName by remember { mutableStateOf(walletName) }
                            AlertDialog(onDismissRequest = { showRename = false }, confirmButton = { TextButton(onClick = { lifecycleScope.launch(Dispatchers.IO) { wm.getActive()?.let { val seed = it.seed; wm.delete(it.id); wm.import(newName, seed); wm.init(); withContext(Dispatchers.Main) { walletName = newName; showRename = false } } } }) { Text("Lưu") } }, title = { Text("Đổi tên ví") }, text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) })
                        }

                        if (showDetails) {
                            val seed = wm.getSeed(); val addr = wm.getAddress()
                            AlertDialog(onDismissRequest = { showDetails = false }, confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Đóng") } }, title = { Text("Chi tiết ví") }, text = { Column { Text("Tên: $walletName", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Địa chỉ:"); SelectionContainer { Text(addr) }; Spacer(Modifier.height(8.dp)); Text("Seed:"); SelectionContainer { Text(seed) } } })
                        }
                    }
                }
            }
        }
    }
}