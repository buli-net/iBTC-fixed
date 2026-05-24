package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ImportWalletActivity : AppCompatActivity() {
    private val activity_import_wallet = R.layout.activity_import_wallet
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activity_import_wallet)
        
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etSeed = findViewById<EditText>(R.id.etSeed)
        val btnImport = findViewById<Button>(R.id.btnImport)
        
        btnImport?.setOnClickListener {
            finish()
        }
    }
}
