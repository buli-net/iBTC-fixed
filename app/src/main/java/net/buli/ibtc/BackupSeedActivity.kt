package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BackupSeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_seed)

        val testSeed = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        getSharedPreferences("ibtc_prefs", MODE_PRIVATE).edit()
            .putString("temp_seed", testSeed).apply()

        // Hiển thị đẹp 1. abandon  2. ability ...
        val formatted = testSeed.split(" ")
            .mapIndexed { i, w -> "${i + 1}. $w" }
            .joinToString("\n")
        findViewById<TextView>(R.id.tvSeed).text = formatted

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startActivity(Intent(this, VerifySeedActivity::class.java))
        }
    }
}