package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Date

data class WalletInfo(val id: String, val name: String, val seed: String)
data class TransactionInfo(val txId: String, val amount: Double, val type: String, val time: Date)
data class FeeRates(val slow: Int, val normal: Int, val fast: Int)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private var kit: WalletAppKit? = null
    private var active: WalletInfo? = null
    private val prefs = ctx.getSharedPreferences("wallets", Context.MODE_PRIVATE)

    fun hasWallets() = prefs.all.isNotEmpty()
    fun getActive(): WalletInfo? {
        if (active != null) return active
        val id = prefs.all.keys.firstOrNull() ?: return null
        active = WalletInfo(id, prefs.getString("${id}_name","")!!, prefs.getString("${id}_seed","")!!)
        return active
    }
    fun create(name: String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val seed = DeterministicSeed(SecureRandom(), 128, "")
        val info = WalletInfo(id, if(name.isBlank()) "Ví $id" else name, seed.mnemonicCode!!.joinToString(" "))
        prefs.edit().putString("${id}_name",info.name).putString("${id}_seed",info.seed).apply()
        active = info; return info
    }
    fun import(name: String, phrase: String): WalletInfo? {
        try {
            val words = phrase.trim().split("\\s+".toRegex())
            if (words.size < 12) return null
            DeterministicSeed(words, null, "", System.currentTimeMillis()/1000)
            val id = System.currentTimeMillis().toString()
            val info = WalletInfo(id, if(name.isBlank()) "Imported" else name, words.joinToString(" "))
            prefs.edit().putString("${id}_name",info.name).putString("${id}_seed",info.seed).apply()
            active = info; return info
        } catch (e:Exception){ return null }
    }
    fun delete(id: String) { try{stop()}catch(_:Exception){}; prefs.edit().remove("${id}_name").remove("${id}_seed").apply(); File(ctx.filesDir,id).deleteRecursively(); if(active?.id==id) active=null }
    fun init() { val i=getActive()?:return; if(kit!=null) return; val seed=DeterministicSeed(i.seed.split(" "),null,"",0L); kit=WalletAppKit(params,File(ctx.filesDir,i.id),"ibtc").apply{ setBlockingStartup(false); restoreWalletFromSeed(seed); startAsync(); awaitRunning() } }
    fun stop(){ try{kit?.stopAsync()?.awaitTerminated()}catch(_:Exception){}; kit=null }
    fun onProgress(cb:(Int,String)->Unit){ kit?.setDownloadListener(object:DownloadProgressTracker(){ override fun progress(p:Double,b:Int,d:Date?){cb(p.toInt(),if(p<100)"Đang sync ${p.toInt()}%" else "Đã sync")} override fun doneDownload(){cb(100,"Đã sync")} }) }
    fun getBalance()=kit?.wallet()?.balance?.value?.toDouble()?.div(1e8)?:0.0
    fun getAddress()=kit?.wallet()?.currentReceiveAddress().toString()
    fun getSeed()=active?.seed?:""
    fun getTransactions()=kit?.wallet()?.getTransactionsByTime()?.map{ val v=it.getValue(kit!!.wallet()).value.toDouble()/1e8; TransactionInfo(it.txId.toString(),kotlin.math.abs(v),if(v>0)"Nhận"else"Gửi",it.updateTime)}?.reversed()?:emptyList()
    fun send(to:String,amt:Double,fee:Int)=try{ val w=kit!!.wallet(); val r=SendRequest.to(Address.fromString(params,to),Coin.parseCoin(amt.toString())); r.feePerKb=Coin.valueOf(fee.toLong()*1000); w.completeTx(r); w.commitTx(r.tx); kit!!.peerGroup().broadcastTransaction(r.tx).future().get(); r.tx.txId.toString() }catch(e:Exception){"Lỗi: ${e.message}"}

    private fun httpGet(u:String)=try{ (URL(u).openConnection() as HttpURLConnection).apply{ setRequestProperty("User-Agent","Mozilla/5.0"); connectTimeout=5000; readTimeout=5000 }.inputStream.bufferedReader().readText() }catch(_:Exception){""}

    fun price():Double{
        var t=httpGet("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT")
        var p=Regex("\"price\":\"([\\d.]+)\"").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
        if(p!=null) return p
        t=httpGet("https://blockchain.info/ticker")
        p=Regex("\"USD\"[^}]*\"last\":([\\d.]+)").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
        if(p!=null) return p
        t=httpGet("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
        p=Regex("\"usd\":([\\d.]+)").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
        return p?:0.0
    }
    fun getFeeRates():FeeRates{ val t=httpGet("https://mempool.space/api/v1/fees/recommended"); val s=Regex("\"hourFee\":(\\d+)").find(t)?.groupValues?.get(1)?.toInt()?:5; val n=Regex("\"halfHourFee\":(\\d+)").find(t)?.groupValues?.get(1)?.toInt()?:10; val f=Regex("\"fastestFee\":(\\d+)").find(t)?.groupValues?.get(1)?.toInt()?:20; return FeeRates(s,n,f) }
}