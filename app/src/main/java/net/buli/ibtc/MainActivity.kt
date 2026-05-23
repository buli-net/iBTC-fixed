package net.buli.ibtc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle

import java.net.URL
import org.json.JSONObject

private var btcPriceUsd = 75000.0

fun fetchBtcPrice(callback: (Double)->Unit) {
    Thread {
        try {
            val txt = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd").readText()
            val price = JSONObject(txt).getJSONObject("bitcoin").getDouble("usd")
            btcPriceUsd = price
            callback(price)
        } catch(e:Exception){ callback(btcPriceUsd) }
    }.start()
}

data class FeeRates(val low:Long, val med:Long, val high:Long)
fun fetchFees(callback:(FeeRates)->Unit){
    Thread{
        try{
            val txt = URL("https://mempool.space/api/v1/fees/recommended").readText()
            val j = JSONObject(txt)
            val rates = FeeRates(
                j.getLong("hourFee").coerceIn(1,100),
                j.getLong("halfHourFee").coerceIn(1,100),
                j.getLong("fastestFee").coerceIn(1,100)
            )
            callback(rates)
        }catch(e:Exception){ callback(FeeRates(4,8,12)) }
    }.start()
}

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : AppCompatActivity() {
    private lateinit var wm: WalletManager
    private var walletName = "ibtc"
    private lateinit var tvBalance: TextView
    private lateinit var tvUsd: TextView
    private lateinit var tvStatus: TextView
    private var currentFee = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = WalletManager(this)
        if (wm.walletExists(walletName)) showLockScreen() else showWelcome()
    }

    private fun showWelcome() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE); setPadding(60,0,60,0)
        }
        val logo = TextView(this).apply { text = "iBTC"; textSize = 48f; setTextColor(Color.BLACK) }
        val btnCreate = Button(this).apply {
            text = "TẠO VÍ MỚI"; setBackgroundColor(Color.parseColor("#3F51B5")); setTextColor(Color.WHITE)
            setOnClickListener { showCreateDialog() }
        }
        val btnImport = Button(this).apply {
            text = "IMPORT SEED"; setBackgroundColor(Color.WHITE); setTextColor(Color.parseColor("#3F51B5"))
            setOnClickListener { showImportDialog() }
        }
        layout.addView(logo); layout.addView(btnCreate); layout.addView(btnImport)
        setContentView(layout)
    }

    private fun showCreateDialog() {
        val l = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(40,40,40,40) }
        val etName = EditText(this).apply { hint="Tên ví" }
        val etPass = EditText(this).apply { hint="Mật khẩu (>=8)"; inputType=InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val etPass2 = EditText(this).apply { hint="Nhập lại"; inputType=InputType.TYPE_TEXT_VARIATION_PASSWORD }
        listOf(etName,etPass,etPass2).forEach{l.addView(it)}
        AlertDialog.Builder(this).setTitle("Tạo ví mới").setView(l)
            .setPositiveButton("TẠO VÍ"){_,_->
                walletName = etName.text.toString().ifEmpty{"ibtc"}
                val seed = wm.createWallet(walletName, etPass.text.toString())
                AlertDialog.Builder(this).setTitle("LƯU SEED 12 TỪ").setMessage(seed)
                    .setPositiveButton("Đã lưu"){_,_->showLockScreen()}.show()
            }.setNegativeButton("HỦY",null).show()
    }

    private fun showImportDialog() {
        val l = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(40,40,40,40) }
        val etName = EditText(this).apply { hint="Tên ví" }
        val etSeed = EditText(this).apply { hint="Seed 12 từ" }
        val etPass = EditText(this).apply { hint="Mật khẩu mới (>=8)"; inputType=InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val etPass2 = EditText(this).apply { hint="Nhập lại"; inputType=InputType.TYPE_TEXT_VARIATION_PASSWORD }
        listOf(etName,etSeed,etPass,etPass2).forEach{l.addView(it)}
        AlertDialog.Builder(this).setTitle("Import ví").setView(l)
            .setPositiveButton("IMPORT"){_,_->
                walletName = etName.text.toString().ifEmpty{"ibtc"}
                wm.importWallet(walletName, etSeed.text.toString(), etPass.text.toString())
                showLockScreen()
            }.setNegativeButton("HỦY",null).show()
    }

    private fun showLockScreen() {
        val l = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setBackgroundColor(Color.WHITE); setPadding(60,0,60,0) }
        val logo = TextView(this).apply { text="🔒 iBTC"; textSize=32f; gravity=Gravity.CENTER }
        val etPass = EditText(this).apply { hint="Mật khẩu"; inputType=InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val btn = Button(this).apply { text="MỞ KHÓA"; setBackgroundColor(Color.parseColor("#3F51B5")); setTextColor(Color.WHITE)
            setOnClickListener { if(wm.unlock(walletName, etPass.text.toString())) showHome() else Toast.makeText(this@MainActivity,"Sai mật khẩu",0).show() } }
        l.addView(logo); l.addView(etPass); l.addView(btn)
        setContentView(l)
    }

    private fun showHome() {
        val root = ScrollView(this)
        val main = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        root.addView(main)

        // header + menu
        val header = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(30,60,30,20) }
        header.addView(TextView(this).apply { text=walletName; textSize=18f; layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
        val menuBtn = TextView(this).apply { text="⋮"; textSize=24f; setOnClickListener{ showMenu() } }
        header.addView(menuBtn)
        main.addView(header)

        // tabs
        val tabs = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val tabVi = TextView(this).apply { text="Ví"; gravity=Gravity.CENTER; setTextColor(Color.parseColor("#3F51B5")); textSize=16f; setPadding(0,20,0,20) }
        val tabSend = TextView(this).apply { text="Gửi/Nhận"; gravity=Gravity.CENTER; setTextColor(Color.GRAY); textSize=16f }
        tabs.addView(tabVi.apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
        tabs.addView(tabSend.apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
        main.addView(tabs)

        // content container
        val content = FrameLayout(this)
        main.addView(content)

        val viView = createViView()
        val sendView = createSendView()

        content.addView(viView)
        content.addView(sendView.apply { visibility=View.GONE })

        tabVi.setOnClickListener { viView.visibility=View.VISIBLE; sendView.visibility=View.GONE; tabVi.setTextColor(Color.parseColor("#3F51B5")); tabSend.setTextColor(Color.GRAY) }
        tabSend.setOnClickListener { viView.visibility=View.GONE; sendView.visibility=View.VISIBLE; tabVi.setTextColor(Color.GRAY); tabSend.setTextColor(Color.parseColor("#3F51B5")) }

        setContentView(root)
    }

    private fun createViView(): View {
        val l = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(30,20,30,20) }
        val card = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(40,30,40,30)
            background=GradientDrawable().apply{cornerRadius=30f; setColor(Color.parseColor("#EDEDED"))}
        }
        card.addView(TextView(this).apply{ text="Số dư:"; setTextColor(Color.GRAY) })
        tvBalance = TextView(this).apply{ text="0,00000000 BTC"; textSize=28f; setTextColor(Color.BLACK) }
        tvUsd = TextView(this).apply{ text="≈ $0,00"; setTextColor(Color.BLACK) }
        card.addView(tvBalance); card.addView(tvUsd)
        card.addView(TextView(this).apply{ text="$75.108,23 / BTC"; setTextColor(Color.GRAY); textSize=12f })
        tvStatus = TextView(this).apply{ text="Chưa sync • Giá tự động"; setTextColor(Color.GRAY); textSize=12f }
        card.addView(tvStatus)
        l.addView(card)

        val btnSync = Button(this).apply {
            text="SYNC NGAY"; setBackgroundColor(Color.parseColor("#3F51B5")); setTextColor(Color.WHITE)
            layoutParams=LinearLayout.LayoutParams(-1,140).apply{ topMargin=20 }
            setOnClickListener{
                tvStatus.text="Đang sync..."
                wm.startSync{ p -> runOnUiThread{ tvStatus.text="Sync: $p blocks"; updateBalance() } }
            }
        }
        l.addView(btnSync)
        l.addView(TextView(this).apply{ text="Lịch sử"; setPadding(0,30,0,10); textSize=16f })
        updateBalance()
        return l
    }

    private fun createSendView(): View {
        val l = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(30,20,30,20) }
        l.addView(TextView(this).apply{ text="Gửi BTC"; textSize=16f; setPadding(0,0,0,10) })

        val etAddr = EditText(this).apply{ hint="Địa chỉ" }
        val etAmt = EditText(this).apply{ hint="BTC"; inputType=InputType.TYPE_NUMBER_FLAG_DECIMAL }
        l.addView(etAddr); l.addView(etAmt)

        l.addView(TextView(this).apply{ text="Phí mạng:"; setPadding(0,20,0,10) })
        val rg = RadioGroup(this)
        val fees = listOf("Chậm (1 sat/vB)" to 1, "Thường (3 sat/vB)" to 3, "Nhanh (3 sat/vB)" to 3, "Tùy chỉnh" to 3)
        fees.forEachIndexed{ i,(t,v) ->
            val rb = RadioButton(this).apply{ text=t; tag=v; if(i==1) isChecked=true }
            rg.addView(rb)
        }
        rg.setOnCheckedChangeListener{_,id-> currentFee = l.findViewById<RadioButton>(id).tag as Int; updateFeePreview(etAmt.text.toString()) }
        l.addView(rg)

        val feeCard = LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL; setPadding(20,20,20,20)
            background=GradientDrawable().apply{ cornerRadius=20f; setColor(Color.parseColor("#EDEDED")) }
        }
        val tvFee = TextView(this).apply{ text="Ước tính phí: 0,00000750 BTC (≈ $0,56)" }
        val tvTotal = TextView(this).apply{ text="Tổng (gửi + phí): 0,00000750 BTC"; setTypeface(null, android.graphics.Typeface.BOLD) }
        val tvBal = TextView(this).apply{ text="Số dư 0,00000000 BTC"; setTextColor(Color.GRAY); textSize=12f }
        feeCard.addView(tvFee); feeCard.addView(tvTotal); feeCard.addView(tvBal)
        l.addView(feeCard)

        val btnSend = Button(this).apply{
            text="GỬI"; isEnabled=false; setBackgroundColor(Color.LTGRAY)
            setOnClickListener{
                try{
                    val txid = wm.sendCoins(etAddr.text.toString(), etAmt.text.toString(), currentFee)
                    Toast.makeText(this@MainActivity,"Đã gửi: $txid",1).show()
                }catch(e:Exception){ Toast.makeText(this@MainActivity,"Lỗi: ${e.message}",1).show() }
            }
        }
        l.addView(btnSend)

        // Nhận BTC
        l.addView(TextView(this).apply{ text="Nhận BTC"; setPadding(0,30,0,10) })
        val addr = wm.getReceiveAddress()
        val iv = ImageView(this).apply{ setImageBitmap(generateQR(addr)); layoutParams=LinearLayout.LayoutParams(400,400).apply{ gravity=Gravity.CENTER } }
        l.addView(iv)
        l.addView(TextView(this).apply{ text=addr; gravity=Gravity.CENTER; setPadding(0,10,0,10); textSize=12f })
        val btnCopy = Button(this).apply{
            text="COPY"; setBackgroundColor(Color.parseColor("#3F51B5")); setTextColor(Color.WHITE)
            setOnClickListener{
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("addr", addr))
                Toast.makeText(this@MainActivity,"Đã copy",0).show()
            }
        }
        l.addView(btnCopy)
        return l
    }

    private fun updateBalance() {
        val bal = wm.getBalance().replace(" BTC","").replace(".",",")
        tvBalance.text = "$bal BTC"
    }

    private fun updateFeePreview(amt: String){}

    private fun generateQR(text: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 400, 400)
        val bmp = Bitmap.createBitmap(400,400, Bitmap.Config.RGB_565)
        for(x in 0 until 400) for(y in 0 until 400) bmp.setPixel(x,y, if(bitMatrix[x,y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    private fun showMenu() {
        val items = arrayOf("Đổi tên","Đổi mật khẩu","Chi tiết ví","Khóa ví","Xóa ví")
        AlertDialog.Builder(this).setItems(items){_,which->
            when(which){
                0->{ /* đổi tên */ }
                1->{ /* đổi mk */ }
                2-> AlertDialog.Builder(this).setTitle("Seed").setMessage("Nhập mật khẩu để xem").show()
                3-> showLockScreen()
                4-> { wm.deleteWallet(walletName); showWelcome() }
            }
        }.show()
    }
}