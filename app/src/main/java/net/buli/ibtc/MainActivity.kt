package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : BaseNavActivity() {
    private val prefs by lazy { getSharedPreferences("ibtc_prefs", 0) }
    private var btcBalance = 0.0
    private var hideBalance = false
    private val handler = Handler(Looper.getMainLooper())
    private val autoRefresh = object : Runnable {
        override fun run() { loadRealData(false); handler.postDelayed(this, 30000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupNav(R.id.navWallet)
        findViewById<android.view.View>(R.id.btnSend).setOnClickListener { startActivity(Intent(this, SendActivity::class.java)) }
        findViewById<android.view.View>(R.id.btnReceive).setOnClickListener { startActivity(Intent(this, ReceiveActivity::class.java)) }
        findViewById<android.view.View>(R.id.btnEye).setOnClickListener { hideBalance = !hideBalance; updateUI(0.0,0.0) }
        findViewById<TextView>(R.id.btnRefresh).setOnClickListener { Toast.makeText(this,"Đang cập nhật...",Toast.LENGTH_SHORT).show(); loadRealData(true) }
        loadRealData(true)
    }
    override fun onResume() { super.onResume(); handler.postDelayed(autoRefresh,30000) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(autoRefresh) }

    private fun loadRealData(showToast:Boolean=false){
        thread {
            try {
                val address = prefs.getString("btc_address","")?:""
                if(address.isNotEmpty()){
                    val json = URL("https://mempool.space/api/address/$address").readText()
                    val obj = JSONObject(json).getJSONObject("chain_stats")
                    btcBalance = (obj.getLong("funded_txo_sum")-obj.getLong("spent_txo_sum"))/100_000_000.0
                    prefs.edit().putString("last_balance", btcBalance.toString()).apply()
                } else btcBalance = prefs.getString("last_balance","0.0")!!.toDouble()
                
                val priceJson = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true").readText()
                val p = JSONObject(priceJson).getJSONObject("bitcoin")
                val price = p.getDouble("usd"); val change = p.getDouble("usd_24h_change")
                runOnUiThread { updateUI(price,change); if(showToast) Toast.makeText(this,"Đã cập nhật",Toast.LENGTH_SHORT).show() }
            }catch(_:Exception){}
        }
    }
    
    private fun updateUI(price:Double,change24:Double){
        val tvUsd=findViewById<TextView>(R.id.tvUsdBalance)
        val tvBtc=findViewById<TextView>(R.id.tvBtcBalance)
        val tvToday=findViewById<TextView>(R.id.tvTodayChange)
        val tvAmt=findViewById<TextView>(R.id.tvBtcAmount)
        val tvUsd2=findViewById<TextView>(R.id.tvBtcUsd)
        val tvPrice=findViewById<TextView>(R.id.tvBtcPrice)
        val tvChange=findViewById<TextView>(R.id.tvBtcChange)
        val vn=Locale("vi","VN")
        val usdVal=btcBalance*price
        
        if(hideBalance){ 
            tvUsd.text="****"; tvBtc.text="**** BTC"; tvAmt.text="****"; tvUsd2.text="****" 
        } else { 
            tvUsd.text="$${String.format(vn,"%,.2f",usdVal)}"
            tvBtc.text="${String.format(Locale.US,"%.7f",btcBalance)} BTC"
            tvAmt.text=String.format(Locale.US,"%.7f",btcBalance)
            tvUsd2.text="$${String.format(vn,"%,.2f",usdVal)}" 
        }
        
        // GIÁ VÀ % REAL
        tvPrice.text = "$${String.format(vn,"%,.2f",price)}"
        val sign = if(change24>=0) "+" else ""
        tvChange.text = "$sign${String.format(vn,"%.2f",change24)}%"
        val green=0xFF00C076.toInt(); val red=0xFFFF4444.toInt()
        tvChange.setTextColor(if(change24>=0)green else red)
        
        val arrow=if(change24>=0)"▲" else "▼"
        tvToday.text="Hôm nay $sign$${String.format(vn,"%.2f",kotlin.math.abs(usdVal*change24/100))} $arrow${String.format(vn,"%.2f",kotlin.math.abs(change24))}%"
        tvToday.setTextColor(if(change24>=0)green else red)
    }
}