package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : BaseNavActivity() {
    private val prefs by lazy { getSharedPreferences("ibtc_prefs", 0) }
    private var btcBalance = 0.0459968
    private var hideBalance = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupNav(R.id.navWallet)

        findViewById<android.view.View>(R.id.btnSend).setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnReceive).setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnEye).setOnClickListener {
            hideBalance = !hideBalance
            updateUI(0.0, 0.0)
        }

        loadPrice()
    }

    private fun loadPrice() {
        thread {
            try {
                val json = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true").readText()
                val obj = JSONObject(json).getJSONObject("bitcoin")
                val price = obj.getDouble("usd")
                val change = obj.getDouble("usd_24h_change")
                runOnUiThread { updateUI(price, change) }
            } catch (_: Exception) {}
        }
    }

    private fun updateUI(price: Double, change24: Double) {
        val tvUsd = findViewById<TextView>(R.id.tvUsdBalance)
        val tvBtc = findViewById<TextView>(R.id.tvBtcBalance)
        val tvToday = findViewById<TextView>(R.id.tvTodayChange)
        val tvBtcPrice = findViewById<TextView>(R.id.tvBtcPrice)
        val tvBtcChange = findViewById<TextView>(R.id.tvBtcChange)
        val tvBtcAmt = findViewById<TextView>(R.id.tvBtcAmount)
        val tvBtcUsd = findViewById<TextView>(R.id.tvBtcUsd)

        val usdValue = btcBalance * price
        val todayUsd = usdValue * change24 / 100.0

        if (hideBalance) {
            tvUsd.text = "****"
            tvBtc.text = "**** BTC"
            tvBtcAmt.text = "****"
            tvBtcUsd.text = "****"
        } else {
            tvUsd.text = "$${String.format("%,.2f", usdValue)}"
            tvBtc.text = "$btcBalance BTC"
            tvBtcAmt.text = btcBalance.toString()
            tvBtcUsd.text = "$${String.format("%,.2f", usdValue)}"
        }

        val arrow = if (change24 >= 0) "▲" else "▼"
        val sign = if (todayUsd >= 0) "+" else ""
        tvToday.text = "Hôm nay $sign$${String.format("%.2f", todayUsd)} $arrow${String.format("%.2f", kotlin.math.abs(change24))}%"

        val green = 0xFF00C076.toInt()
        val red = 0xFFFF4444.toInt()
        tvToday.setTextColor(if (change24 >= 0) green else red)

        tvBtcPrice.text = "$${String.format("%,.2f", price)}"
        val changeSign = if (change24 >= 0) "+" else ""
        tvBtcChange.text = " $changeSign${String.format("%.2f", change24)}%"
        tvBtcChange.setTextColor(if (change24 >= 0) green else red)
    }
}