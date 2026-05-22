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
 * MainActivity - Ví Bitcoin với khóa mật khẩu
 * FIX: Ép light theme để không bị dark mode hệ thống làm hỏng màu
 */
class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)

        setContent {
            // ÉP LIGHT THEME - giống bản cũ
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
                var showMenu by remember { mutableStateOf(false) }
                var showSeed by remember { mutableStateOf(false) }
                var showChangePass by remember { mutableStateOf(false) }
                var tab by remember { mutableStateOf(0) }
                var txs by remember { mutableStateOf(listOf<TransactionInfo>()) }
                var feeRates by remember { mutableStateOf(FeeRates(5,10,20)) }

                // ========== MÀN HÌNH KHÓA ==========
                if (isLocked) {
                    var pass by remember { mutableStateOf("") }
                    var error by remember { mutableStateOf("") }
                    var isUnlocking by remember { mutableStateOf(false) }
                    
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("🔒 Ví Bitcoin", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text("Nhập mật khẩu để mở khóa")
                        Spacer(Modifier.height(32.dp))
                        OutlinedTextField(pass, { pass = it; error = "" }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !isUnlocking)
                        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            isUnlocking = true
                            lifecycleScope.launch(Dispatchers.IO) {
                                val ok = wm.unlock(pass)
                                withContext(Dispatchers.Main) {
                                    if (ok) { isLocked = false; wm.init(); wm.onProgress { p, t -> syncProgress = p; syncText = t } }
                                    else { error = "Sai mật khẩu!"; isUnlocking = false }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = pass.isNotEmpty() && !isUnlocking) {
                            Text(if (isUnlocking) "Đang mở..." else "MỞ KHÓA")
                        }
                    }
                    return@MaterialTheme
                }

                // ========== CHƯA CÓ VÍ ==========
                if (!hasWallet) {
                    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center) {
                        Text("Tạo ví Bitcoin mới", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(32.dp))
                        Button({ showCreate = true }, Modifier.fillMaxWidth().height(50.dp)) { Text("Tạo ví mới") }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton({ showImport = true }, Modifier.fillMaxWidth().height(50.dp)) { Text("Import ví") }
                    }
                    
                    if (showCreate) {
                        var name by remember { mutableStateOf("") }; var p1 by remember { mutableStateOf("") }; var p2 by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showCreate = false },
                            confirmButton = { TextButton({ if (name.isBlank()||p1.length<4||p1!=p2) return@TextButton; showCreate=false; lifecycleScope.launch(Dispatchers.IO){ wm.create(name,p1); wm.init(); withContext(Dispatchers.Main){ hasWallet=true; isLocked=false } } }) { Text("Tạo") } },
                            dismissButton = { TextButton({ showCreate=false }) { Text("Hủy") } },
                            title = { Text("Tạo ví") },
                            text = { Column { OutlinedTextField(name,{name=it},label={Text("Tên")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(p1,{p1=it},label={Text("Pass")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth()); OutlinedTextField(p2,{p2=it},label={Text("Nhập lại")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth()); if(err.isNotEmpty()) Text(err,color=MaterialTheme.colorScheme.error) } }
                        )
                    }
                    if (showImport) {
                        var name by remember { mutableStateOf("") }; var phrase by remember { mutableStateOf("") }; var p1 by remember { mutableStateOf("") }; var p2 by remember { mutableStateOf("") }
                        AlertDialog(onDismissRequest = { showImport = false },
                            confirmButton = { TextButton({ showImport=false; lifecycleScope.launch(Dispatchers.IO){ val ok = wm.import(name,phrase,p1)!=null; if(ok) wm.init(); withContext(Dispatchers.Main){ if(ok) hasWallet=true } } }) { Text("Import") } },
                            dismissButton = { TextButton({ showImport=false }) { Text("Hủy") } },
                            title = { Text("Import") },
                            text = { Column { OutlinedTextField(name,{name=it},label={Text("Tên")}); OutlinedTextField(phrase,{phrase=it},label={Text("12 từ")}); OutlinedTextField(p1,{p1=it},label={Text("Pass mới")},visualTransformation=PasswordVisualTransformation()); OutlinedTextField(p2,{p2=it},label={Text("Nhập lại")},visualTransformation=PasswordVisualTransformation()) } }
                        )
                    }
                    return@MaterialTheme
                }

                // ========== VÍ ĐÃ MỞ ==========
                LaunchedEffect(Unit) {
                    wm.init(); wm.onProgress { p, t -> syncProgress = p; syncText = t }
                    while (true) { balance = wm.getBalance(); price = wm.price(); address = wm.getAddress(); txs = wm.getTransactions(); feeRates = wm.getFeeRates(); delay(5000) }
                }

                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(wm.getActive()?.name ?: "Ví", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Box { IconButton({ showMenu = true }) { Text("⋮", fontSize = 24.sp) }
                            DropdownMenu(showMenu, { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Xem seed") }, onClick = { showMenu=false; showSeed=true })
                                DropdownMenuItem(text = { Text("Đổi pass") }, onClick = { showMenu=false; showChangePass=true })
                                DropdownMenuItem(text = { Text("Khóa ví") }, onClick = { showMenu=false; wm.lock(); isLocked=true })
                            }
                        }
                    }
                    
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), Alignment.CenterHorizontally) {
                            Text("${String.format(Locale.US,"%.8f",balance)} BTC", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("≈ $${String.format(Locale.US,"%,.2f",balance*price)}")
                            Spacer(Modifier.height(8.dp))
                            Text("Giá: $${String.format(Locale.US,"%,.0f",price)} | $syncText", fontSize = 12.sp)
                            LinearProgressIndicator(syncProgress/100f, Modifier.fillMaxWidth().padding(top=8.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    TabRow(tab, containerColor = MaterialTheme.colorScheme.surface) {
                        Tab(tab==0, {tab=0}, text={Text("Ví")})
                        Tab(tab==1, {tab=1}, text={Text("Gửi")})
                    }
                    
                    if (tab==0) {
                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { Text("Địa chỉ nhận", fontWeight = FontWeight.Bold); OutlinedTextField(address, {}, readOnly=true, modifier=Modifier.fillMaxWidth(), label={Text("Nhấn giữ để copy")}); Spacer(Modifier.height(16.dp)); Text("Lịch sử", fontWeight = FontWeight.Bold) }
                            if (txs.isEmpty()) item { Card(Modifier.fillMaxWidth()){ Box(Modifier.padding(24.dp)){ Text("Chưa có giao dịch") } } }
                            else items(txs){ tx -> Card(Modifier.fillMaxWidth()){ Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween){ Column{ Text(if(tx.type=="Nhận")"↓ Nhận"else"↑ Gửi", fontWeight=FontWeight.Bold, color=if(tx.type=="Nhận")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); Text(SimpleDateFormat("dd/MM HH:mm",Locale.US).format(tx.time), fontSize=11.sp) }; Text("${if(tx.type=="Nhận")"+"else"-"}${String.format(Locale.US,"%.8f",tx.amount)}") } } } }
                        }
                    }
                    
                    if (tab==1) {
                        var to by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var feeSel by remember { mutableStateOf(1) }; var res by remember { mutableStateOf("") }
                        Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
                            Text("Gửi Bitcoin", fontSize=20.sp, fontWeight=FontWeight.Bold)
                            OutlinedTextField(to,{to=it},label={Text("Địa chỉ")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(amt,{amt=it},label={Text("Số BTC")},modifier=Modifier.fillMaxWidth())
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ FilterChip(feeSel==0,{feeSel=0},label={Text("Chậm")}); FilterChip(feeSel==1,{feeSel=1},label={Text("Thường")}); FilterChip(feeSel==2,{feeSel=2},label={Text("Nhanh")}) }
                            Button({ lifecycleScope.launch(Dispatchers.IO){ val fee=when(feeSel){0->feeRates.slow;2->feeRates.fast;else->feeRates.normal}; val txid=wm.send(to,amt.toDoubleOrNull()?:0.0,fee); withContext(Dispatchers.Main){res=txid} } }, Modifier.fillMaxWidth()){ Text("GỬI") }
                            if(res.isNotEmpty()) Text("Kết quả: $res", fontSize=12.sp)
                        }
                    }
                }

                if (showSeed) {
                    var pass by remember { mutableStateOf("") }; var seed by remember { mutableStateOf<String?>(null) }
                    AlertDialog({ showSeed=false }, confirmButton={ TextButton({showSeed=false}){Text("Đóng")} }, title={Text("Seed")},
                        text={ Column{ if(seed==null){ OutlinedTextField(pass,{pass=it},label={Text("Pass")},visualTransformation=PasswordVisualTransformation()); Button({ seed=try{wm.getDecryptedSeed(pass)}catch(e:Exception){null} }){Text("Hiện")} } else { Text(seed!!, fontWeight=FontWeight.Bold) } } })
                }
                
                if (showChangePass) {
                    var old by remember { mutableStateOf("") }; var n1 by remember { mutableStateOf("") }; var n2 by remember { mutableStateOf("") }
                    AlertDialog({ showChangePass=false }, confirmButton={ TextButton({ lifecycleScope.launch(Dispatchers.IO){ wm.changePassword(old,n1) } }){Text("Đổi")} }, title={Text("Đổi pass")},
                        text={ Column{ OutlinedTextField(old,{old=it},label={Text("Cũ")},visualTransformation=PasswordVisualTransformation()); OutlinedTextField(n1,{n1=it},label={Text("Mới")},visualTransformation=PasswordVisualTransformation()); OutlinedTextField(n2,{n2=it},label={Text("Nhập lại")},visualTransformation=PasswordVisualTransformation()) } })
                }
            }
        }
    }
}

data class FeeRates(val slow:Int, val normal:Int, val fast:Int)