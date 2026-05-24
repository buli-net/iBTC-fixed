package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("has_wallet", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        findViewById<Button>(R.id.btnCreate).setOnClickListener {
            startActivity(Intent(this, BackupSeedActivity::class.java))
        }

        // ĐÃ SỬA: mở màn hình Import full SafePal
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
    }
}