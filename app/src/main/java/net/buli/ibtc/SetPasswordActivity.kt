package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_password)

        val prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
        val seed = prefs.getString("temp_seed", "")!!

        findViewById<Button>(R.id.btnSetPass).setOnClickListener {
            val p1 = findViewById<EditText>(R.id.etPass1).text.toString()
            val p2 = findViewById<EditText>(R.id.etPass2).text.toString()
            
            if (p1.length < 6) {
                Toast.makeText(this, "Mật khẩu tối thiểu 6 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (p1 != p2) {
                Toast.makeText(this, "Mật khẩu không khớp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            prefs.edit()
                .putString("seed", seed)
                .putString("password", p1)
                .putBoolean("has_wallet", true)
                .remove("temp_seed")
                .apply()
            
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        }
    }
}