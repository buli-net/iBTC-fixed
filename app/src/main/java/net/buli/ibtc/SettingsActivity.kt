package net.buli.ibtc
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = getSharedPreferences("ibtc", MODE_PRIVATE)
        findViewById<Button>(R.id.btnChangePwd).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        findViewById<Button>(R.id.btnShowSeed).setOnClickListener {
            Toast.makeText(this, prefs.getString("seed",""), Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnBackup).setOnClickListener {
            Toast.makeText(this, "Backup: "+prefs.getString("seed",""), Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            Toast.makeText(this, "iBTC-fixed v1.0", Toast.LENGTH_SHORT).show()
        }
    }
}