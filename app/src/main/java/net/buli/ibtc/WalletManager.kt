package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL

data class WalletInfo(val id:String, var name:String, val seed:String)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private val prefs = ctx.getSharedPreferences("ibtc_w", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null
    private var wallet: Wallet? = null
    @Volatile private var ready = false

    fun hasWallets() = getAll().isNotEmpty()
    fun getAll(): List<WalletInfo> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return ids.map { id -> WalletInfo(id, prefs.getString("n_$id","Ví")!!, prefs.getString("s_$id","")!!) }
    }
    private fun saveIds(ids:Set<String>){ prefs.edit().putStringSet("ids", ids).apply() }

    fun create(name:String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val seed = DeterministicSeed(System.currentTimeMillis(), "", "", System.currentTimeMillis()/1000)
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val info = WalletInfo(id, name.ifBlank{"Ví mới"}, mnemonic)
        val ids = getAll().map{it.id}.toMutableSet().apply{ add(id) }
        saveIds(ids); prefs.edit().putString("n_$id",info.name).putString("s_$id",mnemonic).apply()
        setActive(id); return info
    }
    fun import(name:String, mnemonic:String): WalletInfo? = try {
        DeterministicSeed(mnemonic.trim().split("\\s+".toRegex()), null, "", 0)
        val id = System.currentTimeMillis().toString()
        val info = WalletInfo(id, name.ifBlank{"Ví import"}, mnemonic.trim())
        val ids = getAll().map{it.id}.toMutableSet().apply{ add(id) }
        saveIds(ids); prefs.edit().putString("n_$id",info.name).putString("s_$id",info.seed).apply()
        setActive(id); info
    }catch(_:Exception){ null }

    fun setActive(id:String){ prefs.edit().putString("active",id).apply() }
    fun getActive() = getAll().find{ it.id == prefs.getString("active",null) }
    fun rename(id:String, newName:String){ prefs.edit().putString("n_$id",newName).apply() }
    fun delete(id:String){
        kit?.stopAsync(); kit=null; wallet=null; ready=false
        val ids = getAll().map{it.id}.toMutableSet().apply{ remove(id) }
        saveIds(ids); prefs.edit().remove("n_$id").remove("s_$id").apply()
        File(ctx.filesDir,"wallets").listFiles()?.filter{ it.name.contains(id) }?.forEach{ it.delete() }
        if(prefs.getString("active",null)==id){ ids.firstOrNull()?.let{ setActive(it) } }
    }
    fun switchTo(id:String){ setActive(id); init() }

    fun isReady() = ready
    fun init(){
        ready=false; kit?.stopAsync()
        val info = getActive() ?: return
        val dir = File(ctx.filesDir,"wallets").apply{ mkdirs() }
        val seed = DeterministicSeed(info.seed.split(" "), null, "", 0)
        kit = object: WalletAppKit(params, dir, "wallet-${info.id}"){ override fun onSetupCompleted(){ wallet = wallet() } }
            .apply{ restoreWalletFromSeed(seed); setAutoSave(true); setBlockingStartup(false); startAsync(); awaitRunning() }
        wallet = kit!!.wallet(); ready=true
    }

    fun getAddress() = if(isReady()) wallet!!.currentReceiveAddress().toString() else ""
    fun getBalance() = if(isReady()) wallet!!.getBalance(Wallet.BalanceType.ESTIMATED).value/1e8 else 0.0
    fun getSeed() = getActive()?.seed ?: ""
    fun sync(){ if(isReady()) try{ kit!!.peerGroup().downloadBlockChain() }catch(_:Exception){} }
    fun price() = try{ val j=URL("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT").readText()
        """"price":"([0-9.]+)"""".toRegex().find(j)?.groupValues?.get(1)?.toDouble()?:0.0 }catch(_:Exception){0.0}
    fun send(to:String, amt:Double, fee:Long) = try{
        val req = SendRequest.to(Address.fromString(params,to), Coin.valueOf((amt*1e8).toLong()))
        req.feePerKb = Coin.valueOf(fee*1000)
        val r = wallet!!.sendCoins(kit!!.peerGroup(), req); r.broadcastComplete.get(); "TXID: ${r.tx.txId}"
    }catch(e:Exception){ "Lỗi: ${e.message}" }
}