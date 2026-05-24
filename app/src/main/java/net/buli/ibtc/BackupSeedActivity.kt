package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.wallet.DeterministicSeed
import java.security.SecureRandom

class BackupSeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_seed)

        // === TẠO 12 TỪ NGẪU NHIÊN THẬT ===
        val seed = DeterministicSeed(SecureRandom(), 128, "", System.currentTimeMillis() / 1000)
        val mnemonic = seed.mnemonicCode.joinToString(" ")

        // Lưu tạm để verify
        getSharedPreferences("ibtc_prefs", MODE_PRIVATE).edit()
            .putString("temp_seed", mnemonic).apply()

        // Hiển thị 1. word  2. word ... giống SafePal
        val formatted = mnemonic.split(" ")
            .mapIndexed { i, w -> "${i + 1}. $w" }
            .joinToString("\n")
        findViewById<TextView>(R.id.tvSeed).text = formatted

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startActivity(Intent(this, VerifySeedActivity::class.java))
        }
    }
}