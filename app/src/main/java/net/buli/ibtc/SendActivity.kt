package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*
import okhttp3.*
import org.bitcoinj.core.*
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.json.JSONArray
import org.json.JSONObject

class SendActivity:BaseActivity(){private val sec by lazy{SecurePrefs(this)};private val client=OkHttpClient()
override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_send)
    val etA=findViewById<EditText>(R.id.etAddress);val etB=findViewById<EditText>(R.id.etAmount)
    findViewById<Button>(R.id.btnSend).setOnClickListener{
        val addr=etA.text.toString();val amt=etB.text.toString().toDoubleOrNull()?:return@setOnClickListener
        CoroutineScope(Dispatchers.IO).launch{try{
            val params=MainNetParams.get();val seed=DeterministicSeed(sec.getSeed().split(" "),null,"",0)
            val wallet=org.bitcoinj.wallet.Wallet.fromSeed(params,seed)
            val from=getSharedPreferences("ibtc_prefs",0).getString("btc_address","")!!
            val utxos=JSONArray(client.newCall(Request.Builder().url("https://mempool.space/api/address/$from/utxo").build()).execute().body!!.string())
            for(i in 0 until utxos.length()){val u=utxos.getJSONObject(i);wallet.addUTXO(TransactionOutPoint(params,u.getLong("vout"),Sha256Hash.wrap(u.getString("txid"))),Coin.valueOf(u.getLong("value")))}
            val tx=wallet.createSend(Address.fromString(params,addr),Coin.parseCoin(amt.toString()));wallet.commitTx(tx)
            val hex=tx.tx.bitcoinSerialize().joinToString(""){"%02x".format(it)}
            client.newCall(Request.Builder().url("https://mempool.space/api/tx").post(RequestBody.create(MediaType.parse("text/plain"),hex)).build()).execute()
            withContext(Dispatchers.Main){Toast.makeText(this@SendActivity,"Đã gửi",0).show();finish()}
        }catch(e:Exception){withContext(Dispatchers.Main){Toast.makeText(this@SendActivity,"Lỗi: ${e.message}",1).show()}}}}}
}