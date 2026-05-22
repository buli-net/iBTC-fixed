package net.buli.ibtc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)

        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                // TRẠNG THÁI KHÓA - nếu chưa unlock thì hiện màn hình nhập pass
                var isLocked by remember { mutableStateOf(hasWallet && !wm.isUnlocked()) }
                var balance by remember { mutableStateOf(0.0) }
                var price by remember { mutableStateOf(0.0) }
                var address by remember { mutableStateOf("") }
                var syncText by remember { mutableStateOf("Chưa sync") }
                var syncProgress by remember { mutableStateOf(0) }
                var showCreate by remember { mutableStateOf(false) }
                var showImport by remember { mutableStateOf(false) }
                var showMenu by remember { mutableStateOf(false) }
                var showSeed by remember { mutableStateOf(false) }
                var showChangePass by remember { mutableStateOf(false) }
                var tab by remember { mutableStateOf(0) }
                var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
                var feeRates by remember { mutableStateOf(FeeRates(5,10,20)) }

                // MÀN HÌNH KHÓA - BẮT BUỘC NHẬP PASS TRƯỚC KHI VÀO VÍ
                if (isLocked) {
                    var pass by remember { mutableStateOf("") }
                    var error by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Ví Bitcoin", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text("Nhập mật khẩu để mở khóa", fontSize = 16.sp)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it; error = "" },
                            label = { Text("Mật khẩu") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error.isNotEmpty()) {
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val ok = wm.unlock(pass)
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            isLocked = false
                                            wm.init()
                                            // bắt đầu sync sau khi unlock
                                            wm.onProgress { p, t -> syncProgress = p; syncText = t }
                                        } else {
                                            error = "Sai mật khẩu"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("MỞ KHÓA") }
                    }
                    return@MaterialTheme
                }

                // Nếu chưa có ví - màn hình tạo/import
                if (!hasWallet) {
                    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center) {
                        Text("Tạo ví Bitcoin", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showCreate = true }, Modifier.fillMaxWidth()) { Text("Tạo ví mới") }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showImport = true }, Modifier.fillMaxWidth()) { Text("Import ví") }
                    }

                    // DIALOG TẠO VÍ - BẮT BUỘC NHẬP PASS 2 LẦN
                    if (showCreate) {
                        var name by remember { mutableStateOf("") }
                        var p1 by remember { mutableStateOf("") }
                        var p2 by remember { mutableStateOf("") }
                        var err by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showCreate = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (p1.length < 4) { err = "Pass tối thiểu 4 ký tự"; return@TextButton }
                                    if (p1 != p2) { err = "Mật khẩu không khớp"; return@TextButton }
                                    showCreate = false
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        wm.create(name, p1) // tạo và mã hóa
                                        wm.init()
                                        withContext(Dispatchers.Main) { hasWallet = true; isLocked = false }
                                    }
                                }) { Text("Tạo") }
                            },
                            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Hủy") } },
                            title = { Text("Tạo ví mới") },
                            text = {
                                Column {
                                    OutlinedTextField(name, {name=it}, label={Text("Tên ví")}, modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(p1, {p1=it; err=""}, label={Text("Mật khẩu")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(p2, {p2=it; err=""}, label={Text("Nhập lại mật khẩu")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    if (err.isNotEmpty()) Text(err, color=MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }

                    // DIALOG IMPORT - BẮT BUỘC PASS
                    if (showImport) {
                        var name by remember { mutableStateOf("") }
                        var phrase by remember { mutableStateOf("") }
                        var p1 by remember { mutableStateOf("") }
                        var p2 by remember { mutableStateOf("") }
                        var err by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showImport = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (p1.length < 4) { err = "Pass tối thiểu 4 ký tự"; return@TextButton }
                                    if (p1 != p2) { err = "Mật khẩu không khớp"; return@TextButton }
                                    showImport = false
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val ok = wm.import(name, phrase, p1) != null
                                        if (ok) wm.init()
                                        withContext(Dispatchers.Main) { if (ok) hasWallet = true }
                                    }
                                }) { Text("Import") }
                            },
                            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Hủy") } },
                            title = { Text("Import ví") },
                            text = {
                                Column {
                                    OutlinedTextField(name, {name=it}, label={Text("Tên")}, modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(phrase, {phrase=it}, label={Text("12 từ seed")}, modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(p1, {p1=it; err=""}, label={Text("Mật khẩu mới")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(p2, {p2=it; err=""}, label={Text("Nhập lại")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    if (err.isNotEmpty()) Text(err, color=MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                    return@MaterialTheme
                }

                // ĐÃ CÓ VÍ VÀ ĐÃ UNLOCK - BẮT ĐẦU SYNC VÀ LẤY DỮ LIỆU
                LaunchedEffect(Unit) {
                    wm.init()
                    wm.onProgress { p, t -> syncProgress = p; syncText = t }
                    while (true) {
                        balance = wm.getBalance()
                        price = wm.price()
                        address = wm.getAddress()
                        txs = wm.getTransactions()
                        feeRates = wm.getFeeRates()
                        delay(5000)
                    }
                }



                // GIAO DIỆN CHÍNH - HEADER VÀ SỐ DƯ
                Column(Modifier.fillMaxSize()) {
                    // Header với menu 3 chấm
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(wm.getActive()?.name ?: "Ví", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Box {
                            IconButton(onClick = { showMenu = true }) { Text("⋮", fontSize = 24.sp) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Chi tiết ví") }, onClick = { showMenu = false; showSeed = true })
                                DropdownMenuItem(text = { Text("Đổi mật khẩu") }, onClick = { showMenu = false; showChangePass = true })
                                DropdownMenuItem(text = { Text("Khóa ví") }, onClick = { showMenu = false; wm.lock(); isLocked = true })
                                DropdownMenuItem(text = { Text("Xóa ví") }, onClick = {
                                    showMenu = false
                                    wm.getActive()?.let { wm.delete(it.id); hasWallet = false; isLocked = false }
                                })
                            }
                        }
                    }

                    // Card hiển thị số dư và giá BTC
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${String.format("%.8f", balance)} BTC", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("≈ $${String.format("%,.2f", balance * price)}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Giá: $${String.format("%,.0f", price)} | $syncText", fontSize = 12.sp)
                            LinearProgressIndicator(progress = syncProgress / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tabs chuyển giữa Ví và Gửi
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ví") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Gửi") })
                    }

                    if (tab == 0) {
                        // TAB VÍ - hiển thị địa chỉ nhận và lịch sử giao dịch
                        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                Text("Địa chỉ nhận BTC", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = address,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nhấn giữ để copy") }
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("Lịch sử giao dịch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                            }
                            if (txs.isEmpty()) {
                                item {
                                    Card(Modifier.fillMaxWidth()) {
                                        Text("Chưa có giao dịch", modifier = Modifier.padding(16.dp))
                                    }
                                }
                            } else {
                                items(txs) { tx ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(Modifier.padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(tx.type, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(
                                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(tx.time),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    tx.txId.take(12) + "...",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                "${if (tx.type == "Nhận") "+" else "-"}${String.format("%.8f", tx.amount)}",
                                                fontWeight = FontWeight.Bold,
                                                color = if (tx.type == "Nhận") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // TAB GỬI - form gửi BTC với chọn phí
                        var toAddr by remember { mutableStateOf("") }
                        var amount by remember { mutableStateOf("") }
                        var selectedFee by remember { mutableStateOf(1) } // 0 chậm, 1 thường, 2 nhanh
                        var result by remember { mutableStateOf("") }
                        var isSending by remember { mutableStateOf(false) }
                        
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Gửi Bitcoin", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = toAddr,
                                onValueChange = { toAddr = it },
                                label = { Text("Địa chỉ nhận") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = { Text("Số lượng BTC") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text(
                                "Số dư: ${String.format("%.8f", balance)} BTC",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            Text("Chọn tốc độ giao dịch:", fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            
                            // Chọn phí
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selectedFee == 0, onClick = { selectedFee = 0 })
                                        Column(Modifier.weight(1f)) {
                                            Text("Chậm", fontWeight = FontWeight.Medium)
                                            Text("${feeRates.slow} sat/vB - ~60 phút", fontSize = 12.sp)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selectedFee == 1, onClick = { selectedFee = 1 })
                                        Column(Modifier.weight(1f)) {
                                            Text("Thường", fontWeight = FontWeight.Medium)
                                            Text("${feeRates.normal} sat/vB - ~30 phút", fontSize = 12.sp)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selectedFee == 2, onClick = { selectedFee = 2 })
                                        Column(Modifier.weight(1f)) {
                                            Text("Nhanh", fontWeight = FontWeight.Medium)
                                            Text("${feeRates.fast} sat/vB - ~10 phút", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            
                            Button(
                                onClick = {
                                    isSending = true
                                    val fee = when(selectedFee) { 0 -> feeRates.slow; 2 -> feeRates.fast; else -> feeRates.normal }
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val txid = wm.send(toAddr, amount.toDoubleOrNull() ?: 0.0, fee)
                                        withContext(Dispatchers.Main) {
                                            result = txid
                                            isSending = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                enabled = !isSending && toAddr.isNotBlank() && amount.isNotBlank()
                            ) {
                                Text(if (isSending) "ĐANG GỬI..." else "GỬI BTC")
                            }
                            
                            if (result.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (result.startsWith("Lỗi")) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Text(
                                        "Kết quả: $result",
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }



                // DIALOG CHI TIẾT VÍ - HIỆN SEED (chỉ hiện khi đã unlock)
                if (showSeed) {
                    AlertDialog(
                        onDismissRequest = { showSeed = false },
                        confirmButton = {
                            TextButton(onClick = { showSeed = false }) { Text("Đóng") }
                        },
                        title = { Text("Chi tiết ví", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Tên ví:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(wm.getActive()?.name ?: "", fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(12.dp))
                                
                                Text("Địa chỉ nhận:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(address, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                
                                Text("Seed 12 từ (TUYỆT ĐỐI BẢO MẬT):", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Text(
                                        wm.getSeed(),
                                        modifier = Modifier.padding(12.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "⚠️ Không chia sẻ 12 từ này cho bất kỳ ai. Ai có seed sẽ kiểm soát toàn bộ tiền.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }

                // DIALOG ĐỔI MẬT KHẨU
                if (showChangePass) {
                    var oldPass by remember { mutableStateOf("") }
                    var new1 by remember { mutableStateOf("") }
                    var new2 by remember { mutableStateOf("") }
                    var err by remember { mutableStateOf("") }
                    var isChanging by remember { mutableStateOf(false) }
                    
                    AlertDialog(
                        onDismissRequest = { if (!isChanging) showChangePass = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (new1 != new2) { err = "Mật khẩu mới không khớp"; return@TextButton }
                                    if (new1.length < 4) { err = "Tối thiểu 4 ký tự"; return@TextButton }
                                    isChanging = true
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val ok = wm.changePassword(oldPass, new1)
                                        withContext(Dispatchers.Main) {
                                            isChanging = false
                                            if (ok) {
                                                showChangePass = false
                                            } else {
                                                err = "Sai mật khẩu cũ"
                                            }
                                        }
                                    }
                                },
                                enabled = !isChanging
                            ) {
                                Text(if (isChanging) "Đang đổi..." else "Đổi")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showChangePass = false },
                                enabled = !isChanging
                            ) { Text("Hủy") }
                        },
                        title = { Text("Đổi mật khẩu ví") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = oldPass,
                                    onValueChange = { oldPass = it; err = "" },
                                    label = { Text("Mật khẩu cũ") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = new1,
                                    onValueChange = { new1 = it; err = "" },
                                    label = { Text("Mật khẩu mới") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = new2,
                                    onValueChange = { new2 = it; err = "" },
                                    label = { Text("Nhập lại mật khẩu mới") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                if (err.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // TỰ ĐỘNG KHÓA KHI THOÁT APP - bảo mật
        // wm.lock() // bỏ comment nếu muốn tự khóa mỗi lần thoát
    }

    override fun onDestroy() {
        super.onDestroy()
        wm.stop()
    }
}