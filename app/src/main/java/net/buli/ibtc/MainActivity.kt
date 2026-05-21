package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    private var qrCallback: ((String)->Unit)? = null
    private val qrLauncher = registerForActivityResult(ScanContract()){ res -> res.contents?.let{ qrCallback?.invoke(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent { MaterialTheme {
            var tab by remember{ mutableStateOf(0) }
            Scaffold(topBar = {
                TabRow(tab){ Tab(tab==0,{tab=0}){Text("Ví")}; Tab(tab==1,{tab=1}){Text("Quản lý")} }
            }){ p -> Box(Modifier.padding(p)){ if(tab==0) WalletTab() else ManageTab() } }
        }}
    }

    @Composable fun WalletTab(){
        var bal by remember{ mutableStateOf(0.0) }
        var price by remember{ mutableStateOf(0.0) }
        var addr by remember{ mutableStateOf("") }
        var status by remember{ mutableStateOf("Chưa sync") }
        LaunchedEffect(Unit){
            wm.initWallet(); addr = wm.getReceiveAddress(); bal = wm.getBalance()
            price = withContext(Dispatchers.IO){ wm.getBtcPrice() }
        }
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally){
            Text("iBTC - SPV", fontSize=28.sp, fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp)){
                    Text("Số dư:")
                    Text(String.format("%.8f BTC", bal), fontSize=28.sp, fontWeight=FontWeight.Bold)
                    Text("≈ $${String.format("%.2f", bal*price)}", fontSize=16.sp)
                    Text("BTC/USD: $${String.format("%.0f", price)}", fontSize=12.sp)
                    Text("Trạng thái: $status", fontSize=12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                status="Đang sync..."; CoroutineScope(Dispatchers.IO).launch{
                    wm.sync{}; bal = wm.getBalance(); price = wm.getBtcPrice()
                    withContext(Dispatchers.Main){ status="Đã sync" }
                }
            }, Modifier.fillMaxWidth()){ Text("SYNC TỪ MẠNG BTC") }
        }
    }

    @Composable fun ManageTab(){
        val ctx = LocalContext.current
        var to by remember{ mutableStateOf("") }
        var amt by remember{ mutableStateOf("") }
        var feeSel by remember{ mutableStateOf(1) }
        var customFee by remember{ mutableStateOf("10") }
        var result by remember{ mutableStateOf("") }
        var showSeed by remember{ mutableStateOf(false) }
        val addr = remember{ wm.getReceiveAddress() }
        val price = remember{ mutableStateOf(0.0) }
        LaunchedEffect(Unit){ price.value = withContext(Dispatchers.IO){ wm.getBtcPrice() } }

        val feeRate = when(feeSel){0->5L;1->10L;2->20L;else->customFee.toLongOrNull()?:10L}
        val feeBtc = 250*feeRate/1e8
        val total = (amt.toDoubleOrNull()?:0.0)+feeBtc

        Column(Modifier.padding(16.dp).fillMaxSize()){
            Text("Gửi BTC", fontWeight=FontWeight.Bold)
            OutlinedTextField(to,{to=it},label={Text("Địa chỉ")},modifier=Modifier.fillMaxWidth(),
                trailingIcon={ TextButton(onClick={
                    qrCallback={to=it}; qrLauncher.launch(ScanOptions().setPrompt("Quét QR"))
                }){ Text("QR") } })
            OutlinedTextField(amt,{amt=it},label={Text("Số BTC")},modifier=Modifier.fillMaxWidth())
            Text("Phí:",modifier=Modifier.padding(top=8.dp))
            Row{ listOf("Chậm","Thường","Nhanh","Tùy").forEachIndexed{i,t-> RadioButton(feeSel==i,{feeSel=i}); Text(t,Modifier.padding(end=8.dp)) } }
            if(feeSel==3) OutlinedTextField(customFee,{customFee=it},label={Text("sat/vB")})
            Text("Phí ước tính: ${String.format("%.8f",feeBtc)} BTC (≈ $${String.format("%.2f",feeBtc*price.value)})")
            Text("Tổng: ${String.format("%.8f",total)} BTC")
            Button({ CoroutineScope(Dispatchers.IO).launch{
                val r = wm.sendCoins(to, amt.toDoubleOrNull()?:0.0, feeRate)
                withContext(Dispatchers.Main){ result=r }
            }},Modifier.fillMaxWidth()){ Text("GỬI") }
            if(result.isNotEmpty()) Text(result, fontSize=12.sp)

            Divider(Modifier.padding(vertical=12.dp))
            Text("Nhận BTC", fontWeight=FontWeight.Bold)
            val qr = remember{ generateQr(addr) }
            Image(qr.asImageBitmap(),null,Modifier.size(180.dp).align(Alignment.CenterHorizontally))
            Text(addr,fontSize=12.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
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
        val bmp=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565)
        for(x in 0 until size) for(y in 0 until size) bmp.setPixel(x,y,if(bits.get(x,y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        return bmp
    }
}