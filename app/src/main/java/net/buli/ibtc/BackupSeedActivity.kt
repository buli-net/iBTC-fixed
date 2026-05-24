package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.wallet.DeterministicSeed
import java.security.SecureRandom

class BackupSeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_seed)

        // Tạo 12 từ BIP39 ngẫu nhiên thật
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val words = MnemonicCode.INSTANCE.toMnemonic(entropy)
        val mnemonic = words.joinToString(" ")

        // Lưu tạm để verify
        getSharedPreferences("ibtc_prefs", MODE_PRIVATE).edit()
            .putString("temp_seed", mnemonic).apply()

        // Hiển thị dạng 1. xxx  2. xxx
        val formatted = words.mapIndexed { index, word -> "${index + 1}. $word" }
            .joinToString("\n")
        findViewById<TextView>(R.id.tvSeed).text = formatted

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startActivity(Intent(this, VerifySeedActivity::class.java))
        }
    }
}