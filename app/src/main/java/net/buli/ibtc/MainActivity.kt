package net.buli.ibtc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val wm = remember { WalletManager(context) }
    var bal by remember { mutableStateOf(0.0) }
    var price by remember { mutableStateOf(0.0) }
    var prog by remember { mutableStateOf(0) }
    var stat by remember { mutableStateOf("Chưa sync") }
    var txs by remember { mutableStateOf(emptyList<TxInfo>()) }

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
            delay(30000)
        }
    }

    Column(Modifier.padding(16.dp)) {
        Text("Số dư: %.8f BTC".format(bal), style = MaterialTheme.typography.headlineSmall)
        Text("Giá: $${"%,.0f".format(price)}")
        Text(stat)
        if (prog < 100) {
            LinearProgressIndicator(progress = prog / 100f, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("SYNC") }
        Spacer(Modifier.height(12.dp))
        Text("Địa chỉ: ${wm.address()}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(txs) { t ->
                Text("${t.type} %.8f BTC".format(t.amt))
            }
        }
    }
}