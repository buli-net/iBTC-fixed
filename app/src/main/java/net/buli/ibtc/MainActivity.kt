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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    // Quản lý ví Bitcoin
    private lateinit var wm: WalletManager
    
    // Callback để nhận kết quả quét QR
    private var qrCallback: ((String) -> Unit)? = null
    
    // Launcher để mở camera quét QR
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { qrCallback?.invoke(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        
        setContent {
            MaterialTheme {
                // State để kiểm tra đã có ví chưa
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                // Giá BTC hiện tại
                var price by remember { mutableStateOf(0.0) }

                // Khởi tạo ví khi app mở
                LaunchedEffect(hasWallet) {
                    if (hasWallet) {
                        withContext(Dispatchers.IO) {
                            wm.init()
                            price = wm.price()
                        }
                    }
                }

                if (!hasWallet) {
                    // Màn hình onboarding: tạo ví mới hoặc import
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
                    // Màn hình chính với 3 tab
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
                                // Hiển thị loading khi ví chưa sẵn sàng
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                when (tab) {
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
    }

    @Composable
    fun Onboarding(onCreate: (String) -> Unit, onImport: (String, String) -> Unit) {
        var showCreate by remember { mutableStateOf(false) }
        var showImport by remember { mutableStateOf(false) }
        
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth()
            ) { 
                Text("TẠO VÍ MỚI") 
            }
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = { showImport = true },
                modifier = Modifier.fillMaxWidth()
            ) { 
                Text("IMPORT SEED") 
            }
        }
        
        // Dialog tạo ví mới
        if (showCreate) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreate = false },
                confirmButton = {
                    TextButton(onClick = { showCreate = false; onCreate(name) }) {
                        Text("Tạo")
                    }
                },
                title = { Text("Tên ví") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tên") }
                    )
                }
            )
        }
        
        // Dialog import ví
        if (showImport) {
            var name by remember { mutableStateOf("") }
            var seed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showImport = false },
                confirmButton = {
                    TextButton(onClick = { showImport = false; onImport(name, seed) }) {
                        Text("Import")
                    }
                },
                title = { Text("Import ví") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Tên") }
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = seed,
                            onValueChange = { seed = it },
                            label = { Text("12 từ seed") }
                        )
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
        var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        
        // Lắng nghe tiến độ sync
        LaunchedEffect(Unit) {
            wm.onProgress { p, t -> 
                pct = p
                txt = t 
            }
        }
        
        // Sync lần đầu
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                wm.sync()
                balance = wm.getBalance()
                txs = wm.getTransactions()
            }
        }
        
        // Tự động refresh số dư theo cài đặt
        LaunchedEffect(Unit) {
            while (true) {
                delay(wm.getRefreshSec() * 1000)
                withContext(Dispatchers.IO) {
                    balance = wm.getBalance()
                    txs = wm.getTransactions()
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Số dư:")
                    Text(
                        "%.8f BTC".format(balance),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("≈ $%.2f".format(balance * price))
                    Spacer(Modifier.height(4.dp))
                    Text(txt, fontSize = 12.sp)
                    if (pct in 1..99) {
                        LinearProgressIndicator(
                            progress = pct / 100f,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        wm.sync()
                        delay(2000)
                        balance = wm.getBalance()
                        txs = wm.getTransactions()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SYNC NGAY")
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text("Lịch sử giao dịch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            
            Spacer(Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                if (txs.isEmpty()) {
                    item {
                        Text(
                            "Chưa có giao dịch",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(txs) { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.type,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.type == "Nhận") 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "%.8f BTC".format(tx.amount),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = dateFormat.format(tx.time),
                                        fontSize = 11.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = tx.txId.take(8) + "...",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ManageTab() {
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

        // Lấy phí từ mạng khi mở tab
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            item {
                Text("Danh sách ví", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            
            items(wallets) { w ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(w.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (w.id == wm.getActive()?.id) "Đang dùng" else "ID: ${w.id.take(6)}",
                                fontSize = 12.sp
                            )
                        }
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Text("⋮")
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Chuyển") },
                                    onClick = {
                                        expanded = false
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            wm.switchTo(w.id)
                                            wallets = wm.getAll()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Đổi tên") },
                                    onClick = { 
                                        expanded = false
                                        renameId = w.id 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Chi tiết") },
                                    onClick = { 
                                        expanded = false
                                        detailId = w.id 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Xóa") },
                                    onClick = {
                                        expanded = false
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            wm.delete(w.id)
                                            wallets = wm.getAll()
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
                            wm.create("Ví ${wallets.size + 1}")
                            wm.init()
                            wallets = wm.getAll()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("THÊM VÍ")
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text("Gửi BTC", fontWeight = FontWeight.Bold)
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("Địa chỉ") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = {
                            qrCallback = { to = it }
                            qrLauncher.launch(ScanOptions())
                        }) {
                            Text("QR")
                        }
                    }
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Số BTC") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text("Phí giao dịch:", fontSize = 14.sp)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = feeSel == 0,
                        onClick = { feeSel = 0 }
                    )
                    Text("Chậm (${fees.slow})")
                    
                    Spacer(Modifier.width(8.dp))
                    
                    RadioButton(
                        selected = feeSel == 1,
                        onClick = { feeSel = 1 }
                    )
                    Text("Thường (${fees.normal})")
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = feeSel == 2,
                        onClick = { feeSel = 2 }
                    )
                    Text("Nhanh (${fees.fast})")
                    
                    Spacer(Modifier.width(8.dp))
                    
                    RadioButton(
                        selected = feeSel == 3,
                        onClick = { feeSel = 3 }
                    )
                    Text("Tùy")
                }
                
                if (feeSel == 3) {
                    OutlinedTextField(
                        value = customFee,
                        onValueChange = { customFee = it },
                        label = { Text("sat/vB tùy chỉnh") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                val feeBtc = 250 * feeRate / 1e8
                Text(
                    text = "Phí ước tính: %.8f BTC".format(feeBtc),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Button(
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            result = wm.send(to, amount.toDoubleOrNull() ?: 0.0, feeRate)
                            wm.setDefaultCustomFee(feeRate)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GỬI")
                }
                
                if (result.isNotEmpty()) {
                    Text(
                        text = result,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text("Nhận BTC", fontWeight = FontWeight.Bold)
                
                Spacer(Modifier.height(8.dp))
            }
            
            item {
                val addr = wm.getAddress()
                val qr = remember(addr) { generateQr(addr) }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = addr,
                        fontSize = 12.sp
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Button(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("btc", addr))
                        Toast.makeText(ctx, "Đã copy", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("COPY")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Button(
                    onClick = {
                        Toast.makeText(ctx, wm.getSeed(), Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("XUẤT 12 TỪ SEED")
                }
            }
        }
        
        // Dialog đổi tên ví
        renameId?.let { id ->
            var newName by remember { mutableStateOf(wallets.find { it.id == id }?.name ?: "") }
            AlertDialog(
                onDismissRequest = { renameId = null },
                confirmButton = {
                    TextButton(onClick = {
                        wm.rename(id, newName)
                        wallets = wm.getAll()
                        renameId = null
                    }) {
                        Text("Lưu")
                    }
                },
                title = { Text("Đổi tên ví") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Tên mới") }
                    )
                }
            )
        }
        
        // Dialog xem chi tiết ví
        detailId?.let { id ->
            val w = wallets.find { it.id == id }
            AlertDialog(
                onDismissRequest = { detailId = null },
                confirmButton = {
                    TextButton(onClick = { detailId = null }) {
                        Text("Đóng")
                    }
                },
                title = { Text("Chi tiết ví") },
                text = {
                    Text("Tên: ${w?.name}\n\nSeed:\n${w?.seed}")
                }
            )
        }
    }

    @Composable
    fun SettingsTab() {
        var apiUrl by remember { mutableStateOf(wm.getFeeApiUrl()) }
        var refresh by remember { mutableStateOf(wm.getRefreshSec().toString()) }
        var customFee by remember { mutableStateOf(wm.getDefaultCustomFee().toString()) }
        val ctx = LocalContext.current
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Tùy chỉnh nâng cao",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API phí (mempool.space)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = refresh,
                onValueChange = { refresh = it },
                label = { Text("Auto-refresh (giây)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = customFee,
                onValueChange = { customFee = it },
                label = { Text("Phí tùy chỉnh mặc định (sat/vB)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = {
                    wm.setFeeApiUrl(apiUrl)
                    wm.setRefreshSec(refresh.toLongOrNull() ?: 60)
                    wm.setDefaultCustomFee(customFee.toLongOrNull() ?: 10)
                    Toast.makeText(ctx, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("LƯU")
            }
        }
    }

    // Tạo QR code từ text
    private fun generateQr(text: String): Bitmap {
        val size = 512
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(
                        x, 
                        y, 
                        if (bits.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
        }
    }
}