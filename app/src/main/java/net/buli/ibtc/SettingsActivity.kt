package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("ibtc", MODE_PRIVATE)
        
        findViewById<View>(R.id.btnChangePwd)?.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        findViewById<View>(R.id.btnShowSeed)?.setOnClickListener {
            val seed = prefs.getString("seed", "Chưa có ví")
            Toast.makeText(this, seed, Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.btnBackup)?.setOnClickListener {
            val seed = prefs.getString("seed", "")
            Toast.makeText(this, "Sao lưu: $seed", Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.btnAbout)?.setOnClickListener {
            Toast.makeText(this, "iBTC-fixed v1.0 by buli-net", Toast.LENGTH_SHORT).show()
        }
    }
}