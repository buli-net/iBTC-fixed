package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class CreateWalletActivity : AppCompatActivity() {
    private val activity_create_wallet = R.layout.activity_create_wallet
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activity_create_wallet)
        
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val btnCreate = findViewById<Button>(R.id.btnCreate)
        
        btnCreate?.setOnClickListener {
            finish()
        }
    }
}
