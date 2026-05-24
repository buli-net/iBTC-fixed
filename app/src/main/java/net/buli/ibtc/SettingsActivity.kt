package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val etOldPwd = findViewById<EditText>(R.id.etOldPwd)
        val etNewPwd = findViewById<EditText>(R.id.etNewPwd)
        val etConfirmPwd = findViewById<EditText>(R.id.etConfirmPwd)
        val btnSave = findViewById<Button>(R.id.btnSave)
        
        btnSave?.setOnClickListener {
            finish()
        }
    }
}
