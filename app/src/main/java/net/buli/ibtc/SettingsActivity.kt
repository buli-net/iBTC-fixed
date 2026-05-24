package net.buli.ibtc

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class SettingsActivity : BaseNavActivity() {
    private val prefs by lazy { getSharedPreferences("ibtc_prefs", 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // QUAN TRỌNG: giữ thanh nav
        setupNav(R.id.navSettings)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            // chỉ back về Home, không finish để nav vẫn hoạt động
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.itemBackupSeed).setOnClickListener {
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "Nhập mật khẩu"
            }
            AlertDialog.Builder(this)
                .setTitle("Xác nhận mật khẩu")
                .setView(et)
                .setPositiveButton("Xem") { _, _ ->
                    if (et.text.toString() == prefs.getString("password", "")) {
                        AlertDialog.Builder(this)
                            .setTitle("Seed phrase")
                            .setMessage(prefs.getString("seed", "Chưa có seed"))
                            .setPositiveButton("Đóng", null)
                            .show()
                    } else {
                        Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        findViewById<LinearLayout>(R.id.itemChangePass).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.itemDelete).setOnClickListener {
            val view = layoutInflater.inflate(R.layout.dialog_delete, null)
            val etPass = view.findViewById<EditText>(R.id.etPassDel)
            AlertDialog.Builder(this)
                .setTitle("Xóa ví")
                .setView(view)
                .setPositiveButton("Xóa") { _, _ ->
                    if (etPass.text.toString() == prefs.getString("password", "")) {
                        prefs.edit().clear().apply()
                        startActivity(Intent(this, WelcomeActivity::class.java))
                        finishAffinity()
                    } else {
                        Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }
}