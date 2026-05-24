package net.buli.ibtc
import android.os.Bundle
import android.widget.*
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed

class CreateWalletActivity:BaseActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_create_wallet)
    val sec=SecurePrefs(this);val et=findViewById<EditText>(R.id.etPassword)
    findViewById<Button>(R.id.btnCreate).setOnClickListener{
        val pwd=et.text.toString();if(pwd.length<6){Toast.makeText(this,"MK >=6 ký tự",0).show();return@setOnClickListener}
        val seed=DeterministicSeed(MnemonicCode.INSTANCE.generateMnemonic(),null,"",System.currentTimeMillis()/1000)
        sec.saveSeed(seed.mnemonicCode!!.joinToString(" "));sec.savePwd(pwd)
        val w=org.bitcoinj.wallet.Wallet.fromSeed(MainNetParams.get(),seed);getSharedPreferences("ibtc_prefs",0).edit().putString("btc_address",w.currentReceiveAddress().toString()).apply()
        Toast.makeText(this,"Tạo ví xong",0).show();finish()
    }}}