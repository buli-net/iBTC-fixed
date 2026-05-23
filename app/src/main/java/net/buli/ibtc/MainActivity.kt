package net.buli.ibtc

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFFFFA726),
                onPrimary = Color.Black,
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E)
            )
            MaterialTheme(colorScheme = darkColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val walletManager = remember { WalletManager(context) }

    var currentTabIndex by remember { mutableStateOf(0) }
    val tabTitles = listOf("VÍ", "NHẬN", "GỬI")

    var btcBalance by remember { mutableStateOf(0.0) }
    var usdPrice by remember { mutableStateOf(0.0) }
    var syncPercentage by remember { mutableStateOf(0) }
    var syncMessage by remember { mutableStateOf("Đang khởi tạo ví...") }
    var transactionList by remember { mutableStateOf(emptyList<TxInfo>()) }
    var isBalanceHidden by remember { mutableStateOf(true) }
    var currentReceiveAddress by remember { mutableStateOf("") }
    var qrCodeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var sendRecipientAddress by remember { mutableStateOf("") }
    var sendBtcAmount by remember { mutableStateOf("") }
    var isTransactionSending by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    LaunchedEffect(key1 = Unit) {
        if (!walletManager.hasWallet()) {
            val mnemonic = walletManager.newWallet()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Đã tạo ví mới", Toast.LENGTH_SHORT).show()
            }
        }

        walletManager.onProg = { progress, message ->
            syncPercentage = progress
            syncMessage = message
        }

        withContext(Dispatchers.IO) {
            walletManager.init()
            currentReceiveAddress = walletManager.address()
            qrCodeBitmap = walletManager.generateQr(currentReceiveAddress)
        }

        while (true) {
            try {
                withContext(Dispatchers.IO) {
                    btcBalance = walletManager.balance()
                    usdPrice = walletManager.priceUsd()
                    transactionList = walletManager.txs()
                    if (currentReceiveAddress.isBlank()) {
                        currentReceiveAddress = walletManager.address()
                        qrCodeBitmap = walletManager.generateQr(currentReceiveAddress)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(15000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "iBTC Wallet v4.1",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = currentTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = currentTabIndex == index,
                        onClick = { currentTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (currentTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (currentTabIndex) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBalanceHidden) "••••••••" else String.format(Locale.US, "%.8f", btcBalance),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { isBalanceHidden = !isBalanceHidden }) {
                                Text(text = if (isBalanceHidden) "HIỆN" else "ẨN")
                            }
                        }
                        Text(
                            text = "BTC",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "≈ $%,.2f USD", btcBalance * usdPrice),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = syncMessage, style = MaterialTheme.typography.bodySmall)
                        if (syncPercentage < 100) {
                            LinearProgressIndicator(
                                progress = syncPercentage / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .height(6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Lịch sử giao dịch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(transactionList) { tx ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.type,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = dateFormatter.format(tx.time),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = tx.id.take(10) + "..." + tx.id.takeLast(6),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.DarkGray,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = String.format(Locale.US, "%+.8f", tx.amt),
                                        color = if (tx.amt >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.End
                                    )
                                }
                                Divider(color = Color(0xFF2A2A2A))
                            }
                        }
                    }
                }
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Quét mã để nhận BTC", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(24.dp))
                        if (qrCodeBitmap != null) {
                            Image(
                                bitmap = qrCodeBitmap!!.asImageBitmap(),
                                contentDescription = "Bitcoin Address QR",
                                modifier = Modifier.size(260.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(260.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = currentReceiveAddress,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(currentReceiveAddress))
                                Toast.makeText(context, "Đã sao chép địa chỉ", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text("SAO CHÉP")
                        }
                    }
                }
                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("Gửi Bitcoin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedTextField(
                            value = sendRecipientAddress,
                            onValueChange = { sendRecipientAddress = it },
                            label = { Text("Địa chỉ nhận") },
                            placeholder = { Text("bc1q...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = sendBtcAmount,
                            onValueChange = { sendBtcAmount = it },
                            label = { Text("Số lượng BTC") },
                            placeholder = { Text("0.001") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Số dư khả dụng: ${String.format(Locale.US, "%.8f BTC", btcBalance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                isTransactionSending = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val amount = sendBtcAmount.toDouble()
                                        val txId = walletManager.send(sendRecipientAddress, amount)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Thành công! TXID: $txId", Toast.LENGTH_LONG).show()
                                            sendRecipientAddress = ""
                                            sendBtcAmount = ""
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Gửi thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isTransactionSending = false
                                        }
                                    }
                                }
                            },
                            enabled = !isTransactionSending && sendRecipientAddress.isNotBlank() && sendBtcAmount.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = if (isTransactionSending) "ĐANG GỬI..." else "XÁC NHẬN GỬI",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}