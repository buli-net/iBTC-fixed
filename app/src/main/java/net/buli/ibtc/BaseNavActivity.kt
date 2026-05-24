package net.buli.ibtc
import android.content.Intent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

abstract class BaseNavActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("ibtc_prefs", MODE_PRIVATE) }
    override fun onPause() { super.onPause(); if(prefs.getBoolean("has_wallet",false)) prefs.edit().putBoolean("locked",true).apply() }
    override fun onResume() { super.onResume(); if(prefs.getBoolean("locked",false)&&prefs.getBoolean("has_wallet",false)) startActivity(Intent(this,LockActivity::class.java)) }
    protected fun setupNav(selectedId:Int){
        val purple=ContextCompat.getColor(this,R.color.safepal_purple); val gray=ContextCompat.getColor(this,R.color.safepal_gray)
        val tabs=listOf(Triple(R.id.navWallet,R.id.icWallet to R.id.tvWallet,MainActivity::class.java),Triple(R.id.navMarket,R.id.icMarket to R.id.tvMarket,MarketActivity::class.java),Triple(R.id.navDapp,R.id.icDapp to R.id.tvDapp,DAppActivity::class.java),Triple(R.id.navSwap,R.id.icSwap to R.id.tvSwap,SwapActivity::class.java),Triple(R.id.navSettings,R.id.icSettings to R.id.tvSettings,SettingsActivity::class.java))
        tabs.forEach{(navId,ids,cls)->val(icId,tvId)=ids;findViewById<android.view.View>(navId)?.setOnClickListener{if(navId!=selectedId){prefs.edit().putBoolean("locked",true).apply();startActivity(Intent(this,cls));overridePendingTransition(0,0);finish()}};val c=if(navId==selectedId)purple else gray;findViewById<ImageView>(icId)?.setColorFilter(c);findViewById<TextView>(tvId)?.setTextColor(c)}
    }
}