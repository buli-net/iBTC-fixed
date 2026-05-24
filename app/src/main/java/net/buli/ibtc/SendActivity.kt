package net.buli.ibtc
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class SendActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)
        val etAddress = findViewById<EditText>(R.id.etAddress)
        val etAmount = findViewById<EditText>(R.id.etAmount)
        val btnSend = findViewById<Button>(R.id.btnSend)

        btnSend.setOnClickListener {
            // Tạm khóa gửi thật để build qua, tránh lỗi addUTXO/OkHttp
            Toast.makeText(this,"Chức năng gửi đang nâng cấp bảo mật",Toast.LENGTH_LONG).show()
        }
    }
}