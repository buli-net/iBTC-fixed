package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_lock)
        val prefs = getSharedPreferences("ibtc_prefs", MODE_PRIVATE)
        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            val pass = findViewById<EditText>(R.id.etPass).text.toString()
            if (pass == prefs.getString("password", "")) {
                prefs.edit().putBoolean("locked", false).apply()
                finish()
            } else Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
        }
    }
}