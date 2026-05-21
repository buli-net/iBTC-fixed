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
        
        // FIX KHỞI ĐỘNG: dọn ví hỏng nếu có
        try {
            if (wm.hasWallets() && wm.getActive() == null) {
                val all = wm.getAll()
                if (all.isNotEmpty()) wm.switchTo(all.first().id)
            }
        } catch (_: Exception) {
            try { deleteDatabase("wallets.db") } catch (_: Exception) {}
        }
        
        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var price by remember { mutableStateOf(0.0) }
                var activeId by remember { mutableStateOf("") }

                LaunchedEffect(hasWallet) {
                    if (hasWallet) {
                        withContext(Dispatchers.IO) {
                            try {
                                wm.init()
                                activeId = wm.getActive()?.id ?: ""
                                repeat(3) {
                                    val p = wm.price()
                                    if (p > 0) { price = p; return@repeat }
                                    delay(2000)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                LaunchedEffect(price, hasWallet) {
                    while (hasWallet) {
                        delay(30000)
                        withContext(Dispatchers.IO) {
                            try {
                                val p = wm.price()
                                if (p > 0) price = p
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
                                        try {
                                            wm.create(name)
                                            wm.init()
                                            withContext(Dispatchers.Main) { hasWallet = true }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
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
                                        try {
                                            if (wm.import(name, seed) != null) {
                                                wm.init()
                                                withContext(Dispatchers.Main) { hasWallet = true }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@MainActivity, "Lỗi import", Toast.LENGTH_LONG).show()
                                            }
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
                    Scaffold(
                        topBar = {
                            TabRow(selectedTabIndex = tab) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Ví", modifier = Modifier.padding(12.dp)) }
                                Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Quản lý", modifier = Modifier.padding(12.dp)) }
                                Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("Tùy chỉnh", modifier = Modifier.padding(12.dp)) }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            if (!wm.isReady() && activeId.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(8.dp))
                                        Text("Đang tải ví...")
                                    }
                                }
                            } else when (tab) {
                                0 -> {
                                    var balance by remember { mutableStateOf(0.0) }
                                    var progress by remember { mutableStateOf(0) }
                                    var status by remember { mutableStateOf("Chưa sync") }
                                    var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
                                    val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

                                    LaunchedEffect(activeId) {
                                        wm.onProgress { p, t -> progress = p; status = t }
                                        withContext(Dispatchers.IO) {
                                            try {
                                                balance = wm.getBalance()
                                                txs = wm.getTransactions()
                                                val p = wm.price()
                                                if (p > 0) price = p
                                            } catch (_: Exception) {}
                                        }
                                    }

                                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text("Số dư:")
                                                Text("%.8f BTC".format(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                                Text("≈ $%.2f".format(balance * price))
                                                Text(status, fontSize = 12.sp)
                                                if (progress in 1..99) {
                                                    LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    status = "Đang sync..."
                                                    try {
                                                        balance = wm.getBalance()
                                                        txs = wm.getTransactions()
                                                        val p = wm.price()
                                                        if (p > 0) price = p
                                                        status = "Đã đồng bộ"
                                                    } catch (_: Exception) {
                                                        status = "Lỗi sync"
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("SYNC NGAY") }

                                        Spacer(Modifier.height(16.dp))
                                        Text("Lịch sử", fontWeight = FontWeight.Bold)
                                        LazyColumn(Modifier.fillMaxSize()) {
                                            items(txs) { tx ->
                                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                }
                                1 -> {
                                    val ctx = LocalContext.current
                                    var wallets by remember { mutableStateOf(wm.getAll()) }
                                    var to by remember { mutableStateOf("") }
                                    var amount by remember { mutableStateOf("") }
                                    var feeSel by remember { mutableStateOf(1) }
                                    var customFee by remember { mutableStateOf(wm.getDefaultCustomFee().toString()) }
                                    var result by remember { mutableStateOf("") }
                                    var fees by remember { mutableStateOf(FeeRates(5, 10, 20)) }
                                    var currentAddress by remember { mutableStateOf("") }
                                    var showSeed by remember { mutableStateOf(false) }

                                    LaunchedEffect(activeId) {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                fees = wm.getFeeRates()
                                                currentAddress = wm.getAddress()
                                                val p = wm.price()
                                                if (p > 0) price = p
                                            } catch (_: Exception) {}
                                        }
                                    }

                                    val feeRate = when (feeSel) {
                                        0 -> fees.slow
                                        1 -> fees.normal
                                        2 -> fees.fast
                                        else -> customFee.toLongOrNull() ?: fees.normal
                                    }

                                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                                        item { Text("Danh sách ví", fontWeight = FontWeight.Bold) }
                                        items(wallets, key = { it.id }) { w ->
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
                                                            DropdownMenuItem(
                                                                text = { Text("Chuyển sang ví này") },
                                                                onClick = {
                                                                    expanded = false
                                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            wm.stop()
                                                                            wm.switchTo(w.id)
                                                                            wm.init()
                                                                            withContext(Dispatchers.Main) {
                                                                                wallets = wm.getAll()
                                                                                activeId = w.id
                                                                                currentAddress = wm.getAddress()
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            withContext(Dispatchers.Main) {
                                                                                Toast.makeText(ctx, "Lỗi chuyển ví", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Xóa ví") },
                                                                onClick = {
                                                                    expanded = false
                                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            val wasActive = w.id == activeId
                                                                            try { wm.stop() } catch (_: Exception) {}
                                                                            wm.delete(w.id)
                                                                            val remaining = wm.getAll()
                                                                            withContext(Dispatchers.Main) {
                                                                                wallets = remaining
                                                                                if (remaining.isEmpty()) {
                                                                                    hasWallet = false
                                                                                } else {
                                                                                    val nextId = if (wasActive) remaining.first().id else activeId
                                                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                                                        try {
                                                                                            wm.switchTo(nextId)
                                                                                            wm.init()
                                                                                            withContext(Dispatchers.Main) {
                                                                                                activeId = nextId
                                                                                                currentAddress = try { wm.getAddress() } catch (_: Exception) { "" }
                                                                                            }
                                                                                        } catch (_: Exception) {
                                                                                            withContext(Dispatchers.Main) { hasWallet = false }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            withContext(Dispatchers.Main) {
                                                                                Toast.makeText(ctx, "Lỗi xóa: ${e.message}", Toast.LENGTH_LONG).show()
                                                                                hasWallet = wm.hasWallets()
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
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        try {
                                                            wm.create("Ví " + (wallets.size + 1))
                                                            wm.init()
                                                            withContext(Dispatchers.Main) {
                                                                wallets = wm.getAll()
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("THÊM VÍ MỚI") }

                                            Spacer(Modifier.height(16.dp))
                                            Text("Gửi BTC", fontWeight = FontWeight.Bold)
                                            OutlinedTextField(
                                                value = to,
                                                onValueChange = { to = it },
                                                label = { Text("Địa chỉ nhận") },
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = {
                                                    TextButton(onClick = {
                                                        qrCallback = { to = it }
                                                        qrLauncher.launch(ScanOptions())
                                                    }) { Text("QR") }
                                                }
                                            )
                                            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Số BTC") }, modifier = Modifier.fillMaxWidth())

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = feeSel == 0, onClick = { feeSel = 0 })
                                                Text("Chậm (${fees.slow})")
                                                Spacer(Modifier.width(8.dp))
                                                RadioButton(selected = feeSel == 1, onClick = { feeSel = 1 })
                                                Text("Thường (${fees.normal})")
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = feeSel == 2, onClick = { feeSel = 2 })
                                                Text("Nhanh (${fees.fast})")
                                                Spacer(Modifier.width(8.dp))
                                                RadioButton(selected = feeSel == 3, onClick = { feeSel = 3 })
                                                Text("Tùy chỉnh")
                                            }
                                            if (feeSel == 3) {
                                                OutlinedTextField(value = customFee, onValueChange = { customFee = it }, label = { Text("sat/vB") }, modifier = Modifier.fillMaxWidth())
                                            }
                                            Button(
                                                onClick = {
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        result = try {
                                                            wm.send(to, amount.toDoubleOrNull() ?: 0.0, feeRate)
                                                        } catch (e: Exception) {
                                                            "Lỗi: ${e.message}"
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("GỬI") }
                                            if (result.isNotEmpty()) {
                                                Text(result, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                            }

                                            Spacer(Modifier.height(16.dp))
                                            Text("Nhận BTC", fontWeight = FontWeight.Bold)

                                            val qr = remember(currentAddress) {
                                                val size = 512
                                                val data = if (currentAddress.isBlank()) "bitcoin:" else currentAddress
                                                val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
                                                Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                                                    for (x in 0 until size) {
                                                        for (y in 0 until size) {
                                                            setPixel(x, y, if (bits.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                                        }
                                                    }
                                                }
                                            }

                                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image