package net.buli.ibtc
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed

class ImportWalletActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_wallet)
        val sec = SecurePrefs(this)
        val etSeed = findViewById<EditText>(R.id.etSeed)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnImport = findViewById<Button>(R.id.btnImport)
        btnImport.setOnClickListener {
            try {
                val words = etSeed.text.toString().trim().split("\s+".toRegex())
                MnemonicCode.INSTANCE.check(words)
                val seed = DeterministicSeed(words, null, "", System.currentTimeMillis()/1000L)
                sec.saveSeed(words.joinToString(" "))
                sec.savePwd(etPassword.text.toString())
                val wallet = org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(), seed)
                getSharedPreferences("ibtc_prefs",0).edit().putString("btc_address", wallet.currentReceiveAddress().toString()).putBoolean("has_wallet",true).apply()
                Toast.makeText(this,"Nhập xong",Toast.LENGTH_SHORT).show()
                finish()
            } catch (e:Exception) { Toast.makeText(this,"Seed sai",Toast.LENGTH_LONG).show() }
        }
    }
}
