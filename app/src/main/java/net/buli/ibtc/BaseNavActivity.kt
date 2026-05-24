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

        val items = listOf(
            R.id.navWallet to Pair(R.id.icWallet to R.id.tvWallet, MainActivity::class.java),
            R.id.navMarket to Pair(R.id.icMarket to R.id.tvMarket, MarketActivity::class.java),
            R.id.navDapp to Pair(R.id.icDapp to R.id.tvDapp, DAppActivity::class.java),
            R.id.navSwap to Pair(R.id.icSwap to R.id.tvSwap, SwapActivity::class.java),
            R.id.navSettings to Pair(R.id.icSettings to R.id.tvSettings, SettingsActivity::class.java)
        )

        items.forEach { (navId, pair) ->
            val (icons, cls) = pair
            val (icId, tvId) = icons
            findViewById<android.view.View>(navId).setOnClickListener {
                if (navId != selectedId) {
                    startActivity(Intent(this, cls))
                    overridePendingTransition(0,0)
                    finish()
                }
            }
            val color = if (navId == selectedId) purple else gray
            findViewById<ImageView>(icId).setColorFilter(color)
            findViewById<TextView>(tvId).setTextColor(color)
        }
    }
}