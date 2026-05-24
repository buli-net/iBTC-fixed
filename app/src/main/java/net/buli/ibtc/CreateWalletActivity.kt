package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CreateWalletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_wallet)
        
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val btnCreate = findViewById<Button>(R.id.btnCreate)
        
        btnCreate?.setOnClickListener {
            val p1 = etPassword.text.toString()
            val p2 = etConfirm.text.toString()
            if (p1.isNotEmpty() && p1 == p2) {
                Toast.makeText(this, "Wallet created", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
