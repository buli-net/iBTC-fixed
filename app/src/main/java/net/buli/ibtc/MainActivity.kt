package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = SecureStorage.prefs(this)
        val address = prefs.getString("address", "")

        findViewById<TextView>(R.id.tvAddress).text = address

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }

        findViewById<Button>(R.id.btnReceive).setOnClickListener {
            Toast.makeText(this, "Chức năng Nhận đang phát triển", Toast.LENGTH_SHORT).show()
        }

        loadBalance(address)
    }

    private fun loadBalance(address: String?) {
        scope.launch {
            val balance = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://blockstream.info/api/address/$address")
                        .build()
                    val response = client.newCall(request).execute().body?.string() ?: ""
                    val funded = Regex("\"funded_txo_sum\":(\\d+)").find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    val spent = Regex("\"spent_txo_sum\":(\\d+)").find(response)?.groupValues?.get(1)?.toLong() ?: 0L
                    (funded - spent) / 1e8
                } catch (e: Exception) {
                    0.0
                }
            }
            findViewById<TextView>(R.id.tvBalance).text = String.format("%.8f BTC", balance)
        }
    }
}