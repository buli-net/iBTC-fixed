package net.buli.ibtc
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : BaseNavActivity() {
    private val prefs by lazy { getSharedPreferences("ibtc_prefs",0) }
    private var btcBalance=0.0459968
    private var hide=false
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);setupNav(R.id.navWallet)
        findViewById<android.view.View>(R.id.btnSend).setOnClickListener{startActivity(Intent(this,SendActivity::class.java))}
        findViewById<android.view.View>(R.id.btnReceive).setOnClickListener{startActivity(Intent(this,ReceiveActivity::class.java))}
        findViewById<android.view.View>(R.id.btnEye).setOnClickListener{hide=!hide;update(0.0,0.0)}
        load()
    }
    private fun load(){thread{try{val j=URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true").readText();val o=JSONObject(j).getJSONObject("bitcoin");val p=o.getDouble("usd");val c=o.getDouble("usd_24h_change");runOnUiThread{update(p,c)}}catch(_:Exception){}}}
    private fun update(price:Double,change:Double){
        val usd=btcBalance*price;val today=usd*change/100
        val tvUsd=findViewById<TextView>(R.id.tvUsdBalance);val tvBtc=findViewById<TextView>(R.id.tvBtcBalance);val tvT=findViewById<TextView>(R.id.tvTodayChange)
        val tvP=findViewById<TextView>(R.id.tvBtcPrice);val tvC=findViewById<TextView>(R.id.tvBtcChange);val tvA=findViewById<TextView>(R.id.tvBtcAmount);val tvU=findViewById<TextView>(R.id.tvBtcUsd)
        if(hide){tvUsd.text="****";tvBtc.text="**** BTC";tvA.text="****";tvU.text="****"}else{tvUsd.text="$${String.format("%,.2f",usd)}";tvBtc.text="$btcBalance BTC";tvA.text=btcBalance.toString();tvU.text="$${String.format("%,.2f",usd)}"}
        tvT.text="Hôm nay ${if(today>=0)"+" else ""}$${String.format("%.2f",today)} ${if(change>=0)"▲" else "▼"}${String.format("%.2f",kotlin.math.abs(change))}%";tvT.setTextColor(if(change>=0)0xFF00C076.toInt()else0xFFFF4444.toInt())
        tvP.text="$${String.format("%,.2f",price)}";tvC.text="${if(change>=0)"+" else ""}${String.format("%.2f",change)}%";tvC.setTextColor(if(change>=0)0xFF00C076.toInt()else0xFFFF4444.toInt())
    }
}