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

        val p1 = findViewById<EditText>(R.id.etPass1)
        val p2 = findViewById<EditText>(R.id.etPass2)

        findViewById<Button>(R.id.btnSetPass).setOnClickListener {
            val pass = p1.text.toString()
            if (pass.length < 8) { Toast.makeText(this, "Mật khẩu ≥8 ký tự", 0).show(); return@setOnClickListener }
            if (pass != p2.text.toString()) { Toast.makeText(this, "Không khớp", 0).show(); return@setOnClickListener }

            val prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
            val seed = prefs.getString("temp_seed", "")
            prefs.edit().putString("seed", seed).putString("password", pass)
                .putBoolean("has_wallet", true).remove("temp_seed").apply()

            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        }
    }
}