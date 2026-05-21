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

                LaunchedEffect(hasWallet) {
                    if (hasWallet) {
                        withContext(Dispatchers.IO) {
                            wm.init()
                            price = wm.price()
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
                            }
                        },
                        onImport = { name, seed ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (wm.import(name, seed) != null) {
                                    wm.init()
                                    hasWallet = true
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
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            if (!wm.isReady()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                if (tab == 0) WalletTab(price) else ManageTab()
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
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("TẠO VÍ MỚI") }
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) { Text("IMPORT SEED") }
        }
        if (showCreate) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreate = false },
                confirmButton = { TextButton(onClick = { showCreate = false; onCreate(name) }) { Text("Tạo") } },
                title = { Text("Tên ví") },
                text = { OutlinedTextField(name, { name = it }, label = { Text("Tên") }) }
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
                        OutlinedTextField(name, { name = it }, label = { Text("Tên") })
                        OutlinedTextField(seed, { seed = it }, label = { Text("12 từ seed") })
                    }
                }
            )
        }
    }

    @Composable
    fun WalletTab(price: Double) {
        var balance by remember { mutableStateOf(wm.getBalance()) }
        var pct by remember { mutableStateOf(0) }
        var txt by remember { mutableStateOf("Chưa sync") }
        LaunchedEffect(Unit) { wm.onProgress { p, t -> pct = p; txt = t } }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { wm.sync(); balance = wm.getBalance() } }
        LaunchedEffect(Unit) { while (true) { delay(60000); withContext(Dispatchers.IO) { balance = wm.getBalance() } } }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Số dư:")
                    Text("%.8f BTC".format(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("≈ $%.2f".format(balance * price))
                    Text(txt, fontSize = 12.sp)
                    if (pct in 1..99) LinearProgressIndicator(progress = pct / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { CoroutineScope(Dispatchers.IO).launch { wm.sync(); delay(2000); balance = wm.getBalance() } }, modifier = Modifier.fillMaxWidth()) { Text("SYNC NGAY") }
        }
    }

    @Composable
    fun ManageTab() {
        val ctx = LocalContext.current
        var wallets by remember { mutableStateOf(wm.getAll()) }
        var to by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var feeSel by remember { mutableStateOf(1) }
        var customFee by remember { mutableStateOf("10") }
        var result by remember { mutableStateOf("") }
        var renameId by remember { mutableStateOf<String?>(null) }
        var detailId by remember { mutableStateOf<String?>(null) }
        val feeRate = when (feeSel) { 0 -> 5L; 1 -> 10L; 2 -> 20L; else -> customFee.toLongOrNull() ?: 10L }
        val feeBtc = 250 * feeRate / 1e8

        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item { Text("Danh sách ví", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
            items(wallets) { w ->
                var expanded by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, fontWeight = FontWeight.Bold)
                            Text(if (w.id == wm.getActive()?.id) "Đang dùng" else "ID: ${w.id.take(6)}", fontSize = 12.sp)
                        }
                        Box {
                            IconButton(onClick = { expanded = true }) { Text("⋮") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Chuyển") }, onClick = { expanded = false; lifecycleScope.launch(Dispatchers.IO) { wm.switchTo(w.id); wallets = wm.getAll() } })
                                DropdownMenuItem(text = { Text("Đổi tên") }, onClick = { expanded = false; renameId = w.id })
                                DropdownMenuItem(text = { Text("Chi tiết") }, onClick = { expanded = false; detailId = w.id })
                                DropdownMenuItem(text = { Text("Xóa") }, onClick = { expanded = false; lifecycleScope.launch(Dispatchers.IO) { wm.delete(w.id); wallets = wm.getAll() } })
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { lifecycleScope.launch(Dispatchers.IO) { wm.create("Ví ${wallets.size + 1}"); wm.init(); wallets = wm.getAll() } }, modifier = Modifier.fillMaxWidth()) { Text("THÊM VÍ") }
                Divider(Modifier.padding(vertical = 12.dp))
                Text("Gửi BTC", fontWeight = FontWeight.Bold)
                OutlinedTextField(to, { to = it }, label = { Text("Địa chỉ") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { qrCallback = { to = it }; qrLauncher.launch(ScanOptions()) }) { Text("QR") } })
                OutlinedTextField(amount, { amount = it }, label = { Text("Số BTC") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { listOf("Chậm", "Thường", "Nhanh", "Tùy").forEachIndexed { i, t -> RadioButton(selected = feeSel == i, onClick = { feeSel = i }); Text(t) } }
                if (feeSel == 3) OutlinedTextField(customFee, { customFee = it }, label = { Text("sat/vB") })
                Text("Phí ước tính: %.8f BTC".format(feeBtc))
                Button(onClick = { CoroutineScope(Dispatchers.IO).launch { result = wm.send(to, amount.toDoubleOrNull() ?: 0.0, feeRate) } }, modifier = Modifier.fillMaxWidth()) { Text("GỬI") }
                if (result.isNotEmpty()) Text(result, fontSize = 12.sp)
                Divider(Modifier.padding(vertical = 12.dp))
                Text("Nhận BTC", fontWeight = FontWeight.Bold)
            }
            item {
                val addr = wm.getAddress()
                val qr = remember(addr) { generateQr(addr) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(200.dp))
                    Text(addr, fontSize = 12.sp)
                    Button(onClick = { val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("btc", addr)); Toast.makeText(ctx, "Đã copy", Toast.LENGTH_SHORT).show() }) { Text("COPY") }
                }
                Divider(Modifier.padding(vertical = 12.dp))
                Button(onClick = { Toast.makeText(ctx, wm.getSeed(), Toast.LENGTH_LONG).show() }, modifier = Modifier.fillMaxWidth()) { Text("XUẤT 12 TỪ SEED") }
            }
        }
        renameId?.let { id ->
            var newName by remember { mutableStateOf(wallets.find { it.id == id }?.name ?: "") }
            AlertDialog(onDismissRequest = { renameId = null }, confirmButton = { TextButton(onClick = { wm.rename(id, newName); wallets = wm.getAll(); renameId = null }) { Text("Lưu") } }, title = { Text("Đổi tên ví") }, text = { OutlinedTextField(newName, { newName = it }, label = { Text("Tên mới") }) })
        }
        detailId?.let { id ->
            val w = wallets.find { it.id == id }
            AlertDialog(onDismissRequest = { detailId = null }, confirmButton = { TextButton(onClick = { detailId = null }) { Text("Đóng") } }, title = { Text("Chi tiết ví") }, text = { Text("Tên: ${w?.name}\n\nSeed:\n${w?.seed}") })
        }
    }

    private fun generateQr(text: String): Bitmap {
        val size = 512
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) { for (y in 0 until size) { setPixel(x, y, if (bits.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
        }
    }
}