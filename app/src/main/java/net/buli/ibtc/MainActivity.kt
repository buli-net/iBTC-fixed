package net.buli.ibtc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { 
            MaterialTheme(colorScheme = darkColorScheme()) { // ép dark như v4
                Surface { App() } 
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    val wm = remember { WalletManager(context) }
    val clip = LocalClipboardManager.current
    var tab by remember { mutableStateOf(0) }
    var bal by remember { mutableStateOf(0.0) }
    var price by remember { mutableStateOf(0.0) }
    var prog by remember { mutableStateOf(0) }
    var stat by remember { mutableStateOf("Đang kết nối...") }
    var txs by remember { mutableStateOf(emptyList<TxInfo>()) }
    var hide by remember { mutableStateOf(true) }
    var to by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!wm.hasWallet()) wm.newWallet()
        wm.onProg = { p, s -> prog = p; stat = s }
        withContext(Dispatchers.IO) { wm.init() }
        while (true) {
            withContext(Dispatchers.IO) {
                bal = wm.balance()
                price = wm.price()
                txs = wm.txs()
            }
            delay(15000)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("iBTC v4") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab==0, onClick = {tab=0}, text = {Text("Ví")})
                Tab(selected = tab==1, onClick = {tab=1}, text = {Text("Gửi")})
            }
            Spacer(Modifier.height(16.dp))

            if (tab==0) {
                // SỐ DƯ - fix mờ
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hide) "••••••••" else "%.8f".format(bal),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface // <-- FIX: không dùng alpha
                    )
                    IconButton(onClick = { hide = !hide }) {
                        Icon(if(hide) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                }
                Text("≈ $${"%,.2f".format(bal*price)}", color = MaterialTheme.colorScheme.primary)
                Text(stat, style = MaterialTheme.typography.bodySmall)
                if (prog<100) LinearProgressIndicator(prog/100f, Modifier.fillMaxWidth().padding(top=8.dp))

                Spacer(Modifier.height(20.dp))
                OutlinedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("Địa chỉ nhận", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(wm.address(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { clip.setText(AnnotatedString(wm.address())) }) {
                                Icon(Icons.Default.ContentCopy, null)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Lịch sử", style = MaterialTheme.typography.titleMedium)
                LazyColumn { items(txs) { t -> 
                    ListItem(headlineContent = { Text(t.type) }, supportingContent = { Text(t.id.take(10)+"...") }, trailingContent = { Text("%.8f".format(t.amt)) })
                }}
            } else {
                OutlinedTextField(to, {to=it}, label={Text("Địa chỉ BTC")}, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(amt, {amt=it}, label={Text("Số lượng")}, modifier=Modifier.fillMaxWidth())
                Button(onClick = {
                    try { wm.send(to, amt.toDouble()) } catch (_:Exception){}
                }, modifier=Modifier.fillMaxWidth().padding(top=12.dp)) { Text("GỬI") }
            }
        }
    }
}