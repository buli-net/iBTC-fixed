package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        
        btnUnlock?.setOnClickListener {
            finish()
        }
    }
}
