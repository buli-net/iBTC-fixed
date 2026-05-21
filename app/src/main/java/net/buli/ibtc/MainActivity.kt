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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    private val qrLauncher = registerForActivityResult(ScanContract()){ it.contents?.let{qrCallback?.invoke(it)} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent {
            MaterialTheme {
                var hasWallet by remember{ mutableStateOf(wm.hasWallets()) }
                var price by remember{ mutableStateOf(0.0) }
                LaunchedEffect(hasWallet){
                    if(hasWallet){ withContext(Dispatchers.IO){ wm.init(); price = wm.price() } }
                }
                if(!hasWallet){
                    Onboarding({n-> lifecycleScope.launch(Dispatchers.IO){ wm.create(n); wm.init(); hasWallet=true } },
                               {n,s-> lifecycleScope.launch(Dispatchers.IO){ if(wm.import(n,s)!=null){wm.init();hasWallet=true}}})
                } else {
                    var tab by remember{ mutableStateOf(0) }
                    Scaffold(topBar={ TabRow(tab){ Tab(tab==0,{tab=0}){Text("Ví")}; Tab(tab==1,{tab=1}){Text("Quản lý")} } }){p->
                        Box(Modifier.padding(p)){
                            if(!wm.isReady()) Box(Modifier.fillMaxSize(),Alignment.Center){ CircularProgressIndicator() }
                            else if(tab==0) WalletTab(price) else ManageTab()
                        }
                    }
                }
            }
        }
    }

    @Composable fun Onboarding(onCreate:(String)->Unit,onImport:(String,String)->Unit){
        var c by remember{mutableStateOf(false)}; var i by remember{mutableStateOf(false)}
        Column(Modifier.fillMaxSize().padding(32.dp),Arrangement.Center,Alignment.CenterHorizontally){
            Text("iBTC",fontSize=32.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(24.dp))
            Button({c=true},Modifier.fillMaxWidth()){Text("TẠO VÍ MỚI")}
            OutlinedButton({i=true},Modifier.fillMaxWidth()){Text("IMPORT SEED")}
        }
        if(c){ var n by remember{mutableStateOf("")}; AlertDialog({c=false},{TextButton({c=false;onCreate(n)}){Text("Tạo")}},title={Text("Tên ví")},text={OutlinedTextField(n,{n=it},label={Text("Tên")})}) }
        if(i){ var n by remember{mutableStateOf("")}; var s by remember{mutableStateOf("")}; AlertDialog({i=false},{TextButton({i=false;onImport(n,s)}){Text("Import")}},title={Text("Import")},text={Column{OutlinedTextField(n,{n=it},label={Text("Tên")}); OutlinedTextField(s,{s=it},label={Text("12 từ")})}})}
    }

    @Composable fun WalletTab(price:Double){
        var bal by remember{mutableStateOf(wm.getBalance())}
        var pct by remember{mutableStateOf(0)}; var txt by remember{mutableStateOf("Chưa sync")}
        LaunchedEffect(Unit){ wm.onProgress{p,t-> pct=p; txt=t } }
        LaunchedEffect(Unit){ withContext(Dispatchers.IO){ wm.sync(); bal=wm.getBalance() } }
        LaunchedEffect(Unit){ while(true){ delay(60_000); withContext(Dispatchers.IO){ bal=wm.getBalance() } } }
        Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Card(Modifier.fillMaxWidth()){ Column(Modifier.padding(16.dp)){
                Text("Số dư:"); Text("%.8f BTC".format(bal),fontSize=28.sp,fontWeight=FontWeight.Bold)
                Text("≈ $%.2f".format(bal*price)); Text(txt,fontSize=12.sp)
                if(pct in 1..99) LinearProgressIndicator(pct/100f,Modifier.fillMaxWidth().padding(top=8.dp))
            } }
            Spacer(Modifier.height(16.dp))
            Button({ CoroutineScope(Dispatchers.IO).launch{ wm.sync(); delay(2000); bal=wm.getBalance() } },Modifier.fillMaxWidth()){ Text("SYNC NGAY") }
        }
    }

    @Composable fun ManageTab(){
        val ctx=LocalContext.current; var wallets by remember{mutableStateOf(wm.getAll())}
        var to by remember{mutableStateOf("")}; var amt by remember{mutableStateOf("")}
        var feeSel by remember{mutableStateOf(1)}; var custom by remember{mutableStateOf("10")}
        var res by