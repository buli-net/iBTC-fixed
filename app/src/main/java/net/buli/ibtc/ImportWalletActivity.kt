package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed

class ImportWalletActivity:BaseActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_import_wallet)
    val sec=SecurePrefs(this);val etS=findViewById<EditText>(R.id.etSeed);val etP=findViewById<EditText>(R.id.etPassword)
    findViewById<Button>(R.id.btnImport).setOnClickListener{
        try{val words=etS.text.toString().trim().split(" ");MnemonicCode.INSTANCE.check(words)
            val seed=DeterministicSeed(words,null,"",System.currentTimeMillis()/1000);sec.saveSeed(words.joinToString(" "));sec.savePwd(etP.text.toString())
            val w=org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(),seed);getSharedPreferences("ibtc_prefs",0).edit().putString("btc_address",w.currentReceiveAddress().toString()).apply()
            Toast.makeText(this,"Nhập ví xong",0).show();finish()
        }catch(e:Exception){Toast.makeText(this,"Seed sai",0).show()}}}}