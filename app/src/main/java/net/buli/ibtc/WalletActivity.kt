package net.buli.ibtc

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class WalletActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_wallet)
        val tvAddr = findViewById<TextView>(R.id.tvAddress)
        val tvBal = findViewById<TextView>(R.id.tvBalance)
        val addr = WalletManager.getAddress(this)
        tvAddr.text = addr
        tvAddr.setOnClickListener { tvAddr.text = addr } // copy tay
        fetchBalance(addr) { tvBal.text = "Số dư: $it BTC (mainnet)" }
    }
    private fun fetchBalance(a:String, cb:(String)->Unit){
        val req = Request.Builder().url("https://blockchain.info/q/addressbalance/$a").build()
        client.newCall(req).enqueue(object:Callback{
            override fun onFailure(c:Call,e:IOException){ runOnUiThread{cb("lỗi mạng")} }
            override fun onResponse(c:Call,r:Response){
                val sat = r.body?.string()?.toLongOrNull() ?:0L
                val btc = sat/100_000_000.0
                runOnUiThread{cb("%.8f".format(btc))}
            }
        })
    }
}