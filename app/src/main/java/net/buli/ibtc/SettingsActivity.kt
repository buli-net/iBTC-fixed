package net.buli.ibtc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
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
            // change password logic
        }
        btnShowSeed.setOnClickListener { }
        btnBackup.setOnClickListener { }
        btnAbout.setOnClickListener { }
    }
}
