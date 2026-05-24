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
            val seedPhrase = etSeed.text.toString().trim()
            val pwd = etPassword.text.toString()

            if (pwd.length < 6) {
                Toast.makeText(this, "Mật khẩu >= 6 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val words = seedPhrase.split(" ")
                MnemonicCode.INSTANCE.check(words) // validate 12/24 từ

                val seed = DeterministicSeed(words, null, "", System.currentTimeMillis() / 1000)
                
                // LƯU MÃ HÓA
                sec.saveSeed(seedPhrase)
                sec.savePwd(pwd)

                val wallet = org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(), seed)
                val address = wallet.currentReceiveAddress().toString()

                getSharedPreferences("ibtc_prefs", 0).edit()
                    .putString("btc_address", address)
                    .putBoolean("has_wallet", true)
                    .apply()

                Toast.makeText(this, "Nhập ví thành công", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Seed không hợp lệ", Toast.LENGTH_LONG).show()
            }
        }
    }
}