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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val work = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("btc-sync", ExistingPeriodicWorkPolicy.KEEP, work)
        setContent{ MaterialTheme(colorScheme = darkColorScheme()){ App() } }
    }
}

@Composable fun App(){
    val context = LocalContext.current
    val wm = remember{ WalletManager(context) }
    var tab by remember{ mutableIntStateOf(0) }
    var price by remember{ mutableDoubleStateOf(0.0) }
    val scope = rememberCoroutineScope()

    Scaffold(bottomBar = {
        NavigationBar{
            NavigationBarItem(selected = tab==0, onClick={tab=0}, icon={}, label={Text("Ví")})
            NavigationBarItem(selected = tab==1, onClick={tab=1}, icon={}, label={Text("Gửi/Nhận")})
        }
    }){ pad ->
        Box(Modifier.padding(pad)){
            if(tab==0){
                var bal by remember{ mutableStateOf(0.0) }
                var prog by remember{ mutableStateOf(0) }
                var st by remember{ mutableStateOf("Chưa sync") }
                var txs by remember{ mutableStateOf(listOf<TransactionInfo>()) }
                var lastSync by remember{ mutableStateOf("--:--:--") }
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                LaunchedEffect(Unit){
                    wm.onProgress{ p,t -> prog=p; st=t }
                    withContext(Dispatchers.IO){
                        try{ wm.init(); bal=wm.getBalance(); price=wm.price(); txs=wm.getTransactions(); lastSync=timeFmt.format(Date()) }catch(_:Exception){}
                    }
                    while(true){
                        delay(30000)
                        withContext(Dispatchers.IO){
                            try{
                                if(wm.getActive()!=null){
                                    price=wm.price(); bal=wm.getBalance(); txs=wm.getTransactions()
                                    lastSync=timeFmt.format(Date())
                                    st = if(prog>=99) "Đã sync" else st
                                }
                            }catch(_:Exception){}
                        }
                    }
                }

                Column(Modifier.padding(16.dp)){
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(16.dp)){
                            Text("Số dư:")
                            Text("%.8f BTC".format(bal), fontSize=28.sp, fontWeight=FontWeight.Bold)
                            Text(if(price>0) "$${"%,.0f".format(price)} / BTC" else "$--- / BTC")
                            Text(st, fontSize=12.sp)
                            Text("Auto sync: $lastSync", fontSize=11.sp, color=MaterialTheme.colorScheme.primary)
                            if(prog in 1..99) LinearProgressIndicator(prog/100f, Modifier.fillMaxWidth().padding(top=8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick={
                        scope.launch(Dispatchers.IO){
                            try{ bal=wm.getBalance(); price=wm.price(); txs=wm.getTransactions(); lastSync=timeFmt.format(Date()); st="Sync tay lúc $lastSync" }catch(_:Exception){}
                        }
                    }, Modifier.fillMaxWidth()){ Text("SYNC") }
                    Spacer(Modifier.height(16.dp))
                    Text("Lịch sử", fontWeight=FontWeight.Bold)
                    LazyColumn(Modifier.fillMaxSize()){
                        items(txs){ tx ->
                            Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                                Row(Modifier.padding(12.dp)){
                                    Column(Modifier.weight(1f)){
                                        Text(tx.type, fontWeight=FontWeight.Bold)
                                        Text("%.8f".format(tx.amount))
                                        Text(fmt.format(tx.time), fontSize=11.sp)
                                    }
                                    Text(tx.txId.take(8), fontSize=12.sp)
                                }
                            }
                        }
                    }
                }
            }else{
                var to by remember{ mutableStateOf("") }
                var amt by remember{ mutableStateOf("") }
                var msg by remember{ mutableStateOf("") }
                Column(Modifier.padding(16.dp)){
                    OutlinedTextField(to,{to=it}, label={Text("Địa chỉ")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(amt,{amt=it}, label={Text("Số BTC")}, modifier=Modifier.fillMaxWidth())
                    Button(onClick={
                        scope.launch(Dispatchers.IO){
                            try{ msg = wm.send(to, amt.toDouble()) }catch(e:Exception){ msg = e.message?: "Lỗi" }
                        }
                    }){ Text("Gửi") }
                    Text(msg)
                    Spacer(Modifier.height(16.dp))
                    Text("Địa chỉ nhận:")
                    Text(wm.getAddress())
                }
            }
        }
    }
}