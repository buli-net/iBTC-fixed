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

    // Dùng SecurePrefs đã mã hóa thay cho SharedPreferences thường
    private val sec by lazy { SecurePrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etOldPwd = findViewById<EditText>(R.id.etOldPwd)
        val etNewPwd = findViewById<EditText>(R.id.etNewPwd)
        val btnChangePwd = findViewById<Button>(R.id.btnChangePwd)
        val btnShowSeed = findViewById<Button>(R.id.btnShowSeed)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnAbout = findViewById<Button>(R.id.btnAbout)
        val tvSeed = findViewById<TextView>(R.id.tvSeed)

        // --- ĐỔI MẬT KHẨU ---
        btnChangePwd.setOnClickListener {
            val oldPwd = etOldPwd.text.toString()
            val newPwd = etNewPwd.text.toString()

            if (oldPwd.isEmpty() || newPwd.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // FIX: check hash thay vì so sánh plaintext
            if (sec.checkPwd(oldPwd)) {
                if (newPwd.length < 6) {
                    Toast.makeText(this, "Mật khẩu mới tối thiểu 6 ký tự", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sec.savePwd(newPwd) // lưu dưới dạng SHA-256
                etOldPwd.text.clear()
                etNewPwd.text.clear()
                Toast.makeText(this, "Đổi mật khẩu thành công", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Mật khẩu cũ không đúng", Toast.LENGTH_SHORT).show()
            }
        }

        // --- HIỂN THỊ SEED ---
        btnShowSeed.setOnClickListener {
            val pwd = etOldPwd.text.toString()
            if (sec.checkPwd(pwd)) {
                val seed = sec.getSeed()
                tvSeed.text = if (seed.isNotEmpty()) seed else "Chưa tạo ví"
                Toast.makeText(this, "CẢNH BÁO: Không chia sẻ cho ai!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
                tvSeed.text = ""
            }
        }

        // --- BACKUP SEED ---
        btnBackup.setOnClickListener {
            val pwd = etOldPwd.text.toString()
            if (sec.checkPwd(pwd)) {
                val seed = sec.getSeed()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("IBTC Seed", seed))
                Toast.makeText(this, "Đã copy seed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nhập mật khẩu để backup", Toast.LENGTH_SHORT).show()
            }
        }

        // --- ABOUT ---
        btnAbout.setOnClickListener {
            Toast.makeText(this, "IBTC Wallet v1.1 - Secure by Android Keystore", Toast.LENGTH_LONG).show()
        }
    }
}