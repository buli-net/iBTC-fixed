package net.buli.ibtc

import android.content.Context
import android.content.SharedPreferences
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

data class WalletInfo(val id:String, val name:String, val seed:String, val xpub:String)
data class TransactionInfo(val txId:String, val amount:Double, val time:Date, val type:String)

class WalletManager(private val ctx:Context){
    private val params = MainNetParams.get()
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null
    private var lastPrice = prefs.getFloat("last_price", 0f).toDouble()
    private var progressCb: ((Int,String)->Unit)? = null

    fun onProgress(cb:(Int,String)->Unit){ progressCb = cb }

    fun init(){
        val info = getActive()?: return
        if(kit!= null) return
        val seed = DeterministicSeed(null, info.seed.split(" "), 0L)
        kit = WalletAppKit(params, File(ctx.filesDir, info.id), "ibtc").apply {
            setBlockingStartup(false)
            restoreWalletFromSeed(seed)
            try { setCheckpoints(ctx.assets.open("bitcoin-checkpoints.txt")) } catch(_:Exception){}
            peerGroup.maxConnections = 4
            setBloomFilterFalsePositiveRate(0.00001)
            setUserAgent("iBTC","1.0","")
            setDownloadListener(object : org.bitcoinj.core.listeners.DownloadProgressTracker(){
                override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                    val d = date?.let{ SimpleDateFormat("MM/yy").format(it) }?:""
                    progressCb?.invoke(pct.toInt(), "Sync ${pct.toInt()}% $d")
                }
                override fun doneDownload() { progressCb?.invoke(100, "Đã sync") }
            })
            startAsync(); awaitRunning()
        }
    }

    fun createWallet(name:String):WalletInfo{
        val id = UUID.randomUUID().toString().take(8)
        val seed = DeterministicSeed(SecureRandom(), 128, "")
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val info = WalletInfo(id, name, mnemonic, "")
        prefs.edit().putString("w_$id","$name|$mnemonic|").putString("active",id).apply()
        kit?.stopAsync(); kit=null; init(); return info
    }

    fun getWallets() = prefs.all.keys.filter{it.startsWith("w_")}.mapNotNull{ k->
        prefs.getString(k,"")?.split("|")?.let{ WalletInfo(k.removePrefix("w_"), it[0], it[1], it.getOrElse(2){""}) }
    }
    fun getActive() = prefs.getString("active",null)?.let{id->
        prefs.getString("w_$id","")?.split("|")?.let{ WalletInfo(id,it[0],it[1],it.getOrElse(2){""}) }
    }
    fun switch(id:String){ prefs.edit().putString("active",id).apply(); kit?.stopAsync(); kit=null; init() }
    fun getBalance() = kit?.wallet()?.getBalance(Wallet.BalanceType.ESTIMATED)?.toBtc()?.toDouble()?:0.0
    fun getAddress() = kit?.wallet()?.currentReceiveAddress().toString()?: ""
    fun getTransactions() = kit?.wallet()?.getTransactionsByTime()?.map{tx->
        val v = tx.getValue(kit!!.wallet())
        TransactionInfo(tx.txId.toString(), v.toBtc().toDouble(), tx.updateTime, if(v.isPositive)"Nhận" else "Gửi")
    }?.reversed()?: emptyList()

    private fun httpGet(u:String) = try{ (URL(u).openConnection() as HttpURLConnection).apply{connectTimeout=5000;readTimeout=5000}.inputStream.bufferedReader().readText() }catch(_:Exception){""}
    fun price():Double{
        val urls = listOf("https://blockchain.info/ticker","https://mempool.space/api/v1/prices")
        for(u in urls){ val t=httpGet(u); val p=Regex("([0-9]{4,}\\.[0-9]+)").find(t)?.value?.toDoubleOrNull(); if(p!=null&&p>1000){ lastPrice=p; prefs.edit().putFloat("last_price",p.toFloat()).apply(); return p } }
        return lastPrice
    }
    fun send(to:String, amt:Double):String{
        val coin = Coin.parseCoin("%.8f".format(amt))
        val tx = kit!!.wallet().createSend(Address.fromString(params,to), coin)
        kit!!.wallet().commitTx(tx); kit!!.peerGroup().broadcastTransaction(tx).future().get()
        return tx.txId.toString()
    }
}