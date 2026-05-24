package net.buli.ibtc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed

class CreateWalletActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_wallet)

        val sec = SecurePrefs(this)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnCreate = findViewById<Button>(R.id.btnCreate)

        btnCreate.setOnClickListener {
            val pwd = etPassword.text.toString()
            if (pwd.length < 6) {
                Toast.makeText(this, "MK >=6 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val mnemonic = MnemonicCode.INSTANCE.generateMnemonic()
                val seed = DeterministicSeed(mnemonic, null, "", System.currentTimeMillis() / 1000)
                
                sec.saveSeed(mnemonic.joinToString(" "))
                sec.savePwd(pwd)

                val wallet = org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(), seed)
                val address = wallet.currentReceiveAddress().toString()
                
                getSharedPreferences("ibtc_prefs", 0).edit()
                    .putString("btc_address", address).apply()

                Toast.makeText(this, "Tạo ví xong", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}