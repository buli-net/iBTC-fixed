package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val etSeed = findViewById<EditText>(R.id.etSeed)
        findViewById<TextView>(R.id.btnPaste).setOnClickListener {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            val text = clip?.getItemAt(0)?.text?.toString() ?: ""
            etSeed.setText(text)
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener {
            val seed = etSeed.text.toString().trim().lowercase()
                .replace("\n"," ").replace("\\s+".toRegex()," ")
            val p1 = findViewById<EditText>(R.id.etPass1).text.toString()
            val p2 = findViewById<EditText>(R.id.etPass2).text.toString()

            if (seed.split(" ").size != 12) {
                Toast.makeText(this,"Cụm từ phải đủ 12 từ", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (p1.length < 6 || p1 != p2) {
                Toast.makeText(this,"Mật khẩu ≥6 và phải khớp", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            getSharedPreferences("ibtc_prefs", MODE_PRIVATE).edit()
                .putString("seed", seed).putString("password", p1)
                .putBoolean("has_wallet", true).apply()
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        }
    }
}