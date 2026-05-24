package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ImportWalletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_wallet)
        
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etSeed = findViewById<EditText>(R.id.etSeed)
        val btnImport = findViewById<Button>(R.id.btnImport)
        
        btnImport?.setOnClickListener {
            val seed = etSeed.text.toString().trim()
            val pwd = etPassword.text.toString()
            if (seed.isNotEmpty() && pwd.isNotEmpty()) {
                Toast.makeText(this, "Wallet imported", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Enter seed and password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
