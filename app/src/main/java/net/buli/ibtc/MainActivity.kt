package net.buli.ibtc

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.script.Script.ScriptType

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
        val seedPhrase = prefs.getString("seed", "")!!.trim()

        // === FIX: dùng constructor List<String> ===
        val words = seedPhrase.split(" ").filter { it.isNotBlank() }
        val params: NetworkParameters = MainNetParams.get()
        val seed = DeterministicSeed(words, null, "", 0L)
        val wallet = Wallet.fromSeed(params, seed, ScriptType.P2PKH)
        val address = wallet.currentReceiveAddress().toString()

        // Cập nhật UI SafePal
        findViewById<TextView>(R.id.tvAddress).text = address
        findViewById<TextView>(R.id.tvUsd).text = "$0.00"
        findViewById<TextView>(R.id.tvBalance).text = "≈ 0 BTC"
        findViewById<TextView>(R.id.tvBtcAmount)?.text = "0 BTC"

        // Copy địa chỉ
        findViewById<TextView>(R.id.tvAddress).setOnClickListener {
            getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText("addr", address))
            Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
        }

        // Lấy giá BTC (không bắt buộc)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    java.net.URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
                        .readText()
                }
            } catch (_: Exception) {}
        }
    }
}