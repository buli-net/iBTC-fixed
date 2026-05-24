package net.buli.ibtc
import android.os.Bundle

class DAppActivity : BaseNavActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_dapp)
        setupNav(R.id.navDapp)
    }
}