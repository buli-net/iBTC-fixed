package net.buli.ibtc
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        val prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("has_wallet", false)) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        findViewById<Button>(R.id.btnCreate).setOnClickListener {
            startActivity(Intent(this, BackupSeedActivity::class.java))
        }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            val v = layoutInflater.inflate(R.layout.dialog_import, null)
            val etSeed = v.findViewById<EditText>(R.id.etSeed)
            val p1 = v.findViewById<EditText>(R.id.etPass1)
            val p2 = v.findViewById<EditText>(R.id.etPass2)
            val dlg = AlertDialog.Builder(this).setTitle("Khôi phục ví").setView(v)
                .setPositiveButton("Khôi phục", null).setNegativeButton("Hủy", null).create()
            dlg.show()
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val seed = etSeed.text.toString().trim().lowercase()
                    .replace("\n"," ").replace("\\s+".toRegex()," ")
                val pass = p1.text.toString()
                if (seed.split(" ").size != 12) {
                    Toast.makeText(this,"Nhập đủ 12 từ",0).show(); return@setOnClickListener
                }
                if (pass.length<6 || pass!=p2.text.toString()){
                    Toast.makeText(this,"Mật khẩu ≥6 và khớp",0).show(); return@setOnClickListener
                }
                prefs.edit().putString("seed",seed).putString("password",pass)
                    .putBoolean("has_wallet",true).apply()
                dlg.dismiss(); startActivity(Intent(this,MainActivity::class.java)); finish()
            }
        }
    }
}