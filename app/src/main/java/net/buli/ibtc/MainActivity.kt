package net.buli.ibtc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    WalletScreen()
                }
            }
        }
    }

    @Composable
    fun WalletScreen() {
        var balance by remember { mutableStateOf("0.00000000") }
        var address by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Chưa sync") }
        var isSyncing by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            walletManager.initWallet()
            address = walletManager.getReceiveAddress()
            balance = walletManager.getBalance()
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("iBTC - SPV Wallet", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Số dư:", fontSize = 14.sp)
                    Text("$balance BTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Trạng thái: $status", fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text("Địa chỉ nhận:", fontSize = 14.sp)
            Text(address, fontSize = 12.sp)
            
            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = {
                    isSyncing = true
                    status = "Đang kết nối mạng BTC..."
                    CoroutineScope(Dispatchers.IO).launch {
                        walletManager.sync { progress ->
                            CoroutineScope(Dispatchers.Main).launch {
                                status = "Sync: $progress%"
                            }
                        }
                        val newBalance = walletManager.getBalance()
                        withContext(Dispatchers.Main) {
                            balance = newBalance
                            status = "Đã sync xong"
                            isSyncing = false
                        }
                    }
                },
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSyncing) "ĐANG SYNC..." else "SYNC TỪ MẠNG BTC")
            }
        }
    }
}