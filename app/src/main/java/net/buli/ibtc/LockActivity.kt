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
            finish(); return
        }

        val etPwd = findViewById<EditText>(R.id.etPassword)
        val btn = findViewById<Button>(R.id.btnUnlock)

        btn.setOnClickListener {
            val hash = sha256(etPwd.text.toString())
            if (hash == prefs.getString("pwd_hash", "")) {
                toast("Mở khóa thành công")
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            } else toast("Sai mật khẩu")
        }
    }
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}