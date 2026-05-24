package net.buli.ibtc
import android.os.Bundle

class SwapActivity : BaseNavActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_swap)
        setupNav(R.id.navSwap)
    }
}