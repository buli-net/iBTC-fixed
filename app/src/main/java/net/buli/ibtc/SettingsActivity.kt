package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : BaseActivity() {

    private val sec by lazy { SecurePrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // ĐÚNG ID theo layout của mày
        val etOldPwd = findViewById<EditText>(R.id.etOldPwd)
        val etNewPwd = findViewById<EditText>(R.id.etNewPwd)
        val btnChangePwd = findViewById<Button>(R.id.btnChangePwd)
        val btnShowSeed = findViewById<Button>(R.id.btnShowSeed)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnAbout = findViewById<Button>(R.id.btnAbout)
        
        // tvSeed có thể không có trong layout, lấy an toàn
        val tvSeed = findViewById<TextView?>(R.id.tvSeed)

        btnChangePwd.setOnClickListener {
            val old = etOldPwd.text.toString()
            val new = etNewPwd.text.toString()

            if (old.isEmpty() || new.isEmpty()) {
                Toast.makeText(this, "Nhập đủ mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (sec.checkPwd(old)) {
                if (new.length < 6) {
                    Toast.makeText(this, "MK mới >= 6 ký tự", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sec.savePwd(new)
                etOldPwd.text.clear()
                etNewPwd.text.clear()
                Toast.makeText(this, "Đổi mật khẩu thành công", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Mật khẩu cũ sai", Toast.LENGTH_SHORT).show()
            }
        }

        btnShowSeed.setOnClickListener {
            val pwd = etOldPwd.text.toString()
            if (sec.checkPwd(pwd)) {
                val seed = sec.getSeed()
                tvSeed?.text = seed
                Toast.makeText(this, "Seed: $seed", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }

        btnBackup.setOnClickListener {
            val pwd = etOldPwd.text.toString()
            if (sec.checkPwd(pwd)) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("IBTC Seed", sec.getSeed()))
                Toast.makeText(this, "Đã copy seed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nhập MK cũ để backup", Toast.LENGTH_SHORT).show()
            }
        }

        btnAbout.setOnClickListener {
            Toast.makeText(this, "IBTC Wallet v1.1 - Secure", Toast.LENGTH_SHORT).show()
        }
    }
}