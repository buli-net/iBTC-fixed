package net.buli.ibtc
import android.os.Bundle
import android.view.View
import android.widget.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

class SendActivity:BaseNavActivity(){
    private var slow=5;private var normal=8;private var fast=12;private var rate=8
    private val qr=registerForActivityResult(ScanContract()){r->if(r.contents!=null)findViewById<EditText>(R.id.etAddress).setText(r.contents)}
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_send)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener{finish()}
        findViewById<ImageView>(R.id.btnScan).setOnClickListener{qr.launch(ScanOptions().setPrompt("Quét địa chỉ BTC"))}
        val rg=findViewById<RadioGroup>(R.id.rgFee);val et=findViewById<EditText>(R.id.etCustomFee);val tv=findViewById<TextView>(R.id.tvFeeInfo)
        fun up(){val s=rate*140;tv.text="≈ $s sat (${s/1e8} BTC) – $rate sat/vB"}
        rg.setOnCheckedChangeListener{_,id->et.visibility=if(id==R.id.rbCustom)View.VISIBLE else View.GONE;rate=when(id){R.id.rbSlow->slow;R.id.rbFast->fast;R.id.rbCustom->et.text.toString().toIntOrNull()?:normal;else->normal};up()}
        thread{try{val o=JSONObject(URL("https://mempool.space/api/v1/fees/recommended").readText());fast=o.getInt("fastestFee");normal=o.getInt("halfHourFee");slow=o.getInt("hourFee");runOnUiThread{findViewById<RadioButton>(R.id.rbSlow).text="Chậm (~$slow)";findViewById<RadioButton>(R.id.rbNormal).text="Thường (~$normal)";findViewById<RadioButton>(R.id.rbFast).text="Nhanh (~$fast)";rate=normal;up()}}catch(_:Exception){}}
    }
}