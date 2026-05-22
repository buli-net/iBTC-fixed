// ... giữ nguyên phần import đầu file ...

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var wm: WalletManager
    private var qrCallback: ((String) -> Unit)? = null
    private val qrLauncher = registerForActivityResult(ScanContract()) { it.contents?.let { c -> qrCallback?.invoke(c) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        setContent {
            MaterialTheme {
                var hasWallet by remember { mutableStateOf(wm.hasWallets()) }
                var price by remember { mutableStateOf(0.0) }
                var walletName by remember { mutableStateOf(wm.getActive()?.name ?: "") }
                val ctx = LocalContext.current

                LaunchedEffect(hasWallet) { if(hasWallet) withContext(Dispatchers.IO){ try{wm.init(); walletName=wm.getActive()?.name?:""; price=wm.price()}catch(_:Exception){} } }

                if (!hasWallet) { /* màn tạo/import giữ nguyên như bản trước */ 
                    var showCreate by remember { mutableStateOf(false) }
                    var showImport by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("iBTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Button({showCreate=true}, Modifier.fillMaxWidth()){ Text("TẠO VÍ MỚI") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton({showImport=true}, Modifier.fillMaxWidth()){ Text("IMPORT SEED") }
                    }
                    if(showCreate){ var n by remember{mutableStateOf("")}; AlertDialog(onDismissRequest={showCreate=false}, confirmButton={ TextButton({showCreate=false; lifecycleScope.launch(Dispatchers.IO){ wm.create(n); wm.init(); withContext(Dispatchers.Main){ hasWallet=true; walletName=wm.getActive()?.name?:"" } }}){Text("Tạo")} }, title={Text("Tên ví")}, text={ OutlinedTextField(n,{n=it}) }) }
                    if(showImport){ var n by remember{mutableStateOf("")}; var s by remember{mutableStateOf("")}; AlertDialog(onDismissRequest={showImport=false}, confirmButton={ TextButton({showImport=false; lifecycleScope.launch(Dispatchers.IO){ if(wm.import(n,s)!=null){ wm.init(); withContext(Dispatchers.Main){ hasWallet=true; walletName=wm.getActive()?.name?:"" } } }}){Text("Import")} }, title={Text("Import")}, text={ Column{ OutlinedTextField(n,{n=it}, label={Text("Tên")}); Spacer(Modifier.height(8.dp)); OutlinedTextField(s,{s=it}, label={Text("Seed 12 từ")}) } }) }
                } else {
                    var tab by remember { mutableStateOf(0) }
                    var showMenu by remember { mutableStateOf(false) }
                    var showRename by remember { mutableStateOf(false) }
                    var showDetails by remember { mutableStateOf(false) }

                    Scaffold(topBar = {
                        TopAppBar(title={Text(walletName)}, actions={
                            IconButton({showMenu=true}){ Text("⋮", fontSize=20.sp) }
                            DropdownMenu(showMenu, {showMenu=false}) {
                                DropdownMenuItem(text={Text("Đổi tên")}, onClick={showMenu=false; showRename=true})
                                DropdownMenuItem(text={Text("Chi tiết ví")}, onClick={showMenu=false; showDetails=true})
                                DropdownMenuItem(text={Text("Xóa ví")}, onClick={showMenu=false; val id=wm.getActive()?.id; lifecycleScope.launch(Dispatchers.IO){ try{wm.stop()}catch(_:Exception){}; if(id!=null) wm.delete(id); withContext(Dispatchers.Main){ hasWallet=false } } })
                            }
                        })
                    }){ padding ->
                        Box(Modifier.padding(padding)){
                            Column(Modifier.fillMaxSize()){
                                TabRow(tab){ Tab(tab==0,{tab=0}){Text("Ví",Modifier.padding(12.dp))}; Tab(tab==1,{tab=1}){Text("Gửi/Nhận",Modifier.padding(12.dp))} }
                                if(tab==0){
                                    var balance by remember{mutableStateOf(0.0)}
                                    var progress by remember{mutableStateOf(0)}
                                    var status by remember{mutableStateOf("Chưa sync")}
                                    var txs by remember{mutableStateOf(listOf<TransactionInfo>())}
                                    val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())

                                    // AUTO SYNC
                                    LaunchedEffect(Unit){
                                        wm.onProgress{ p,t -> lifecycleScope.launch(Dispatchers.Main){ progress=p; status=t } }
                                        // lần đầu
                                        withContext(Dispatchers.IO){ balance=wm.getBalance(); price=wm.price(); txs=wm.getTransactions() }
                                        // lặp 60s
                                        while(true){
                                            delay(60_000)
                                            withContext(Dispatchers.IO){
                                                try{
                                                    val b=wm.getBalance(); val pr=wm.price(); val ts=wm.getTransactions()
                                                    withContext(Dispatchers.Main){ balance=b; price=pr; txs=ts; status="Auto sync ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}" }
                                                }catch(_:Exception){}
                                            }
                                        }
                                    }

                                    Column(Modifier.padding(16.dp)){
                                        Card(Modifier.fillMaxWidth()){ Column(Modifier.padding(16.dp)){ Text("Số dư:"); Text("%.8f BTC".format(balance), fontSize=28.sp, fontWeight=FontWeight.Bold); Text("≈ $%.2f".format(balance*price)); Text(status, fontSize=12.sp); if(progress in 1..99) LinearProgressIndicator(progress/100f, Modifier.fillMaxWidth().padding(top=8.dp)) } }
                                        Spacer(Modifier.height(8.dp))
                                        Button({ lifecycleScope.launch(Dispatchers.IO){ val b=wm.getBalance(); val pr=wm.price(); val ts=wm.getTransactions(); withContext(Dispatchers.Main){ balance=b; price=pr; txs=ts; status="Sync tay" } } }, Modifier.fillMaxWidth()){ Text("SYNC NGAY") }
                                        Spacer(Modifier.height(16.dp)); Text("Lịch sử", fontWeight=FontWeight.Bold)
                                        LazyColumn(Modifier.fillMaxSize()){ items(txs){ tx -> Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){ Row(Modifier.padding(12.dp)){ Column(Modifier.weight(1f)){ Text(tx.type, fontWeight=FontWeight.Bold); Text("%.8f".format(tx.amount)); Text(fmt.format(tx.time), fontSize=11.sp) }; Text(tx.txId.take(8), fontSize=12.sp) } } } }
                                    }
                                } else {
                                    // tab Gửi/Nhận giữ nguyên như bản trước
                                    var to by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var result by remember{mutableStateOf("")}; var address by remember{mutableStateOf("")}; var fees by remember{mutableStateOf(FeeRates(5,10,20))}; var feeSel by remember{mutableStateOf(1)}
                                    LaunchedEffect(Unit){ withContext(Dispatchers.IO){ address=wm.getAddress(); fees=wm.getFeeRates() } }
                                    val feeRate = when(feeSel){0->fees.slow;1->fees.normal;else->fees.fast}
                                    androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(16.dp)){ item{
                                        Text("Gửi BTC", fontWeight=FontWeight.Bold)
                                        OutlinedTextField(to,{to=it}, label={Text("Địa chỉ")}, modifier=Modifier.fillMaxWidth(), trailingIcon={ TextButton({ qrCallback={to=it}; qrLauncher.launch(com.journeyapps.barcodescanner.ScanOptions()) }){Text("QR")} })
                                        OutlinedTextField(amount,{amount=it}, label={Text("BTC")}, modifier=Modifier.fillMaxWidth())
                                        Row(verticalAlignment=Alignment.CenterVertically){ RadioButton(feeSel==0,{feeSel=0}); Text("Chậm"); Spacer(Modifier.width(8.dp)); RadioButton(feeSel==1,{feeSel=1}); Text("Thường"); Spacer(Modifier.width(8.dp)); RadioButton(feeSel==2,{feeSel=2}); Text("Nhanh") }
                                        Button({ lifecycleScope.launch(Dispatchers.IO){ result = wm.send(to, amount.toDoubleOrNull()?:0.0, feeRate) } }, Modifier.fillMaxWidth()){ Text("GỬI") }
                                        if(result.isNotEmpty()) Text(result, fontSize=12.sp)
                                        Spacer(Modifier.height(24.dp)); Text("Nhận BTC", fontWeight=FontWeight.Bold)
                                        val qr = remember(address){ val s=512; val b=com.google.zxing.qrcode.QRCodeWriter().encode(address.ifEmpty{"bitcoin:"}, com.google.zxing.BarcodeFormat.QR_CODE, s,s); android.graphics.Bitmap.createBitmap(s,s,android.graphics.Bitmap.Config.RGB_565).apply{ for(x in 0 until s) for(y in 0 until s) setPixel(x,y, if(b.get(x,y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
                                        Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally){ Image(qr.asImageBitmap(),null,Modifier.size(220.dp)); Spacer(Modifier.height(8.dp)); androidx.compose.foundation.text.selection.SelectionContainer{ Text(address) } }
                                        Button({ val cm=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cm.setPrimaryClip(android.content.ClipData.newPlainText("btc",address)); android.widget.Toast.makeText(ctx,"Đã copy",android.widget.Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()){ Text("COPY") }
                                    } }
                                }
                            }
                        }
                        if(showRename){ var newName by remember{mutableStateOf(walletName)}; AlertDialog(onDismissRequest={showRename=false}, confirmButton={ TextButton({ lifecycleScope.launch(Dispatchers.IO){ wm.getActive()?.let{ val seed=it.seed; wm.delete(it.id); wm.import(newName,seed); wm.init(); withContext(Dispatchers.Main){ walletName=newName; showRename=false } } } }){Text("Lưu")} }, title={Text("Đổi tên ví")}, text={ OutlinedTextField(newName,{newName=it}, singleLine=true) }) }
                        if(showDetails){ val seed=wm.getSeed(); val addr=wm.getAddress(); AlertDialog(onDismissRequest={showDetails=false}, confirmButton={ TextButton({showDetails=false}){Text("Đóng")} }, title={Text("Chi tiết ví")}, text={ Column{ Text("Tên: $walletName", fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Địa chỉ:"); androidx.compose.foundation.text.selection.SelectionContainer{ Text(addr) }; TextButton({ val cm=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cm.setPrimaryClip(android.content.ClipData.newPlainText("addr",addr)); android.widget.Toast.makeText(ctx,"Đã copy địa chỉ",android.widget.Toast.LENGTH_SHORT).show() }){Text("Copy địa chỉ")}; Spacer(Modifier.height(8.dp)); Text("Seed 12 từ:"); androidx.compose.foundation.text.selection.SelectionContainer{ Text(seed) }; TextButton({ val cm=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cm.setPrimaryClip(android.content.ClipData.newPlainText("seed",seed)); android.widget.Toast.makeText(ctx,"Đã copy seed",android.widget.Toast.LENGTH_SHORT).show() }){Text("Copy seed")} } }) }
                    }
                }
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); try{ wm.stop() }catch(_:Exception){} }
}