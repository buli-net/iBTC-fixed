package net.buli.ibtc
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class SettingsActivity : BaseActivity() {
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

        btnChangePwd.setOnClickListener {
            if (sec.checkPwd(etOldPwd.text.toString())) {
                sec.savePwd(etNewPwd.text.toString())
                Toast.makeText(this,"Đổi xong",Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this,"Sai MK cũ",Toast.LENGTH_SHORT).show()
        }
        btnShowSeed.setOnClickListener {
            if (sec.checkPwd(etOldPwd.text.toString()))
                Toast.makeText(this, sec.getSeed(), Toast.LENGTH_LONG).show()
        }
        btnBackup.setOnClickListener {
            if (sec.checkPwd(etOldPwd.text.toString())) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("seed", sec.getSeed()))
                Toast.makeText(this,"Đã copy",Toast.LENGTH_SHORT).show()
            }
        }
        btnAbout.setOnClickListener { Toast.makeText(this,"IBTC v1.1",Toast.LENGTH_SHORT).show() }
    }
}