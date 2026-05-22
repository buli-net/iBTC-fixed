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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    private var qrCallback: ((String) -> Unit)? = null
    private val qrLauncher = registerForActivityResult(ScanContract()) { r -> r.contents?.let { qrCallback?.invoke(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if(dark) darkColorScheme() else lightColorScheme()) {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var price by remember { mutableStateOf(0.0) }
                var walletName by remember { mutableStateOf(wm.getActive()?.name ?: "") }
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()

                LaunchedEffect(hasWallet){ if(hasWallet){ withContext(Dispatchers.IO){ try{wm.init(); walletName=wm.getActive()?.name?:""; price=wm.price()}catch(_:Exception){} } } }

                if(!hasWallet){
                    var c by remember{mutableStateOf(false)}; var i by remember{mutableStateOf(false)}
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally){
                        Text("iBTC", fontSize=32.sp, fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Button({c=true}, Modifier.fillMaxWidth()){Text("TẠO VÍ MỚI")}
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton({i=true}, Modifier.fillMaxWidth()){Text("IMPORT SEED")}
                    }
                    if(c){ var n by remember{mutableStateOf("")}; AlertDialog(onDismissRequest={c=false}, confirmButton={TextButton({c=false; lifecycleScope.launch(Dispatchers.IO){wm.create(n); wm.init(); withContext(Dispatchers.Main){hasWallet=true; walletName=wm.getActive()?.name?:""}}}){Text("Tạo")}}, title={Text("Tên ví")}, text={OutlinedTextField(n,{n=it},singleLine=true)}) }
                    if(i){ var n by remember{mutableStateOf("")}; var s by remember{mutableStateOf("")}; AlertDialog(onDismissRequest={i=false}, confirmButton={TextButton({i=false; lifecycleScope.launch(Dispatchers.IO){if(wm.import(n,s.trim())!=null){wm.init(); withContext(Dispatchers.Main){hasWallet=true; walletName=wm.getActive()?.name?:""}}}}) {Text("Import")}}, title={Text("Import")}, text={Column{OutlinedTextField(n,{n=it},label={Text("Tên")},singleLine=true); Spacer(Modifier.height(8.dp)); OutlinedTextField(s,{s=it},label={Text("Seed 12 từ")})}})}
                } else {
                    var tab by remember{mutableStateOf(0)}; var menu by remember{mutableStateOf(false)}; var rename by remember{mutableStateOf(false)}; var details by remember{mutableStateOf(false)}; var del by remember{mutableStateOf(false)}
                    Scaffold(topBar={TopAppBar(title={Text(walletName)}, actions={IconButton({menu=true}){Text("⋮",fontSize=20.sp)}; DropdownMenu(menu,{menu=false}){ DropdownMenuItem({Text("Đổi tên")},{menu=false;rename=true}); DropdownMenuItem({Text("Chi tiết")},{menu=false;details=true}); DropdownMenuItem({Text("Xóa ví")},{menu=false;del=true}) }})}){ p->
                        Box(Modifier.padding(p)){
                            Column(Modifier.fillMaxSize()){
                                TabRow(tab){ Tab(tab==0,{tab=0}){Text("Ví",Modifier.padding(12.dp))}; Tab(tab==1,{tab=1}){Text("Gửi/Nhận",Modifier.padding(12.dp))} }
                                if(tab==0){
                                    var bal by remember{mutableStateOf(0.0)}; var prog by remember{mutableStateOf(0)}; var st by remember{mutableStateOf("Chưa sync")}; var txs by remember{mutableStateOf(listOf<TransactionInfo>())}; val fmt=SimpleDateFormat("dd/MM HH:mm",Locale.getDefault())
                                    LaunchedEffect(Unit){ wm.onProgress{p,t-> scope.launch{prog=p;st=t}}; withContext(Dispatchers.IO){ bal=wm.getBalance(); price=wm.price(); txs=wm.getTransactions() } }
                                    Column(Modifier.padding(16.dp)){
                                        Card(Modifier.fillMaxWidth()){ Column(Modifier.padding(16.dp)){ Text("Số dư:"); Text("%.8f BTC".format(bal), fontSize=28.sp, fontWeight=FontWeight.Bold); Text(if(price>0)"≈ $%.2f".format(bal*price) else "≈ $---"); Text(st, fontSize=12.sp); if(prog in 1..99) LinearProgressIndicator(prog/100f, Modifier.fillMaxWidth().padding(top=8.dp)) } }
                                        Spacer(Modifier.height(8.dp)); Button({scope.launch(Dispatchers.IO){ bal=wm.getBalance(); price=wm.price(); txs=wm.getTransactions(); st="Đã sync" }}, Modifier.fillMaxWidth()){Text("SYNC")}
                                        Spacer(Modifier.height(16.dp)); Text("Lịch sử", fontWeight=FontWeight.Bold)
                                        LazyColumn(Modifier.fillMaxSize()){ items(txs){ tx-> Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){ Row(Modifier.padding(12.dp)){ Column(Modifier.weight(1f)){ Text(tx.type, fontWeight=FontWeight.Bold); Text("%.8f".format(tx.amount)); Text(fmt.format(tx.time), fontSize=11.sp) }; Text(tx.txId.take(8), fontSize=12.sp) } } } }
                                    }
                                } else {
                                    var to by remember{mutableStateOf("")}; var amt by remember{mutableStateOf("")}; var res by remember{mutableStateOf("")}; var addr by remember{mutableStateOf("")}; var fees by remember{mutableStateOf(FeeRates(4,6,8))}; var sel by remember{mutableStateOf(1)}; var cache by remember{mutableStateOf(0L)}
                                    LaunchedEffect(Unit){ withContext(Dispatchers.IO){ addr=wm.getAddress(); if(System.currentTimeMillis()-cache>60000){fees=wm.getFeeRates(); cache=System.currentTimeMillis()} } }
                                    val fee = when(sel){0->fees.slow;1->fees.normal;else->fees.fast}
                                    LazyColumn(Modifier.padding(16.dp)){ item{
                                        Text("Gửi BTC", fontWeight=FontWeight.Bold)
                                        OutlinedTextField(to,{to=it}, label={Text("bc1...")}, modifier=Modifier.fillMaxWidth(), trailingIcon={TextButton({qrCallback={s-> if(s.startsWith("bc1")||s.startsWith("1")||s.startsWith("3")||s.startsWith("bitcoin:")) to=s.replace("bitcoin:","").substringBefore("?")}; qrLauncher.launch(ScanOptions())}){Text("QR")}})
                                        OutlinedTextField(amt,{amt=it}, label={Text("BTC")}, modifier=Modifier.fillMaxWidth())
                                        Row(verticalAlignment=Alignment.CenterVertically){ RadioButton(sel==0,{sel=0}); Text("Chậm ${fees.slow}"); Spacer(Modifier.width(8.dp)); RadioButton(sel==1,{sel=1}); Text("Thường ${fees.normal}"); Spacer(Modifier.width(8.dp)); RadioButton(sel==2,{sel=2}); Text("Nhanh ${fees.fast}") }
                                        Button({ lifecycleScope.launch(Dispatchers.IO){ res = wm.send(to, amt.toDoubleOrNull()?:0.0, fee) } }, Modifier.fillMaxWidth()){Text("GỬI (RBF)")}
                                        if(res.isNotEmpty()) Text(res, fontSize=12.sp)
                                        Spacer(Modifier.height(24.dp)); Text("Nhận BTC", fontWeight=FontWeight.Bold)
                                        val qr = remember(addr){ val sz=512; val bm=QRCodeWriter().encode(addr.ifEmpty{"bitcoin:"}, BarcodeFormat.QR_CODE, sz,sz); Bitmap.createBitmap(sz,sz,Bitmap.Config.RGB_565).apply{ for(x in 0 until sz) for(y in 0 until sz) setPixel(x,y, if(bm.get(x,y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
                                        Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally){ Image(qr.asImageBitmap(),null,Modifier.size(220.dp)); Spacer(Modifier.height(8.dp)); SelectionContainer{Text(addr)} }
                                        Button({ val c=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; c.setPrimaryClip(ClipData.newPlainText("btc",addr)); Toast.makeText(ctx,"Đã copy",Toast.LENGTH_SHORT).show(); scope.launch{delay(30000); c.setPrimaryClip(ClipData.newPlainText("",""))} }, Modifier.fillMaxWidth()){Text("COPY")}
                                    } }
                                }
                            }
                        }
                        if(rename){ var n by remember{mutableStateOf(walletName)}; AlertDialog({rename=false},{TextButton({wm.rename(wm.getActive()!!.id,n); walletName=n; rename=false}){Text("Lưu")}}, title={Text("Đổi tên")}, text={OutlinedTextField(n,{n=it},singleLine=true)}) }
                        if(details){ AlertDialog({details=false},{TextButton({details=false}){Text("Đóng")}}, title={Text("Chi tiết")}, text={ Column{ val s=wm.getSeed(); val a=wm.getAddress(); Text("Tên: $walletName", fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Địa chỉ:"); SelectionContainer{Text(a)}; Spacer(Modifier.height(8.dp)); Text("Seed:"); SelectionContainer{Text(s)} } }) }
                        if(del){ AlertDialog({del=false},{TextButton({ val id=wm.getActive()?.id; lifecycleScope.launch(Dispatchers.IO){ try{wm.stop()}catch(_:Exception){}; if(id!=null) wm.delete(id); withContext(Dispatchers.Main){hasWallet=false; del=false} }}){Text("Xóa")}}, dismissButton={TextButton({del=false}){Text("Hủy")}}, title={Text("Xóa ví?")}, text={Text("Không thể hoàn tác")}) }
                    }
                }
            }
        }
    }
    override fun onDestroy(){ super.onDestroy(); try{wm.stop()}catch(_:Exception){} }
}