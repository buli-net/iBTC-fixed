package net.buli.ibtc

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        
        val etCurrent = findViewById<EditText>(R.id.etCurrent)
        val etNew = findViewById<EditText>(R.id.etNew)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val btnChange = findViewById<Button>(R.id.btnChange)
        
        btnChange?.setOnClickListener {
            val cur = etCurrent.text.toString()
            val newP = etNew.text.toString()
            val conf = etConfirm.text.toString()
            if (newP.isNotEmpty() && newP == conf) {
                Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
