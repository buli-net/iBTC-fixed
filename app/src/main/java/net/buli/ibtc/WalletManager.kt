package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL

class WalletManager(private val context: Context) {
    private val params: NetworkParameters = MainNetParams.get()
    private lateinit var kit: WalletAppKit
    private lateinit var wallet: Wallet

    fun initWallet() {
        val dir = File(context.filesDir, "wallets")
        if (!dir.exists()) dir.mkdirs()
        kit = object : WalletAppKit(params, dir, "ibtc-spv") {
            override fun onSetupCompleted() { wallet = wallet() }
        }
        kit.setAutoSave(true); kit.setBlockingStartup(false)
        kit.startAsync(); kit.awaitRunning()
        wallet = kit.wallet()
    }

    fun getReceiveAddress() = wallet.currentReceiveAddress().toString()
    fun getBalance(): Double = wallet.getBalance(Wallet.BalanceType.ESTIMATED).value / 1e8
    fun sync(cb: (Int)->Unit){ try{ kit.peerGroup().downloadBlockChain(); cb(100)}catch(_:Exception){} }
    fun getSeed() = wallet.keyChainSeed?.mnemonicCode?.joinToString(" ") ?: ""

    fun getBtcPrice(): Double = try {
        val json = URL("https://api.coindesk.com/v1/bpi/currentprice/USD.json").readText()
        """"rate_float":([0-9.]+)""".toRegex().find(json)?.groupValues?.get(1)?.toDouble() ?: 0.0
    } catch(e:Exception){ 0.0 }

    fun sendCoins(to:String, amountBtc:Double, feeSatVb:Long):String = try {
        val amount = Coin.valueOf((amountBtc*1e8).toLong())
        val target = Address.fromString(params, to)
        val req = SendRequest.to(target, amount)
        req.feePerKb = Coin.valueOf(feeSatVb * 1000)
        val res = wallet.sendCoins(kit.peerGroup(), req)
        res.broadcastComplete.get()
        "Đã gửi! TXID: ${res.tx.txId}"
    } catch(e:Exception){ "Lỗi: ${e.message}" }
}