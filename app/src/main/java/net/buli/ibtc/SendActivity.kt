package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SendActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_send)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val to = findViewById<EditText>(R.id.etTo).text.toString()
            val amt = findViewById<EditText>(R.id.etAmount).text.toString()
            val pass = findViewById<EditText>(R.id.etPass).text.toString()
            val prefs = getSharedPreferences("ibtc_prefs", 0)
            if (to.isBlank() || amt.isBlank()) { Toast.makeText(this,"Nhập đủ",0).show(); return@setOnClickListener }
            if (pass != prefs.getString("password","")) { Toast.makeText(this,"Sai pass",0).show(); return@setOnClickListener }
            Toast.makeText(this,"Gửi $amt BTC tới $to (demo)",1).show()
            finish()
        }
    }
}