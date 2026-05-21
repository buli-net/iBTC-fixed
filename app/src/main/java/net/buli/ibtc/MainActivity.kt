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
                var activeId by remember { mutableStateOf(wm.getActive()?.id ?: "") }

                LaunchedEffect(hasWallet) {
                    if (hasWallet && wm.hasWallets()) {
                        withContext(Dispatchers.IO) {
                            wm.init()
                            price = wm.price()
                            activeId = wm.getActive()?.id ?: ""
                        }
                    }
                }

                if (!hasWallet) {
                    var showCreate by remember { mutableStateOf(false) }
                    var showImport by remember { mutableStateOf(false) }
                    
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        Arrangement.Center,
                        Alignment.CenterHorizontally
                    ) {
                        Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { showCreate = true },
                            Modifier.fillMaxWidth()
                        ) { Text("TẠO VÍ MỚI") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showImport = true },
                            Modifier.fillMaxWidth()
                        ) { Text("IMPORT SEED") }
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
                                        hasWallet = true
                                    }
                                }) { Text("Tạo") }
                            },
                            title = { Text("Tên ví") },
                            text = { OutlinedTextField(name, { name = it }) }
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
                                            hasWallet = true
                                        }
                                    }
                                }) { Text("Import") }
                            },
                            title = { Text("Import") },
                            text = {
                                Column {
                                    OutlinedTextField(name, { name = it }, label = { Text("Tên") })
                                    OutlinedTextField(seed, { seed = it }, label = { Text("Seed") })
                                }
                            }
                        )
                    }
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
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else when (tab) {
                                0 -> {
                                    var balance by remember { mutableStateOf(0.0) }
                                    var progress by remember { mutableStateOf(0) }
                                    var status by remember { mutableStateOf("Chưa sync") }
                                    var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
                                    val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

                                    LaunchedEffect(activeId) {
                                        wm.onProgress { p, t ->
                                            progress = p
                                            status = t
                                        }
                                        withContext(Dispatchers.IO) {
                                            balance = wm.getBalance()
                                            txs = wm.getTransactions()
                                            price = wm.price()
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
                                                Text(
                                                    "%.8f BTC".format(balance),
                                                    fontSize = 28.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text("≈ $%.2f".format(balance * price))
                                                Text(status, fontSize = 12.sp)
                                                if (progress in 1..99) {
                                                    LinearProgressIndicator(
                                                        progress / 100f,
                                                        Modifier.fillMaxWidth().padding(top = 8.dp)
                                                    )
                                                }
                                            }
                                        }





                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    status = "Đang sync..."
                                                    balance = wm.getBalance()
                                                    txs = wm.getTransactions()
                                                    price = wm.price()
                                                    status = "Đã đồng bộ"
                                                }
                                            },
                                            Modifier.fillMaxWidth()
                                        ) { Text("SYNC NGAY") }

                                        Spacer(Modifier.height(16.dp))
                                        Text("Lịch sử", fontWeight = FontWeight.Bold)
                                        LazyColumn(Modifier.fillMaxSize()) {
                                            items(txs) { tx ->
                                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                    Row(Modifier.padding(12.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(tx.type, fontWeight = FontWeight.Bold)
                                                            Text("%.8f".format(tx.amount))
                                                            Text(
                                                                formatter.format(tx.time),
                                                                fontSize = 11.sp
                                                            )
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
                                    var renameId by remember { mutableStateOf<String?>(null) }
                                    var detailId by remember { mutableStateOf<String?>(null) }
                                    var fees by remember { mutableStateOf(FeeRates(5, 10, 20)) }
                                    var currentAddress by remember { mutableStateOf(wm.getAddress()) }
                                    var showSeed by remember { mutableStateOf(false) }

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

                                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                                        item {
                                            Text("Danh sách ví", fontWeight = FontWeight.Bold)
                                        }
                                        items(wallets) { w ->
                                            var expanded by remember { mutableStateOf(false) }
                                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                Row(
                                                    Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(w.name, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            if (w.id == wm.getActive()?.id) "Đang dùng" else w.id.take(6),
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    Box {
                                                        IconButton(onClick = { expanded = true }) {
                                                            Text("⋮")
                                                        }
                                                        DropdownMenu(expanded, { expanded = false }) {
                                                            DropdownMenuItem(
                                                                text = { Text("Chuyển") },
                                                                onClick = {
                                                                    expanded = false
                                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                                        wm.switchTo(w.id)
                                                                        wallets = wm.getAll()
                                                                        currentAddress = wm.getAddress()
                                                                        activeId = w.id
                                                                    }
                                                                }
                                                            )








                                                            DropdownMenuItem(
                                                                text = { Text("Đổi tên") },
                                                                onClick = { expanded = false; renameId = w.id }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Chi tiết") },
                                                                onClick = { expanded = false; detailId = w.id }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Xóa") },
                                                                onClick = {
                                                                    expanded = false
                                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                                        val wasActive = w.id == wm.getActive()?.id
                                                                        wm.delete(w.id)
                                                                        wallets = wm.getAll()
                                                                        if (wallets.isEmpty()) {
                                                                            withContext(Dispatchers.Main) {
                                                                                hasWallet = false
                                                                            }
                                                                        } else {
                                                                            if (wasActive) {
                                                                                wm.switchTo(wallets.first().id)
                                                                                wm.init()
                                                                            }
                                                                            withContext(Dispatchers.Main) {
                                                                                currentAddress = wm.getAddress()
                                                                                activeId = wm.getActive()?.id ?: ""
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
                                            Button(
                                                onClick = {
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        wm.create("Ví " + (wallets.size + 1))
                                                        wm.init()
                                                        wallets = wm.getAll()
                                                        currentAddress = wm.getAddress()
                                                    }
                                                },
                                                Modifier.fillMaxWidth()
                                            ) { Text("THÊM VÍ") }

                                            Spacer(Modifier.height(16.dp))
                                            Text("Gửi BTC", fontWeight = FontWeight.Bold)
                                            OutlinedTextField(
                                                to, { to = it },
                                                label = { Text("Địa chỉ") },
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = {
                                                    TextButton(onClick = {
                                                        qrCallback = { to = it }
                                                        qrLauncher.launch(ScanOptions())
                                                    }) { Text("QR") }
                                                }
                                            )
                                            OutlinedTextField(
                                                amount, { amount = it },
                                                label = { Text("Số BTC") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(feeSel == 0, { feeSel = 0 }); Text("Chậm (${fees.slow})")
                                                Spacer(Modifier.width(8.dp))
                                                RadioButton(feeSel == 1, { feeSel = 1 }); Text("Thường (${fees.normal})")
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(feeSel == 2, { feeSel = 2 }); Text("Nhanh (${fees.fast})")
                                                Spacer(Modifier.width(8.dp))
                                                RadioButton(feeSel == 3, { feeSel = 3 }); Text("Tùy")
                                            }
                                            if (feeSel == 3) {
                                                OutlinedTextField(customFee, { customFee = it }, label = { Text("sat/vB") })
                                            }
                                            Button(
                                                onClick = {
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        result = wm.send(to, amount.toDoubleOrNull() ?: 0.0, feeRate)
                                                    }
                                                },
                                                Modifier.fillMaxWidth()
                                            ) { Text("GỬI") }
                                            if (result.isNotEmpty()) Text(result, fontSize = 12.sp)

                                            Spacer(Modifier.height(16.dp))
                                            Text("Nhận BTC", fontWeight = FontWeight.Bold)
                                            val qr = remember(currentAddress) {
                                                val size = 512
                                                val bits = QRCodeWriter().encode(currentAddress, BarcodeFormat.QR_CODE, size, size)
                                                Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                                                    for (x in 0 until size) for (y in 0 until size) {
                                                        setPixel(x, y, if (bits.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                                    }
                                                }
                                            }
                                            Image(qr.asImageBitmap(), null, Modifier.size(200.dp))
                                            Text(currentAddress, fontSize = 12.sp)
                                            Button(
                                                onClick = {
                                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("btc", currentAddress))
                                                },
                                                Modifier.fillMaxWidth()
                                            ) { Text("COPY") }
                                            Button({ showSeed = true }, Modifier.fillMaxWidth()) { Text("XUẤT SEED") }
                                        }
                                    }
                                    if (showSeed) {
                                        AlertDialog(
                                            onDismissRequest = { showSeed = false },
                                            confirmButton = { TextButton({ showSeed = false }) { Text("Đóng") } },
                                            title = { Text("Seed") },
                                            text = { SelectionContainer { Text(wm.getSeed()) } }
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
                                        OutlinedTextField(api, { api = it }, label = { Text("API") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(refresh, { refresh = it }, label = { Text("Refresh") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(custom, { custom = it }, label = { Text("Phí") }, modifier = Modifier.fillMaxWidth())
                                        Button(
                                            onClick = {
                                                wm.setFeeApiUrl(api)
                                                wm.setRefreshSec(refresh.toLongOrNull() ?: 60)
                                                wm.setDefaultCustomFee(custom.toLongOrNull() ?: 10)
                                                Toast.makeText(ctx, "Đã lưu", Toast.LENGTH_SHORT).show()
                                            },
                                            Modifier.fillMaxWidth()
                                        ) { Text("LƯU") }
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