package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.crypto.MnemonicCode
import java.security.SecureRandom

class BackupSeedActivity : AppCompatActivity() {
    private lateinit var seedWords: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_seed)

        // Sinh 12 từ chuẩn BIP39
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        seedWords = MnemonicCode.INSTANCE.toMnemonic(entropy)

        findViewById<TextView>(R.id.tvSeed).text = seedWords.joinToString("  ")

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            // Lưu tạm seed để verify
            getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
                .edit().putString("temp_seed", seedWords.joinToString(" ")).apply()
            startActivity(Intent(this, VerifySeedActivity::class.java))
        }
    }
}