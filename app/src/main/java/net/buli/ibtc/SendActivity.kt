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
            qrLauncher.launch(ScanOptions().setPrompt("Quét địa chỉ BTC").setBeepEnabled(false))
        }

        val rgFee = findViewById<RadioGroup>(R.id.rgFee)
        val etCustom = findViewById<EditText>(R.id.etCustomFee)
        val tvFeeInfo = findViewById<TextView>(R.id.tvFeeInfo)

        fun updateFee() {
            val feeSat = selectedRate * 140
            val feeBtc = feeSat / 100_000_000.0
            tvFeeInfo.text = "≈ $feeSat sat ($feeBtc BTC) – ${selectedRate} sat/vB"
        }

        rgFee.setOnCheckedChangeListener { _, id ->
            etCustom.visibility = if (id == R.id.rbCustom) View.VISIBLE else View.GONE
            selectedRate = when (id) {
                R.id.rbSlow -> feeSlow
                R.id.rbFast -> feeFast
                R.id.rbCustom -> etCustom.text.toString().toIntOrNull() ?: feeNormal
                else -> feeNormal
            }
            updateFee()
        }

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
            } catch (_: Exception) {}
        }
    }
}