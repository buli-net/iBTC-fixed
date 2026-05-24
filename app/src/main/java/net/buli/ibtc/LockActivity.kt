package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        
        btnUnlock?.setOnClickListener {
            val pwd = etPassword.text.toString()
            if (pwd.isNotEmpty()) {
                Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
