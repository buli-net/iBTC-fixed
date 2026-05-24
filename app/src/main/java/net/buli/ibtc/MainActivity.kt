package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script.ScriptType
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet

class MainActivity : BaseNavActivity() {
    private val prefs by lazy { getSharedPreferences("ibtc_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val seedPhrase = prefs.getString("seed", "")!!.trim()
        val words = seedPhrase.split(" ").filter { it.isNotBlank() }
        val seed = DeterministicSeed(words, null, "", 0L)
        val wallet = Wallet.fromSeed(MainNetParams.get(), seed, ScriptType.P2PKH)
        val address = wallet.currentReceiveAddress().toString()

        findViewById<TextView>(R.id.tvAddress).text = address
        findViewById<TextView>(R.id.tvUsd).text = "$0.00"
        findViewById<TextView>(R.id.tvBalance).text = "≈ 0 BTC"
        findViewById<TextView>(R.id.tvBtcAmount)?.text = "0 BTC"

        findViewById<TextView>(R.id.tvAddress).setOnClickListener {
            getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText("addr", address))
            Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btnSend).setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnReceive).setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }

        setupNav(R.id.navWallet)
    }
    // KHÔNG còn onPause/onResume ở đây nữa – BaseNavActivity lo hết
}