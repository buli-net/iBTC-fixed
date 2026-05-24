package net.buli.ibtc

import android.os.Bundle
import android.view.View
import android.widget.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

class SendActivity : BaseNavActivity() {
    private var feeSlow = 5
    private var feeNormal = 8
    private var feeFast = 12
    private var selectedRate = 8

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            findViewById<EditText>(R.id.etAddress).setText(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        
        findViewById<ImageView>(R.id.btnScan).setOnClickListener {
            qrLauncher.launch(
                ScanOptions()
                    .setPrompt("Quét địa chỉ BTC")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
            )
        }

        val rgFee = findViewById<RadioGroup>(R.id.rgFee)
        val etCustom = findViewById<EditText>(R.id.etCustomFee)
        val tvFeeInfo = findViewById<TextView>(R.id.tvFeeInfo)

        fun updateFee() {
            val vsize = 140
            val feeSat = selectedRate * vsize
            val feeBtc = feeSat / 100_000_000.0
            tvFeeInfo.text = "≈ $feeSat sat ($feeBtc BTC) – ${selectedRate} sat/vB"
        }

        rgFee.setOnCheckedChangeListener { _, checkedId ->
            etCustom.visibility = if (checkedId == R.id.rbCustom) View.VISIBLE else View.GONE
            selectedRate = when (checkedId) {
                R.id.rbSlow -> feeSlow
                R.id.rbFast -> feeFast
                R.id.rbCustom -> etCustom.text.toString().toIntOrNull() ?: feeNormal
                else -> feeNormal
            }
            updateFee()
        }

        etCustom.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (rgFee.checkedRadioButtonId == R.id.rbCustom) {
                    selectedRate = s.toString().toIntOrNull() ?: feeNormal
                    updateFee()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // LẤY PHÍ REAL-TIME
        thread {
            try {
                val json = URL("https://mempool.space/api/v1/fees/recommended").readText()
                val obj = JSONObject(json)
                feeFast = obj.getInt("fastestFee")
                feeNormal = obj.getInt("halfHourFee")
                feeSlow = obj.getInt("hourFee")
                runOnUiThread {
                    findViewById<RadioButton>(R.id.rbSlow).text = "Chậm (~${feeSlow} sat/vB)"
                    findViewById<RadioButton>(R.id.rbNormal).text = "Thường (~${feeNormal} sat/vB)"
                    findViewById<RadioButton>(R.id.rbFast).text = "Nhanh (~${feeFast} sat/vB)"
                    selectedRate = feeNormal
                    updateFee()
                }
            } catch (e: Exception) {
                runOnUiThread { tvFeeInfo.text = "Không lấy được phí" }
            }
        }

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            val addr = findViewById<EditText>(R.id.etAddress).text.toString()
            val amt = findViewById<EditText>(R.id.etAmount).text.toString()
            Toast.makeText(this, "Gửi $amt BTC đến $addr\nPhí: $selectedRate sat/vB", Toast.LENGTH_LONG).show()
        }
    }
}