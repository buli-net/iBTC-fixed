package net.buli.ibtc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val etCur = findViewById<EditText>(R.id.etCurrent)
        val etNew = findViewById<EditText>(R.id.etNew)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val btn = findViewById<Button>(R.id.btnSave)
        val prefs = getSharedPreferences("ibtc", MODE_PRIVATE)

        btn.setOnClickListener {
            val curHash = sha256(etCur.text.toString())
            if (curHash != prefs.getString("pwd_hash", "")) { toast("Mật khẩu cũ sai"); return@setOnClickListener }
            val n1 = etNew.text.toString()
            val n2 = etConfirm.text.toString()
            if (n1.length < 6) { toast("Mật khẩu mới ≥6 ký tự"); return@setOnClickListener }
            if (n1 != n2) { toast("Xác nhận không khớp"); return@setOnClickListener }

            prefs.edit().putString("pwd_hash", sha256(n1)).apply()
            toast("Đổi mật khẩu thành công")
            finish()
        }
    }
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}