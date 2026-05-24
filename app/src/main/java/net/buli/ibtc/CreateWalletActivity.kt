package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.security.SecureRandom

class CreateWalletActivity : AppCompatActivity() {
    private val words = listOf("seek","taste","yard","nature","fault","almost","offer","tone","scrap","valve","output","turn","abandon","ability","able","about","above","absent","absorb","abstract")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_wallet)

        val etPwd = findViewById<EditText>(R.id.etPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val btn = findViewById<Button>(R.id.btnCreate)

        btn.setOnClickListener {
            val p1 = etPwd.text.toString()
            val p2 = etConfirm.text.toString()
            if (p1.length < 6) { toast("Mật khẩu ≥6 ký tự"); return@setOnClickListener }
            if (p1 != p2) { toast("Xác nhận không khớp"); return@setOnClickListener }

            val seed = (1..12).map { words[SecureRandom().nextInt(words.size)] }.joinToString(" ")
            val hash = sha256(p1)
            getPrefs().edit()
                .putString("pwd_hash", hash)
                .putString("seed", seed)
                .putBoolean("has_wallet", true)
                .apply()

            toast("Tạo ví thành công")
            startActivity(Intent(this, LockActivity::class.java))
            finish()
        }
    }
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun getPrefs() = getSharedPreferences("ibtc", MODE_PRIVATE)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}