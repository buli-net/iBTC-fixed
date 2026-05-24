package net.buli.ibtc
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity:BaseNavActivity(){
    private val sec by lazy{SecurePrefs(this)}
    private val client=OkHttpClient.Builder().callTimeout(10,TimeUnit.SECONDS).build()
    private var sat=0L
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);setupNav(R.id.navWallet)
        findViewById<android.view.View>(R.id.btnSend).setOnClickListener{startActivity(Intent(this,SendActivity::class.java))}
        findViewById<android.view.View>(R.id.btnReceive).setOnClickListener{startActivity(Intent(this,ReceiveActivity::class.java))}
        findViewById<TextView>(R.id.btnRefresh).setOnClickListener{load(true)}
        sat=sec.getSat();load(true);lifecycleScope.launch{while(isActive){delay(60000);load(false)}}}
    private fun load(toast:Boolean)=lifecycleScope.launch(Dispatchers.IO){try{
        val addr=getSharedPreferences("ibtc_prefs",0).getString("btc_address","")?:""
        if(addr.isNotEmpty()){val r=Request.Builder().url("https://mempool.space/api/address/$addr").build()
            client.newCall(r).execute().use{val j=JSONObject(it.body!!.string()).getJSONObject("chain_stats");sat=j.getLong("funded_txo_sum")-j.getLong("spent_txo_sum");sec.saveSat(sat)}}
        val pr=Request.Builder().url("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true").build()
        val pj=client.newCall(pr).execute().use{JSONObject(it.body!!.string()).getJSONObject("bitcoin")};val price=pj.getDouble("usd");val ch=pj.getDouble("usd_24h_change")
        withContext(Dispatchers.Main){upd(price,ch);if(toast)Toast.makeText(this@MainActivity,"Đã cập nhật",0).show()}
    }catch(_:Exception){}}
    private fun upd(p:Double,c:Double){val btc=BigDecimal(sat).divide(BigDecimal(100000000));val usd=btc.multiply(BigDecimal(p)).setScale(2,RoundingMode.HALF_UP)
        findViewById<TextView>(R.id.tvUsdBalance).text="$${String.format(Locale.US,"%,.2f",usd)}"
        findViewById<TextView>(R.id.tvBtcBalance).text="${btc.setScale(8,RoundingMode.DOWN)} BTC"
        findViewById<TextView>(R.id.tvBtcAmount).text=btc.setScale(8,RoundingMode.DOWN).toPlainString()
        findViewById<TextView>(R.id.tvBtcUsd).text="$${String.format(Locale.US,"%,.2f",usd)}"
        findViewById<TextView>(R.id.tvBtcPrice).text="$${String.format(Locale.US,"%,.2f",p)}"
        val s=if(c>=0)"+" else "";findViewById<TextView>(R.id.tvBtcChange).apply{text="$s${String.format(Locale.US,"%.2f",c)}%";setTextColor(if(c>=0)0xFF00C076.toInt() else 0xFFFF4444.toInt())}}}