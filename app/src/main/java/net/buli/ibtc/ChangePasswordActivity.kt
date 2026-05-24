package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_change_password)
        val prefs = getSharedPreferences("ibtc_prefs", 0)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val old = findViewById<EditText>(R.id.etOld).text.toString()
            val n1 = findViewById<EditText>(R.id.etNew1).text.toString()
            val n2 = findViewById<EditText>(R.id.etNew2).text.toString()
            if (old != prefs.getString("password", "")) {
                Toast.makeText(this, "Sai mật khẩu cũ", 0).show(); return@setOnClickListener }
            if (n1.length < 6 || n1 != n2) {
                Toast.makeText(this, "Mật khẩu mới không hợp lệ", 0).show(); return@setOnClickListener }
            prefs.edit().putString("password", n1).apply()
            Toast.makeText(this, "Đã đổi", 0).show(); finish()
        }
    }
}