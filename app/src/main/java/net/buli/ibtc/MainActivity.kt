package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var walletManager: WalletManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        walletManager = WalletManager(this)

        setContent {
            MaterialTheme {
                var tab by remember { mutableStateOf(0) }
                Scaffold(
                    topBar = {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ví") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Quản lý") })
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        if (tab == 0) WalletTab() else ManageTab()
                    }
                }
            }
        }
    }

    @Composable
    fun WalletTab() {
        var balance by remember { mutableStateOf("0.00000000") }
        var address by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Chưa sync") }
        var isSyncing by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            walletManager.initWallet()
            address = walletManager.getReceiveAddress()
            balance = walletManager.getBalance()
        }

        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("iBTC - SPV", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Số dư:")
                    Text("$balance BTC", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Trạng thái: $status", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Địa chỉ:", fontSize = 12.sp)
            Text(address, fontSize = 11.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                isSyncing = true
                status = "Đang kết nối..."
                CoroutineScope(Dispatchers.IO).launch {
                    walletManager.sync {}
                    val bal = walletManager.getBalance()
                    withContext(Dispatchers.Main) {
                        balance = bal
                        status = "Đã sync"
                        isSyncing = false
                    }
                }
            }, enabled = !isSyncing, modifier = Modifier.fillMaxWidth()) {
                Text(if (isSyncing) "ĐANG SYNC..." else "SYNC TỪ MẠNG BTC")
            }
        }
    }

    @Composable
    fun ManageTab() {
        val context = LocalContext.current
        var sendTo by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var result by remember { mutableStateOf("") }
        var showSeed by remember { mutableStateOf(false) }
        var seed by remember { mutableStateOf("") }
        val address = remember { walletManager.getReceiveAddress() }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Gửi BTC", fontWeight = FontWeight.Bold)
            OutlinedTextField(value = sendTo, onValueChange = { sendTo = it }, label = { Text("Địa chỉ nhận") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Số BTC (vd: 0.001)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    val res = walletManager.sendCoins(sendTo, amount)
                    withContext(Dispatchers.Main) { result = res }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("GỬI") }
            if (result.isNotEmpty()) Text(result, fontSize = 12.sp)

            Divider(Modifier.padding(vertical = 16.dp))

            Text("Nhận BTC", fontWeight = FontWeight.Bold)
            Text(address, fontSize = 12.sp)
            Button(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("btc", address))
                Toast.makeText(context, "Đã copy địa chỉ", Toast.LENGTH_SHORT).show()
            }) { Text("COPY ĐỊA CHỈ") }

            Divider(Modifier.padding(vertical = 16.dp))

            Text("Bảo mật", fontWeight = FontWeight.Bold)
            Button(onClick = {
                seed = walletManager.getSeed()
                showSeed = true
            }, modifier = Modifier.fillMaxWidth()) { Text("XUẤT 12 TỪ SEED") }

            if (showSeed) {
                AlertDialog(onDismissRequest = { showSeed = false },
                    title = { Text("Seed ví") },
                    text = { Text(seed) },
                    confirmButton = { TextButton(onClick = { showSeed = false }) { Text("Đóng") } }
                )
            }
        }
    }
}