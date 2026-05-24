package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.bitcoinj.crypto.MnemonicCode
import java.security.SecureRandom

class BackupSeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_seed)

        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val seedWords = MnemonicCode.INSTANCE.toMnemonic(entropy)

        findViewById<TextView>(R.id.tvSeed).text = seedWords.joinToString("\n")
        getSharedPreferences("ibtc_prefs", MODE_PRIVATE).edit()
            .putString("temp_seed", seedWords.joinToString(" ")).apply()

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startActivity(Intent(this, VerifySeedActivity::class.java))
        }
    }
}