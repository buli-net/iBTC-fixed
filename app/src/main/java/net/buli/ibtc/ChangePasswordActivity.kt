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
        val btn = findViewById<Button>(R.id.btnChange)
        btn.setOnClickListener {
            if (!sec.checkPwd(etOld.text.toString())) { toast("MK cũ sai"); return@setOnClickListener }
            if (etNew.text.toString() != etConfirm.text.toString()) { toast("Không khớp"); return@setOnClickListener }
            sec.savePwd(etNew.text.toString()); toast("Đổi xong"); finish()
        }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}