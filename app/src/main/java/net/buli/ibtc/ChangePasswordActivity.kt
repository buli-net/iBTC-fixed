package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {
    private val activity_change_password = R.layout.activity_change_password
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activity_change_password)
        
        val etCurrent = findViewById<EditText>(R.id.etCurrent)
        val etNew = findViewById<EditText>(R.id.etNew)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val etChange = findViewById<Button>(R.id.btnChange) // maps to expected name
        
        etChange?.setOnClickListener {
            // simple placeholder
            finish()
        }
    }
}
