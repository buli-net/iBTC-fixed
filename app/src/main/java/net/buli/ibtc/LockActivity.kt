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
        val etPwd = findViewById<EditText>(R.id.etPassword)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)

        btnUnlock.setOnClickListener {
            val pwd = etPwd.text.toString()
            
            // FIX: check hash thay vì prefs.getString("password")
            if (sec.checkPwd(pwd)) {
                getSharedPreferences("ibtc_prefs", 0).edit()
                    .putBoolean("locked", false).apply()
                
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
                etPwd.text.clear()
            }
        }
    }

    // Không cho back ra
    override fun onBackPressed() { }
}