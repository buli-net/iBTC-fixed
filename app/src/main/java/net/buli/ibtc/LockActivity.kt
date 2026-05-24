package net.buli.ibtc
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class LockActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        val sec = SecurePrefs(this)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        btnUnlock.setOnClickListener {
            if (sec.checkPwd(etPassword.text.toString())) {
                getSharedPreferences("ibtc_prefs",0).edit().putBoolean("locked",false).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else Toast.makeText(this,"Sai MK",Toast.LENGTH_SHORT).show()
        }
    }
    override fun onBackPressed() {}
}
