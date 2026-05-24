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
    private var feeSlow=5; private var feeNormal=8; private var feeFast=12; private var selectedRate=8

    private val qrLauncher = registerForActivityResult(ScanContract()) { res ->
        if (res.contents != null) findViewById<EditText>(R.id.etAddress).setText(res.contents)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_send)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnScan).setOnClickListener {
            qrLauncher.launch(ScanOptions().setPrompt("Quét địa chỉ BTC"))
        }

        val rg = findViewById<RadioGroup>(R.id.rgFee)
        val etCustom = findViewById<EditText>(R.id.etCustomFee)
        val tvInfo = findViewById<TextView>(R.id.tvFeeInfo)

        fun update() {
            val vsize = 140
            val feeSat = selectedRate * vsize
            val feeBtc = feeSat / 100_000_000.0
            tvInfo.text = "≈ $feeSat sat ($feeBtc BTC) – ${selectedRate} sat/vB"
        }

        rg.setOnCheckedChangeListener { _, id ->
            etCustom.visibility = if (id == R.id.rbCustom) View.VISIBLE else View.GONE
            selectedRate = when(id) {
                R.id.rbSlow -> feeSlow
                R.id.rbFast -> feeFast
                R.id.rbCustom -> etCustom.text.toString().toIntOrNull() ?: feeNormal
                else -> feeNormal
            }
            update()
        }
        etCustom.addTextChangedListener(object: android.text.TextWatcher{
            override fun afterTextChanged(s: android.text.Editable?) {
                if(rg.checkedRadioButtonId==R.id.rbCustom){ selectedRate=s.toString().toIntOrNull()?:feeNormal; update() }
            }
            override fun beforeTextChanged(a: CharSequence?,b:Int,c:Int,d:Int){}
            override fun onTextChanged(a: CharSequence?,b:Int,c:Int,d:Int){}
        })

        // LẤY PHÍ REAL
        thread {
            try {
                val json = URL("https://mempool.space/api/v1/fees/recommended").readText()
                val o = JSONObject(json)
                feeFast = o.getInt("fastestFee")
                feeNormal = o.getInt("halfHourFee")
                feeSlow = o.getInt("hourFee")
                runOnUiThread {
                    findViewById<RadioButton>(R.id.rbSlow).text = "Chậm (~${feeSlow} sat/vB)"
                    findViewById<RadioButton>(R.id.rbNormal).text = "Thường (~${feeNormal} sat/vB)"
                    findViewById<RadioButton>(R.id.rbFast).text = "Nhanh (~${feeFast} sat/vB)"
                    selectedRate = feeNormal; update()
                }
            } catch (_:Exception){}
        }

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            Toast.makeText(this, "Sẽ gửi với phí $selectedRate sat/vB", Toast.LENGTH_SHORT).show()
        }
    }
}