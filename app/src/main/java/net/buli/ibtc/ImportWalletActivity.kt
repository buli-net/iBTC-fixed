package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
class ImportWalletActivity:BaseActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_import_wallet)
 val sec=SecurePrefs(this);val etSeed=findViewById<EditText>(R.id.etSeed);val etPwd=findViewById<EditText>(R.id.etPassword);val btn=findViewById<Button>(R.id.btnImport)
 btn.setOnClickListener{try{val w=etSeed.text.toString().trim().split("\\s+".toRegex());MnemonicCode.INSTANCE.check(w)
 val seed=DeterministicSeed(w,null,"",System.currentTimeMillis()/1000L);sec.saveSeed(w.joinToString(" "));sec.savePwd(etPwd.text.toString())
 val wal=org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(),seed)
 getSharedPreferences("ibtc_prefs",0).edit().putString("btc_address",wal.currentReceiveAddress().toString()).putBoolean("has_wallet",true).apply()
 toast("Nhập xong");finish()}catch(e:Exception){toast("Seed sai")}}}
 private fun toast(s:String)=Toast.makeText(this,s,1).show()}