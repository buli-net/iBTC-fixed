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
    private val qrLauncher = registerForActivityResult(ScanContract()) { result -> result.contents?.let { qrCallback?.invoke(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var price by remember { mutableStateOf(0.0) }
                LaunchedEffect(hasWallet) { if (hasWallet) withContext(Dispatchers.IO) { wm.init(); price = wm.price() } }

                if (!hasWallet) {
                    Onboarding(
                        onCreate = { name -> lifecycleScope.launch(Dispatchers.IO) { wm.create(name); wm.init(); hasWallet = true } },
                        onImport = { name, seed -> lifecycleScope.launch(Dispatchers.IO) { if (wm.import(name, seed) != null) { wm.init(); hasWallet = true } } }
                    )
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
                            if (!wm.isReady()) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                            else when (tab) {
                                0 -> WalletTab(price)
                                1 -> ManageTab()
                                2 -> SettingsTab()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable fun Onboarding(onCreate: (String) -> Unit, onImport: (String, String) -> Unit) { /* giữ nguyên như bản trước */ 
        var showCreate by remember { mutableStateOf(false) }
        var showImport by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button({ showCreate = true }, Modifier.fillMaxWidth()) { Text("TẠO VÍ MỚI") }
            OutlinedButton({ showImport = true }, Modifier.fillMaxWidth()) { Text("IMPORT SEED") }
        }
        if (showCreate) { var n by remember { mutableStateOf("") }; AlertDialog({ showCreate = false }, { TextButton({ showCreate = false; onCreate(n) }) { Text("Tạo") } }, title = { Text("Tên ví") }, text = { OutlinedTextField(n, { n = it }, label = { Text("Tên") }) }) }
        if (showImport) { var n by remember { mutableStateOf("") }; var s by remember { mutableStateOf("") }; AlertDialog({ showImport = false }, { TextButton({ showImport = false; onImport(n, s) }) { Text("Import") } }, title = { Text("Import") }, text = { Column { OutlinedTextField(n, { n = it }, label = { Text("Tên") }); OutlinedTextField(s, { s = it }, label = { Text("12 từ") }) } }) }
    }

    @Composable fun WalletTab(price: Double) {
        var bal by remember { mutableStateOf(wm.getBalance()) }
        var pct by remember { mutableStateOf(0) }; var txt by remember { mutableStateOf("Chưa sync") }
        LaunchedEffect(Unit) { wm.onProgress { p, t -> pct = p; txt = t } }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { wm.sync(); bal = wm.getBalance() } }
        // Auto refresh theo thời gian người dùng cài
        LaunchedEffect(Unit) { while (true) { delay(wm.getRefreshSec() * 1000); withContext(Dispatchers.IO) { bal = wm.getBalance() } } }
        Column(Modifier.fillMaxSize().padding(24.dp), Alignment.CenterHorizontally) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Số dư:"); Text("%.8f BTC".format(bal), fontSize = 28.sp, FontWeight.Bold); Text("≈ $%.2f".format(bal * price)); Text(txt, fontSize = 12.sp); if (pct in 1..99) LinearProgressIndicator(pct / 100f, Modifier.fillMaxWidth().padding(top = 8.dp)) } }
            Spacer(Modifier.height(16.dp)); Button({ CoroutineScope(Dispatchers.IO).launch { wm.sync(); delay(2000); bal = wm.getBalance() } }, Modifier.fillMaxWidth()) { Text("SYNC NGAY") }
        }
    }

    @Composable fun ManageTab() {
        val ctx = LocalContext.current
        var wallets by remember { mutableStateOf(wm.getAll()) }
        var to by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }
        var feeSel by remember { mutableStateOf(1) }; var custom by remember { mutableStateOf(wm.getDefaultCustomFee().toString()) }
        var res by remember { mutableStateOf("") }; var fees by remember { mutableStateOf(FeeRates(5, 10, 20)) }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { fees = wm.getFeeRates() } }
        val feeRate = when (feeSel) { 0 -> fees.slow; 1 -> fees.normal; 2 -> fees.fast; else -> custom.toLongOrNull() ?: fees.normal }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item { Text("Danh sách ví", FontWeight.Bold) }
            items(wallets) { w -> /* ... giữ nguyên code danh sách ví như bản trước ... */ }
            item {
                Text("Gửi BTC", FontWeight.Bold)
                OutlinedTextField(to, { to = it }, label = { Text("Địa chỉ") }, trailingIcon = { TextButton({ qrCallback = { to = it }; qrLauncher.launch(ScanOptions()) }) { Text("QR") } }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amt, { amt = it }, label = { Text("Số BTC") }, modifier = Modifier.fillMaxWidth())
                Row { RadioButton(feeSel == 0, { feeSel = 0 }); Text("Chậm (${fees.slow})"); RadioButton(feeSel == 1, { feeSel = 1 }); Text("Thường (${fees.normal})"); RadioButton(feeSel == 2, { feeSel = 2 }); Text("Nhanh (${fees.fast})"); RadioButton(feeSel == 3, { feeSel = 3 }); Text("Tùy") }
                if (feeSel == 3) OutlinedTextField(custom, { custom = it }, label = { Text("sat/vB") })
                Button({ CoroutineScope(Dispatchers.IO).launch { res = wm.send(to, amt.toDoubleOrNull() ?: 0.0, feeRate); wm.setDefaultCustomFee(feeRate) } }, Modifier.fillMaxWidth()) { Text("GỬI") }
                if (res.isNotEmpty()) Text(res)
            }
        }
    }

    // TAB MỚI: TÙY CHỈNH
    @Composable fun SettingsTab() {
        var apiUrl by remember { mutableStateOf(wm.getFeeApiUrl()) }
        var refresh by remember { mutableStateOf(wm.getRefreshSec().toString()) }
        var customFee by remember { mutableStateOf(wm.getDefaultCustomFee().toString()) }
        val ctx = LocalContext.current
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Tùy chỉnh nâng cao", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(apiUrl, { apiUrl = it }, label = { Text("API phí (mempool.space)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(refresh, { refresh = it }, label = { Text("Auto-refresh (giây)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(customFee, { customFee = it }, label = { Text("Phí tùy chỉnh mặc định (sat/vB)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                wm.setFeeApiUrl(apiUrl)
                wm.setRefreshSec(refresh.toLongOrNull() ?: 60)
                wm.setDefaultCustomFee(customFee.toLongOrNull() ?: 10)
                Toast.makeText(ctx, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("LƯU") }
        }
    }

    private fun generateQr(text: String): Bitmap { val s = 512; val b = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, s, s); return Bitmap.createBitmap(s, s, Bitmap.Config.RGB_565).apply { for (x in 0 until s) for (y in 0 until s) setPixel(x, y, if (b.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
}