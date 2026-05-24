package net.buli.ibtc

import android.content.Intent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

abstract class BaseNavActivity : AppCompatActivity() {
    protected fun setupNav(selectedId: Int) {
        val purple = ContextCompat.getColor(this, R.color.safepal_purple)
        val gray = ContextCompat.getColor(this, R.color.safepal_gray)

        val tabs = listOf(
            Triple(R.id.navWallet, R.id.icWallet to R.id.tvWallet, MainActivity::class.java),
            Triple(R.id.navMarket, R.id.icMarket to R.id.tvMarket, MarketActivity::class.java),
            Triple(R.id.navDapp, R.id.icDapp to R.id.tvDapp, DAppActivity::class.java),
            Triple(R.id.navSwap, R.id.icSwap to R.id.tvSwap, SwapActivity::class.java),
            Triple(R.id.navSettings, R.id.icSettings to R.id.tvSettings, SettingsActivity::class.java)
        )

        tabs.forEach { (navId, ids, cls) ->
            val (icId, tvId) = ids
            findViewById<android.view.View>(navId)?.setOnClickListener {
                if (navId != selectedId) {
                    // tắt khóa tạm để không bị LockActivity nhảy lên khi chuyển tab
                    getSharedPreferences("ibtc_prefs",0).edit().putBoolean("locked",false).apply()
                    startActivity(Intent(this, cls))
                    overridePendingTransition(0,0)
                    finish()
                }
            }
            val color = if (navId == selectedId) purple else gray
            findViewById<ImageView>(icId)?.setColorFilter(color)
            findViewById<TextView>(tvId)?.setTextColor(color)
        }
    }
}