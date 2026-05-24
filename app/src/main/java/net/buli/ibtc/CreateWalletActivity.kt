package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import java.security.SecureRandom
class CreateWalletActivity : BaseActivity() {
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_create_wallet)
        val sec=SecurePrefs(this);val etPwd=findViewById<EditText>(R.id.etPassword);val btn=findViewById<Button>(R.id.btnCreate)
        btn.setOnClickListener{if(etPwd.text.length<6){toast("MK>=6");return@setOnClickListener}
            val m=MnemonicCode.INSTANCE.generateMnemonic(SecureRandom())
            val seed=DeterministicSeed(m,null,"",System.currentTimeMillis()/1000L)
            sec.saveSeed(m.joinToString(" "));sec.savePwd(etPwd.text.toString())
            val w=org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(),seed)
            getSharedPreferences("ibtc_prefs",0).edit().putString("btc_address",w.currentReceiveAddress().toString()).putBoolean("has_wallet",true).apply()
            toast("Tạo ví xong");finish()}}
    private fun toast(s:String)=Toast.makeText(this,s,0).show()
}