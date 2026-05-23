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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { App() } } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val wm = remember { WalletManager(ctx) }
    var bal by remember { mutableStateOf(0.0) }
    var price by remember { mutableStateOf(0.0) }
    var prog by remember { mutableStateOf(0) }
    var stat by remember { mutableStateOf("Chưa sync") }
    var txs by remember { mutableStateOf(emptyList<TxInfo>()) }
    var hide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!wm.hasWallet()) wm.newWallet()
        wm.onProg = { p, s -> prog = p; stat = s }
        withContext(Dispatchers.IO) { wm.init() }
        while (true) { withContext(Dispatchers.IO) { bal = wm.balance(); price = wm.price(); txs = wm.txs() }; delay(30000) }
    }

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // BẢN GỐC: màu trắng alpha 0.6 -> mờ trong dark
            Text(
                if (hide) "••••••••" else "%.8f BTC".format(bal),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { hide = !hide }) { Text(if (hide) "Hiện" else "Ẩn") }
        }
        Text("≈ $${"%,.0f".format(bal * price)}")
        Text(stat)
        if (prog < 100) LinearProgressIndicator(prog / 100f, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Spacer(Modifier.height(12.dp))
        Text("Địa chỉ: ${wm.address()}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn { items(txs) { t -> Text("${t.type} %.8f".format(t.amt)) } }
    }
}