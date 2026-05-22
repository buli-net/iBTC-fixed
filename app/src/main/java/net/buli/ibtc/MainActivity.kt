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
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var isLocked by remember { mutableStateOf(hasWallet && !wm.isUnlocked()) }
                var balance by remember { mutableStateOf(0.0) }
                var price by remember { mutableStateOf(0.0) }
                var address by remember { mutableStateOf("") }
                var syncText by remember { mutableStateOf("Chưa đồng bộ") }
                var syncProgress by remember { mutableStateOf(0) }
                var showCreate by remember { mutableStateOf(false) }
                var showImport by remember { mutableStateOf(false) }
                var tab by remember { mutableStateOf(0) }
                var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }

                if (isLocked) {
                    var pass by remember { mutableStateOf("") }
                    var err by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("🔒 Ví Bitcoin", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(value = pass, onValueChange = { pass = it; err = "" }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (err.isNotEmpty()) { Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val ok = wm.unlock(pass)
                                withContext(Dispatchers.Main) {
                                    if (ok) { isLocked = false; wm.init() } else { err = "Sai mật khẩu" }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = pass.isNotEmpty()) { Text("MỞ KHÓA") }
                    }
                    return@MaterialTheme
                }

                if (!hasWallet) {
                    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Ví Bitcoin", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Tạo ví mới") }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Import ví") }
                    }
                    if (showCreate) {
                        var name by remember { mutableStateOf("") }
                        var p1 by remember { mutableStateOf("") }
                        var p2 by remember { mutableStateOf("") }
                        var err by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showCreate = false }, confirmButton = {
                            TextButton(onClick = {
                                if (name.isBlank()) { err = "Nhập tên"; return@TextButton }
                                if (p1.length < 4) { err = "Pass >=4"; return@TextButton }
                                if (p1 != p2) { err = "Không khớp"; return@TextButton }
                                showCreate = false
                                lifecycleScope.launch(Dispatchers.IO) {
                                    wm.create(name, p1)
                                    wm.init()
                                    withContext(Dispatchers.Main) { hasWallet = true }
                                }
                            }) { Text("Tạo") }
                        }, dismissButton = { TextButton({ showCreate = false }) { Text("Hủy") } }, title = { Text("Tạo ví") }, text = {
                            Column {
                                OutlinedTextField(name, { name = it }, label = { Text("Tên ví") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(p1, { p1 = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(p2, { p2 = it }, label = { Text("Nhập lại") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                            }
                        })
                    }
                    if (showImport) {
                        var name by remember { mutableStateOf("") }
                        var phrase by remember { mutableStateOf("") }
                        var p1 by remember { mutableStateOf("") }
                        var p2 by remember { mutableStateOf("") }
                        var err by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showImport = false }, confirmButton = {
                            TextButton(onClick = {
                                if (name.isBlank()) { err = "Nhập tên"; return@TextButton }
                                if (phrase.trim().split("\\s+".toRegex()).size != 12) { err = "Cần 12 từ"; return@TextButton }
                                if (p1.length < 4) { err = "Pass >=4"; return@TextButton }
                                if (p1 != p2) { err = "Không khớp"; return@TextButton }
                                showImport = false
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val ok = wm.import(name, phrase.trim(), p1)
                                    if (ok) wm.init()
                                    withContext(Dispatchers.Main) { if (ok) hasWallet = true else err = "Seed sai" }
                                }
                            }) { Text("Import") }
                        }, dismissButton = { TextButton({ showImport = false }) { Text("Hủy") } }, title = { Text("Import ví") }, text = {
                            Column {
                                OutlinedTextField(name, { name = it }, label = { Text("Tên ví") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(phrase, { phrase = it }, label = { Text("12 từ seed") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                                OutlinedTextField(p1, { p1 = it }, label = { Text("Mật khẩu mới") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(p2, { p2 = it }, label = { Text("Nhập lại") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                            }
                        })
                    }
                    return@MaterialTheme
                }

                LaunchedEffect(Unit) {
                    wm.init()
                    wm.onProgress { p, t -> syncProgress = p; syncText = t }
                    while (true) {
                        balance = wm.getBalance()
                        price = wm.price()
                        address = wm.getAddress()
                        txs = wm.getTransactions()
                        delay(5000)
                    }
                }

                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(wm.getActiveName(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format(Locale.US, "%.8f BTC", balance), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(String.format(Locale.US, "≈ $%,.2f", balance * price), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(syncText, fontSize = 12.sp)
                            LinearProgressIndicator(progress = syncProgress / 100f, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ví") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Gửi") })
                    }
                    if (tab == 0) {
                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Text("Địa chỉ nhận", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(value = address, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), label = { Text("Tap để copy") })
                                Spacer(Modifier.height(16.dp))
                                Text("Lịch sử", fontWeight = FontWeight.Bold)
                            }
                            if (txs.isEmpty()) {
                                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Chưa có giao dịch", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            } else {
                                items(txs) { tx ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Column {
                                                Text(tx.type, fontWeight = FontWeight.Bold, color = if (tx.type == "Nhận") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                                Text(SimpleDateFormat("dd/MM HH:mm", Locale.US).format(tx.time), fontSize = 11.sp)
                                            }
                                            Text(String.format(Locale.US, "%+.8f", if (tx.type == "Nhận") tx.amount else -tx.amount))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (tab == 1) {
                        var to by remember { mutableStateOf("") }
                        var amt by remember { mutableStateOf("") }
                        var res by remember { mutableStateOf("") }
                        Column(Modifier.padding(16.dp)) {
                            OutlinedTextField(to, { to = it }, label = { Text("Địa chỉ") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(amt, { amt = it }, label = { Text("BTC") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val txid = wm.send(to, amt.toDoubleOrNull() ?: 0.0)
                                    withContext(Dispatchers.Main) { res = txid }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("GỬI") }
                            if (res.isNotEmpty()) Text("KQ: $res", modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}