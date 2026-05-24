package net.buli.ibtc
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_settings)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.itemChangePass).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java)) }
        findViewById<LinearLayout>(R.id.itemDelete).setOnClickListener {
            val v = layoutInflater.inflate(R.layout.dialog_delete, null)
            val et = v.findViewById<EditText>(R.id.etPassDel)
            AlertDialog.Builder(this).setTitle("Xóa ví").setView(v)
                .setPositiveButton("Xóa") { _, _ ->
                    val prefs = getSharedPreferences("ibtc_prefs", 0)
                    if (et.text.toString() == prefs.getString("password", "")) {
                        prefs.edit().clear().apply()
                        startActivity(Intent(this, WelcomeActivity::class.java))
                        finishAffinity()
                    } else Toast.makeText(this, "Sai mật khẩu", 0).show()
                }.setNegativeButton("Hủy", null).show()
        }
    }
}