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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    private var qrCallback: ((String)->Unit)? = null
    private val qrLauncher = registerForActivityResult(ScanContract()){ it.contents?.let{ c-> qrCallback?.invoke(c) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        // init ví ngay, không đợi vào tab
        lifecycleScope.launch(Dispatchers.IO) { wm.initWallet() }

        setContent {
            MaterialTheme {
                var tab by remember { mutableStateOf(0) }
                var price by remember { mutableStateOf(0.0) }
                LaunchedEffect(Unit){ price = withContext(Dispatchers.IO){ wm.getBtcPrice() } }

                Scaffold(topBar = {
                    TabRow(tab){ Tab(tab==0,{tab=0}){Text("Ví")}; Tab(tab==1,{tab=1}){Text("Quản lý")} }
                }){ p ->
                    Box(Modifier.padding(p)){
                        if(!wm.isReady()){
                            Box(Modifier.fillMaxSize(), Alignment.Center){ CircularProgressIndicator() }
                        } else {
                            if(tab==0) WalletTab(price) else ManageTab(price)
                        }
                    }
                }
            }
        }
    }

    @Composable fun WalletTab(price:Double){
        var bal by remember { mutableStateOf(wm.getBalance()) }
        var status by remember { mutableStateOf("Sẵn sàng") }
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally){
            Text("iBTC - SPV", fontSize=28.sp, fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp)){
                    Text("Số dư:")
                    Text(String.format("%.8f BTC", bal), fontSize=28.sp, fontWeight=FontWeight.Bold)
                    Text("≈ $${String.format("%.2f", bal*price)}")
                    Text("BTC/USD: $${String.format("%.0f", price)}", fontSize=12.sp)
                    Text("Trạng thái: $status", fontSize=12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick={
                status="Đang sync..."; CoroutineScope(Dispatchers.IO).launch{
                    wm.sync(); bal = wm.getBalance()
                    withContext(Dispatchers.Main){ status="Đã sync" }
                }
            }, Modifier.fillMaxWidth()){ Text("SYNC TỪ MẠNG BTC") }
        }
    }

    @Composable fun ManageTab(price:Double){
        val ctx = LocalContext.current
        var to by remember{ mutableStateOf("") }
        var amt by remember{ mutableStateOf("") }
        var feeSel by remember{ mutableStateOf(1) }
        var custom by remember{ mutableStateOf("10") }
        var result by remember{ mutableStateOf("") }
        var showSeed by remember{ mutableStateOf(false) }
        val addr = wm.getReceiveAddress()
        val feeRate = when(feeSel){0->5L;1->10L;2->20L;else->custom.toLongOrNull()?:10L}
        val feeBtc = 250*feeRate/1e8
        val feeUsd = feeBtc*price
        val total = (amt.toDoubleOrNull()?:0.0)+feeBtc

        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())){
            Text("Gửi BTC", fontWeight=FontWeight.Bold)
            OutlinedTextField(to,{to=it},label={Text("Địa chỉ")},modifier=Modifier.fillMaxWidth(),
                trailingIcon={ TextButton({ qrCallback={to=it}; qrLauncher.launch(ScanOptions()) }){ Text("QR") } })
            OutlinedTextField(amt,{amt=it},label={Text("Số BTC")},modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically){ listOf("Chậm","Thường","Nhanh","Tùy").forEachIndexed{i,t-> RadioButton(feeSel==i,{feeSel=i}); Text(t)} }
            if(feeSel==3) OutlinedTextField(custom,{custom=it},label={Text("sat/vB")})
            Text("Phí ước tính: ${"%.8f".format(feeBtc)} BTC (≈ $${"%.2f".format(feeUsd)})")
            Text("Tổng: ${"%.8f".format(total)} BTC")
            Button({ CoroutineScope(Dispatchers.IO).launch{
                val r = wm.sendCoins(to, amt.toDoubleOrNull()?:0.0, feeRate)
                withContext(Dispatchers.Main){ result=r }
            }},Modifier.fillMaxWidth()){ Text("GỬI") }
            if(result.isNotEmpty()) Text(result, fontSize=12.sp)

            Divider(Modifier.padding(vertical=12.dp))
            Text("Nhận BTC", fontWeight=FontWeight.Bold)
            val qr = remember(addr){ generateQr(addr) }
            Image(qr.asImageBitmap(),null,Modifier.size(200.dp).align(Alignment.CenterHorizontally))
            Text(addr, fontSize=12.sp, modifier=Modifier.align(Alignment.CenterHorizontally))
            Button({ val cm=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("btc",addr))
                Toast.makeText(ctx,"Đã copy",Toast.LENGTH_SHORT).show()
            },Modifier.align(Alignment.CenterHorizontally)){ Text("COPY") }

            Divider(Modifier.padding(vertical=12.dp))
            Button({showSeed=true},Modifier.fillMaxWidth()){ Text("XUẤT 12 TỪ SEED") }
            if(showSeed) AlertDialog(onDismissRequest={showSeed=false}, confirmButton={TextButton({showSeed=false}){Text("Đóng")}},
                title={Text("Seed")}, text={Text(wm.getSeed())})
        }
    }

    private fun generateQr(text:String): Bitmap {
        val size=512; val bits=QRCodeWriter().encode(text, BarcodeFormat.QR_CODE,size,size)
        return Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565).apply{
            for(x in 0 until size) for(y in 0 until size) setPixel(x,y,if(bits.get(x,y)) -16777216 else -1)
        }
    }
}