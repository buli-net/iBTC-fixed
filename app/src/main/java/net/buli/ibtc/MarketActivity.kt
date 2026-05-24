package net.buli.ibtc
import android.os.Bundle
class MarketActivity : BaseNavActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_market)
        setupNav(R.id.navMarket)
    }
}