package net.buli.ibtc
import android.os.Bundle
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.script.Script.ScriptType

class ReceiveActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_receive)
        val prefs = getSharedPreferences("ibtc_prefs",0)
        val words = prefs.getString("seed","")!!.split(" ")
        val wallet = Wallet.fromSeed(MainNetParams.get(), DeterministicSeed(words,null,"",0L), ScriptType.P2PKH)
        val addr = wallet.currentReceiveAddress().toString()
        findViewById<TextView>(R.id.tvAddr).text = addr
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText("a",addr))
            Toast.makeText(this,"Đã copy",0).show()
        }
        val wv = findViewById<WebView>(R.id.wvQr)
        wv.loadUrl("https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=$addr")
    }
}