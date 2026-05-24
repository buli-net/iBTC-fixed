package net.buli.ibtc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val etCurrent = findViewById<EditText>(R.id.etCurrent)
        val etNew = findViewById<EditText>(R.id.etNew)
        val etConfirm = findViewById<EditText>(R.id.etConfirm)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            // save logic
        }
    }
}
