package net.buli.ibtc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { App() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val wm = remember { WalletManager(ctx) }
    val clip = LocalClipboardManager.current
    var tab by remember { mutableStateOf(0) }
    var bal by remember { mutableStateOf(0.0) }
    var price by remember { mutableStateOf(0.0) }
    var prog by remember { mutableStateOf(0) }
    var stat by remember { mutableStateOf("Khởi tạo...") }
    var txs by remember { mutableStateOf(emptyList<TxInfo>()) }
    var hide by remember { mutableStateOf(true) }
    var to by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(Unit) {
        if (!wm.hasWallet()) wm.newWallet()
        wm.onProg = { p, s -> prog = p; stat = s }
        withContext(Dispatchers.IO) { wm.init(); qr = wm.qrBitmap(wm.address()) }
        while (true) {
            withContext(Dispatchers.IO) { bal = wm.balance(); price = wm.price(); txs = wm.txs() }
            delay(15000)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("iBTC v4.2") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            TabRow(tab) {
                listOf("Ví","Nhận","Gửi").forEachIndexed { i,t -> Tab(selected = tab==i, onClick={tab=i}, text={Text(t)}) }
            }
            Spacer(Modifier.height(16.dp))

            when(tab) {
                0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if(hide) "••••••••" else "%.8f BTC".format(bal),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight=FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface) // FIX DARK
                        IconButton({hide=!hide}){ Icon(if(hide) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) }
                    }
                    Text("≈ $${"%,.2f".format(bal*price)}", color=MaterialTheme.colorScheme.primary)
                    Text(stat, style=MaterialTheme.typography.bodySmall)
                    if(prog<100) LinearProgressIndicator(prog/100f, Modifier.fillMaxWidth().padding(top=8.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Lịch sử", style=MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        items(txs){t-> ListItem(
                            headlineContent={Text(t.type)},
                            supportingContent={Text(t.time.toString().take(16))},
                            trailingContent={Text("%+.8f".format(t.amt), color=if(t.amt>0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}
                        )}
                    }
                }
                1 -> {
                    Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.fillMaxWidth()) {
                        qr?.let{ Image(it.asImageBitmap(), null, Modifier.size(240.dp)) }
                        Spacer(Modifier.height(12.dp))
                        Text(wm.address(), style=MaterialTheme.typography.bodySmall)
                        IconButton({clip.setText(AnnotatedString(wm.address()))}){ Icon(Icons.Default.ContentCopy,null) }
                    }
                }
                2 -> {
                    OutlinedTextField(to,{to=it},label={Text("Địa chỉ")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(amt,{amt=it},label={Text("BTC")},modifier=Modifier.fillMaxWidth())
                    Button({ try{ wm.send(to, amt.toDouble()) }catch(_:Exception){} }, Modifier.fillMaxWidth().padding(top=12.dp)){ Text("GỬI") }
                }
            }
        }
    }
}