package net.buli.ibtc
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val etOldPwd = findViewById<EditText>(R.id.etOldPwd)
        val etNewPwd = findViewById<EditText>(R.id.etNewPwd)
        val btnChangePwd = findViewById<Button>(R.id.btnChangePwd)
        val btnShowSeed = findViewById<Button>(R.id.btnShowSeed)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnAbout = findViewById<Button>(R.id.btnAbout)
        val sec = SecurePrefs(this)
        btnChangePwd.setOnClickListener {
            if (sec.checkPwd(etOldPwd.text.toString())) {
                sec.savePwd(etNewPwd.text.toString())
                Toast.makeText(this,"OK",Toast.LENGTH_SHORT).show()
            }
        }
    }
}
