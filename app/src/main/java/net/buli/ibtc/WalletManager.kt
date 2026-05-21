package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL

class WalletManager(private val context: Context) {
    private val params = MainNetParams.get()
    private lateinit var kit: WalletAppKit
    private lateinit var wallet: Wallet
    @Volatile private var ready = false

    fun initWallet() {
        if (ready) return
        val dir = File(context.filesDir, "wallets").apply { mkdirs() }
        kit = object : WalletAppKit(params, dir, "ibtc-spv") {
            override fun onSetupCompleted() { wallet = wallet() }
        }
        kit.setAutoSave(true); kit.setBlockingStartup(false)
        kit.startAsync(); kit.awaitRunning()
        wallet = kit.wallet()
        ready = true
    }

    fun isReady() = ready
    fun getReceiveAddress() = if (ready) wallet.currentReceiveAddress().toString() else ""
    fun getBalance() = if (ready) wallet.getBalance(Wallet.BalanceType.ESTIMATED).value / 1e8 else 0.0
    fun getSeed() = if (ready) wallet.keyChainSeed?.mnemonicCode?.joinToString(" ") ?: "" else ""
    fun sync() { if(ready) try{ kit.peerGroup().downloadBlockChain() }catch(_:Exception){} }

    // Dùng Binance - không bị chặn ở VN
    fun getBtcPrice(): Double = try {
        val json = URL("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT").readText()
        """"price":"([0-9.]+)"""".toRegex().find(json)?.groupValues?.get(1)?.toDouble() ?: 0.0
    } catch(_:Exception){ 0.0 }

    fun sendCoins(to:String, amountBtc:Double, feeSatVb:Long):String = try {
        if(!ready) return "Ví chưa sẵn sàng"
        val amount = Coin.valueOf((amountBtc*1e8).toLong())
        val target = Address.fromString(params, to)
        val req = SendRequest.to(target, amount)
        req.feePerKb = Coin.valueOf(feeSatVb * 1000)
        val res = wallet.sendCoins(kit.peerGroup(), req)
        res.broadcastComplete.get()
        "Đã gửi! TXID: ${res.tx.txId}"
    } catch(e:Exception){ "Lỗi: ${e.message}" }
}