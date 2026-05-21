package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    private var qrCallback: ((String) -> Unit)? = null
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { qrCallback?.invoke(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var price by remember { mutableStateOf(0.0) }
                var activeWalletId by remember { mutableStateOf(wm.getActive()?.id ?: "") }

                LaunchedEffect(hasWallet) {
                    if (hasWallet && wm.hasWallets()) {
                        withContext(Dispatchers.IO) {
                            wm.init()
                            price = wm.price()
                            wm.getActive()?.let { activeWalletId = it.id }
                        }
                    }
                }

                if (!hasWallet) {
                    Onboarding(
                        onCreate = { name ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                wm.create(name)
                                wm.init()
                                hasWallet = true
                                wm.getActive()?.let { activeWalletId = it.id }
                            }
                        },
                        onImport = { name, seed ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (wm.import(name, seed) != null) {
                                    wm.init()
                                    hasWallet = true
                                    wm.getActive()?.let { activeWalletId = it.id }
                                }
                            }
                        }
                    )
                } else {
                    var tab by remember { mutableStateOf(0) }
                    Scaffold(
                        topBar = {
                            TabRow(selectedTabIndex = tab) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Ví") }
                                Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Quản lý") }
                                Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("Tùy chỉnh") }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            if (!wm.isReady() && wm.hasWallets()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (!wm.hasWallets()) {
                                LaunchedEffect(Unit) { hasWallet = false }
                            } else {
                                when (tab) {
                                    0 -> WalletTab(activeWalletId, price) { price = it }
                                    1 -> ManageTab(onSwitched = { activeWalletId = it }, onNoWallets = { hasWallet = false })
                                    2 -> SettingsTab()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Onboarding(onCreate: (String) -> Unit, onImport: (String, String) -> Unit) {
        var showCreate by remember { mutableStateOf(false) }
        var showImport by remember { mutableStateOf(false) }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            Arrangement.Center,
            Alignment.CenterHorizontally
        ) {
            Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { showCreate = true }, Modifier.fillMaxWidth()) { Text("TẠO VÍ MỚI") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showImport = true }, Modifier.fillMaxWidth()) { Text("IMPORT SEED") }
        }
        if (showCreate) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreate = false },
                confirmButton = { TextButton(onClick = { showCreate = false; onCreate(name) }) { Text("Tạo") } },
                title = { Text("Tên ví") },
                text = { OutlinedTextField(name, { name = it }, label = { Text("Tên") }) }
            )
        }
        if (showImport) {
            var name by remember { mutableStateOf("") }
            var seed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showImport = false },
                confirmButton = { TextButton(onClick = { showImport = false; onImport(name, seed) }) { Text("Import") } },
                title = { Text("Import ví") },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it }, label = { Text("Tên") })
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(seed, { seed = it }, label = { Text("12 từ seed") })
                    }
                }
            )
        }
    }