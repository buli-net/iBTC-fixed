package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BackupSeedActivity : AppCompatActivity() {
    // 12 từ test - sau này thay bằng bitcoinj thật
    private val testSeed = "abandon ability able about above absent absorb abstract absurd abuse access accident"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_seed)

        findViewById<TextView>(R.id.tvSeed).text = testSeed.replace(" ", "\n")
        
        getSharedPreferences("ibtc_prefs", MODE_PRIVATE).edit()
            .putString("temp_seed", testSeed).apply()

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startActivity(Intent(this, VerifySeedActivity::class.java))
        }
    }
}