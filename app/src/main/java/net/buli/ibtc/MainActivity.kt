package net.buli.ibtc

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    @Composable
    fun App() {
        var address by remember { mutableStateOf("") }
        var balance by remember { mutableStateOf("0.00000000") }
        var syncStatus by remember { mutableStateOf("") }
        var isSyncing by remember { mutableStateOf(false) }
        var showSeed by remember { mutableStateOf<List<String>?>(null) }
        var sendTo by remember { mutableStateOf("") }
        var sendAmount by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            if (WalletManager.hasWallet(this@MainActivity)) {
                address = WalletManager.getAddress(this@MainActivity)
            }
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("iBTC Wallet - SPV", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (!WalletManager.hasWallet(this@MainActivity)) {
                Button(onClick = {
                    showSeed = WalletManager.createWallet(this@MainActivity)
                    address = WalletManager.getAddress(this@MainActivity)
                }, Modifier.fillMaxWidth()) { Text("TẠO VÍ BTC MỚI") }
                
                showSeed?.let {
                    Text("GHI 12 TỪ NÀY RA GIẤY:", color = MaterialTheme.colorScheme.error)
                    Text(it.joinToString(" "), fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Địa chỉ bc1 của bạn:", fontSize = 12.sp)
                Text(address, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                
                Spacer(Modifier.height(8.dp))
                Text("Số dư: $balance BTC", fontSize = 18.sp)
                if (syncStatus.isNotEmpty()) Text(syncStatus, fontSize = 12.sp)
                
                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            balance = WalletManager.syncAndGetBalance(this@MainActivity) { status ->
                                syncStatus = status
                            }
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isSyncing) "Đang sync..." else "SYNC TỪ MẠNG BTC") }
                
                Spacer(Modifier.height(16.dp))
                if (address.isNotEmpty()) {
                    val bitmap = remember(address) {
                        BarcodeEncoder().encodeBitmap(address, BarcodeFormat.QR_CODE, 400, 400)
                    }
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR", Modifier.align(Alignment.CenterHorizontally))
                }

                Spacer(Modifier.height(16.dp))
                Text("GỬI BTC", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = sendTo, onValueChange = { sendTo = it }, label = { Text("Địa chỉ nhận") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sendAmount, onValueChange = { sendAmount = it }, label = { Text("Số lượng BTC") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        scope.launch {
                            val result = WalletManager.send(this@MainActivity, sendTo, sendAmount)
                            Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing
                ) { Text("GỬI") }
            }
        }
    }
}