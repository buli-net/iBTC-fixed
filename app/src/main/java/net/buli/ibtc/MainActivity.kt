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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    private var qrCallback: ((String) -> Unit)? = null
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { content ->
            qrCallback?.invoke(content)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)

        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(60000)
                try {
                    wm.init()
                    wm.getBalance()
                    wm.price()
                } catch (_: Exception) {}
            }
        }

        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var price by remember { mutableStateOf(0.0) }
                var walletName by remember { mutableStateOf(wm.getActive()?.name ?: "") }
                val context = LocalContext.current

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
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showCreate = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("TẠO VÍ MỚI")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showImport = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("IMPORT SEED")
                        }
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
                                        withContext(Dispatchers.Main) {
                                            hasWallet = true
                                            walletName = wm.getActive()?.name ?: ""
                                        }
                                    }
                                }) {
                                    Text("Tạo")
                                }
                            },
                            title = { Text("Tên ví") },
                            text = {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true
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
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val ok = wm.import(name, seed) != null
                                        if (ok) {
                                            wm.init()
                                            withContext(Dispatchers.Main) {
                                                hasWallet = true
                                                walletName = wm.getActive()?.name ?: ""
                                            }
                                        }
                                    }
                                }) {
                                    Text("Import")
                                }
                            },
                            title = { Text("Import") },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("Tên") },
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = seed,
                                        onValueChange = { seed = it },
                                        label = { Text("Seed 12 từ") }
                                    )
                                }
                            }
                        )
                    }
                } else {
                    var tab by remember { mutableStateOf(0) }
                    var showMenu by remember { mutableStateOf(false) }
                    var showRename by remember { mutableStateOf(false) }
                    var showDetails by remember { mutableStateOf(false) }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(walletName) },
                                actions = {
                                    IconButton(onClick = { showMenu = true }) {
                                        Text("⋮", fontSize = 20.sp)
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Đổi tên") },
                                            onClick = {
                                                showMenu = false
                                                showRename = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Chi tiết ví") },
                                            onClick = {
                                                showMenu = false
                                                showDetails = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Xóa ví") },
                                            onClick = {
                                                showMenu = false
                                                val id = wm.getActive()?.id
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    try {
                                                        wm.stop()
                                                    } catch (_: Exception) {}
                                                    if (id != null) {
                                                        wm.delete(id)
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        hasWallet = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TabRow(selectedTabIndex = tab) {
                                    Tab(selected = tab == 0, onClick = { tab = 0 }) {
                                        Text("Ví", modifier = Modifier.padding(12.dp))
                                    }
                                    Tab(selected = tab == 1, onClick = { tab = 1 }) {
                                        Text("Gửi/Nhận", modifier = Modifier.padding(12.dp))
                                    }
                                }

                                if (tab == 0) {
                                    var balance by remember { mutableStateOf(0.0) }
                                    var progress by remember { mutableStateOf(0) }
                                    var status by remember { mutableStateOf("Chưa sync") }
                                    var transactions by remember { mutableStateOf(listOf<TransactionInfo>()) }
                                    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                                    LaunchedEffect(Unit) {
                                        wm.onProgress { p, t ->
                                            lifecycleScope.launch(Dispatchers.Main) {
                                                progress = p
                                                status = t
                                            }
                                        }
                                        withContext(Dispatchers.IO) {
                                            balance = wm.getBalance()
                                            price = wm.price()
                                            transactions = wm.getTransactions()
                                        }
                                        while (true) {
                                            delay(60000)
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val newBalance = wm.getBalance()
                                                    val newPrice = wm.price()
                                                    val newTransactions = wm.getTransactions()
                                                    withContext(Dispatchers.Main) {
                                                        balance = newBalance
                                                        price = newPrice
                                                        transactions = newTransactions
                                                        status = "Auto sync " + timeFormat.format(Date())
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Số dư:")
                                                Text(
                                                    text = "%.8f BTC".format(balance),
                                                    fontSize = 28.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text("≈ $%.2f / BTC".format(price))
                                                Text("$status • Giá tự động", fontSize = 12.sp)
                                                if (progress in 1..99) {
                                                    LinearProgressIndicator(
                                                        progress = progress / 100f,
                                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val newBalance = wm.getBalance()
                                                    val newPrice = wm.price()
                                                    val newTransactions = wm.getTransactions()
                                                    withContext(Dispatchers.Main) {
                                                        balance = newBalance
                                                        price = newPrice
                                                        transactions = newTransactions
                                                        status = "Sync tay"
                                                        progress = 100
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("SYNC NGAY")
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Lịch sử", fontWeight = FontWeight.Bold)
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(transactions) { tx ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Row(modifier = Modifier.padding(12.dp)) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(tx.type, fontWeight = FontWeight.Bold)
                                                            Text("%.8f".format(tx.amount))
                                                            Text(
                                                                dateFormat.format(tx.time),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                        Text(tx.txId.take(8), fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    var toAddress by remember { mutableStateOf("") }
                                    var amount by remember { mutableStateOf("") }
                                    var result by remember { mutableStateOf("") }
                                    var receiveAddress by remember { mutableStateOf("") }
                                    var fees by remember { mutableStateOf(FeeRates(5, 10, 20)) }
                                    var feeSelection by remember { mutableStateOf(1) }

                                    LaunchedEffect(Unit) {
                                        withContext(Dispatchers.IO) {
                                            receiveAddress = wm.getAddress()
                                            fees = wm.getFeeRates()
                                        }
                                    }

                                    val selectedFee = when (feeSelection) {
                                        0 -> fees.slow
                                        1 -> fees.normal
                                        else -> fees.fast
                                    }

                                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                                        item {
                                            Text("Gửi BTC", fontWeight = FontWeight.Bold)
                                            OutlinedTextField(
                                                value = toAddress,
                                                onValueChange = { toAddress = it },
                                                label = { Text("Địa chỉ") },
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = {
                                                    TextButton(onClick = {
                                                        qrCallback = { scanned -> toAddress = scanned }
                                                        qrLauncher.launch(ScanOptions())
                                                    }) {
                                                        Text("QR")
                                                    }
                                                }
                                            )
                                            OutlinedTextField(
                                                value = amount,
                                                onValueChange = { amount = it },
                                                label = { Text("BTC") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = feeSelection == 0,
                                                    onClick = { feeSelection = 0 }
                                                )
                                                Text("Chậm")
                                                Spacer(modifier = Modifier.width(8.dp))
                                                RadioButton(
                                                    selected = feeSelection == 1,
                                                    onClick = { feeSelection = 1 }
                                                )
                                                Text("Thường")
                                                Spacer(modifier = Modifier.width(8.dp))
                                                RadioButton(
                                                    selected = feeSelection == 2,
                                                    onClick = { feeSelection = 2 }
                                                )
                                                Text("Nhanh")
                                            }
                                            Button(
                                                onClick = {
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        result = wm.send(
                                                            toAddress,
                                                            amount.toDoubleOrNull() ?: 0.0,
                                                            selectedFee
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("GỬI")
                                            }
                                            if (result.isNotEmpty()) {
                                                Text(result, fontSize = 12.sp)
                                            }
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Text("Nhận BTC", fontWeight = FontWeight.Bold)
                                            val qrBitmap = remember(receiveAddress) {
                                                val size = 512
                                                val bitMatrix = QRCodeWriter().encode(
                                                    receiveAddress.ifEmpty { "bitcoin:" },
                                                    BarcodeFormat.QR_CODE,
                                                    size,
                                                    size
                                                )
                                                Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                                                    for (x in 0 until size) {
                                                        for (y in 0 until size) {
                                                            setPixel(
                                                                x,
                                                                y,
                                                                if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Image(
                                                    bitmap = qrBitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(220.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                SelectionContainer {
                                                    Text(receiveAddress)
                                                }
                                            }
                                            Button(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText("btc", receiveAddress)
                                                    )
                                                    Toast.makeText(context, "Đã copy", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("COPY")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showRename) {
                            var newName by remember { mutableStateOf(walletName) }
                            AlertDialog(
                                onDismissRequest = { showRename = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            wm.getActive()?.let { activeWallet ->
                                                val seed = activeWallet.seed
                                                wm.delete(activeWallet.id)
                                                wm.import(newName, seed)
                                                wm.init()
                                                withContext(Dispatchers.Main) {
                                                    walletName = newName
                                                    showRename = false
                                                }
                                            }
                                        }
                                    }) {
                                        Text("Lưu")
                                    }
                                },
                                title = { Text("Đổi tên ví") },
                                text = {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        singleLine = true
                                    )
                                }
                            )
                        }

                        if (showDetails) {
                            val seed = wm.getSeed()
                            val address = wm.getAddress()
                            AlertDialog(
                                onDismissRequest = { showDetails = false },
                                confirmButton = {
                                    TextButton(onClick = { showDetails = false }) {
                                        Text("Đóng")
                                    }
                                },
                                title = { Text("Chi tiết ví") },
                                text = {
                                    Column {
                                        Text("Tên: $walletName", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Địa chỉ:")
                                        SelectionContainer { Text(address) }
                                        TextButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("addr", address))
                                            Toast.makeText(context, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("Copy địa chỉ")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Seed 12 từ:")
                                        SelectionContainer { Text(seed) }
                                        TextButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("seed", seed))
                                            Toast.makeText(context, "Đã copy seed", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("Copy seed")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            wm.stop()
        } catch (_: Exception) {}
    }
}