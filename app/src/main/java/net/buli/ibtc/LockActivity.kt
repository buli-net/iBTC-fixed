package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class LockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        val prefs = getSharedPreferences("ibtc", MODE_PRIVATE)
        
        if (!prefs.getBoolean("has_wallet", false)) {
            startActivity(Intent(this, CreateWalletActivity::class.java))
            finish()
            return
        }

        val etPwd = findViewById<EditText>(R.id.etPassword)
        val btn = findViewById<Button>(R.id.btnUnlock)

        btn.setOnClickListener {
            val input = etPwd.text.toString()
            if (input.isEmpty()) {
                toast("Nhập mật khẩu")
                return@setOnClickListener
            }
            
            val hash = sha256(input)
            val savedHash = prefs.getString("pwd_hash", "")
            
            if (hash == savedHash) {
                toast("Mở khóa thành công")
                startActivity(Intent(this, SettingsActivity::class.java))
                etPwd.text.clear()
            } else {
                toast("Sai mật khẩu")
                etPwd.text.clear()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<EditText>(R.id.etPassword)?.text?.clear()
    }

    private fun sha256(s: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}