package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VerifySeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_seed)

        val prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
        val original = prefs.getString("temp_seed", "")!!

        findViewById<Button>(R.id.btnVerify).setOnClickListener {
            val input = findViewById<EditText>(R.id.etVerify).text.toString().trim()
            if (input == original) {
                startActivity(Intent(this, SetPasswordActivity::class.java))
            } else {
                Toast.makeText(this, "Sai thứ tự, nhập lại", Toast.LENGTH_SHORT).show()
            }
        }
    }
}