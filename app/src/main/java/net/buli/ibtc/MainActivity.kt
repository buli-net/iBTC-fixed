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
import androidx.work.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        val work = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("btc-sync", ExistingPeriodicWorkPolicy.KEEP, work)
        setContent{ MaterialTheme(colorScheme = darkColorScheme()){ App() } }
    }
}
@Composable fun App(){
    val wm = remember{ WalletManager(LocalContext.current) }
    var tab by remember{ mutableIntStateOf(0) }; var price by remember{ mutableDoubleStateOf(0.0) }; val scope=rememberCoroutineScope()
    Scaffold(bottomBar={ NavigationBar{ NavigationBarItem(tab==0,{tab=0},{},{Text("Ví")}); NavigationBarItem(tab==1,{tab=1},{},{Text("Gửi")}) } }){p->
        Box(Modifier.padding(p)){
            if(tab==0){
                var bal by remember{mutableStateOf(0.0)}; var prog by remember{mutableStateOf(0)}; var st by remember{mutableStateOf("Chưa sync")}; var txs by remember{mutableStateOf(listOf<TransactionInfo>())}; var last by remember{mutableStateOf("--:--:--")}; val tf=SimpleDateFormat("HH:mm:ss"); val df=SimpleDateFormat("dd/MM HH:mm")
                LaunchedEffect(Unit){ wm.onProgress{p,t->prog=p;st=t}; withContext(Dispatchers.IO){try{wm.init();bal=wm.getBalance();price=wm.price();txs=wm.getTransactions();last=tf.format(Date())}catch(_:Exception){}}; while(true){delay(30000); withContext(Dispatchers.IO){try{price=wm.price();bal=wm.getBalance();txs=wm.getTransactions();last=tf.format(Date())}catch(_:Exception){}}}}
                Column(Modifier.padding(16.dp)){ Card(Modifier.fillMaxWidth()){ Column(Modifier.padding(16.dp)){ Text("Số dư:"); Text("%.8f BTC".format(bal),28.sp,FontWeight.Bold); Text(if(price>0)"$${"%,.0f".format(price)} / BTC" else "$---"); Text(st,12.sp); Text("Auto sync: $last",11.sp,color=MaterialTheme.colorScheme.primary); if(prog in 1..99) LinearProgressIndicator(prog/100f,Modifier.fillMaxWidth().padding(top=8.dp)) } }; Button({scope.launch(Dispatchers.IO){try{bal=wm.getBalance();price=wm.price();txs=wm.getTransactions();last=tf.format(Date())}catch(_:Exception){}}},Modifier.fillMaxWidth()){Text("SYNC")}; Spacer(16.dp); Text("Lịch sử",fontWeight=FontWeight.Bold); LazyColumn(Modifier.fillMaxSize()){items(txs){tx-> Card(Modifier.fillMaxWidth().padding(4.dp)){ Row(Modifier.padding(12.dp)){ Column(Modifier.weight(1f)){Text(tx.type,FontWeight.Bold);Text("%.8f".format(tx.amount));Text(df.format(tx.time),11.sp)}; Text(tx.txId.take(8),12.sp) } } } } }
            }else{ var to by remember{mutableStateOf("")}; var amt by remember{mutableStateOf("")}; var msg by remember{mutableStateOf("")}; Column(Modifier.padding(16.dp)){ OutlinedTextField(to,{to=it},label={Text("Địa chỉ")},Modifier.fillMaxWidth()); OutlinedTextField(amt,{amt=it},label={Text("Số BTC")},Modifier.fillMaxWidth()); Button({scope.launch(Dispatchers.IO){try{msg=wm.send(to,amt.toDouble())}catch(e:Exception){msg=e.message?:"Lỗi"}}}){Text("Gửi")}; Text(msg); Spacer(16.dp); Text("Địa chỉ nhận:"); Text(wm.getAddress()) } }
        }
    }
}