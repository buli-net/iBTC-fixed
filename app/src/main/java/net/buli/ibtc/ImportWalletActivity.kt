package net.buli.ibtc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class ImportWalletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_wallet)

        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etSeed = findViewById<EditText>(R.id.etSeed)
        val btnImport = findViewById<Button>(R.id.btnImport)

        btnImport.setOnClickListener {
            val phrase = etSeed.text.toString().trim()
            val words = phrase.split("\\s+".toRegex())
            // import logic
        }
    }
}
