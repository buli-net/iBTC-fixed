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
            if (!sec.checkPwd(etOld.text.toString())) { Toast.makeText(this,"MK cũ sai",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (etNew.text.toString() != etConfirm.text.toString()) { Toast.makeText(this,"Không khớp",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            sec.savePwd(etNew.text.toString())
            Toast.makeText(this,"Đổi xong",Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
