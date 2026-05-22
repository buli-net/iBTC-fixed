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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    private val qrLauncher = registerForActivityResult(ScanContract()) { result -> result.contents?.let { qrCallback?.invoke(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(60000)
                try { wm.init(); wm.getBalance(); wm.price() } catch (_: Exception) {}
            }
        }
        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var isLocked by remember { mutableStateOf(false) }
                var currentId by remember { mutableStateOf("") }
                var price by remember { mutableStateOf(0.0) }
                var walletName by remember { mutableStateOf("") }
                val context = LocalContext.current

                LaunchedEffect(hasWallet) {
                    if (hasWallet) {
                        currentId = wm.getActiveId() ?: ""
                        isLocked = true
                        walletName = ""
                    }
                }

                // MÀN KHÓA
                if (isLocked && hasWallet) {
                    var pass by remember { mutableStateOf("") }
                    var err by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("🔒 Ví Bitcoin", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(pass, { pass = it; err = "" }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val ok = wm.unlock(currentId, pass)
                                withContext(Dispatchers.Main) {
                                    if (ok) { isLocked = false; wm.init(); walletName = wm.getActive()?.name ?: "" } else err = "Sai mật khẩu"
                                }
                            }
                        }, Modifier.fillMaxWidth()) { Text("MỞ KHÓA") }
                    }
                    return@MaterialTheme
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
                        var p1 by remember { mutableStateOf("") }
                        var p2 by remember { mutableStateOf("") }
                        var err by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showCreate = false }, confirmButton = {
                            TextButton(onClick = {
                                if (p1.length < 4) { err = "Pass >=4"; return@TextButton }
                                if (p1 != p2) { err = "Không khớp"; return@TextButton }
                                showCreate = false
                                lifecycleScope.launch(Dispatchers.IO) {
                                    wm.create(name, p1); wm.init()
                                    withContext(Dispatchers.Main) { hasWallet = true; isLocked = false; walletName = wm.getActive()?.name ?: "" }
                                }
                            }) { Text("Tạo") }
                        }, title = { Text("Tạo ví") }, text = {
                            Column {
                                OutlinedTextField(name, { name = it }, label = { Text("Tên") }, singleLine = true)
                                OutlinedTextField(p1, { p1 = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation())
                                OutlinedTextField(p2, { p2 = it }, label = { Text("Nhập lại") }, visualTransformation = PasswordVisualTransformation())
                                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        })
                    }
                    if (showImport) {
                        var name by remember { mutableStateOf("") }
                        var seed by remember { mutableStateOf("") }
                        var p1 by remember { mutableStateOf("") }
                        var p2 by remember { mutableStateOf("") }
                        var err by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showImport = false }, confirmButton = {
                            TextButton(onClick = {
                                if (p1.length < 4) { err = "Pass >=4"; return@TextButton }
                                if (p1 != p2) { err = "Không khớp"; return@TextButton }
                                showImport = false
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val ok = wm.import(name, seed, p1) != null
                                    if (ok) wm.init()
                                    withContext(Dispatchers.Main) { if (ok) { hasWallet = true; isLocked = false; walletName = wm.getActive()?.name ?: "" } }
                                }
                            }) { Text("Import") }
                        }, title = { Text("Import") }, text = {
                            Column {
                                OutlinedTextField(name, { name = it }, label = { Text("Tên") }, singleLine = true)
                                OutlinedTextField(seed, { seed = it }, label = { Text("Seed 12 từ") })
                                OutlinedTextField(p1, { p1 = it }, label = { Text("Mật khẩu mới") }, visualTransformation = PasswordVisualTransformation())
                                OutlinedTextField(p2, { p2 = it }, label = { Text("Nhập lại") }, visualTransformation = PasswordVisualTransformation())
                                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        })
                    }
                } else {
                    // CODE GỐC CỦA MÀY TỪ ĐÂY - GIỮ NGUYÊN
                    var tab by remember { mutableStateOf(0) }
                    var showMenu by remember { mutableStateOf(false) }
                    var showRename by remember { mutableStateOf(false) }
                    var showDetails by remember { mutableStateOf(false) }
                    Scaffold(topBar = {
                        TopAppBar(title = { Text(walletName) }, actions = {
                            IconButton(onClick = { showMenu = true }) { Text("⋮", fontSize = 20.sp) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Đổi tên") }, onClick = { showMenu = false; showRename = true })
                                DropdownMenuItem(text = { Text("Chi tiết ví") }, onClick = { showMenu = false; showDetails = true })
                                DropdownMenuItem(text = { Text("Khóa ví") }, onClick = { showMenu = false; lifecycleScope.launch(Dispatchers.IO){ wm.stop() }; isLocked = true })
                                DropdownMenuItem(text = { Text("Xóa ví") }, onClick = {
                                    showMenu = false
                                    val id = wm.getActive()?.id
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try { wm.stop() } catch (_: Exception) {}
                                        if (id != null) wm.delete(id)
                                        withContext(Dispatchers.Main) { hasWallet = false }
                                    }
                                })
                            }
                        })
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
                                    var transactions by remember { mutableStateOf(listOf<TransactionInfo>()) }
                                    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    LaunchedEffect(Unit) {
                                        wm.onProgress { p, t -> lifecycleScope.launch(Dispatchers.Main) { progress = p; status = t } }
                                        withContext(Dispatchers.IO) { balance = wm.getBalance(); price = wm.price(); transactions = wm.getTransactions() }
                                        while (true) {
                                            delay(60000)
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val nb = wm.getBalance(); val np = wm.price(); val nt = wm.getTransactions()
                                                    withContext(Dispatchers.Main) { balance = nb; price = np; transactions = nt; status = "Auto sync " + timeFormat.format(Date()) }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                    Column(Modifier.padding(16.dp)) {
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text("Số dư:"); Text("%.8f BTC".format(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                                Text("≈ $%.2f / BTC".format(price)); Text("$status • Giá tự động", fontSize = 12.sp)
                                                if (progress in 1..99) LinearProgressIndicator(progress = progress / 100f, Modifier.fillMaxWidth().padding(top = 8.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = { lifecycleScope.launch(Dispatchers.IO) { val nb = wm.getBalance(); val np = wm.price(); val nt = wm.getTransactions(); withContext(Dispatchers.Main){ balance = nb; price = np; transactions = nt; status = "Sync tay"; progress = 100 } } }, Modifier.fillMaxWidth()) { Text("SYNC NGAY") }
                                        Spacer(Modifier.height(16.dp)); Text("Lịch sử", fontWeight = FontWeight.Bold)
                                        LazyColumn(Modifier.fillMaxSize()) { items(transactions) { tx -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(Modifier.padding(12.dp)) { Column(Modifier.weight(1f)) { Text(tx.type, fontWeight = FontWeight.Bold); Text("%.8f".format(tx.amount)); Text(dateFormat.format(tx.time), fontSize = 11.sp) }; Text(tx.txId.take(8), fontSize = 12.sp) } } } }
                                    }
                                } else {
                                    var toAddress by remember { mutableStateOf("") }
                                    var amount by remember { mutableStateOf("") }
                                    var result by remember { mutableStateOf("") }
                                    var receiveAddress by remember { mutableStateOf("") }
                                    var fees by remember { mutableStateOf(FeeRates(5, 10, 20)) }
                                    var feeSelection by remember { mutableStateOf(1) }
                                    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { receiveAddress = wm.getAddress(); fees = wm.getFeeRates() } }
                                    val selectedFee = when (feeSelection) { 0 -> fees.slow; 1 -> fees.normal; else -> fees.fast }
                                    LazyColumn(Modifier.padding(16.dp)) {
                                        item {
                                            Text("Gửi BTC", fontWeight = FontWeight.Bold)
                                            OutlinedTextField(toAddress, { toAddress = it }, label = { Text("Địa chỉ") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { qrCallback = { s -> toAddress = s }; qrLauncher.launch(ScanOptions()) }) { Text("QR") } })
                                            OutlinedTextField(amount, { amount = it }, label = { Text("BTC") }, modifier = Modifier.fillMaxWidth())
                                            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(feeSelection == 0, { feeSelection = 0 }); Text("Chậm"); Spacer(Modifier.width(8.dp)); RadioButton(feeSelection == 1, { feeSelection = 1 }); Text("Thường"); Spacer(Modifier.width(8.dp)); RadioButton(feeSelection == 2, { feeSelection = 2 }); Text("Nhanh") }
                                            Button(onClick = { lifecycleScope.launch(Dispatchers.IO) { result = wm.send(toAddress, amount.toDoubleOrNull() ?: 0.0, selectedFee) } }, Modifier.fillMaxWidth()) { Text("GỬI") }
                                            if (result.isNotEmpty()) Text(result, fontSize = 12.sp)
                                            Spacer(Modifier.height(24.dp)); Text("Nhận BTC", fontWeight = FontWeight.Bold)
                                            val qrBitmap = remember(receiveAddress) { val size = 512; val bm = QRCodeWriter().encode(receiveAddress.ifEmpty { "bitcoin:" }, BarcodeFormat.QR_CODE, size, size); Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply { for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (bm.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
                                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Image(qrBitmap.asImageBitmap(), null, Modifier.size(220.dp)); Spacer(Modifier.height(8.dp)); SelectionContainer { Text(receiveAddress) } }
                                            Button(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("btc", receiveAddress)); Toast.makeText(context, "Đã copy", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()) { Text("COPY") }
                                        }
                                    }
                                }
                            }
                        }
                        if (showRename) {
                            var newName by remember { mutableStateOf(walletName) }
                            AlertDialog(onDismissRequest = { showRename = false }, confirmButton = { TextButton(onClick = { lifecycleScope.launch(Dispatchers.IO) { wm.getActive()?.let { a -> val s = a.seed; wm.delete(a.id); /* giữ pass cũ không đổi */ }; withContext(Dispatchers.Main){ walletName = newName; showRename = false } } }) { Text("Lưu") } }, title = { Text("Đổi tên") }, text = { OutlinedTextField(newName, { newName = it }, singleLine = true) })
                        }
                        if (showDetails) {
                            val seed = wm.getSeed()
                            val address = wm.getAddress()
                            AlertDialog(onDismissRequest = { showDetails = false }, confirmButton = { TextButton({ showDetails = false }) { Text("Đóng") } }, title = { Text("Chi tiết") }, text = { Column { Text("Tên: $walletName", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Địa chỉ:"); SelectionContainer{ Text(address) }; TextButton({ val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("a", address)) }){ Text("Copy") }; Spacer(Modifier.height(8.dp)); Text("Seed:"); SelectionContainer{ Text(seed) } } })
                        }
                    }
                }
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); try { wm.stop() } catch (_: Exception) {} }
}