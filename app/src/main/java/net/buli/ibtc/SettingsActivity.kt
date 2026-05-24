package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
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
            val old = etOldPwd.text.toString()
            val newP = etNewPwd.text.toString()
            val conf = etConfirmPwd.text.toString()
            if (newP.isNotEmpty() && newP == conf) {
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
