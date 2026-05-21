package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (WalletManager.hasWallet(this)) {
            startActivity(Intent(this, WalletActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btnCreate).setOnClickListener {
            val words = WalletManager.createWallet(this)
            Toast.makeText(this, "GHI 12 TỪ: ${words.joinToString(" ")}", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, WalletActivity::class.java)); finish()
        }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            val w = findViewById<EditText>(R.id.edtMnemonic).text.toString()
            if (WalletManager.importWallet(this,w)) {
                startActivity(Intent(this, WalletActivity::class.java)); finish()
            } else Toast.makeText(this,"Sai 12 từ",0).show()
        }
    }
}