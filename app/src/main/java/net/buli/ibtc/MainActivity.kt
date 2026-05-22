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

/**
 * MainActivity - Ví Bitcoin với bảo mật mật khẩu
 * 
 * THAY ĐỔI LỚN SO VỚI BẢN CŨ:
 * 1. FIX: Ép lightColorScheme() - luôn nền trắng, không bị dark mode hệ thống
 * 2. Thêm màn hình khóa (lock screen) khi mở app
 * 3. Seed phrase được mã hóa AES-256, mật khẩu không lưu
 * 4. Thêm chức năng xem seed và đổi mật khẩu
 */
class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)

        setContent {
            // QUAN TRỌNG: Ép theme sáng để giống bản cũ
            MaterialTheme(colorScheme = lightColorScheme()) {
                
                // ========== CÁC STATE QUẢN LÝ UI ==========
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var isLocked by remember { mutableStateOf(hasWallet && !wm.isUnlocked()) }
                var balance by remember { mutableStateOf(0.0) }
                var price by remember { mutableStateOf(0.0) }
                var address by remember { mutableStateOf("") }
                var syncText by remember { mutableStateOf("Chưa đồng bộ") }
                var syncProgress by remember { mutableStateOf(0) }
                var showCreate by remember { mutableStateOf(false) }
                var showImport by remember { mutableStateOf(false) }
                var showMenu by remember { mutableStateOf(false) }
                var showSeed by remember { mutableStateOf(false) }
                var showChangePass by remember { mutableStateOf(false) }
                var tab by remember { mutableStateOf(0) }
                var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
                var feeRates by remember { mutableStateOf(FeeRates(5, 10, 20)) }

                // ========== MÀN HÌNH 1: KHÓA VÍ (HIỆN KHI MỞ APP) ==========
                if (isLocked) {
                    var pass by remember { mutableStateOf("") }
                    var error by remember { mutableStateOf("") }
                    var isUnlocking by remember { mutableStateOf(false) }
                    
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔒 Ví Bitcoin", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text("Nhập mật khẩu để mở khóa", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it; error = "" },
                            label = { Text("Mật khẩu") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isUnlocking
                        )
                        
                        if (error.isNotEmpty()) {
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                isUnlocking = true
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val ok = wm.unlock(pass)
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            isLocked = false
                                            wm.init()
                                            wm.onProgress { p, t -> syncProgress = p; syncText = t }
                                        } else {
                                            error = "Sai mật khẩu! Thử lại."
                                            isUnlocking = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = pass.isNotEmpty() && !isUnlocking
                        ) {
                            Text(if (isUnlocking) "Đang mở..." else "MỞ KHÓA")
                        }
                    }
                    return@MaterialTheme
                }

                // ========== MÀN HÌNH 2: CHƯA CÓ VÍ ==========
                if (!hasWallet) {
                    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center) {
                        Text("Tạo ví Bitcoin mới", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Ví sẽ được mã hóa bằng mật khẩu của bạn", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        
                        Button(onClick = { showCreate = true }, Modifier.fillMaxWidth().height(50.dp)) { 
                            Text("Tạo ví mới") 
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { showImport = true }, Modifier.fillMaxWidth().height(50.dp)) { 
                            Text("Import ví có sẵn") 
                        }
                    }
                    
                    // Dialog tạo ví mới
                    if (showCreate) {
                        var name by remember { mutableStateOf("") }
                        var p1 by remember { mutableStateOf("") }
                        var p2 by remember { mutableStateOf("") }
                        var err by remember { mutableStateOf("") }
                        
                        AlertDialog(
                            onDismissRequest = { showCreate = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (name.isBlank()) { err = "Nhập tên ví"; return@TextButton }
                                    if (p1.length < 4) { err = "Mật khẩu tối thiểu 4 ký tự"; return@TextButton }
                                    if (p1 != p2) { err = "Mật khẩu không khớp"; return@TextButton }
                                    showCreate = false
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        wm.create(name, p1)
                                        wm.init()
                                        withContext(Dispatchers.Main) { 
                                            hasWallet = true
                                            isLocked = false
                                        }
                                    }
                                }) { Text("Tạo ví") }
                            },
                            dismissButton = { TextButton({ showCreate = false }) { Text("Hủy") } },
                            title = { Text("Tạo ví mới") },
                            text = {
                                Column {
                                    OutlinedTextField(name, {name=it}, label={Text("Tên ví")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(p1, {p1=it}, label={Text("Mật khẩu")}, visualTransformation=PasswordVisualTransformation(), singleLine=true, modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(p2, {p2=it}, label={Text("Nhập lại mật khẩu")}, visualTransformation=PasswordVisualTransformation(), singleLine=true, modifier=Modifier.fillMaxWidth())
                                    if (err.isNotEmpty()) Text(err, color=MaterialTheme.colorScheme.error, modifier=Modifier.padding(top=8.dp))
                                }
                            }
                        )
                    }

                    // Dialog import ví
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
                                    if (name.isBlank()) { err = "Nhập tên"; return@TextButton }
                                    if (phrase.trim().split("\\s+".toRegex()).size != 12) { err = "Cần đúng 12 từ"; return@TextButton }
                                    if (p1.length < 4) { err = "Pass tối thiểu 4"; return@TextButton }
                                    if (p1 != p2) { err = "Không khớp"; return@TextButton }
                                    showImport = false
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val ok = wm.import(name, phrase.trim(), p1)
                                        if (ok) wm.init()
                                        withContext(Dispatchers.Main) { if (ok) hasWallet = true }
                                    }
                                }) { Text("Import") }
                            },
                            dismissButton = { TextButton({ showImport = false }) { Text("Hủy") } },
                            title = { Text("Import ví") },
                            text = {
                                Column {
                                    OutlinedTextField(name, {name=it}, label={Text("Tên ví")}, modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(phrase, {phrase=it}, label={Text("12 từ seed")}, modifier=Modifier.fillMaxWidth(), maxLines=3)
                                    OutlinedTextField(p1, {p1=it}, label={Text("Mật khẩu mới")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(p2, {p2=it}, label={Text("Nhập lại")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    if (err.isNotEmpty()) Text(err, color=MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                    return@MaterialTheme
                }
                
                // ========== MÀN HÌNH 3: VÍ ĐÃ MỞ ==========
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

                Column(Modifier.fillMaxSize()) {
                    // Header với menu
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(wm.getActive()?.name ?: "Ví Bitcoin", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Box {
                            IconButton(onClick = { showMenu = true }) { Text("⋮", fontSize = 24.sp) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Xem seed phrase") }, onClick = { showMenu = false; showSeed = true })
                                DropdownMenuItem(text = { Text("Đổi mật khẩu") }, onClick = { showMenu = false; showChangePass = true })
                                DropdownMenuItem(text = { Text("Khóa ví") }, onClick = { showMenu = false; wm.lock(); isLocked = true })
                            }
                        }
                    }
                    
                    // Card số dư
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${String.format(Locale.US, "%.8f", balance)} BTC", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("≈ $${String.format(Locale.US, "%,.2f", balance * price)}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Giá: $${String.format(Locale.US, "%,.0f", price)} | $syncText", fontSize = 12.sp)
                            LinearProgressIndicator(progress = syncProgress / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Tabs
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ví") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Gửi") })
                    }
                    
                    // Tab Ví
                    if (tab == 0) {
                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Text("Địa chỉ nhận Bitcoin", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = address, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), label = { Text("Nhấn giữ để copy") })
                                Spacer(Modifier.height(16.dp))
                                Text("Lịch sử giao dịch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            if (txs.isEmpty()) {
                                item { Card(Modifier.fillMaxWidth()) { Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) { Text("Chưa có giao dịch", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                            } else {
                                items(txs) { tx ->
                                    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                                        Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(if (tx.type == "Nhận") "↓ Nhận" else "↑ Gửi", fontWeight = FontWeight.Bold, color = if (tx.type == "Nhận") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                                Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(tx.time), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("${if (tx.type == "Nhận") "+" else "-"}${String.format(Locale.US, "%.8f", tx.amount)}", fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Tab Gửi
                    if (tab == 1) {
                        var to by remember { mutableStateOf("") }
                        var amt by remember { mutableStateOf("") }
                        var feeSel by remember { mutableStateOf(1) }
                        var res by remember { mutableStateOf("") }
                        
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Gửi Bitcoin", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("Địa chỉ nhận") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Số lượng BTC") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("Phí mạng:", fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = feeSel == 0, onClick = { feeSel = 0 }, label = { Text("Chậm (${feeRates.slow})") })
                                FilterChip(selected = feeSel == 1, onClick = { feeSel = 1 }, label = { Text("Thường (${feeRates.normal})") })
                                FilterChip(selected = feeSel == 2, onClick = { feeSel = 2 }, label = { Text("Nhanh (${feeRates.fast})") })
                            }
                            Button(onClick = {
                                val fee = when (feeSel) { 0 -> feeRates.slow; 2 -> feeRates.fast; else -> feeRates.normal }
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val txid = wm.send(to, amt.toDoubleOrNull() ?: 0.0, fee)
                                    withContext(Dispatchers.Main) { res = txid }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = to.isNotBlank() && amt.isNotBlank()) { Text("GỬI BITCOIN") }
                            if (res.isNotEmpty()) {
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                    Text("Kết quả: $res", modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Dialog xem seed
                if (showSeed) {
                    var pass by remember { mutableStateOf("") }
                    var seed by remember { mutableStateOf<String?>(null) }
                    var error by remember { mutableStateOf("") }
                    
                    AlertDialog(
                        onDismissRequest = { showSeed = false; seed = null },
                        confirmButton = { TextButton(onClick = { showSeed = false; seed = null }) { Text(if (seed == null) "Hủy" else "Đóng") } },
                        title = { Text("Seed Phrase (12 từ)") },
                        text = {
                            Column {
                                if (seed == null) {
                                    Text("Nhập mật khẩu để xem seed. Ai có seed sẽ kiểm soát ví!", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(value = pass, onValueChange = { pass = it; error = "" }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { try { seed = wm.getDecryptedSeed(pass) } catch (e: Exception) { error = "Sai mật khẩu!" } }, modifier = Modifier.fillMaxWidth()) { Text("HIỂN THỊ SEED") }
                                } else {
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                        Text(seed!!, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("VIẾT RA GIẤY VÀ CẤT KỸ!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    )
                }

                // Dialog đổi mật khẩu
                if (showChangePass) {
                    var old by remember { mutableStateOf("") }
                    var n1 by remember { mutableStateOf("") }
                    var n2 by remember { mutableStateOf("") }
                    var err by remember { mutableStateOf("") }
                    var success by remember { mutableStateOf(false) }
                    
                    AlertDialog(
                        onDismissRequest = { showChangePass = false },
                        confirmButton = {
                            TextButton(onClick = {
                                if (n1.length < 4) { err = "Tối thiểu 4 ký tự"; return@TextButton }
                                if (n1 != n2) { err = "Không khớp"; return@TextButton }
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val ok = wm.changePassword(old, n1)
                                    withContext(Dispatchers.Main) { if (ok) { success = true; err = "" } else { err = "Sai mật khẩu cũ!" } }
                                }
                            }) { Text("Đổi") }
                        },
                        dismissButton = { TextButton({ showChangePass = false }) { Text(if (success) "Đóng" else "Hủy") } },
                        title = { Text("Đổi mật khẩu ví") },
                        text = {
                            Column {
                                if (!success) {
                                    OutlinedTextField(old, {old=it}, label={Text("Mật khẩu cũ")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(n1, {n1=it}, label={Text("Mật khẩu mới")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(n2, {n2=it}, label={Text("Nhập lại mới")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                                    if (err.isNotEmpty()) Text(err, color=MaterialTheme.colorScheme.error, modifier=Modifier.padding(top=8.dp))
                                } else {
                                    Text("✓ Đổi mật khẩu thành công!", color=MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

data class FeeRates(val slow: Int, val normal: Int, val fast: Int)