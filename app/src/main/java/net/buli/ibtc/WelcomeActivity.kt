package net.buli.ibtc

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)

        // Nếu đã có ví → vào thẳng Main
        if (prefs.getBoolean("has_wallet", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // BƯỚC FIX: bấm Tạo ví → sang màn Backup Seed luôn
        findViewById<Button>(R.id.btnCreate).setOnClickListener {
            startActivity(Intent(this, BackupSeedActivity::class.java))
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener { 
            showImportDialog() 
        }
    }

    private fun showImportDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_import, null)
        val etSeed = view.findViewById<EditText>(R.id.etSeed)
        val p1 = view.findViewById<EditText>(R.id.etPass1)
        val p2 = view.findViewById<EditText>(R.id.etPass2)

        AlertDialog.Builder(this)
            .setTitle("Khôi phục ví")
            .setView(view)
            .setPositiveButton("Khôi phục") { _, _ ->
                val seed = etSeed.text.toString().trim()
                val pass = p1.text.toString()
                if (seed.split(" ").size != 12) {
                    Toast.makeText(this, "Cần đúng 12 từ", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass.length < 8 || pass != p2.text.toString()) {
                    Toast.makeText(this, "Mật khẩu ≥8 ký tự và phải khớp", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.edit()
                    .putString("seed", seed)
                    .putString("password", pass)
                    .putBoolean("has_wallet", true)
                    .apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}