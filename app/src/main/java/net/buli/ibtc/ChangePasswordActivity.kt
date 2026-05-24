package net.buli.ibtc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class ChangePasswordActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val sec = SecurePrefs(this)
        val etOld = findViewById<EditText>(R.id.etOldPassword)
        val etNew = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)
        val btnChange = findViewById<Button>(R.id.btnChange)

        btnChange.setOnClickListener {
            val old = etOld.text.toString()
            val new = etNew.text.toString()
            val confirm = etConfirm.text.toString()

            if (!sec.checkPwd(old)) {
                Toast.makeText(this, "Mật khẩu cũ sai", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (new.length < 6) {
                Toast.makeText(this, "MK mới >= 6 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (new != confirm) {
                Toast.makeText(this, "Xác nhận không khớp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sec.savePwd(new)
            Toast.makeText(this, "Đổi mật khẩu thành công", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}