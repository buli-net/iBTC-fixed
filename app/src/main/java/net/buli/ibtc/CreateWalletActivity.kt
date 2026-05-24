package net.buli.ibtc
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import java.security.SecureRandom

class CreateWalletActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PHẢI CÓ R.layout.
        setContentView(R.layout.activity_create_wallet)
        
        val sec = SecurePrefs(this)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnCreate = findViewById<Button>(R.id.btnCreate)

        btnCreate.setOnClickListener {
            val pwd = etPassword.text.toString()
            if (pwd.length < 6) { Toast.makeText(this,"MK>=6",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            
            // FIX generateMnemonic + overload
            val mnemonic = MnemonicCode.INSTANCE.generateMnemonic(SecureRandom())
            val seed = DeterministicSeed(mnemonic, null, "", System.currentTimeMillis() / 1000L)
            
            sec.saveSeed(mnemonic.joinToString(" "))
            sec.savePwd(pwd)
            
            val wallet = org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(), seed)
            getSharedPreferences("ibtc_prefs",0).edit()
                .putString("btc_address", wallet.currentReceiveAddress().toString())
                .putBoolean("has_wallet", true).apply()
            
            Toast.makeText(this,"Tạo ví xong",Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}